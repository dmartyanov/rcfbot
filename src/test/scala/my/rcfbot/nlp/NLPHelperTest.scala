package my.rcfbot.nlp

import org.scalatest.{FlatSpec, Matchers}

class NLPHelperTest extends FlatSpec with Matchers {

  "NLP Test" should "detect positive" in {
    NLPHelper.positive("yes") should be (true)
    NLPHelper.positive("1") should be (true)
    NLPHelper.positive("yep") should be (true)
    NLPHelper.positive("sie") should be (true)
    NLPHelper.positive("go") should be (true)
    NLPHelper.positive("yes, I will join no matter what") should be (true)
  }

  "NLP Test" should "not detect negative" in {
    NLPHelper.negative("yes") should be (false)
    NLPHelper.negative("yes, I will join no matter what") should be (false)
    NLPHelper.negative("1") should be (false)
    NLPHelper.negative("yep") should be (false)
    NLPHelper.negative("sie") should be (false)
    NLPHelper.negative("go") should be (false)
    NLPHelper.negative("yes, I will join no matter what") should be (false)
  }

  "NLP Test" should "not detect positive" in {
    NLPHelper.positive("no") should be (false)
    NLPHelper.positive("0") should be (false)
    NLPHelper.positive("nope") should be (false)
    NLPHelper.positive("no way") should be (false)
    NLPHelper.positive("no,  unfortunately I will be off this week") should be (false)
    NLPHelper.positive("no,  I have not come yet from PTO") should be (false)
  }

  "NLP Test" should "detect negative" in {
    NLPHelper.negative("no") should be (true)
    NLPHelper.negative("0") should be (true)
    NLPHelper.negative("nope") should be (true)
    NLPHelper.negative("no way") should be (true)
    NLPHelper.negative("no,  unfortunatelly I will be off this week") should be (true)
    NLPHelper.negative("no,  I have not come yet from PTO") should be (true)
  }


}
