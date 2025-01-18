package my.rcfbot.service

import my.rcfbot.domain.RcfSessionDocument

import scala.concurrent.Future

object RcfSessionManager {
  val inited = "inited"
  val started = "started"
  val ending = "ending"
  val closed = "closed"
  val cancelled = "cancelled"
}

trait RcfSessionManager {

  def getSession(sessionId: String): Future[RcfSessionDocument]

  def createNewSession(startIn: Double, duration: Int, series: Boolean = false): Future[RcfSessionDocument]

  def createRawSession(startInTs: Long, durationTs: Long, series: Boolean = true): Future[RcfSessionDocument]

  def sessionsToRun: Future[List[RcfSessionDocument]]

  def getInitedSessions: Future[List[RcfSessionDocument]]

  def sessionsPassedOverHalf(): Future[List[RcfSessionDocument]]

  def sessionsToClose(): Future[List[RcfSessionDocument]]

  def updateSession(session: RcfSessionDocument): Future[RcfSessionDocument]

  def sessions(): Future[List[RcfSessionDocument]]
}

trait RcfSessionComponent {
  def sessionManager: RcfSessionManager
}
