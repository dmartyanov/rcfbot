package my.rcfbot.mongo

import java.time.Instant

import com.typesafe.scalalogging.Logger
import my.rcfbot.domain.RcfSlackMessage
import my.rcfbot.service.SlackMessagePersistenceService
import my.rcfbot.util.{ConfigHelper, ExecutionContextHelper, LoggerHelper}
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

abstract class RcfMessageServiceImpl extends SlackMessagePersistenceService with MongoConnector[RcfSlackMessage] {
  this: ExecutionContextHelper with ConfigHelper with LoggerHelper =>

  import my.rcfbot.mongo.Documents._

  override val log: Logger = Logger[RcfMessageServiceImpl]

  override def colName: String = conf.get[String]("mongo.collections.messages", "messages")

  override def appendMessage(doc: RcfSlackMessage): Future[RcfSlackMessage] =
    insertDocument(doc.copy(toTs = Some(Instant.now.toEpochMilli)))


  override def messagesFor(im: String, fromTs: Option[Long]): Future[List[RcfSlackMessage]] = {
    findCustom(
      BSONDocument(
        Seq("_id" -> BSONDocument("$regex" -> s"$im.*")) ++
          fromTs.map(ts => Seq("toTs" -> BSONDocument("$gt" -> ts))).getOrElse(Seq())
      )
    )
  }

  override def notInsertedExceptionText(obj: RcfSlackMessage): String = insertionErrorTplt("Slack message", obj.id)

  override def notFoundExceptionText(id: String): String = lookupErrorTplt("Slack message", id)
}
