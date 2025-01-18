package my.rcfbot.mongo

import java.time.Instant

import com.typesafe.scalalogging.Logger
import my.rcfbot.conversation.ConversationScenario
import my.rcfbot.domain.TransitionDocument
import my.rcfbot.service.TransitionService
import my.rcfbot.util.{ConfigHelper, ExecutionContextHelper, LoggerHelper}

import scala.concurrent.Future

abstract class TransitionServiceImpl extends TransitionService with MongoConnector[TransitionDocument] {
  this: ConfigHelper with LoggerHelper with ExecutionContextHelper =>

  override val log: Logger = Logger[TransitionServiceImpl]

  import Documents._

  override def putTransition(im: String, scenario: Option[ConversationScenario],
                             scenarioStep: Option[String]): Future[TransitionDocument] = {
    val ts = Instant.now.toEpochMilli
    upsertDocument(TransitionDocument(
      id = im,
      ts = ts,
      lastScenario = scenario.map(_.id),
      lastScenarioStep = scenarioStep,
      scenarioParams = scenario.map(_.params).getOrElse(Map())
    ))
  }

  override def getCurrentTransition(im: String): Future[Option[TransitionDocument]] =
    findById(im).map(Option.apply).recover {case err => None}

  override def colName: String = conf.get[String]("mongo.collections.transitions", "transitions")

  override def notInsertedExceptionText(obj: TransitionDocument): String = insertionErrorTplt("Transition", obj.id)

  override def notFoundExceptionText(id: String): String = lookupErrorTplt("Transition", id)
}
