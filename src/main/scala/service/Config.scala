package service

import zio.{Layer, ZIO, ZLayer}
import zio.config.magnolia.deriveConfig

case class Config(port: Int, servers: List[String], groupId: String, topics: List[String])

object Config {
  val config: zio.Config[Config] = deriveConfig[Config].nested("kafka")

  val live: Layer[zio.Config.Error, Config] = {
    ZLayer.fromZIO {
      ZIO.config[Config](config)
    }
  }
}
