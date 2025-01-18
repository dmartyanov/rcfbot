package my.rcfbot.slack.rtm

import my.rcfbot.slack.models._
import akka.actor._

private[rtm] object EventHandlerActor {
  def apply(f: (SlackEvent) => Unit)(implicit arf: ActorRefFactory): ActorRef = {
    arf.actorOf(Props(new EventHandlerActor(f)))
  }
}

private[rtm] class EventHandlerActor(f: (SlackEvent) => Unit) extends Actor with ActorLogging {
  def receive = {
    case e: SlackEvent =>
      try {
        f(e)
      } catch {
        case e: Exception =>
          log.error(e, "Caught exception in event handler")
      }
    case _ =>
  }
}

private[rtm] object MessageHandlerActor {
  def apply(f: (SlackMessage) => Unit)(implicit arf: ActorRefFactory): ActorRef = {
    arf.actorOf(Props(new MessageHandlerActor(f)))
  }
}

private[rtm] class MessageHandlerActor(f: (SlackMessage) => Unit) extends Actor with ActorLogging {
  def receive = {
    case m: SlackMessage =>
      try {
        f(m)
      } catch {
        case e: Exception =>
          log.error(e, "Caught exception in message handler")
      }
    case _ =>
  }
}
