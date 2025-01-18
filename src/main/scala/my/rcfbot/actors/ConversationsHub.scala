package my.rcfbot.actors

import java.time.Instant

import akka.pattern.pipe
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import my.rcfbot.actions.{CommandRegistry, ContactAdminsAction, RcfAction}
import my.rcfbot.actors.AdminActor.AdminErrorNotification
import my.rcfbot.actors.ConversationsHub.{StartConversationInternal, StartScenarioMsg}
import my.rcfbot.actors.CoordinatorActor.RegisterUserFromDM
import my.rcfbot.actors.ImConversationHandler.{SendAdminMessage, StartConversation, Transition}
import my.rcfbot.boot.Wired._
import my.rcfbot.conversation.{ConversationScenario, ScenariosRegistry}
import my.rcfbot.domain.{RcfUser, TransitionDocument}
import my.rcfbot.service._
import my.rcfbot.slack.api.SlackApiClient
import my.rcfbot.slack.models.{MessageReplied, SlackMessage}

import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ConversationsHub {

  val RegisterCommand = "register"
  val StopCommand = "stop"

  case class StartScenarioMsg(user: RcfUser, scenario: ConversationScenario)

  case class StartConversationInternal(im: String, message: StartConversation)

}

class ConversationsHub(apiClient: SlackApiClient) extends Actor with ActorLogging {
  this: ConfigurationImpl with RcfUserComponent with TransitionComponent
    with SessionInviteComponent with RcfSessionComponent =>

  implicit val ec = context.dispatcher
  implicit val system = context.system

  val domain = conf.get[String]("rcf.domain", "paypal.com")

  val imHandlers: mutable.Map[String, ActorRef] = mutable.Map()
  val adminMessagesCache: mutable.Map[String, SlackMessage] = mutable.Map()

  val adminActor = system.actorSelection("/user/admin")
  val coordinatorActor = system.actorSelection("/user/coordinator")
  val commandHandler = system.actorSelection("/user/commands")

  val adminChannel = conf.get[String]("rcf.admin.channel")

  var actionHandler = system.actorOf(Props(new ActionHandler with RcfUserMongoComponent with RcfSessionMongoComponent
    with RcfParticipationMongoComponent with ConfigurationImpl))
  context.watch(actionHandler)

  val timeout = 5 seconds

  override def receive: Receive = {

    case StartConversationInternal(im, message) =>
      forwardMessageToImHandler(im, message, "StartConversation")

    case msg: StartScenarioMsg if msg.user.slackId.isDefined && msg.user.im.isDefined =>
      if (msg.scenario.id.equals(SessionInviteService.SessionInviteScenario) ||
            msg.scenario.id.equals(SessionInviteService.SessionReInviteScenario)) {

        val sessionId = msg.scenario.params.getOrElse(SessionInviteService.SessionKey, "error_session")
        sessionInviteService.appendInvite(sessionId, msg.user.id) map { doc =>
          StartConversationInternal(msg.user.im.get, StartConversation(msg.scenario))
        } recover {
          case err =>
            val errorMessage = s"Error happened during session [$sessionId] invite persistence for user [${msg.user.id}], but scenario will be continued"
            log.error(err, errorMessage)
            StartConversationInternal(msg.user.im.get, StartConversation(msg.scenario))
        } pipeTo self

      } else {

        forwardMessageToImHandler(msg.user.im.get, StartConversation(msg.scenario), "StartConversation")

      }

    case msg: StartScenarioMsg if msg.user.im.isEmpty =>
      initDMChannelForUser(msg.user) map { user =>
        StartScenarioMsg(user, msg.scenario)
      } pipeTo self

    case msg: StartScenarioMsg =>
      log.error(s"Scenario [${msg.scenario.id}] can't be started for user with empty fields [${msg.user}]")


    //   !!!   admin channel related activities  !!!
    case msg: SlackMessage if msg.channel == adminChannel && msg.text.startsWith("!") =>
      CommandRegistry.processCommand(msg) foreach { command => commandHandler ! command }

    case msg: SlackMessage if msg.channel == adminChannel =>
      log.info(s"Admin channel msg [${msg.toString}]")
      adminMessagesCache.put(buildMsgKey(msg.user, msg.ts), msg)

    case msg: MessageReplied =>
      val replyKeys = buildReplyKeys(msg).head
      val success = adminMessagesCache.contains(replyKeys)
      adminMessagesCache.get(replyKeys).foreach { adminMessage =>
        val q = msg.message.text
        val a = adminMessage.text

        parseAdminMessage(q).foreach { case (im, _) =>
          forwardMessageToImHandler(im, SendAdminMessage(a), "FromAdminMessage")
        }
      }

    case msg: SlackMessage if msg.channel.startsWith("D") && ConversationsHub.RegisterCommand.equals(msg.text.toLowerCase) =>
      log.info(s"Registration from DM channel is requested for slackId [${msg.user}]")
      coordinatorActor ! RegisterUserFromDM(msg.user)

    case msg: SlackMessage if msg.channel.startsWith("D") =>
      log.debug(msg.toString)
      forwardMessageToImHandler(msg.channel, msg, "SlackMessageReceived")

    case msg: SlackMessage =>
      log.debug(s"Foreign message is observed [${msg.toString}]")

    case Terminated(actor) =>
      if (actor == actionHandler) {

        actionHandler = system.actorOf(Props(new ActionHandler with RcfUserMongoComponent
          with RcfSessionMongoComponent with ConfigurationImpl with RcfParticipationMongoComponent))

        context.watch(actionHandler)
        log.info(s"ActionHandler is restarted")
      } else {
        val im = actor.asInstanceOf[ImConversationHandler].im
        log.info(s"Actor for IM [$im] is dead, recreating new one")
        getImHandlerSafe(im) match {
          case Success(a) =>
            log.info(s"IM [$im] Actor is recreated")

          case Failure(err) =>
            log.error(err, s"IM [$im] Actor is not recreated")
        }
      }

    case m: ContactAdminsAction =>
      Try(
        Await.result(rcfUserService.findUserByIm(m.im), timeout)
      ) match {
        case Success(user) =>
          adminActor ! m.copy(text = s"[${m.im}] [${user.id}] ")
        case Failure(err) =>
          log.warning("Error for finding user by IM ")
          adminActor ! m.copy(text = s"[${m.im}] [unknown] ")

      }

    case m: RcfAction =>
      actionHandler ! m

    case m: Transition =>
      forwardMessageToImHandler(m.im, m, "Transition")
  }

  val bracketsRegexp = "\\[.*\\]\\s\\[(.*)\\]\\s\\[.*\\]\\s(.*)".r

  def parseAdminMessage(text: String): Option[(String, String)] =
    bracketsRegexp.findFirstMatchIn(text).map(m => (m.group(1), m.group(2)))

  def initDMChannelForUser(rcfUser: RcfUser): Future[RcfUser] = rcfUser match {

    case user: RcfUser if user.im.isDefined && user.slackId.isDefined =>
      log.warning(s"No need to init channel for user [$rcfUser]")
      Future {
        user
      }

    case user: RcfUser if user.im.isEmpty && user.slackId.isDefined =>
      apiClient.openIm(user.slackId.get) flatMap { dmChannelId =>
        rcfUserService.update(user.copy(
          im = Some(dmChannelId),
          imTs = Some(Instant.now.toEpochMilli)))
      }

    case user: RcfUser if user.slackId.isEmpty =>
      log.error(s"Cannot open direct channel for a user with no SlackId")
      Future.failed(new IllegalArgumentException("Cannot open direct channel for a user with no SlackId"))
  }

  def forwardMessageToImHandler[T](im: String, msg: T, desc: String)(implicit cls: ClassTag[T]): Unit = {
    getImHandlerSafe(im) match {
      case Success(actor) => actor ! msg
      case Failure(err) =>
        val msg = s"Message [$desc] was not delivered to IM actor [$im]"
        log.error(err, msg)
        adminActor ! AdminErrorNotification(msg, err)
    }
  }

  //intentionally synchronous to avoid locks for imHandler creation
  def getImHandlerSafe(im: String): Try[ActorRef] =
    imHandlers.get(im) match {
      case Some(actor) => Success(actor)
      case _ =>
        Try(Await.result(conversationHandler(im), timeout)) map { imActor =>
          context.watch(imActor)
          imHandlers.put(im, imActor)
          imActor
        }
    }

  def conversationHandler(im: String): Future[ActorRef] =
    (for {
      user <- rcfUserService.findUserByIm(im)
      transition <- transitionService.getCurrentTransition(im)
    } yield (user, transition)) transform {
      case Success((user, trs)) if user.status == RcfUserService.Status.inited =>
        Try(context.actorOf(Props(
          new ImConversationHandler(
            im = im,
            slackClient = apiClient,

            currentScenario = trs
              .flatMap(_.lastScenario)
              .flatMap(ScenariosRegistry.staticScenarios.get)
              .orElse(Some(ScenariosRegistry.activation)),

            currentScenarioStep = trs
              .flatMap(_.lastScenarioStep)
              .orElse(Some(ScenariosRegistry.activation.first))

          ) with ConfigurationImpl with AkkaExecutionContext
            with RcfUserMongoComponent with SlackMessagePersistenceMongoComponent with TransitionMongoComponent)))

      case Success((user, trs)) =>
        Try {
          val scenario = trs
            .flatMap(_.lastScenario)
            .flatMap(ScenariosRegistry.staticScenarios.get)
            .orElse(buildScenarioFromTransitionDoc(trs))
          val step = trs.flatMap(_.lastScenarioStep)

          context.actorOf(Props(new ImConversationHandler(
            im = im,
            slackClient = apiClient,
            currentScenario = scenario,
            currentScenarioStep = step
          ) with ConfigurationImpl with AkkaExecutionContext
            with RcfUserMongoComponent with SlackMessagePersistenceMongoComponent with TransitionMongoComponent))
        }

      case Failure(err) =>
        log.warning(s"User for IM [$im] is not found")
        Try(context.actorOf(Props(new ImConversationHandler(im, apiClient) with ConfigurationImpl
          with AkkaExecutionContext with RcfUserMongoComponent
          with SlackMessagePersistenceMongoComponent with TransitionMongoComponent)))
    }

  private def buildScenarioFromTransitionDoc(trs: Option[TransitionDocument]) = {
    Try {
      trs match {
        case Some(
        TransitionDocument(_, _, Some(SessionInviteService.SessionInviteScenario), _, params)
        ) if params.get(SessionInviteService.SessionKey).isDefined =>

          val sessionId = params(SessionInviteService.SessionKey)
          val session = Await.result(sessionManager.getSession(sessionId), 5 seconds)
          ScenariosRegistry.buildSessionOnboardingScenario(session)

        case Some(
        TransitionDocument(_, _, Some(SessionInviteService.SessionClosureScenario), _, params)
        ) if params.get(SessionInviteService.SessionKey).isDefined =>

          val sessionId = params(SessionInviteService.SessionKey)
          val session = Await.result(sessionManager.getSession(sessionId), 5 seconds)
          ScenariosRegistry.buildSessionClosureScenario(session)

        case Some(
        TransitionDocument(_, _, Some(SessionInviteService.SessionReInviteScenario), _, params)
        ) if params.get(SessionInviteService.SessionKey).isDefined &&
          params.get(SessionInviteService.PrevSessionKey).isDefined =>

          val sessionId = params(SessionInviteService.SessionKey)
          val prevSessionId = params(SessionInviteService.PrevSessionKey)

          val session = Await.result(sessionManager.getSession(sessionId), 5 seconds)
          val prevSession = Await.result(sessionManager.getSession(prevSessionId), 5 seconds)

          ScenariosRegistry.buildSessionReOnboarding(session, prevSession)

        case _ => throw new IllegalStateException("Scenario STep is not recoverable for conversation actor")

      }
    }.toOption
  }

  def buildMsgKey(userId: String, ts: String) = s"${userId}_$ts"

  def buildReplyKeys(replied: MessageReplied): Seq[String] =
    replied.message.replies
      .map(marker => buildMsgKey(marker.user, marker.ts))
}
