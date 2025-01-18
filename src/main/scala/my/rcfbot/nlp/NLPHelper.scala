package my.rcfbot.nlp

object NLPHelper {
  val noRegEx = "n[a-z]{1,3}\\s*.*".r
  val yesRegEx = "y[a-z]{1,2}\\s*.*".r
  val siRegEx = "s[a-z]{1,4}\\s*.*".r

  val core = 10d

  def negative(s: String): Boolean = {
    val (ps, ns) = score(s.toLowerCase().replace("you", "").replace("thank", " "))
    ns >= ps
  }

  def positive(s: String): Boolean = {
    val (ps, ns) = score(s.toLowerCase().replace("you", "").replace("thank", " "))
    ps >= ns
  }

  def posEl(s: String): Boolean = {
    val ls = s.toLowerCase()
    ls.toLowerCase.equals("y") ||
      ls.contains("1") ||
      yesRegEx.pattern.matcher(ls).matches ||
      siRegEx.pattern.matcher(ls).matches ||
      ls.contains("go") ||
      ls.contains("sure")
  }

  def negEl(s: String): Boolean = {
    val ls = s.toLowerCase()
    ls.equals("n") ||
      ls.contains("0") ||
      noRegEx.pattern.matcher(ls).matches ||
      ls.contains("skip") ||
      ls.contains("no")
  }

  def score(s: String): (Double, Double) = {
    var pscore = 0d
    var nscore = 0d
    s.replaceAll("\\s+", " ").split(" ").zipWithIndex.foreach {
      case (el, index) =>
        if(posEl(el)) {
          pscore += core / (index + 1)
        }

        if(negEl(el)) {
          nscore += core / (index + 1)
        }
    }
    (pscore, nscore)
  }

}
