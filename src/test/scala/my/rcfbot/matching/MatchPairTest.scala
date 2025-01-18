package my.rcfbot.matching

import org.scalatest.{FlatSpec, Matchers}

class MatchPairTest extends FlatSpec with Matchers {

  "MatchPair" should "build right code" in {
    MatchingPair("a", "b").code should be ("a-b")
    MatchingPair("b", "a").code should be ("a-b")
  }
}
