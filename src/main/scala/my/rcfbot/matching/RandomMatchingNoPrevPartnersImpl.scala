package my.rcfbot.matching

import my.rcfbot.actors.CoordinatorActor
import my.rcfbot.service.ParticipationComponent
import my.rcfbot.util.{ConfigHelper, DateUtils, ExecutionContextHelper}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

abstract class RandomMatchingNoPrevPartnersImpl extends MatchingService {
  this: ParticipationComponent with ConfigHelper with ExecutionContextHelper =>

  val nonRepetitiveWeeks = conf.get[Int]("rcf.nonRepetitiveMatchesInWeeks", 14)

  override def pair(candidates: List[String]): List[AbstractPair] =
    Await.result(
      participationService
        .getAllParticipationDocuments(Some(DateUtils.getWeeksAgoTs(nonRepetitiveWeeks))).map { docs =>
          val restrictedPairs = docs
            .filter(_.accept)
            .filter(d => d.partnerId.isDefined && !d.partnerId.contains(CoordinatorActor.EmptyPartner))
            .map(d => MatchingPair(d.userId, d.partnerId.get))
            .map(_.code)
            .toSet
        PairingUtils.randomWithRestrictedPairs(candidates, restrictedPairs)
      } , 20 seconds)
}
