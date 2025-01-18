package my.rcfbot.actors

import akka.actor.{Actor, ActorLogging}
import my.rcfbot.actions._
import my.rcfbot.actors.AdminActor.AdminSystemMessage
import my.rcfbot.actors.ConversationsHub.StartScenarioMsg
import my.rcfbot.actors.ImConversationHandler.Transition
import my.rcfbot.boot.Wired.ConfigurationImpl
import my.rcfbot.conversation.ScenariosRegistry
import my.rcfbot.service._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


class ActionHandler extends Actor with ActorLogging {
  this: ConfigurationImpl with RcfUserComponent with RcfSessionComponent with ParticipationComponent =>

  val timeout = 5 seconds

  val hubActor = context.system.actorSelection("/user/hub")
  val adminActor = context.system.actorSelection("/user/admin")

  implicit val ex = context.dispatcher

  override def receive: Receive = {

    case AddLocation(location, im) =>

      processFutureAction(
        rcfUserService.findUserByIm(im)
          .map(user => user.copy(location = Some(location)))
          .flatMap(user => rcfUserService.update(user)),
        im, "AddLocation")

    case AddMemo(memo, im) =>

      processFutureAction(
        rcfUserService.findUserByIm(im)
          .map(user => user.copy(memo = Some(memo), status = RcfUserService.Status.active))
          .flatMap(user => rcfUserService.update(user)),
        im, "AddMemo")

//    case ActivateUser(im) =>
//
//      processFutureAction(
//        rcfUserService.findUserByIm(im)
//          .map(user => user.copy(status = RcfUserService.Status.active))
//          .flatMap(user => rcfUserService.update(user)),
//        im, "ActivateUser")

    case CheckInitedSessions(im) =>
      processFutureAction(
        (for {
          u <- rcfUserService.findUserByIm(im)
          ss <- sessionManager.getInitedSessions
        } yield (u, ss)) flatMap {
          case (user, session :: tail) =>
            participationService.getParticipation(user.id, session.id) transform {
              case Success(partDoc) => Try {
                log.warning(s"User [${user.id}] is already enrolled to the session [${session.id}] with status [${partDoc.accept}]")
              }
              case Failure(err) => Try {
                log.info(s"User [${user.id}] enrollment for session [${session.id}] is started after registration")
                hubActor ! StartScenarioMsg(user, ScenariosRegistry.buildSessionOnboardingScenario(session))
              }
            }

          case (user, Seq()) =>
            log.info(s"There is 0 inited sessions,  so user [${user.id}] don't need to get through onboarding")
            Future.unit
        }, im, "CheckInitedSessions")

    case DeactivateUser(im) =>

      processFutureAction(
        rcfUserService.findUserByIm(im)
          .map(user => user.copy(status = RcfUserService.Status.inactive))
          .flatMap(user => rcfUserService.update(user)),
        im, "DeactivateUser")


    case RegisterUserWithinSession(sessionId, im, accept) =>

      processFutureAction(
        (for {
          u <- rcfUserService.findUserByIm(im)
          s <- sessionManager.getSession(sessionId)
        } yield (u, s)) flatMap {
          case (us, sess) if sess.status == RcfSessionManager.inited =>
            participationService.userInSession(us.id, sessionId, inTime = true, accept = accept) map {
              case doc if doc.accept =>
                hubActor ! Transition(im, Some(ScenariosRegistry.buildSessionOnboardingScenario(sess)), Some("accept"))
              case doc if !doc.accept =>
                hubActor ! Transition(im, Some(ScenariosRegistry.buildSessionOnboardingScenario(sess)), Some("decline"))
            }
          case (us, sess) =>
            participationService.userInSession(us.id, sessionId, inTime = false, accept) map {
              partDoc => hubActor ! Transition(im, Some(ScenariosRegistry.buildSessionOnboardingScenario(sess)), Some("closed"))
            }
        }, im, "Session IN")


    case ReRegisterUserWithinSession(snId, soId, im, accept) =>
      processFutureAction(
        (for {
          u <- rcfUserService.findUserByIm(im)
          sn <- sessionManager.getSession(snId)
          so <- sessionManager.getSession(soId)
        } yield (u, sn, so)) flatMap {
          case (us, sn, so) if sn.status == RcfSessionManager.inited =>
            participationService.userInSession(us.id, snId, inTime = true, accept = accept, prevSession = Some(soId)) map {
              case doc if doc.accept =>
                hubActor ! Transition(im, Some(ScenariosRegistry.buildSessionReOnboarding(sn, so)), Some("accept_n_met"))
              case doc if !doc.accept =>
                hubActor ! Transition(im, Some(ScenariosRegistry.buildSessionReOnboarding(sn, so)), Some("decline_n_met"))
            }
          case (us, sn, so) =>
            participationService.userInSession(us.id, snId, inTime = false, accept, prevSession = Some(soId)) map {
              partDoc => hubActor ! Transition(im, Some(ScenariosRegistry.buildSessionReOnboarding(sn, so)), Some("closed_n_met"))
            }
        }, im, "Session RE IN")


    case AddToWaitingList(sessionId, im, waitList) =>
      processFutureAction(
        rcfUserService.findUserByIm(im) flatMap { user =>
          participationService.addToWaitingList(user.id, sessionId, waitList) map {
            case Some(doc) =>
              log.info(s"User {${user.id} is added to waiting list for session $sessionId ")
            case None =>
              log.warning(s"User {${user.id} was not added to waiting list for session $sessionId ")
          }
        }, im, "Add to waiting list"
      )

    case AddMeetingConfirmation(sessionId, im, met) =>
      processFutureAction(
        rcfUserService.findUserByIm(im).flatMap { user =>
          participationService.addFeedback(user.id, sessionId, None, met)
        }, im, "Add Session Feedback"
      )

    case AddMeetingFeedback(t, sessionId, im, met) =>
      processFutureAction(
        rcfUserService.findUserByIm(im).flatMap { user =>
          participationService.addFeedback(user.id, sessionId, Some(t), met)
        }, im, "Add Session Feedback"
      )

    case StartActivationScenario(t, im) =>
      hubActor ! Transition(im, Some(ScenariosRegistry.activation), Some("location"))

    case ReminderDeclineAction(t, im) => t.toLowerCase match {
      case "1" =>
        hubActor ! Transition(im, Some(ScenariosRegistry.reminder_activation), Some("remind_me_again"))

      case "2" =>
        hubActor ! Transition(im, Some(ScenariosRegistry.reminder_activation), Some("bye"))
        hubActor ! DeactivateUser(im)

      case smth =>
        hubActor ! Transition(im, Some(ScenariosRegistry.reminder_activation), Some("remind_me_again_wtf"))
        adminActor ! AdminSystemMessage(s"From IM $im received a message $smth")
    }

  }

  def processFutureAction[T](fa: => Future[T], im: String, name: String) = {
    Try(Await.result(fa, timeout)) match {
      case Success(obj) =>
        log.info(s"Action [$name] is completed for IM [$im]")
      case Failure(err) =>
        log.error(err, s"Action [$name] is failed IM: [$im], error [${err.getMessage}]")
    }
  }
}
