package my.rcfbot.actions

import my.rcfbot.domain.RcfSessionDocument
import my.rcfbot.slack.models.SlackMessage

trait ActionBuilder[T <: RcfAction] {
  def build(msg: SlackMessage, im: String): T
}

object AddLocationBuilder extends ActionBuilder[AddLocation] {
  override def build(msg: SlackMessage, im: String): AddLocation = AddLocation(msg.text, im)
}

object AddMemoBuilder extends ActionBuilder[AddMemo] {
  override def build(msg: SlackMessage, im: String): AddMemo = AddMemo(msg.text, im)
}

//object ActivateUserBuilder extends ActionBuilder[ActivateUser] {
//  override def build(msg: SlackMessage, im: String): ActivateUser = ActivateUser(im)
//}

object CheckInitedSessionsBuilder extends ActionBuilder[CheckInitedSessions] {
  override def build(msg: SlackMessage, im: String): CheckInitedSessions = CheckInitedSessions(im)
}

object DeactivateUserBuilder extends ActionBuilder[DeactivateUser] {
  override def build(msg: SlackMessage, im: String): DeactivateUser = DeactivateUser(im)
}

object ContactAdminsBuilder extends ActionBuilder[ContactAdminsAction] {
  override def build(msg: SlackMessage, im: String): ContactAdminsAction = ContactAdminsAction(msg.text, im)
}

class RegisterUserWithinSessionBuilder(session: RcfSessionDocument, accept: Boolean)
  extends ActionBuilder[RegisterUserWithinSession] {
  override def build(msg: SlackMessage, im: String): RegisterUserWithinSession =
    RegisterUserWithinSession(sessionId = session.id, im = im, accept = accept)
}

class ReRegisterUserWithinSessionBuilder(sn: RcfSessionDocument, so: RcfSessionDocument, accept: Boolean)
  extends ActionBuilder[ReRegisterUserWithinSession] {
  override def build(msg: SlackMessage, im: String): ReRegisterUserWithinSession =
    ReRegisterUserWithinSession(sessionId = sn.id, prevSessionId = so.id, im = im, accept = accept)
}

class AddToWaitingListBuilder(session: RcfSessionDocument, waitList: Boolean) extends ActionBuilder[AddToWaitingList] {
  override def build(msg: SlackMessage, im: String): AddToWaitingList =
    AddToWaitingList(sessionId = session.id, im = im, waitList = waitList)
}

class AddMeetingConfirmationBuilder(session: RcfSessionDocument, met: Boolean) extends ActionBuilder[AddMeetingConfirmation] {
  override def build(msg: SlackMessage, im: String): AddMeetingConfirmation =
    AddMeetingConfirmation(session.id, im, met)
}

class AddMeetingFeedbackBuilder(session: RcfSessionDocument, met: Boolean) extends ActionBuilder[AddMeetingFeedback] {
  override def build(msg: SlackMessage, im: String): AddMeetingFeedback =
    AddMeetingFeedback(msg.text, session.id, im, met)
}

object ReminderDeclineActionBuilder extends ActionBuilder[ReminderDeclineAction] {
  override def build(msg: SlackMessage, im: String): ReminderDeclineAction = ReminderDeclineAction(msg.text, im)
}

object StartActivationScenarioBuilder extends ActionBuilder[StartActivationScenario] {
  override def build(msg: SlackMessage, im: String): StartActivationScenario = StartActivationScenario(msg.text, im)
}


