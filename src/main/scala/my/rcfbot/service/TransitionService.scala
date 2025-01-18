package my.rcfbot.service

import my.rcfbot.conversation.ConversationScenario
import my.rcfbot.domain.TransitionDocument

import scala.concurrent.Future

trait TransitionService {

  def putTransition(im: String, scenario: Option[ConversationScenario], scenarioStep: Option[String]): Future[TransitionDocument]

  def getCurrentTransition(im: String): Future[Option[TransitionDocument]]
}

trait TransitionComponent {
  def transitionService: TransitionService
}