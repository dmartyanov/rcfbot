package my.rcfbot.slack.rtm

import my.rcfbot.slack.api._
import my.rcfbot.slack.models._
import my.rcfbot.slack.rtm.SlackRtmConnectionActor._

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor._
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import akka.pattern.ask
import my.rcfbot.boot.Wired.AkkaExecutionContext
import my.rcfbot.util.ExecutionContextHelper

object SlackRtmClient {
  def apply(token: String,
            slackApiBaseUri: Uri = SlackApiClientUtil.defaultSlackApiBaseUri,
            duration: FiniteDuration = 5.seconds,
            lightweight: Boolean = true)(implicit arf: ActorSystem): SlackRtmClient = {
    new SlackRtmClient(token, slackApiBaseUri, duration, lightweight) with AkkaExecutionContext
  }
}

class SlackRtmClient(token: String, slackApiBaseUri: Uri, duration: FiniteDuration,
                     lightweight: Boolean)(
                      implicit arf: ActorSystem
                    ) {
  this: ExecutionContextHelper =>

  private implicit val timeout = new Timeout(duration)

  val apiClient = SlackApiClient(token, slackApiBaseUri)
  private val actor = SlackRtmConnectionActor(apiClient, lightweight, null, "rtm")

  def onEvent(f: (SlackEvent) => Unit): ActorRef = {
    val handler = EventHandlerActor(f)
    addEventListener(handler)
    handler
  }

  def onMessage(f: (SlackMessage) => Unit): ActorRef = {
    val handler = MessageHandlerActor(f)
    addEventListener(handler)
    handler
  }

  def sendMessage(channelId: String, text: String, thread_ts: Option[String] = None): Future[Long] = {
    (actor ? SendMessage(channelId, text, thread_ts)).mapTo[Long]
  }

  def editMessage(channelId: String, ts: String, text: String) {
    actor ! BotEditMessage(channelId, ts, text)
  }

  def indicateTyping(channel: String) {
    actor ! TypingMessage(channel)
  }

  def addEventListener(listener: ActorRef) {
    actor ! AddEventListener(listener)
  }

  def removeEventListener(listener: ActorRef) {
    actor ! RemoveEventListener(listener)
  }

  def getState(): Future[RtmState] = {
    (actor ? StateRequest()).mapTo[StateResponse].map(_.state)
  }

  def close() {
    arf.stop(actor)
  }
}
