package scala.school.api

import zio._
import zio.json._
import zhttp.http._

import scala.school.domain._
import scala.school.application.ApplicationService
import scala.school.api.healthcheck.HealthCheckService

trait ApiZioHttp {
  val apps: UHttpApp
}

object ApiZioHttp {

  implicit class RichRequest(val request: Request) extends AnyVal {
    def bodyAs[T](implicit ev: JsonDecoder[T]): IO[String, T] =
      for {
        body <- request.bodyAsString.orDie
        t    <- ZIO.succeed(body.fromJson[T]).absolve
      } yield t

  }

  implicit class RichDomain[T](val data: T) extends AnyVal {

    def toResponseZIO(implicit ev: JsonEncoder[T]): UIO[Response] = toResponseZIO(Status.Ok)

    def toResponseZIO(status: Status)(implicit ev: JsonEncoder[T]): UIO[Response] = ZIO.succeed {
      Response.json(data.toJson).setStatus(status)
    }

    def asNoContentResponseZIO: UIO[Response] = ZIO.succeed(Response.status(Status.NoContent))
  }

  def extractLong(str: String): IO[ValidationError, Long] = ZIO
    .attempt(str.toLong)
    .refineToOrDie[NumberFormatException]
    .mapError(err => ValidationError(err.getMessage))

  def handleError(err: DomainError): UIO[Response] = err match {
    case NotFoundError(None)      => ZIO.succeed(Response.status(Status.NotFound))
    case NotFoundError(Some(msg)) => msg.toResponseZIO(Status.NotFound)
    case ValidationError(msg)     => msg.toResponseZIO(Status.BadRequest)
    case RepositoryError(cause)   => cause.getMessage.toResponseZIO(Status.InternalServerError)
  }

  class HealthCheckApp(healthCheck: HealthCheckService) {

    val app: UHttpApp = Http.collectZIO[Request] {

      case Method.HEAD -> !! / "healthcheck" =>
        ZIO.succeed(Response.status(Status.NoContent))

      case Method.GET -> !! / "healthcheck" =>
        healthCheck.healthCheck.map { dbStatus =>
          if (dbStatus.status) Response.ok
          else Response.status(Status.InternalServerError)
        }
    }
  }

  class ItemApp(application: ApplicationService) extends JsonSupport {

    val app: UHttpApp = Http.collectZIO[Request] {

      case Method.GET -> !! / "items" =>
        application.getItems
          .foldZIO(handleError, _.toResponseZIO)

      case request @ Method.POST -> !! / "items" =>
        val effect: IO[DomainError, Item] = for {
          r  <- request.bodyAs[CreateItemRequest].mapError(ValidationError)
          id <- application.addItem(r.name, r.price)
        } yield Item(id, name = r.name, price = r.price)

        effect.foldZIO(handleError, _.toResponseZIO(Status.Created))

      case Method.GET -> !! / "items" / itemId =>
        val effect: IO[DomainError, Item] = for {
          id        <- extractLong(itemId)
          maybeItem <- application.getItem(ItemId(id))
          ans       <- maybeItem
                         .map((lll: Item) => ZIO.succeed(lll))
                         .getOrElse(ZIO.fail(NotFoundError(s"Item $id not found")))
        } yield ans

        effect.foldZIO(handleError, _.toResponseZIO)

      case Method.DELETE -> !! / "items" / itemId =>
        val effect: IO[DomainError, Unit] = for {
          id     <- extractLong(itemId)
          amount <- application.deleteItem(ItemId(id))
          _      <- if (amount == 0) ZIO.fail(NotFoundError.empty)
                    else ZIO.unit
        } yield ()

        effect.foldZIO(handleError, _.asNoContentResponseZIO)

      case request @ Method.PATCH -> !! / "items" / itemId =>
        val effect: IO[DomainError, String] = for {
          id           <- extractLong(itemId)
          r            <- request.bodyAs[PartialUpdateItemRequest].mapError(ValidationError)
          maybeUpdated <- application.partialUpdateItem(ItemId(id), r.name, r.price)
          ans          <- maybeUpdated
                            .map(_ => ZIO.succeed(s"Item $id was updated"))
                            .getOrElse(ZIO.fail(NotFoundError(s"Item $id not found")))
        } yield ans

        effect.foldZIO(handleError, _.toResponseZIO)

      case request @ Method.PUT -> !! / "items" / itemId =>
        val effect: IO[DomainError, String] = for {
          id           <- extractLong(itemId)
          r            <- request.bodyAs[UpdateItemRequest].mapError(ValidationError)
          maybeUpdated <- application.updateItem(ItemId(id), r.name, r.price)
          ans          <- maybeUpdated
                            .map(_ => ZIO.succeed(s"Item $id was updated"))
                            .getOrElse(ZIO.fail(NotFoundError(s"Item $id not found")))
        } yield ans

        effect.foldZIO(handleError, _.toResponseZIO)
    }
  }

  // accessors
  val apps: URIO[ApiZioHttp, UHttpApp] =
    ZIO.environmentWithZIO[ApiZioHttp](api => ZIO.succeed(api.get.apps))

  val live: URLayer[HealthCheckService with ApplicationService, ApiZioHttp] = ZLayer {
    for {
      healthCheck <- ZIO.service[HealthCheckService]
      application <- ZIO.service[ApplicationService]
    } yield new ApiZioHttp {
      override val apps: UHttpApp =
        new HealthCheckApp(healthCheck).app
          .defaultWith(new ItemApp(application).app)
    }

  }

}
