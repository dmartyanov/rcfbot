package my.rcfbot.mongo

import my.rcfbot.domain._
import my.rcfbot.slack.models.{SlackMessage, User, UserProfile}
import spray.json.DefaultJsonProtocol

object Documents extends DefaultJsonProtocol {

  //RcfUserDOcument
  implicit val rcfUserFmt = jsonFormat16(RcfUser)

  //Slack User Document
  implicit val slackUserProfile = jsonFormat11(UserProfile)
  implicit val slackUserFmt = jsonFormat16(User)
  implicit val userChangeDocFmt = jsonFormat3(UserChangeDoc)
  implicit val transitionDocFmt = jsonFormat5(TransitionDocument)

  //slack messages
  implicit val slackMessage = jsonFormat6(SlackMessage)
  implicit val rcfSlackMessageFmt = jsonFormat7(RcfSlackMessage)

  //session
  implicit val sessionDocFmt = jsonFormat10(RcfSessionDocument)
  implicit val sessionInviteFmr = jsonFormat5(SessionInviteDoc)

  //participation
  implicit val participationDocFmt = jsonFormat11(UserParticipationDoc)
  implicit val sessionParticipationDOcFmt = jsonFormat2(SessionParticipationAgg)
}
