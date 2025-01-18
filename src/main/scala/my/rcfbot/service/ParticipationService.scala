package my.rcfbot.service

import my.rcfbot.domain.UserParticipationDoc

import scala.concurrent.Future

trait ParticipationService {

  def userInSession(userId: String, sessionId: String, inTime: Boolean,
                    accept: Boolean, prevSession: Option[String] = None): Future[UserParticipationDoc]

  def usersForSession(sessionId: String): Future[List[UserParticipationDoc]]

  def usersFromPreviousSession(prevSessionId: String): Future[List[UserParticipationDoc]]

  def addFeedback(userId: String, sessionId: String, feedback: Option[String], met: Boolean): Future[UserParticipationDoc]

  def addPartner(userId: String, sessionId: String, partnerId: String): Future[UserParticipationDoc]

  def getParticipation(userId: String, sessionId: String): Future[UserParticipationDoc]

  def addToWaitingList(userId: String, sessionId: String, waitList: Boolean): Future[Option[UserParticipationDoc]]

  def getAllParticipationDocuments(fromTs: Option[Long] = None): Future[List[UserParticipationDoc]]
}

trait ParticipationComponent {
  def participationService: ParticipationService
}