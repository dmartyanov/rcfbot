package my.rcfbot.domain

case class TransitionDocument(id: String,
                              ts: Long,
                              lastScenario: Option[String] = None,
                              lastScenarioStep: Option[String] = None,
                              scenarioParams: Map[String, String] = Map()
                             ) extends JsonModel
