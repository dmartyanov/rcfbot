package my.rcfbot.service

import my.rcfbot.domain.RcfUser
import my.rcfbot.slack.models.User
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

object RcfUserService {

  object Status {
    val nova = "new"
    val inited = "inited"
    val active = "active"
    val inactive = "inactive"
    val left = "left"
  }

  object Channel {
    val web = "web"
    val channel = "channel"
    val dm = "dm"
  }

  val emailRegex = "([a-zA-Z0-9\\.]+)@(.+)".r

  def email(corpId: String, domain: String) = s"$corpId@$domain"

  def fromEmail(email: String) = email.takeWhile(!_.equals('@'))

  def fullname(user: User) =
    s"${user.profile.flatMap(_.first_name).getOrElse("")} ${user.profile.flatMap(_.last_name).getOrElse("")}"

  def parseEmail(email: String): Option[(String, String)] =
    emailRegex.findFirstMatchIn(email).map(m => (m.group(1), m.group(2)))
}

trait RcfUserService {

  def getUser(corpId: String): Future[RcfUser]

  def findUserByIm(im: String): Future[RcfUser]

  def getNewUsers(): Future[Seq[RcfUser]]

  def getRegisteredUsers(): Future[Seq[RcfUser]]

  def register(corpId: String, channel: String, force: Boolean, slackId: Option[String],
               fullname: Option[String], tz_offset: Option[Int]): Future[RcfUser]

  def update(user: RcfUser): Future[RcfUser]

  def users(status: Option[String] = None): Future[Seq[RcfUser]]
}

trait RcfUserComponent {
  def rcfUserService: RcfUserService
}
