package my.rcfbot.domain

import my.rcfbot.slack.models.SlackMessage

case class RcfSlackMessage(
                            id: String,
                            outText: Option[String] = None,
                            toIm: Option[String] = None,
                            toTs: Option[Long] = None,
                            inMsg: Option[SlackMessage] = None,
                            scn: Option[String] = None,
                            scnStep: Option[String] = None,
                          ) extends JsonModel
