package my.rcfbot.actions

import my.rcfbot.actors.MessageLogEntry

sealed trait RcfAction {
  def im: String
}

case class ContactAdminsAction(text: String, im: String, log: Seq[MessageLogEntry] = Seq()) extends RcfAction

case class AddLocation(location: String, im: String) extends RcfAction

case class AddMemo(memo: String, im: String) extends RcfAction

//case class ActivateUser(im: String) extends RcfAction

case class CheckInitedSessions(im: String) extends RcfAction

case class DeactivateUser(im: String) extends RcfAction

case class RegisterUserWithinSession(sessionId: String, im: String, accept: Boolean) extends RcfAction

case class ReRegisterUserWithinSession(sessionId: String, prevSessionId: String, im: String, accept: Boolean) extends RcfAction

case class AddToWaitingList(sessionId: String, im: String, waitList: Boolean) extends RcfAction

case class AddMeetingConfirmation(sessionId: String, im: String, met: Boolean) extends RcfAction

case class AddMeetingFeedback(text: String, sessionId: String, im: String, met: Boolean) extends RcfAction

case class ReminderDeclineAction(text: String, im: String) extends RcfAction

case class StartActivationScenario(text: String, im: String) extends RcfAction

