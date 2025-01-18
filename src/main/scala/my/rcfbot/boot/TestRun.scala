package my.rcfbot.boot

import my.rcfbot.matching.PairingUtils
import my.rcfbot.nlp.NLPHelper

object TestRun extends App {

//  println(NLPHelper.score("no,  I have not come yet    from PTO"))
  println(PairingUtils.randomWithRestrictedPairs(List("a", "b", "c", "d", "e"), Set("a-b", "b-d")))
}
