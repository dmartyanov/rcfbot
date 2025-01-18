package my.rcfbot.mongo

import java.time.Instant

import com.typesafe.scalalogging.Logger
import my.rcfbot.domain.SessionInviteDoc
import my.rcfbot.service.SessionInviteService
import my.rcfbot.util.{ConfigHelper, ExecutionContextHelper, LoggerHelper}
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

abstract class SessionInviteServiceImpl extends SessionInviteService with MongoConnector[SessionInviteDoc] {
  this: ConfigHelper with LoggerHelper with ExecutionContextHelper =>

  override val log: Logger = Logger[SessionInviteServiceImpl]

  import Documents._

  override def appendInvite(sessionId: String, userId: String): Future[SessionInviteDoc] =
    upsertDocument(SessionInviteDoc(
      id = getKey(userId, sessionId),
      sessionId = sessionId,
      userId = userId,
      createTs = Instant.now.toEpochMilli
    ))

  override def getInvitesForSession(sessionId: String): Future[List[SessionInviteDoc]] =
    findByStringField("sessionId", sessionId)

  override def getInvitesForUser(userId: String): Future[List[SessionInviteDoc]] =
    findByStringField("userId", userId)


  override def getAllInvites(fromTs: Option[Long]): Future[List[SessionInviteDoc]] = fromTs match {
    case Some(ts) =>
      findCustom(BSONDocument("createTs" -> BSONDocument("$gt" -> ts)))
    case None =>
      findAll()
  }

  override def colName: String = conf.get[String]("mongo.collections.invites", "invites")

  override def notInsertedExceptionText(obj: SessionInviteDoc): String = insertionErrorTplt("Session Invite", obj.id)

  override def notFoundExceptionText(id: String): String = lookupErrorTplt("Session Invite", id)

  def getKey(userId: String, sessionId: String) = s"${userId}_$sessionId"
}
