package service

import message.Message
import zio.json.{DecoderOps, EncoderOps}
import zio.kafka.consumer.Subscription.Topics
import zio.kafka.consumer.{Consumer, ConsumerSettings}
import zio.kafka.serde.Serde
import zio.stream.Stream
import zio.{RLayer, URIO, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success}


trait BrokerConsumer {
  val consume: Stream[Throwable, Message]
}

object BrokerConsumer {
  val consume: URIO[BrokerConsumer, Stream[Throwable, Message]] =
    ZIO.serviceWith[BrokerConsumer](_.consume)
}

final case class KafkaBrokerConsumer(topics: Set[String], consumer: Consumer) extends BrokerConsumer {
  override val consume: Stream[Throwable, Message] = {
    consumer.plainStream[Any, Array[Byte], Array[Byte]](Topics(topics), Serde.byteArray, Serde.byteArray)
      .map(r => {
        val value = new String(r.value, StandardCharsets.UTF_8).replaceAll("\n", "")
        val message: Either[String, Message] = value.fromJson[Message]
        (
          message match {
            case Left(value) => Failure(new Exception(value))
            case Right(value) => Success(value)
          }
        ).get
      })
  }
}

object KafkaBrokerConsumer {
  val live: RLayer[Config, BrokerConsumer] = {
    ZLayer.scoped {
      for {
        cfg <- ZIO.service[Config]
        topics = cfg.topics.toSet
        consumer <- Consumer.make(ConsumerSettings(cfg.servers).withGroupId(cfg.groupId))
      } yield KafkaBrokerConsumer(topics, consumer)
    }
  }
}
