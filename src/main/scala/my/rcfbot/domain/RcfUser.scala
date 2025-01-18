package my.rcfbot.domain

case class RcfUser(
                    id: String,
                    status: String,
                    createTs: Long,
                    channel: String,
                    updateTs: Option[Long] = None,
                    memo: Option[String] = None,
                    fullname: Option[String] = None,
                    slackId: Option[String] = None,
                    location: Option[String] = None,
                    im: Option[String] = None,
                    imTs: Option[Long] = None,
                    lastScenario: Option[String] = None,
                    lastScenarioTs: Option[Long] = None,
                    lastScenarioStep: Option[String] = None,
                    lastScenarioStepTs: Option[Long] = None,
                    tz_offset: Option[Int] = None
                  ) extends JsonModel
