package io.km8.core.kafka

import com.dimafeng.testcontainers.KafkaContainer
import zio.*
import zio.clock.Clock
import zio.kafka.consumer.Consumer
import zio.kafka.consumer.Consumer.{AutoOffsetStrategy, OffsetRetrieval}
import zio.kafka.consumer.ConsumerSettings

import io.km8.core.config.{ClusterConfig, ClusterProperties, ClusterSettings}

object itlayers:

  val kafkaContainer: ZLayer[Any, Nothing, KafkaContainer] =
    ZManaged.make {
      effectBlocking {
        val container = new KafkaContainer()
        container.start()
        container
      }.orDie
    }(container => effectBlocking(container.stop()).orDie).toLayer

  def consumerSettings(cg: String): ZManaged[KafkaContainer, Nothing, ConsumerSettings] =
    ZIO
      .service[KafkaContainer]
      .map(c =>
        ConsumerSettings(List(c.bootstrapServers))
          .withGroupId(cg)
          .withOffsetRetrieval(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
      )
      .toManaged_

  def consumerLayer(cgroup: String): ZLayer[KafkaContainer with Clock , Nothing, Consumer] =
    consumerSettings(cgroup).flatMap(Consumer.make(_)).orDie.toLayer

  def clusterConfig(clusterId: String): ZLayer[KafkaContainer, Nothing, ClusterConfig] =
    ZIO
      .service[KafkaContainer]
      .map(kafkaContainer =>
        new ClusterConfig {

          override def readClusters: Task[ClusterProperties] = Task(
            ClusterProperties(clusters =
              List(
                ClusterSettings(
                  id = clusterId,
                  name = kafkaContainer.containerName,
                  kafkaHosts = List(kafkaContainer.bootstrapServers),
                  schemaRegistryUrl = None
                )
              )
            )
          )

          override def writeClusters(cluster: ClusterSettings): Task[Unit] = ???

          override def deleteCluster(clusterId: String): Task[ClusterProperties] = ???
        }
      )
      .toLayer
