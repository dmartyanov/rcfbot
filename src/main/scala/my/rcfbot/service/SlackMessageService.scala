package my.rcfbot.service

import my.rcfbot.domain.RcfSlackMessage

import scala.concurrent.Future

trait SlackMessagePersistenceService {
  def appendMessage(doc: RcfSlackMessage): Future[RcfSlackMessage]

  def messagesFor(im: String, fromTs: Option[Long]): Future[List[RcfSlackMessage]]
}

trait SlackMessagePersistenceComponent {
  def messageService: SlackMessagePersistenceService
}
