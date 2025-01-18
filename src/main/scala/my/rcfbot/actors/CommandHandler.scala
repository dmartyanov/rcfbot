package my.rcfbot.actors

import akka.actor.{Actor, ActorLogging}
import my.rcfbot.actions._
import my.rcfbot.actors.AdminActor.{AdminErrorNotification, AdminSystemMessage}
import my.rcfbot.actors.CoordinatorActor.{BroadcastToUser, OnboardSleepyUser, SetPairForSession}
import my.rcfbot.boot.Wired.ConfigurationImpl
import my.rcfbot.domain.{RcfSlackMessage, RcfUser}
import my.rcfbot.service._
import my.rcfbot.slack.api.SlackApiClient
import my.rcfbot.stats.StatsProvider
import my.rcfbot.util.{DateUtils, SessionHelper}
import spray.json.RootJsonFormat

import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps

abstract class CommandHandler(slackApiClient: SlackApiClient) extends Actor with ActorLogging
  with SessionHelper with StatsProvider {
  this: ConfigurationImpl with RcfUserComponent with RcfSessionComponent
    with ParticipationComponent with SlackMessagePersistenceComponent
    with SessionInviteComponent =>

  implicit val ec = context.dispatcher
  implicit val system = context.system

  val adminActor = system.actorSelection("/user/admin")
  val coordinatorActor = system.actorSelection("/user/coordinator")

  val rcfChannel = conf.get[String]("rcf.register.channels", "CHA291U21")

  val timeout = 5 seconds

  import my.rcfbot.mongo.Documents._

  override def receive: Receive = {

    case SetTopicCommand(id, topic, channelId) =>
      log.info(s"$id is received")
      Try(Await.result(
        slackApiClient.setChannelTopic(channelId.getOrElse(rcfChannel), topic), timeout
      )) match {
        case Success(t) =>
        case Failure(err) =>
          log.error(err, s"$id command failed, error: [${err.getMessage}]")
          adminActor ! AdminSystemMessage(s"$id command failed, error: [${err.getMessage}]")
      }

    case ListUsersCommand(id, status) =>
      log.info(s"$id command is received for status $status")
      rcfUserService.users(status) onComplete {
        case Success(uss) =>
          adminActor ! AdminSystemMessage(s"`${uss.map(_.id).mkString(", ")}`")

        case Failure(err) =>
          adminActor ! AdminSystemMessage(s"$id command failed, error: [${err.getMessage}]")
      }

    case UserInfoCommand(id, userId) =>
      log.info(s"$id command is received with id $userId")
      rcfUserService.getUser(userId) onComplete {
        case Success(user) =>
          val format = implicitly[RootJsonFormat[RcfUser]]
          adminActor ! AdminSystemMessage(s"```${format.write(user).prettyPrint}```")

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminSystemMessage(msg)
      }

    case ListSessions(id) =>
      log.info(s"$id command is received ")

      (for {
        sessions <- sessionManager.sessions()
        partDocs <- participationService.getAllParticipationDocuments()
        invitations <- sessionInviteService.getAllInvites()
      } yield (sessions, partDocs, invitations)) map {
        case (sessions, partDocs, invitations) =>
          val inivitationStats = invitations.groupBy(_.sessionId).mapValues(_.size)
          val participationStats = partDocs.groupBy(_.sessionId).mapValues(_.size)
          val acceptanceStats = partDocs.filter(_.accept).groupBy(_.sessionId).mapValues(_.size)
          val declineStats = partDocs.filterNot(_.accept).groupBy(_.sessionId).mapValues(_.size)

          sessions map { s =>
            s"[${s.id}] - (${sessToStr(s)}) - [${inivitationStats.getOrElse(s.id, 0)}] - " +
              s"[${participationStats.getOrElse(s.id, 0)}] - [${acceptanceStats.getOrElse(s.id, 0)}] - " +
              s"[${declineStats.getOrElse(s.id, 0)}]"
          }

      } onComplete {

        case Success(sessionsDesc) =>
          adminActor ! AdminSystemMessage(s"```${sessionsDesc.mkString("\n")}```")

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminSystemMessage(msg)
      }

    case ListPairsCommand(id, sessionId) =>
      log.info(s"$id command is received ")
      participationService.usersForSession(sessionId) map { partDocs =>
        val set = mutable.Set[String]()
        val result = mutable.ArrayBuffer[(String, String)]()

        partDocs.filter(_.accept).foreach { doc =>
          if (!set.contains(doc.userId)) {
            result.append((doc.userId, doc.partnerId.getOrElse("unkz")))
            set.add(doc.userId)
            doc.partnerId.foreach(set.add)
          }
        }
        result.toList
      } onComplete {
        case Success(pairs) =>
          adminActor ! AdminSystemMessage(s"```${pairs.map(kv => s"${kv._1} <-> ${kv._2}").mkString("\n")}```")

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminSystemMessage(msg)
      }

    case ChatWith(id, userId, fromTs) =>
      log.info(s"$id command is received for user [$userId] fromTs: $fromTs")
      rcfUserService.getUser(userId).flatMap {

        case u: RcfUser if u.im.isDefined =>
          messageService.messagesFor(u.im.get, fromTs).map { messages =>
            messages.map(msgToStr)
          }

        case _ => Future {
          List()
        }
      } onComplete {
        case Success(lines) =>
          adminActor ! AdminSystemMessage(s"```${lines.mkString("\n")}```")

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminSystemMessage(msg)
      }

    case SetPairCommand(id, user1, user2, sessionId) =>
      log.info(s"$id command is received for session [$sessionId]")
      sessionManager.getSession(sessionId).onComplete {
        case Success(sessionDocument) =>
          coordinatorActor ! SetPairForSession(user1, user2, sessionDocument)

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminSystemMessage(msg)
      }


    case SessionStats(id, sessionId, toChannel) =>
      log.info(s"$id command is received for session [$sessionId]")
      sessionManager.getSession(sessionId).flatMap { session =>
        buildStats(sessionId) map { st =>
          log.info(s"Stats for session [$sessionId] is extracted: ${st.toString}")
          statsToMessage(session, st, toChannel.isDefined)
        }
      } onComplete {
        case Success(statsMessage) =>
          adminActor ! AdminSystemMessage(statsMessage)

          toChannel.foreach {
            case channelId if rcfChannel.equals(channelId) =>
              Try(Await.result(
                slackApiClient.postChatMessage(channelId, statsMessage), timeout
              )) match {
                case Success(t) =>
                case Failure(err) =>
                  log.error(err, s"$id command failed, error: [${err.getMessage}]")
                  adminActor ! AdminSystemMessage(s"$id command failed, error: [${err.getMessage}]")
              }
            case channelId =>
              log.warning(s"Message for unknown channel [$channelId] won't be delivered \n $statsMessage")
          }

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminErrorNotification(msg, err)
      }

    case m: RetrieveLastDeleted =>
      log.info(s"${m.id} command is received and will be forwarded to Admin")
      adminActor ! m

    case OnboardSleepyUsers(id) =>
      log.info(s"$id command is received")
      rcfUserService.users(Some(RcfUserService.Status.inited)) onComplete {
        case Success(initedUsers) =>
          initedUsers.foreach(u => coordinatorActor ! OnboardSleepyUser(u))

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminErrorNotification(msg, err)
      }

    case CheckInactive(id) =>
      log.info(s"$id command is received")
      rcfUserService.users() map {
        users =>
          users
            .filter(_.status != RcfUserService.Status.left)
            .flatMap {
              case user if user.slackId.isDefined =>
                Try(Await.result(slackApiClient.getUserInfo(user.slackId.get), 5 seconds)).toOption
                  .fold {
                    log.warning(s"User ${user.id} was not found in slack")
                    List.empty[RcfUser]
                  } {
                    case slackUser if slackUser.deleted.contains(true) =>
                      log.info(s"User ${user.id} will be marked as left")
                      Try(Await.result(
                        rcfUserService.update(user.copy(status = RcfUserService.Status.left)), 5 seconds))
                        .toOption.toList
                    case _ => List.empty[RcfUser]
                  }
              case _ => List.empty[RcfUser]
            }
      } onComplete {
        case Success(leftUsers) =>
          adminActor ! AdminSystemMessage(s"$id command finished, updated users are: \n " +
            s"${leftUsers.map(u => s"`${u.id}`").mkString(", ")}")

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminErrorNotification(msg, err)
      }

    case BroadcastToActive(id, text) =>
      log.info(s"$id command is received")
      rcfUserService.users(Some(RcfUserService.Status.active)) onComplete {
        case Success(users) =>
          users.foreach(u => coordinatorActor ! BroadcastToUser(u, text))

        case Failure(err) =>
          val msg = s"$id command failed, error: [${err.getMessage}]"
          log.error(err, msg)
          adminActor ! AdminErrorNotification(msg, err)
      }
  }

  def msgToStr(m: RcfSlackMessage) = m match {
    case msg: RcfSlackMessage if msg.inMsg.isDefined =>
      s"${DateUtils.slackMessageDate(msg)} User: [${msg.inMsg.get.text}]"
    case msg: RcfSlackMessage =>
      s"${DateUtils.slackMessageDate(msg)} Bot: [${msg.outText.map(_.drop(1).dropRight(1)).getOrElse("<empty_out>")}]"
  }
}
