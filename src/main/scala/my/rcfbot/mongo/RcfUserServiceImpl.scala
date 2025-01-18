package my.rcfbot.mongo

import java.time.Instant

import com.typesafe.scalalogging.Logger
import my.rcfbot.boot.Wired.RCFAkkaSystem
import my.rcfbot.domain.RcfUser
import my.rcfbot.service.RcfUserService
import my.rcfbot.util._
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future
import scala.util.{Failure, Success}

abstract class RcfUserServiceImpl extends RcfUserService with MongoConnector[RcfUser]
  with RCFAkkaSystem with CacheComponent[RcfUser] {
  this: ExecutionContextHelper with LoggerHelper with ConfigHelper =>

  override val log: Logger = logger[RcfUserServiceImpl]

  val hubActor = system.actorSelection("/user/hub")

  import my.rcfbot.mongo.Documents._

  override def colName: String = conf.get[String]("mongo.collections.users", "users")

  override def getUser(corpId: String): Future[RcfUser] = cacheService.get(corpId) match {
    case None =>
      log.warn(s"cache miss for $corpId")
      findById(corpId) map {
        userDoc => cacheService.put(corpId, userDoc)
      }

    case Some(user) => Future {
      user
    }
  }

  override def findUserByIm(im: String): Future[RcfUser] = cacheService.get(im) match {
    case None =>
      log.warn(s"cache miss for [$im]")
      findByStringField("im", im) map { ls =>
        cacheService.put(im, ls.head)
      }

    case Some(user) => Future {
      user
    }
  }

  override def register(corpId: String, channel: String, force: Boolean, slackId: Option[String],
                        fullname: Option[String], tz_offset: Option[Int]): Future[RcfUser] = {
    findById(corpId) transformWith  {
      case Success(user) =>
        if(force) {
         upsertDocument(user.copy(
           status = RcfUserService.Status.nova,
           channel = channel,
           fullname = fullname,
           slackId = slackId
         )) transformWith {
           case Success(u) =>
             log.info(s"User ${u.id} was re-registered ")
             Future { user }
           case Failure(err) =>
             val msg = s"User $corpId was not re-registered, error [${err.getMessage}]"
             log.warn(msg)
             Future.failed(new RuntimeException(msg))
         }
        } else {
          Future.failed(new RuntimeException(s"User $corpId is already registered"))
        }
      case Failure(err) => insertDocument(RcfUser(
        id = corpId,
        status = RcfUserService.Status.nova,
        createTs = Instant.now.toEpochMilli,
        channel = channel,
        fullname = fullname,
        slackId = slackId
      )) map { userDoc => cacheService.put(corpId, userDoc) }
    }
  }

  override def update(user: RcfUser): Future[RcfUser] =
    upsertDocument(user.copy(updateTs = Some(Instant.now.toEpochMilli))) map { u =>
      cacheService.put(u.id, u)
      u.im.foreach(im => cacheService.put(im, u))
      u
    }

  override def users(status: Option[String] = None): Future[Seq[RcfUser]] = status match {
    case Some(st) => findCustom(BSONDocument("status" -> st))
    case _ => findAll()
  }

  override def getNewUsers(): Future[Seq[RcfUser]] = findByStringField("status", RcfUserService.Status.nova)

  override def getRegisteredUsers(): Future[Seq[RcfUser]] = findByStringField("status", RcfUserService.Status.active)

  override def notInsertedExceptionText(obj: RcfUser): String = insertionErrorTplt("RCF User", obj.id)

  override def notFoundExceptionText(id: String): String = lookupErrorTplt("RCF User", id)

}
