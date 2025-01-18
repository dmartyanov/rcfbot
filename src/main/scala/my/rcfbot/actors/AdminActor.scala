package my.rcfbot.actors

import java.time.Instant

import akka.actor.{Actor, ActorLogging}
import my.rcfbot.actions.{ContactAdminsAction, RetrieveLastDeleted}
import my.rcfbot.actors.AdminActor.{AdminErrorNotification, AdminHeartBitNotifications, AdminSessionStart, AdminSystemMessage}
import my.rcfbot.domain.RcfSessionDocument
import my.rcfbot.service.{RcfUserComponent, RcfUserService, UserChangeEventHandlerComponent}
import my.rcfbot.slack.api.SlackApiClient
import my.rcfbot.slack.models.{User, UserChange}
import my.rcfbot.util.{ConfigHelper, DateUtils, ExecutionContextHelper, SessionHelper}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object AdminActor {

  case object AdminHeartBitNotifications

  case class UserIsDeleted(u: User)

  case class AdminSessionStart(sess: RcfSessionDocument, candidates: List[String])

  case class AdminErrorNotification(err: String, t: Throwable)

  case class AdminSystemMessage(text: String)

}

class AdminActor(apiClient: SlackApiClient) extends Actor with ActorLogging with SessionHelper {
  this: ConfigHelper with ExecutionContextHelper with RcfUserComponent with UserChangeEventHandlerComponent =>

  implicit val system = context.system

  val timeout = 5 seconds
  val statsTimeout = 1 minute
  val statsMessageInterval = 24

  val adminChannel = conf.get[String]("rcf.admin.channel")
  val adminPrefix = conf.get[String]("rcf.admin.prefix", "[rcf_admin]")

  val messageId = Try {
    Await.result(apiClient.postChatMessage(adminChannel, adminMsg("RandomCoffee Bot is restarted")), timeout)
  } match {
    case Success(_) =>
    case Failure(err) =>
      log.error(err, "SLACK API is not available")
  }

  context.system.scheduler.schedule(5 minutes, statsMessageInterval hours, self, AdminHeartBitNotifications)

  override def receive: Receive = {

    case m: UserChange if m.user.deleted.contains(true) =>
      log.debug(m.toString)
      //      if(m.user.deleted.getOrElse(false)) {
      //        Try(
      //          Await.result(apiClient.postChatMessage(adminChannel,
      //            adminMsg(s"User  ${m.user.profile.flatMap(_.first_name).getOrElse("")} ${m.user.profile.flatMap(_.last_name).getOrElse("")} is deleted")), timeout)
      //        )
      //      }
      Try(
        Await.result(
          userChangeEventHandler
            .append(m)
            .flatMap { userChangeDoc =>
              rcfUserService.getUser(RcfUserService.fromEmail(m.user.profile.get.email.get))
                .map(Some(_))
                .recover { case err => None }
            } flatMap {
              case Some(user) =>
                rcfUserService
                  .update(user.copy(status = RcfUserService.Status.left))
                  .map(Some(_))
              case None => Future.successful(None)
            }, timeout)
      ) match {
        case Success(Some(user)) =>
          self ! AdminSystemMessage(s"User `${user.id}` (${user.fullname.getOrElse("unknown")}) is marked as left")
          log.debug(s"User change document was successfully inserted for user [${m.user.id}]")
        case Success(None) => log.debug(s"User change document was successfully inserted for user [${m.user.id}]")
        case Failure(err) => log.warning(s"User change document insertion failed for user [${m.user.id}], error: [${err.getMessage}]")
      }

    case m: UserChange =>
      log.debug(m.toString)

    case m: ContactAdminsAction =>
      Try(
        Await.result(apiClient.postChatMessage(adminChannel, buildAdminContactMessage(m)), timeout)
      ) match {
        case Success(msgId) => log.info(s"Message from IM [${m.im}] was successfully delivery to admins")
        case Failure(err) => log.warning(s"Message from IM [${m.im}] was failed in  delivery to admins error: [${err.getMessage}]")
      }

    case AdminSessionStart(s, cs) =>
      Try(
        Await.result(apiClient.postChatMessage(adminChannel,
          adminMsg(s"Session [${s.id}] (${sessToStr(s)}) is started with ${cs.length} candidates")), timeout)
      ) match {
        case Success(msgId) => log.info(s"Message about started session was successfully delivery to admins")
        case Failure(err) => log.warning(s"Message about started session was failed in  delivery to admins error: [${err.getMessage}]")
      }

    case AdminHeartBitNotifications =>
      checkRegisteredUsers()

    case AdminErrorNotification(msg, t) =>
      Try(
        Await.result(apiClient.postChatMessage(adminChannel, adminMsg(s"FATAL ERROR: \n$msg")), timeout)
      ) match {
        case Success(msgId) =>
          log.debug(s"Fatal error message was successfully delivered to admins [$msgId]")
        case Failure(err) =>
          log.error(s"Fatal error message [$msg] was not delivered to admins , error: [${t.getMessage}]")
      }

    case AdminSystemMessage(text) =>
      Try(
        Await.result(apiClient.postChatMessage(adminChannel, adminMsg(s"System message: \n $text")), timeout)
      ) match {
        case Success(msgId) =>
          log.debug(s"System message was successfully delivered to admins [$msgId]")
        case Failure(err) =>
          log.error(s"System message [$text] was not delivered to admins , error: [${err.getMessage}]")
      }

    case RetrieveLastDeleted(id, periodTs, list) =>
      Try(Await.result(
        userChangeEventHandler.lastDeleted(periodTs.getOrElse(DateUtils.OneWeekInMillis)).flatMap {
          deleted =>
            val uniqueUsers = deleted.groupBy(_.user.id).mapValues(_.head).values.toList
            val msg = list match {
              case true =>
                s"List of deleted users: \n" +
                  (uniqueUsers.map { du =>
                    s"${du.user.profile.flatMap(_.first_name).getOrElse("unkz")} " +
                      s"${du.user.profile.flatMap(_.last_name).getOrElse("unkz")}"
                  } mkString "\n")
              case false =>
                s"[${uniqueUsers.length}] users were deleted during provided period"

            }
            apiClient.postChatMessage(adminChannel, adminMsg(s"System message: \n $msg"))
        }
        , timeout)
      ) match {
        case Success(msgId) =>
          log.debug(s"System message was successfully delivered to admins [$msgId]")
        case Failure(err) =>
          log.error(s"System message about deleted users was not delivered to admins , error: [${err.getMessage}]")
      }
  }

  def checkRegisteredUsers() =
    Try(
      Await.result(rcfUserService.users() flatMap { users =>
        val newUsers = users.filter(_.createTs > (Instant.now.toEpochMilli - 1000 * 3600 * statsMessageInterval))
        val stats = newUsers.groupBy(_.channel).mapValues(_.length)
        val msg =
          s"""
             | For last [$statsMessageInterval] hours [${newUsers.length}] were registered, with total users [${users.length}]
             | distribution: `${stats.map(kv => s"${kv._1} -> ${kv._2}").mkString(", ")}`
        """.stripMargin

        apiClient.postChatMessage(adminChannel, adminMsg(msg))
      }, statsTimeout)) match {
      case Success(msgId) => log.info(s"CheckRegistered users message was successfully delivery to admins")
      case Failure(err) => log.warning(s"CheckRegistered users message was failed in  delivery to admins error: [${err.getMessage}]")
    }


  def adminMsg(msg: String) = s"$adminPrefix $msg"

  def buildAdminContactMessage(m: ContactAdminsAction) =
    s"$adminPrefix ${m.text} \n ```" +
      m.log.map(entry => s"[${entry.whom}]: ${entry.text}").mkString("\n") +
      "```"
}
