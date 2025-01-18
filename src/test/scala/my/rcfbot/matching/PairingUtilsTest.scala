package my.rcfbot.matching

import org.scalatest.{FlatSpec, Matchers}

class PairingUtilsTest extends FlatSpec with Matchers {

  "PairingUtils" should "exclude pair matching with repetitions" in {
    val cs = List("a", "b", "c", "d", "e")
    val existingPairs = List(MatchingPair("a", "c"), MatchingPair("a", "b"), MatchingPair("e", "d"))

    for (x <- 1 to 100) {
      val pairs = PairingUtils.randomWithRestrictedPairs(cs, existingPairs.map(_.code).toSet)
      existingPairs.exists(p => pairs.map(_.code).toSet.contains(p.code)) should be (false)
    }
  }
}
