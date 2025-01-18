package my.rcfbot.service

import my.rcfbot.domain.UserChangeDoc
import my.rcfbot.slack.models.UserChange

import scala.concurrent.Future

trait UserChangeEventHandler {

  def append(userChange: UserChange): Future[UserChangeDoc]

  def lastDeleted(periodTs: Long): Future[List[UserChangeDoc]]
}

trait UserChangeEventHandlerComponent {
  def userChangeEventHandler: UserChangeEventHandler
}
