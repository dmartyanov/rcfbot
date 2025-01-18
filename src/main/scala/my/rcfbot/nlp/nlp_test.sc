import my.rcfbot.nlp.NLPHelper
import my.rcfbot.nlp.NLPHelper.{siRegEx, yesRegEx}

val noRegEx = "n[a-z]{1,3}\\s*.*".r
val yesRegEx = "y[a-z]{1,2}\\s*.*".r
val siRegEx = "s[a-z]{2,4}\\s*.*".r

NLPHelper.positive("Yes,  I would love to")
NLPHelper.positive("Yes")
NLPHelper.positive("Y")

NLPHelper.negative("No, I am busy today")
NLPHelper.negative("No")

val ls = "No, I am busy today".toLowerCase.replace("you", "").replace("thank", " ").replace("yet", " ")
ls.equals("y")
ls.equals("1")
yesRegEx.pattern.matcher(ls).matches
siRegEx.pattern.matcher(ls).matches
ls.contains("go")
ls.contains("sure")

ls.equals("y") || ls.equals("1") || yesRegEx.pattern.matcher(ls).matches ||
  siRegEx.pattern.matcher(ls).matches || ls.contains("go") || ls.contains("sure")

NLPHelper.positive("No, I am busy this week")

NLPHelper.positive("no,  I have not come yet  from PTO")


val s = "no,  I have not come yet    from PTO"
s.replaceAll("\\s+", " ").split(" ")


NLPHelper.score("no,  I have not come yet    from PTO")

NLPHelper.negative("no")

NLPHelper.score("yes no no")

NLPHelper.score("yes no no no")