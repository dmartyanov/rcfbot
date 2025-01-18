package my.rcfbot.actors

import akka.pattern.pipe
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import my.rcfbot.actors.AdminActor.{AdminErrorNotification, AdminSessionStart}
import my.rcfbot.actors.ChannelManagementActor.SendNotification
import my.rcfbot.actors.ConversationsHub.StartScenarioMsg
import my.rcfbot.actors.CoordinatorActor._
import my.rcfbot.conversation.ScenariosRegistry
import my.rcfbot.domain.{RcfSessionDocument, RcfUser, UserParticipationDoc}
import my.rcfbot.matching.{MatchingComponent, MatchingPair, SinglePair}
import my.rcfbot.service._
import my.rcfbot.slack.api.SlackApiClient
import my.rcfbot.slack.models.MemberJoined
import my.rcfbot.slack.rtm.SlackRtmConnectionActor.SendPing
import my.rcfbot.util.{ConfigHelper, SessionHelper}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object CoordinatorActor {

  val EmptyPartner = "!single!"

  case class RegisterUser(
                           corpId: String,
                           channel: String,
                           force: Boolean = false,
                           slackId: Option[String] = None,
                           fullname: Option[String] = None,
                           tz_offset: Option[Int] = None,
                           sender: Option[ActorRef] = None
                         )

  case class RegisterUserFromDM(slackId: String)

  case class OnboardNewUsers(sendback: Boolean = true)

  case class OnboardNewUser(user: RcfUser)

  case class OnboardSleepyUser(user: RcfUser)

  case class BroadcastToUser(user: RcfUser, text: String)

  case class CreateSession(startIn: Double, duration: Int, series: Boolean)

  case class SetPairForSession(u1: String, u2: String, s: RcfSessionDocument)

  case object SendPing

}

abstract class CoordinatorActor(apiClient: SlackApiClient, live: Boolean) extends Actor with ActorLogging
  with SessionHelper with UserOutreachHelper {
  this: RcfSessionComponent with RcfUserComponent with ParticipationComponent
    with MatchingComponent with ConfigHelper  =>

  implicit val ec = context.dispatcher
  implicit val system = context.system

  implicit def int2minutes(n: Int): FiniteDuration = n minutes

  val domain = conf.get[String]("rcf.domain", "paypal.com")
//  val live = conf.get[Boolean]("rcf.live", false)

  val to = 5 seconds

  val initialScenarioBaseDelayInMinutes = 10

  val testUserId = "dmartyanov"

  val rcfChannel = conf.get[String]("rcf.register.channels", "CHA291U21")
  val pingTimeout = conf.get[Int]("rcf.coordination.ping.timeout", 20)

  val hubActor = context.system.actorSelection("/user/hub")
  val adminActor = system.actorSelection("/user/admin")

  context.system.scheduler.schedule(pingTimeout, pingTimeout, self, SendPing)
  context.system.scheduler.schedule((pingTimeout * 0.5).toInt, pingTimeout, self, OnboardNewUsers(false))

  var channelActor = system.actorOf(Props(new ChannelManagementActor(rcfChannel, apiClient)))
  context.watch(channelActor)

  override def receive: Receive = {

    case RegisterUser(corpId, channel, force, Some(slackId), Some(fullname), Some(tz_offset), originSender) =>
      log.info(s"Registration request for $corpId")
      rcfUserService.register(corpId, channel, force, Some(slackId), Some(fullname), Some(tz_offset)) onComplete {
        case Success(user) =>
          val msg = s"New User [$user] is registered successfully"
          log.info(msg)
          originSender.foreach(a => a ! msg)
          self ! OnboardNewUser(user)

        case Failure(err) =>
          val msg = s"User [$corpId] was not registered, error: [${err.getMessage}]"
          log.error(err, msg)
          originSender.foreach(a => a ! msg)
      }

    case RegisterUser(corpId, channel, _, _, _, _, _) =>
      log.info(s"Registration pre-request from $corpId from web")
      val originSender = sender
      apiClient.lookupUserByEmail(RcfUserService.email(corpId, domain)) map { slackUser =>
        RegisterUser(corpId, channel, false, Some(slackUser.id), Some(RcfUserService.fullname(slackUser)), slackUser.tz_offset, Some(originSender))
      } pipeTo self

    case RegisterUserFromDM(slackId) =>
      log.info(s"Registration through DM is handled for slackId $slackId")
      apiClient.getUserInfo(slackId) map { slackUser =>
        log.info(s"Registration for $slackId,  extracted user is $slackUser")
        val corpId = slackUser.profile.flatMap(_.email).map(RcfUserService.fromEmail).get
        RegisterUser(
          corpId, RcfUserService.Channel.dm, true, Some(slackUser.id),
          Some(RcfUserService.fullname(slackUser)), slackUser.tz_offset, None)
      } pipeTo self

    case OnboardNewUsers(sb) =>
      rcfUserService.getNewUsers onComplete {

        case Success(users) =>
          users foreach { u => self ! OnboardNewUser(u) }

          val msg = s"Activation scenario is triggered for [${users.length}] users"
          log.debug(msg)
          if (sb) {
            sender ! msg
          }

        case Failure(err) =>
          val msg = s"Activation scenario is not triggered due to error [${err.getMessage}]"
          log.warning(msg)
          if (sb) {
            sender ! msg
          }
      }

    case OnboardNewUser(user) =>
      rcfUserService.update(user.copy(status = RcfUserService.Status.inited)) onComplete {
        case Success(userDoc) =>
          hubActor ! StartScenarioMsg(userDoc, ScenariosRegistry.activation)

        case Failure(err) =>
          val msg = s"Activation scenario is not triggered for user $user due to error [${err.getMessage}]"
          log.warning(msg)
      }

    case OnboardSleepyUser(user) =>
      execDelayed(user, initialScenarioBaseDelayInMinutes)(runnable(
        hubActor ! StartScenarioMsg(user, ScenariosRegistry.reminder_activation))
      )

    case CreateSession(startIn, duration, series) =>
      val originSender = sender
      sessionManager
        .createNewSession(startIn, duration, series)
        .flatMap { session =>
          rcfUserService.getRegisteredUsers().map { users => (session, users) }
        } map {
        case (session, users) =>
          channelActor ! SendNotification(buildSessionAnnouncementNotification(session))
          users.foreach { user =>
            execDelayed(user, initialScenarioBaseDelayInMinutes)(runnable(
                hubActor ! StartScenarioMsg(user, ScenariosRegistry.buildSessionOnboardingScenario(session)))
            )
          }
          val msg = s"Session onboarding is started for ${users.length} Users"
          log.info(msg)
          msg

      } onComplete {
        case Success(message) =>
          originSender ! message
        case Failure(err) =>
          val msg = s"Session onboarding is failed with error [${err.getMessage}]"
          log.error(msg, err)
          originSender ! msg
      }

    case m: MemberJoined if rcfChannel.equals(m.channel) =>
      log.info(s"New user [${m.user}] has joined RandomCoffee chanel [${m.channel}]")
      apiClient.getUserInfo(m.user) onComplete {
        case Success(user) =>
          user.profile.flatMap(_.email).flatMap(RcfUserService.parseEmail).map(_._1) match {

            case Some(corpId) =>
              self ! RegisterUser(corpId, RcfUserService.Channel.channel, true,
                Some(user.id), Some(RcfUserService.fullname(user)))

            case None =>
              log.error(s"Corp Id was not extracted from User record: [$user]")

          }

        case Failure(err) =>
          log.error(err, s"User with slackId [${m.user}] was not extracted")

      }

    case SendPing =>
      log.debug("Scheduler is Pinged")

      if (live) {
        checkSessionToGetStarted()

        checkSessionToPassHalf()
      }

      checkSessionToClose()

    case SetPairForSession(u1, u2, s) =>
      (
        for {
          user1 <- rcfUserService.getUser(u1)
          user2 <- rcfUserService.getUser(u2)
        } yield (user1, user2)
        ) flatMap { case (user1, user2) =>
        for {
          p1 <- informPartners(user1, user2, s)
          p2 <- informPartners(user2, user1, s)
        } yield (p1, p2)
      } onComplete {
        case Success((part1, part2)) =>
          log.info(s"${part1.userId} and ${part2.userId} are successfully informed")
        case Failure(err) =>
          val msg = s"$u1 and $u2 are not informed about the match during session ${s.id}"
          adminActor ! AdminErrorNotification(msg, err)
          log.error(err, msg)
      }

    case BroadcastToUser(user, text) =>
      execDelayed(user, initialScenarioBaseDelayInMinutes)(runnable(
        hubActor ! StartScenarioMsg(user, ScenariosRegistry.customMessageScenario(text, Map("scenario" -> "broadcast"))))
      )

    case Terminated(actor) =>
      if (actor == channelActor) {
        var channelActor = system.actorOf(Props(new ChannelManagementActor(rcfChannel, apiClient)))
        context.watch(channelActor)
        log.info(s"ChannelActor is restarted")
      }
  }

  def checkSessionToClose(): Unit = {
    sessionManager.sessionsToClose() flatMap { sessions =>
      sessions.headOption map processSessionClosure
    } onComplete {
      case Success(Some(s)) =>
        log.info(s"${s.id} session is closed")

      case Success(None) => ()

      case Failure(err) =>
        log.error(s"Sessions were not closed, error: [${err.getMessage}]")
    }
  }

  def checkSessionToPassHalf(): Unit = {
    sessionManager.sessionsPassedOverHalf() flatMap { sessions =>
      sessions.headOption map overHalfNotification
    } onComplete {
      case Success(Some(s)) =>
        log.info(s"${s.id} session is passed over half")

      case Success(None) => ()

      case Failure(err) =>
        log.error(s"Sessions were not extracted, error: [${err.getMessage}]")
    }
  }

  def checkSessionToGetStarted(): Unit = {
    sessionManager.sessionsToRun flatMap { sessions =>
      sessions.headOption map startSession
    } onComplete {
      case Success(Some(s)) =>
        log.info(s"${s.id} sessions is started")

      case Success(None) => ()

      case Failure(err) =>
        log.error(s"Sessions were not started, error: [${err.getMessage}]")
    }
  }

  def processSessionClosure(s: RcfSessionDocument): Future[RcfSessionDocument] =
    sessionManager.updateSession(s.copy(status = RcfSessionManager.closed)) flatMap {
      case session if session.series => processSessionReCreation(session)
      case session => processSessionJustClosure(session)
    }

  def processSessionReCreation(s: RcfSessionDocument): Future[RcfSessionDocument] = {
    log.info(s"Started session recreation with base session ${s.id} ")
    sessionManager
      .updateSession(s.copy(status = RcfSessionManager.closed))
      .flatMap { so =>
        sessionManager.createRawSession(so.toStartTs - so.createTs, so.duration, so.series)
          .flatMap { newSession =>
            if (live) {
              channelActor ! SendNotification(buildSessionAnnouncementNotification(newSession))
            }
            processSessionReEnrollment(newSession, so) map { _ => newSession }
          }
    }
  }

  def processSessionReEnrollment(sn: RcfSessionDocument, so: RcfSessionDocument): Future[String] =
    (for {
      allUsers <- rcfUserService.getRegisteredUsers()
      soUsers <- participationService.usersForSession(so.id)
    } yield (allUsers, soUsers)) map { case (allUsers, soUsers) =>
      val soParticipantsMap = soUsers
        .filter(upd => upd.partnerId.isDefined && !upd.partnerId.contains(CoordinatorActor.EmptyPartner))
        .groupBy(_.userId)
        .mapValues(_.head)

      allUsers foreach { user =>
        if (live || testUserId.equals(user.id)) {
          soParticipantsMap.get(user.id) match {
            case Some(partDoc) =>
              log.info(s"User [${user.id}] is invited to session [${sn.id}] as active user from session [${so.id}]")
              execDelayed(user, initialScenarioBaseDelayInMinutes)(runnable(
                  hubActor ! StartScenarioMsg(user, ScenariosRegistry.buildSessionReOnboarding(sn, so)))
              )

            case None =>
              log.info(s"User [${user.id}] is invited to session [${sn.id}] as new user")
              execDelayed(user, initialScenarioBaseDelayInMinutes)(runnable(
                  hubActor ! StartScenarioMsg(user, ScenariosRegistry.buildSessionOnboardingScenario(sn)))
              )
          }
        }
      }
      val msg = s"Session onboarding is started for recreated session (${sessToStr(sn)}) with [${allUsers.length}] total users " +
        s" and prolongations for [${soParticipantsMap.size}]"
      log.info(msg)
      msg
    }

  def processSessionJustClosure(s: RcfSessionDocument): Future[RcfSessionDocument] = {
    log.info(s"Started session Closure with base session ${s.id} ")
    sessionManager.updateSession(s.copy(status = RcfSessionManager.closed)) flatMap { sess =>
      participationService.usersForSession(sess.id) map { list =>
        list
          .filter(upd => upd.partnerId.isDefined && !upd.partnerId.contains(CoordinatorActor.EmptyPartner))
          .foreach(participation => closeSessionForUser(participation.userId, s))
      } map { _ => sess }
    }
  }

  def overHalfNotification(s: RcfSessionDocument): Future[RcfSessionDocument] =
    sessionManager.updateSession(s.copy(status = RcfSessionManager.ending)) flatMap { sess =>
      participationService.usersForSession(sess.id) map { list =>
        list
          .filter(upd => upd.partnerId.isDefined && !upd.partnerId.contains(CoordinatorActor.EmptyPartner))
          .foreach(user => notifyHalfSessionUser(user.userId, s))
      } map { _ => sess }
    }

  def startSession(session: RcfSessionDocument) =
    sessionManager
      .updateSession(session.copy(status = RcfSessionManager.started))
      .flatMap { s =>
        participationService.usersForSession(s.id) map { list =>
          val candidates = list.filter(_.accept).map(_.userId)

          adminActor ! AdminSessionStart(s, candidates)

          log.info(s"For session ${s.id} there are ${candidates.length} participants: [${candidates.mkString(", ")}]")

          matchingService.pair(candidates) foreach {
            case MatchingPair(p1, p2) => self ! SetPairForSession(p1, p2, s)

            case SinglePair(single) => sendSingleNotification(single, s, candidates.length)
          }
          s
        }
      }

  def closeSessionForUser(userId: String, s: RcfSessionDocument): Unit = Try {
    Await.result(rcfUserService.getUser(userId) map { user =>
      if (live || testUserId.equals(user.id)) {
        execDelayed(user, initialScenarioBaseDelayInMinutes)(runnable(
            hubActor ! StartScenarioMsg(user, ScenariosRegistry.buildSessionClosureScenario(s)))
        )
      }
    } map { _ => true }, to)
  } match {
    case Success(_) =>

    case Failure(err) =>
      log.error(s"Half session notification was not sent to [$userId], session: ${s.id}")
  }

  def notifyHalfSessionUser(userId: String, s: RcfSessionDocument): Unit = Try {
    Await.result(rcfUserService.getUser(userId) map { user =>
      if (live || testUserId.equals(user.id)) {
        //      hubActor ! StartScenarioMsg(user, ScenariosRegistry.buildHalfSessionNotification(s))
      }
    } map { _ => true }, to)
  } match {
    case Success(_) =>

    case Failure(err) =>
      log.error(s"Half session notification was not sent to [$userId], session: ${s.id}")
  }

  def informPartners(host: RcfUser, guest: RcfUser, s: RcfSessionDocument): Future[UserParticipationDoc] =
    participationService.addPartner(host.id, s.id, guest.id) map { participation =>
      execDelayed(host, initialScenarioBaseDelayInMinutes)(runnable(
          hubActor ! StartScenarioMsg(host, ScenariosRegistry.buildInformPartnersScenario(s, guest)))
      )
      participation
    }

  def sendSingleNotification(userId: String, s: RcfSessionDocument, n: Int): Unit = {
    Await.result(
      rcfUserService.getUser(userId)
        .flatMap { user => participationService.addPartner(userId, s.id, EmptyPartner).map { p => (user, p) } }
        .map { case (user, pa) =>
          execDelayed(user, initialScenarioBaseDelayInMinutes)(runnable(
              hubActor ! StartScenarioMsg(user, ScenariosRegistry.buildSinglePersonScenario(s, n)))
          )
        }, to)
  }

  def buildSessionAnnouncementNotification(s: RcfSessionDocument) =
    s"""
       |Hi Everyone, RandomCoffee sessions keep going! :celebrate:
       |We are happy to announce that the next session (${sessToStr(s)}) will be started soon.
     """.stripMargin

}
