package my.rcfbot.slack.rtm

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.http.scaladsl.model.ws.TextMessage
import my.rcfbot.slack.api.SlackApiClient
import my.rcfbot.slack.models.{MemberJoined, MessageReplied, SlackEvent, SlackMessage, UserChange}
import my.rcfbot.slack.rtm.SlackRtmConnectionActor.SendPing
import my.rcfbot.slack.rtm.WebSocketClientActor.{SendWSMessage, WebSocketClientConnectFailed, WebSocketClientConnected, WebSocketClientDisconnected}
import play.api.libs.json.Json

import scala.concurrent.Await
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

object SlackRtmConnectionActor {

  implicit val sendMessageFmt = Json.format[MessageSend]
  implicit val botEditMessageFmt = Json.format[BotEditMessage]
  implicit val typingMessageFmt = Json.format[MessageTyping]
  implicit val pingMessageFmt = Json.format[Ping]

  case class AddEventListener(listener: ActorRef)
  case class RemoveEventListener(listener: ActorRef)
  case class SendMessage(channelId: String, text: String, ts_thread: Option[String] = None)
  case class BotEditMessage(channelId: String,
                            ts: String,
                            text: String,
                            as_user: Boolean = true,
                            `type`: String = "chat.update")
  case class TypingMessage(channelId: String)
  case class StateRequest()
  case class StateResponse(state: RtmState)
  case object ReconnectWebSocket
  case class SendPing()

  def apply(apiClient: SlackApiClient, lightweight: Boolean, chatHub: ActorRef,name: String)(implicit arf: ActorRefFactory): ActorRef = {
    arf.actorOf(Props(new SlackRtmConnectionActor(apiClient, lightweight, chatHub)), name)
  }
}

import SlackRtmConnectionActor._

class SlackRtmConnectionActor(apiClient: SlackApiClient, lightweight: Boolean, chatHub: ActorRef)
  extends Actor
    with ActorLogging {

  implicit val ec = context.dispatcher
  implicit val system = context.system
//  val listeners = MSet[ActorRef]()
  val idCounter = new AtomicLong(1L)

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: Exception => SupervisorStrategy.Stop
    }

  var connectFailures = 0
  var webSocketClient: Option[ActorRef] = None
  var state: Option[RtmState] = None

  context.system.scheduler.schedule(1.minute, 1.minute, self, SendPing)

  val adminActor = system.actorSelection("/user/admin")
  val coordinatorActor = system.actorSelection("/user/coordinator")

  def receive = {
    case message: TextMessage =>
      try {
        val payload = message.getStrictText
        val payloadJson = Json.parse(payload)
        if ((payloadJson \ "type").asOpt[String].isDefined || (payloadJson \ "reply_to").asOpt[Long].isDefined) {
          Try(payloadJson.as[SlackEvent]) match {

            case Success(event) if event.isInstanceOf[SlackMessage] =>
              chatHub ! event.asInstanceOf[SlackMessage]

            case Success(event) if event.isInstanceOf[MessageReplied] =>
              chatHub ! event.asInstanceOf[MessageReplied]

            case Success(event) if event.isInstanceOf[UserChange] =>
              adminActor ! event.asInstanceOf[UserChange]

            case Success(event) if event.isInstanceOf[MemberJoined] =>
              coordinatorActor ! event.asInstanceOf[MemberJoined]

            case Success(event) =>
//              state.update(event)
//              listeners.foreach(_ ! event)

            case Failure(e) => log.error(e, s"[SlackRtmClient] Error reading event: $payload")
          }
        } else {
          log.warning(s"invalid slack event : $payload")
        }
      } catch {
        case e: Exception => log.error(e, "[SlackRtmClient] Error parsing text message")
      }

    case TypingMessage(channelId) =>
      val nextId = idCounter.getAndIncrement
      val payload = Json.stringify(Json.toJson(MessageTyping(nextId, channelId)))
      webSocketClient.get ! SendWSMessage(TextMessage(payload))

    case SendMessage(channelId, text, ts_thread) =>
      val nextId = idCounter.getAndIncrement
      val payload = Json.stringify(Json.toJson(MessageSend(nextId, channelId, text, ts_thread)))
      webSocketClient.get ! SendWSMessage(TextMessage(payload))
      sender ! nextId

    case bm: BotEditMessage =>
      val payload = Json.stringify(Json.toJson(bm))
      webSocketClient.get ! SendWSMessage(TextMessage(payload))

    case StateRequest() =>
      sender ! StateResponse(state.get)

    case AddEventListener(listener) =>
//      listeners += listener
      context.watch(listener)

    case RemoveEventListener(listener) =>
//      listeners -= listener

    case WebSocketClientConnected =>
      log.info("[SlackRtmConnectionActor] WebSocket Client successfully connected")
      connectFailures = 0

    case WebSocketClientDisconnected =>
      handleWebSocketDisconnect(sender)

    case WebSocketClientConnectFailed =>
      val delay = Math.pow(2.0, connectFailures.toDouble).toInt
      log.info("[SlackRtmConnectionActor] WebSocket Client failed to connect, retrying in {} seconds", delay)
      connectFailures += 1
      webSocketClient = None
      context.system.scheduler.scheduleOnce(delay.seconds, self, ReconnectWebSocket)

    case ReconnectWebSocket =>
      connectWebSocket()

    case Terminated(actor) =>
//      listeners -= actor
      handleWebSocketDisconnect(actor)

    case SendPing =>
      val nextId = idCounter.getAndIncrement
      val payload = Json.stringify(Json.toJson(Ping(nextId)))
      webSocketClient.get ! SendWSMessage(TextMessage(payload))

    case _ =>
      log.warning("doesn't match any case, skip")
  }

  def connectWebSocket() {
    log.info("[SlackRtmConnectionActor] Starting web socket client")
    try {
      val initialRtmState = Await.result(apiClient.startRealTimeMessageSession(lightweight), 5 seconds)
      state = Option(RtmState(initialRtmState))
      webSocketClient = Some(WebSocketClientActor(state.get.url)(context))
      webSocketClient.foreach(context.watch)
    } catch {
      case e: Exception =>
        log.error(e, "Caught exception trying to connect websocket")
        self ! WebSocketClientConnectFailed
    }
  }

  def handleWebSocketDisconnect(actor: ActorRef) {
    if (webSocketClient.isDefined && webSocketClient.get == actor) {
      log.info("[SlackRtmConnectionActor] WebSocket Client disconnected, reconnecting")
      webSocketClient.foreach(context.stop)
      connectWebSocket()
    }
  }

  override def preStart() {
    connectWebSocket()
  }

  override def postStop() {
    webSocketClient.foreach(context.stop)
  }
}

private[rtm] case class MessageSend(id: Long,
                                    channel: String,
                                    text: String,
                                    thread_ts: Option[String] = None,
                                    `type`: String = "message")
private[rtm] case class MessageTyping(id: Long, channel: String, `type`: String = "typing")
private[rtm] case class Ping(id: Long, `type`: String = "ping")
