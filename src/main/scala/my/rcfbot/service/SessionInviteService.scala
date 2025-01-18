package my.rcfbot.service

import my.rcfbot.domain.SessionInviteDoc

import scala.concurrent.Future

object SessionInviteService {

  val SessionInviteScenario = "session_onboarding"
  val SessionReInviteScenario = "session_reonboarding"
  val SessionClosureScenario = "session_closure"

  val SessionKey = "sessionId"
  val PrevSessionKey = "prevSessionId"
}

trait SessionInviteService {

  def appendInvite(sessionId: String, userId: String): Future[SessionInviteDoc]

  def getInvitesForSession(sessionId: String): Future[List[SessionInviteDoc]]

  def getInvitesForUser(userId: String): Future[List[SessionInviteDoc]]

  def getAllInvites(fromTs: Option[Long] = None): Future[List[SessionInviteDoc]]
}

trait SessionInviteComponent {
  def sessionInviteService: SessionInviteService
}
