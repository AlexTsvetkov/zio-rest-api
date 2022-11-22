package scala.school

import com.typesafe.config.Config
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile
import zio._
import zio.logging.backend.SLF4J

import scala.school.infrastructure.slick.{SlickHealthCheckService, SlickItemRepository}
import scala.school.api.healthcheck.HealthCheckService
import scala.school.domain.ItemRepository

object Layers {

  object Logger {

    val live: ULayer[Unit] = ZLayer.make[Unit](
      Runtime.removeDefaultLoggers,
      SLF4J.slf4j
    )
  }

  object Repository {

    val jdbcProfileLayer: ULayer[JdbcProfile] = ZLayer.succeed[JdbcProfile](PostgresProfile)

    val dbConfigLayer: URLayer[Config, Config] = ZLayer {
      for {
        config <- ZIO.service[Config]
        dbConfig = config.getConfig("db")
      } yield dbConfig
    }

    val slickLayer: URLayer[Config, DatabaseProvider] =
      (jdbcProfileLayer ++ dbConfigLayer) >>> DatabaseProvider.live.orDie

    val itemRepository: RLayer[Config, ItemRepository] = (slickLayer >>> SlickItemRepository.live).orDie
    val healthCheckService: RLayer[Config, HealthCheckService] = (slickLayer >>> SlickHealthCheckService.live).orDie
  }
}
