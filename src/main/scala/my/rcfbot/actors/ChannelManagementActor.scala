package my.rcfbot.actors

import akka.actor.{Actor, ActorLogging}
import my.rcfbot.actors.AdminActor.AdminErrorNotification
import my.rcfbot.actors.ChannelManagementActor.SendNotification
import my.rcfbot.slack.api.SlackApiClient

import scala.concurrent.Await
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.language.postfixOps

object ChannelManagementActor {

  case class SendNotification(text: String)
}

class ChannelManagementActor(channelId: String, apiClient: SlackApiClient) extends Actor with ActorLogging {
  implicit val system = context.system

  val timeout = 5 seconds

  val adminActor = system.actorSelection("/user/admin")

  override def receive: Receive = {
    case SendNotification(text) =>
      Try(
        Await.result(apiClient.postChatMessage(channelId, text), timeout)
      ) match {
        case Success(msgId) => log.debug(s"Notification to channel [$channelId] is successfully send")
        case Failure(err) =>
          val msg = s"Notification to channel [$channelId] was not send because of error [${err.getMessage}]"
          adminActor ! AdminErrorNotification(msg, err)
          log.error(err, msg)
      }
  }
}
