package scala.school

import zio._
import zhttp.service.{ EventLoopGroup, Server, ServerChannelFactory }
import zhttp.service.server.ServerChannelFactory

import scala.school.Layers._
import scala.school.api.ApiZioHttp
import scala.school.application.ApplicationService
import scala.school.config.AppConfig
import scala.school.infrastructure.flyway.FlywayProvider

object BootZioHttp extends ZIOAppDefault {

  import http._

  override def run: Task[Nothing] = program.provide(
    Scope.default,
    AppConfig.Api.live,
    Repository.itemRepository,
    Repository.healthCheckService,
    ApplicationService.live,
    ApiZioHttp.live,
    EventLoopGroup.auto(0),
    ServerChannelFactory.auto,
    HttpService.live,
    AppConfig.RawConfig.live,
    FlywayProvider.live,
    Logger.live
  )

  type ProgramEnv = Scope with FlywayProvider with EventLoopGroup with ServerChannelFactory with HttpService

  private val program: RIO[ProgramEnv, Nothing] = {

    val startHttpServer: RIO[Scope with EventLoopGroup with ServerChannelFactory with HttpService, Server.Start] =
      HttpService.start.tap(start => Console.printLine(s"Server online on port: ${start.port}."))

    val migrateDbSchema: RIO[FlywayProvider, Unit] =
      FlywayProvider.flyway
        .flatMap(_.migrate)
        .retry(Schedule.exponential(200.millis))
        .flatMap(res => Console.printLine(s"Flyway migration completed with: $res"))

    startHttpServer *>
    migrateDbSchema *>
    ZIO.never
  }

  object http {

    trait HttpService {
      def start: ZIO[Scope with EventLoopGroup with ServerChannelFactory, Throwable, Server.Start]
    }

    object HttpService {

      val live: ZLayer[ApiZioHttp with AppConfig.ApiConfig, Nothing, HttpService] = ZLayer {
        for {
          api <- ZIO.service[ApiZioHttp]
          app  = Server.app(api.apps)

          cfg <- ZIO.service[AppConfig.ApiConfig]
          host = cfg.host
          port = cfg.port
          bind = Server.bind(host, port)

          server = bind ++ app
        } yield new HttpService {
          override def start: ZIO[Scope with EventLoopGroup with ServerChannelFactory, Throwable, Server.Start] =
            server.make
        }
      }

      def start: ZIO[Scope with EventLoopGroup with ServerChannelFactory with HttpService, Throwable, Server.Start] =
        ZIO.serviceWithZIO[HttpService](_.start)
    }

  }
}
