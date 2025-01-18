package my.rcfbot.api

import akka.actor.{ActorSelection, ActorSystem}
import akka.pattern.ask
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.{logRequest, logResult, pathPrefix}
import com.typesafe.scalalogging.Logger
import my.rcfbot.actors.CoordinatorActor.{CreateSession, OnboardNewUsers, RegisterUser}
import my.rcfbot.domain.PlainResponse
import my.rcfbot.service.RcfUserService
import my.rcfbot.util.{ConfigHelper, ExecutionContextHelper, LoggerHelper}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag

class ApiService(actorSystem: ActorSystem) extends HttpService {
  this: LoggerHelper with ExecutionContextHelper
    with ConfigHelper =>

  implicit def text2plain(str: String): PlainResponse = PlainResponse(str)

  override val log: Logger = logger[ApiService]

  implicit val timeout: akka.util.Timeout = 5 seconds
  lazy val DefaultCharset = "UTF-8"

  val coordinatorActor = actorSystem.actorSelection("/user/coordinator")

  def route = {
    import ApiProtocol._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

    logRequest("rfc-api-request", Logging.InfoLevel) {
      logResult("rfc-api-result", Logging.DebugLevel) {
        pathPrefix("rcf" / "v1") {
          (path("test") & get) {
            onComplete(
              writeResponse[PlainResponse] {
                Future.apply(List("Hello, looks like your crap works"))
              }
            ) {
              complete(_)
            }
          } ~ (path("register") & get) {
            parameters('corpId) { corpId =>
              onComplete(
                writeResponse[PlainResponse] {
                  actorResponse[String, RegisterUser](coordinatorActor, RegisterUser(corpId, RcfUserService.Channel.web))
                    .map(result => List(PlainResponse(result)))
                }
              ) {
                complete(_)
              }
            }
          } ~ (path("newSession") & get) {
            parameters('startIn, 'duration, 'series ?) { case (startIn, dur, series) =>
              onComplete(
                writeResponse[PlainResponse] {
                  actorResponse[String, CreateSession](coordinatorActor,
                    CreateSession(startIn.toDouble, dur.toInt, series.contains("true"))
                  ).map(result => List(PlainResponse(result)))
                }
              ) {
                complete(_)
              }
            }
          }
        }
      }
    }
  }

  def actorResponse[T, U](actor: ActorSelection, msg: U)(implicit tag: ClassTag[T]): Future[T] =
    (actor ? msg).mapTo[T]

}
