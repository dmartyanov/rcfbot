package my.rcfbot.actions

import java.time.Instant

import my.rcfbot.slack.models.SlackMessage
import my.rcfbot.util.DateUtils

import scala.util.Try

trait CommandBuilder[T <: RcfCommand] {
  def build(text: String, m: SlackMessage): Option[T]
}

object SetTopicCmdBuilder extends CommandBuilder[SetTopicCommand] {
  override def build(text: String, m: SlackMessage): Option[SetTopicCommand] =
    Some(SetTopicCommand("SetTopicCommand", text.trim))
}

object ListUsersCmdBuilder extends CommandBuilder[ListUsersCommand] {
  override def build(text: String, m: SlackMessage): Option[ListUsersCommand] =
    Some(ListUsersCommand("ListUsers", text.trim match {
      case m: String if m.length > 2 => Some(m)
      case _ => None
    }))
}

object UserInfoCmdBuilder extends CommandBuilder[UserInfoCommand] {
  override def build(text: String, m: SlackMessage): Option[UserInfoCommand] =
    Some(UserInfoCommand("UserInfo", text))
}

object ListSessionsCmdBuilder extends CommandBuilder[ListSessions] {
  override def build(text: String, m: SlackMessage): Option[ListSessions] =
    Some(ListSessions("ListSessions"))
}

object ListPairsCmdBuilder extends CommandBuilder[ListPairsCommand] {
  override def build(text: String, m: SlackMessage): Option[ListPairsCommand] =
    Some(ListPairsCommand("ListPairs", text))
}

object GetSingleCmdBuilder extends CommandBuilder[GetSingleCommand] {
  override def build(text: String, m: SlackMessage): Option[GetSingleCommand] =
    Some(GetSingleCommand("GetSingle", text))
}

object ChatWithCmdBuilder extends CommandBuilder[ChatWith] {
  override def build(text: String, m: SlackMessage): Option[ChatWith] = {
    val userId = text.takeWhile(!_.isSpaceChar)
    val timeCode = text.drop(userId.length).trim match {
      case s if s.length > 1 => Some(s)
      case _ => None
    }

    Some(ChatWith("ChatWith", userId, timeCode.flatMap(DateUtils.parseTimeCode)))
  }
}

object SetPairCmdBuilder extends CommandBuilder[SetPairCommand] {
  override def build(text: String, m: SlackMessage): Option[SetPairCommand] = {
    text.trim().split(" ") match {
      case Array(sessionId, userId1, userId2) =>
        Some(SetPairCommand("SetPair", userId1, userId2, sessionId))
      case _ => None

    }
  }
}

object SessionStatsCmdBuilder extends CommandBuilder[SessionStats] {
  override def build(text: String, m: SlackMessage): Option[SessionStats] = {
    val sessionId = text.takeWhile(!_.isSpaceChar)
    val channelId = text.drop(sessionId.length).trim match {
      case s: String if s.length > 4 => Some(s)
      case _ => None
    }
    Some(SessionStats("SessionStats", sessionId,  channelId))
  }
}

object RetrieveLastDeletedNo extends CommandBuilder[RetrieveLastDeleted] {
  override def build(text: String, m: SlackMessage): Option[RetrieveLastDeleted] = {
    val timeCode = text.trim match {
      case s: String if s.length > 4 => Some(s)
      case _ => None
    }
    Some(RetrieveLastDeleted("RetrieveLastDeleted", timeCode.flatMap(DateUtils.parseTimeCode)))
  }
}

object RetrieveLastDeletedList extends CommandBuilder[RetrieveLastDeleted] {
  override def build(text: String, m: SlackMessage): Option[RetrieveLastDeleted] = {
    val timeCode = text.trim match {
      case s: String if s.length > 4 => Some(s)
      case _ => None
    }
    Some(RetrieveLastDeleted("RetrieveLastDeleted", timeCode.flatMap(DateUtils.parseTimeCode), list = true))
  }
}

object OnboardSleepyUsersBuilder extends CommandBuilder[OnboardSleepyUsers] {
  override def build(text: String, m: SlackMessage): Option[OnboardSleepyUsers] =
    Some(OnboardSleepyUsers("OnboardSleepyUsers"))
}

object CheckInactiveBuilder extends CommandBuilder[CheckInactive] {
  override def build(text: String, m: SlackMessage): Option[CheckInactive] =
    Some(CheckInactive("CheckInactive"))
}

object BroadcastToActiveBuilder extends CommandBuilder[BroadcastToActive] {
  override def build(text: String, m: SlackMessage): Option[BroadcastToActive] =
    Some(BroadcastToActive("BroadcastToActive", text))
}

object CommandRegistry {
  val basicCommands = Map(
    "settopic" -> SetTopicCmdBuilder,
    "users" -> ListUsersCmdBuilder,
    "userinfo" -> UserInfoCmdBuilder,
    "sessions" -> ListSessionsCmdBuilder,
    "pairs" -> ListPairsCmdBuilder,
    "setpair" -> SetPairCmdBuilder,
    "single" -> GetSingleCmdBuilder,
    "chat" -> ChatWithCmdBuilder,
    "sessionstats" -> SessionStatsCmdBuilder,
    "deletedNo" -> RetrieveLastDeletedNo,
    "deletedList" -> RetrieveLastDeletedList,
    "onboardsleepy" -> OnboardSleepyUsersBuilder,
    "checkinactive" -> CheckInactiveBuilder,
    "broadcasttoactive" -> BroadcastToActiveBuilder,
  )

  def processCommand(m: SlackMessage): Option[RcfCommand] = {
    val text = m.text
    val command = text.drop(1).takeWhile(_.isLetter)
    val msg = text.drop(1 + command.length).trim()

    basicCommands.get(command).flatMap(_.build(msg, m))
  }
}