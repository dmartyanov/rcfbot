package my.rcfbot.mongo

import java.time.Instant

import com.typesafe.scalalogging.Logger
import my.rcfbot.domain.UserChangeDoc
import my.rcfbot.service.UserChangeEventHandler
import my.rcfbot.slack.models.UserChange
import my.rcfbot.util.{ConfigHelper, ExecutionContextHelper, LoggerHelper}
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

abstract class UserChangeEventMongoHandler extends UserChangeEventHandler with MongoConnector[UserChangeDoc] {
  this: ConfigHelper with LoggerHelper with ExecutionContextHelper =>

  override val log: Logger = logger[UserChangeEventMongoHandler]

  import my.rcfbot.mongo.Documents._

  override def append(userChange: UserChange): Future[UserChangeDoc] = {
    insertDocument(UserChangeDoc(
      id = userChangeDocKey(userChange),
      user = userChange.user,
      createTs = Instant.now.toEpochMilli
    ))
  }

  override def lastDeleted(periodTs: Long): Future[List[UserChangeDoc]] = {
    val now = Instant.now.toEpochMilli
    findCustom(BSONDocument(
      "createTs" -> BSONDocument("$gt" -> (now - periodTs)),
      "user.deleted" -> true
    ))
  }

  override def colName: String = conf.get[String]("mongo.collections.user_change", "user_change")

  def userChangeDocKey(m: UserChange) = s"${m.user.id}_${Instant.now.toEpochMilli}"

  override def notInsertedExceptionText(obj: UserChangeDoc): String = insertionErrorTplt("Slack User Change Evt", obj.id)

  override def notFoundExceptionText(id: String): String = lookupErrorTplt("Slack User Change Evt", id)
}
