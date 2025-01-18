package my.rcfbot.mongo

import java.time.Instant

import com.typesafe.scalalogging.Logger
import my.rcfbot.domain.RcfSessionDocument
import my.rcfbot.service.RcfSessionManager
import my.rcfbot.util.{ConfigHelper, DateUtils, ExecutionContextHelper, LoggerHelper}
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

class RcfSessionManagerImpl extends RcfSessionManager with MongoConnector[RcfSessionDocument] {
  this: ConfigHelper with ExecutionContextHelper with LoggerHelper =>

  val milliSecondsInDay = 60 * 60 * 24 * 1000

  override val log: Logger = Logger[RcfSessionManagerImpl]

  import my.rcfbot.mongo.Documents._

  override def getSession(sessionId: String): Future[RcfSessionDocument] = findById(sessionId)

  override def createNewSession(startIn: Double, duration: Int, series: Boolean = false): Future[RcfSessionDocument] =
    createRawSession((milliSecondsInDay * startIn).toLong, duration * milliSecondsInDay, series)

  override def createRawSession(startInTs: Long, durationTs: Long, series: Boolean): Future[RcfSessionDocument] = {
    val nowInMillis = Instant.now.toEpochMilli

    val doc = RcfSessionDocument(
      id = java.util.UUID.randomUUID().toString,
      series = series,
      status = RcfSessionManager.inited,
      toStartTs = nowInMillis + startInTs,
      toEndTs = nowInMillis + durationTs,
      duration = durationTs,
      createTs = nowInMillis
    )

    insertDocument(doc.copy(halfTs = Some(getHalfTs(doc))))
  }

  def getHalfTs(s: RcfSessionDocument): Long = DateUtils.to3PM(s.toStartTs + milliSecondsInDay)

  override def sessionsToRun: Future[List[RcfSessionDocument]] = {
    val nowInMillis = Instant.now().toEpochMilli

    findCustom(
      BSONDocument("status" -> RcfSessionManager.inited,
        "toStartTs" -> BSONDocument("$lt" -> nowInMillis))
    )
  }

  override def getInitedSessions: Future[List[RcfSessionDocument]] =
    findByStringField("status", RcfSessionManager.inited)

  override def updateSession(session: RcfSessionDocument): Future[RcfSessionDocument] =
    upsertDocument(session.copy(
      halfTs = Some(getHalfTs(session)),
      updateTs = Some(Instant.now.toEpochMilli)
    ))

  override def sessionsPassedOverHalf(): Future[List[RcfSessionDocument]] = {
    val nowInMillis = Instant.now().toEpochMilli

    findCustom(
      BSONDocument("status" -> RcfSessionManager.started,
        "halfTs" -> BSONDocument("$lt" -> nowInMillis))
    )
  }

  override def sessionsToClose(): Future[List[RcfSessionDocument]] = {
    val nowInMillis = Instant.now().toEpochMilli

    findCustom(
      BSONDocument(
        "status" -> BSONDocument("$in" -> List(RcfSessionManager.ending, RcfSessionManager.started)),
        "toEndTs" -> BSONDocument("$lt" -> nowInMillis)
      )
    )
  }

  override def sessions(): Future[List[RcfSessionDocument]] = findAll()

  override def colName: String = conf.get[String]("mongo.collections.sessions", "sessions")

  override def notInsertedExceptionText(obj: RcfSessionDocument): String = insertionErrorTplt("RCF Session", obj.id)

  override def notFoundExceptionText(id: String): String = lookupErrorTplt("RCF Session", id)

}
