package my.rcfbot.matching

trait MatchingService {

  def pair(candidates: List[String]): List[AbstractPair]
}

trait MatchingComponent {
  def matchingService: MatchingService
}