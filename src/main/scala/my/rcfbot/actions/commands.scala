package my.rcfbot.actions

sealed trait RcfCommand {
  def id: String
}

case class SetTopicCommand(id: String, topic: String, channelId: Option[String] = None) extends RcfCommand

case class ListUsersCommand(id: String, status: Option[String]) extends RcfCommand

case class UserInfoCommand(id: String, userId: String) extends RcfCommand

case class ListSessions(id: String) extends RcfCommand

case class ListPairsCommand(id: String, sessionId: String) extends RcfCommand

case class GetSingleCommand(id: String, sessionId: String) extends RcfCommand

case class SetPairCommand(id: String, u1: String, u2: String, session: String) extends RcfCommand

case class ChatWith(id: String, userId: String, fromTs: Option[Long]) extends RcfCommand

case class SessionStats(id: String, sessionId: String, toChannel: Option[String] = None) extends RcfCommand

case class RetrieveLastDeleted(id: String, periodTs: Option[Long], list: Boolean = false) extends RcfCommand

case class OnboardSleepyUsers(id: String) extends RcfCommand

case class CheckInactive(id: String) extends RcfCommand

case class BroadcastToActive(id: String, text: String) extends RcfCommand






