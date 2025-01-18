package my.rcfbot.matching

import my.rcfbot.actors.CoordinatorActor

sealed trait AbstractPair {
  def code: String
}

case class MatchingPair(
                       p1: String,
                       p2: String,
                       ) extends AbstractPair {
  def code: String = List(p1, p2).sorted.mkString("-")
}

case class SinglePair(
                     p: String
                     ) extends AbstractPair {
  override def code: String = s"$p-${CoordinatorActor.EmptyPartner}"
}
