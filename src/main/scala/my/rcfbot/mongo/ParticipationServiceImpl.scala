package my.rcfbot.mongo

import java.time.Instant

import com.typesafe.scalalogging.Logger
import my.rcfbot.domain.UserParticipationDoc
import my.rcfbot.service.ParticipationService
import my.rcfbot.util.{ConfigHelper, ExecutionContextHelper, LoggerHelper}
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

class ParticipationServiceImpl extends ParticipationService with MongoConnector[UserParticipationDoc] {
  this: ExecutionContextHelper with ConfigHelper with LoggerHelper =>

  import my.rcfbot.mongo.Documents._

  override val log: Logger = Logger[ParticipationServiceImpl]

  override def userInSession(userId: String, sessionId: String, inTime: Boolean,
                             accept: Boolean, prevSession: Option[String] = None): Future[UserParticipationDoc] =
    insertDocument(buildDocument(userId, sessionId, accept, prevSession).copy(inTime = Some(inTime)))

  override def usersForSession(sessionId: String): Future[List[UserParticipationDoc]] =
    findCustom(BSONDocument("sessionId" -> sessionId))

  override def usersFromPreviousSession(prevSessionId: String): Future[List[UserParticipationDoc]] =
    findCustom(BSONDocument("prevSessionId" -> prevSessionId))

  override def addFeedback(userId: String, sessionId: String, feedback: Option[String], met: Boolean): Future[UserParticipationDoc] =
    findById(getKey(userId, sessionId)) flatMap { participation =>
      upsertDocument(participation.copy(feedback = feedback, success = Some(met)))
    } recoverWith {
      case err =>
        log.warn(s"User document by key ${getKey(userId, sessionId)} was not found but will be recovered, error: [${err.getMessage}]")
        insertDocument(buildDocument(userId, sessionId, true).copy(feedback = feedback, success = Some(met)))
    }

  override def addPartner(userId: String, sessionId: String, partnerId: String): Future[UserParticipationDoc] = {
    findById(getKey(userId, sessionId)) flatMap { user =>
      upsertDocument(user.copy(partnerId = Some(partnerId)))
    } recoverWith {
      case err =>
        log.warn(s"User document by key ${getKey(userId, sessionId)} was not found but will be recovered, error: [${err.getMessage}]")
        insertDocument(buildDocument(userId, sessionId, true).copy(partnerId = Some(partnerId)))
    }
  }

  override def getParticipation(userId: String, sessionId: String): Future[UserParticipationDoc] =
    findById(getKey(userId, sessionId))

  override def colName: String = conf.get[String]("mongo.collections.participation", "participation")

  override def notInsertedExceptionText(obj: UserParticipationDoc): String =
    insertionErrorTplt("RCF Particiaption", obj.id)

  override def notFoundExceptionText(id: String): String =
    lookupErrorTplt("RCF Participation", id)

  def buildDocument(userId: String, sessionId: String, accept: Boolean,
                    prevSessionId: Option[String] = None) = UserParticipationDoc(
    id = getKey(userId, sessionId),
    userId = userId,
    sessionId = sessionId,
    createTs = Instant.now.toEpochMilli,
    accept = accept,
    prevSessionId = prevSessionId
  )

  def getKey(userId: String, sessionId: String) = s"${userId}_$sessionId"

  override def addToWaitingList(userId: String, sessionId: String, waitList: Boolean): Future[Option[UserParticipationDoc]] =
    findById(getKey(userId, sessionId)) flatMap { pa =>
      upsertDocument(pa.copy(waitingList = Some(waitList)))
    } map {
      Option.apply
    } recover {
      case err =>
        log.error(s"User document by key ${getKey(userId, sessionId)} was not found, error: [${err.getMessage}]", err)
        None
    }

  override def getAllParticipationDocuments(fromTs: Option[Long] = None): Future[List[UserParticipationDoc]] =
    fromTs match {
      case Some(ts) =>
        findCustom(BSONDocument("createTs" -> BSONDocument("$gt" -> ts)))
      case None =>
        findAll()
    }

}
