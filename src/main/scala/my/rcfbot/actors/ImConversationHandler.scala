package my.rcfbot.actors

import akka.actor.{Actor, ActorLogging}
import my.rcfbot.actions.{ContactAdminsAction, DeactivateUser}
import my.rcfbot.actors.AdminActor.AdminErrorNotification
import my.rcfbot.actors.ImConversationHandler.{SendAdminMessage, StartConversation, Transition}
import my.rcfbot.conversation.{ConversationHelper, ConversationScenario}
import my.rcfbot.domain.RcfSlackMessage
import my.rcfbot.service.{RcfUserComponent, SlackMessagePersistenceComponent, TransitionComponent}
import my.rcfbot.slack.api.SlackApiClient
import my.rcfbot.slack.models.SlackMessage
import my.rcfbot.util.{ConfigHelper, ExecutionContextHelper}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object ImConversationHandler {

  case class StartConversation(scn: ConversationScenario)

  case class SendAdminMessage(s: String)

  case class Transition(im: String, scn: Option[ConversationScenario], step: Option[String])

}

case class MessageLogEntry(whom: String, text: String)

class ImConversationHandler(val im: String,
                            slackClient: SlackApiClient,
                            var currentScenario: Option[ConversationScenario] = None,
                            var currentScenarioStep: Option[String] = None) extends Actor
  with ActorLogging {
  this: ConfigHelper with ExecutionContextHelper with RcfUserComponent
    with SlackMessagePersistenceComponent  with TransitionComponent =>

  val unkz = "unkz"

  implicit val system = context.system

  val logRetention = conf.get[Int]("rcf.conversation.log.retention", 4)
  val messageLog: mutable.Buffer[MessageLogEntry] = ArrayBuffer()

  val adminActor = system.actorSelection("/user/admin")

  val timeout = 5 seconds

  override def receive: Receive = {
    case StartConversation(scn) =>

      log.info(s"Started new scenario [${scn.id}] for channel [$im]")

      currentScenario = Some(scn)
      currentScenarioStep = Some(scn.first)

      val utterance = scn.utterances.get(scn.first)
      val msgId = sendSlackMessage(utterance)


    case Transition(tIm, scenario, scenarioStep) if tIm == im =>

      updateTransitionState(scenario, scenarioStep)

      (currentScenario, currentScenarioStep) match {
        case (Some(scn), Some(step)) =>
          scn.utterances.get(step).foreach(ut => sendSlackMessage(Some(ut)))
        case _ =>
          log.warning(s"Unknown Transition for [$im] to the state $scenario:$scenarioStep")
      }

    case Transition(tIm, scenario, scenarioStep) =>
      log.warning(s"Received a message for another conversation local [$im] - received [$tIm]")

    //Income message !!!
    case msg: SlackMessage if ConversationsHub.StopCommand.equals(msg.text.toLowerCase()) =>
      prepend(MessageLogEntry("user", msg.text))
      sendSlackMessage(Some("Good Bye, we will miss you :smile:, if you would like to become active again - just send me `register`"))
      context.parent ! ContactAdminsAction(msg.text, im, messageLog.toList)
      context.parent ! DeactivateUser(im)

    case msg: SlackMessage =>
      log.debug(s"Received message [${msg.text}] from IM [$im]")

      prepend(MessageLogEntry("user", msg.text))

      messageService.appendMessage(
        RcfSlackMessage(
          id = s"${msg.channel}_${msg.ts}",
          inMsg = Some(msg),
          toIm = Some(im),
          scn = currentScenario.map(_.id),
          scnStep = currentScenarioStep
        )
      ) onComplete {

        case Success(messageDoc) => (currentScenario, currentScenarioStep) match {
          //not a leaf of a scenario
          case (Some(scn), Some(step)) if scn.transitions.contains(step) =>

            val transition = ConversationHelper.transition(scn, step, msg.text)

            transition.map(_.actionBuilders).getOrElse(Seq()).foreach(ab =>
              context.parent ! ab.build(msg, im)
            )

            transition.flatMap(_.next).foreach { next => self ! Transition(im, currentScenario, Some(next)) }

          //a leaf of a scenario
          case _ =>
            log.warning(s"[$im] Message [${msg.text}] is received when there is no in progress scenario " +
              s"and will be redirected to Admins")

            if (!(ConversationsHub.StopCommand.equals(msg.text.toLowerCase) ||
                ConversationsHub.RegisterCommand.equals(msg.text.toLowerCase))) {
              context.parent ! ContactAdminsAction(msg.text, im, messageLog.toList)
            }
        }

        case Failure(err) =>
          log.error(err, "Messages are not persisted in RCF")
          adminActor ! AdminErrorNotification("Messages are not persisted in RCF", err)
      }

    case SendAdminMessage(text) =>
      sendSlackMessage(Some(text))
  }

  def sendSlackMessage(ut: Option[String]): Option[String] = {
    val msgId = ut.flatMap { text =>
      Try(Await.result(slackClient.postChatMessage(im, text), timeout)) match {
        case Success(messageId) =>

          log.info(s"Message [$ut] is sent to IM [$im]")

          prepend(MessageLogEntry("bot", text))

          val mongoMessage = RcfSlackMessage(
            s"${im}_$messageId",
            outText = Some(text),
            toIm = Some(im),
            scn = currentScenario.map(_.id),
            scnStep = currentScenarioStep)

          Try(Await.result(messageService.appendMessage(mongoMessage), timeout)) match {
            case Success(_) => ()
            case Failure(err) =>
              log.error(s"Out Message [$mongoMessage] was not persisted, error: [${err.getMessage}]")
          }

          Some(messageId)

        case Failure(err) =>
          log.error(s"Error occurred during sending to IM [$im] the first " +
            s"message in scenario [${currentScenario.map(_.id).getOrElse(unkz)}], error: [${err.getMessage}]")

          None
      }
    }
    if (msgId.isEmpty) {
      log.warning(s"Nothing was send to IM [$im] " +
        s"scenario-step: [${currentScenario.map(_.id).getOrElse(unkz)}-${currentScenarioStep.getOrElse(unkz)}]")
    }
    msgId
  }

  def updateTransitionState(scenario: Option[ConversationScenario], scenarioStep: Option[String]) = {
    currentScenario = scenario
    currentScenarioStep = scenarioStep

    //intentionaly sync
    Try(Await.result(transitionService.putTransition(im, currentScenario, currentScenarioStep), timeout)) match {
      case Success(_) => ()
      case Failure(err) =>
        log.error(err, s"Scenario data was not updated for IM [$im]")
    }
  }

  def prepend(e: MessageLogEntry): Unit = {
    messageLog.append(e)
    while (messageLog.length >= logRetention) {
      messageLog.remove(0)
    }
  }
}
