package my.rcfbot.matching

import com.typesafe.scalalogging.Logger
import my.rcfbot.util.LoggerHelper

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

object PairingUtils extends LoggerHelper {

  override val log: Logger = Logger[PairingUtils.type]

  def randomPairing(candidates: List[String]) : List[AbstractPair] =
    scala.util.Random.shuffle(candidates).sliding(2, 2) flatMap  {
      case Seq(p1, p2) =>
        List(MatchingPair(p1, p2))

      case Seq(single) =>
        List(SinglePair(single))

      case _ =>
        log.error("It should never happen")
        List.empty[AbstractPair]
    } toList

  def randomWithRestrictedPairs(candidates: List[String], rps: Set[String]): List[AbstractPair] = {
    randomWithRestrictedPairsTailRec(scala.util.Random.shuffle(candidates), rps)
  }

  def randomWithRestrictedPairsTailRec(cs: List[String], rps: Set[String]): List[AbstractPair] = cs match {
    case first :: second :: tail =>
      val m = MatchingPair(first, second)
      if (rps.contains(m.code)) {
        log.warn(s" Repetitive pair ${m.code} is detected and an array will be reshuffled")
        randomWithRestrictedPairsTailRec(scala.util.Random.shuffle(cs), rps)
      } else {
        List(m) ++ randomWithRestrictedPairsTailRec(tail, rps)
      }
    case Seq(single) => List(SinglePair(single))
    case Seq() => List.empty
  }
}
