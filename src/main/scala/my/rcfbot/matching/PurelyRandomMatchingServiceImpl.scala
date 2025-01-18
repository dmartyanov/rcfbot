package my.rcfbot.matching

class PurelyRandomMatchingServiceImpl extends MatchingService {

  override def pair(candidates: List[String]): List[AbstractPair] =
    PairingUtils.randomPairing(candidates)
}
