package my.rcfbot.conversation

import my.rcfbot.actions._
import my.rcfbot.domain.{RcfSessionDocument, RcfUser, TransitionDocument}
import my.rcfbot.nlp.NLPHelper
import my.rcfbot.service.SessionInviteService
import my.rcfbot.util.SessionHelper

object ScenariosRegistry extends SessionHelper {

  val default = (s: String) => true

  val activation = ConversationScenario(
    id = "user_activation",
    first = "welcome",
    utterances = Map(
      "welcome" ->
        """
          | Greeting from RandomCoffee! :smile:
          |
          | Every week I will be offering you an interesting person from PayPal to meet with, randomly selected among other participants.
          |
          | To start, kindly answer two questions and read a short instruction. Let me know when you are ready.
          | Don't worry, I will not immediately set up a meeting for you, all the RandomCoffee meetings happen during the sessions and you will be asked to enroll into each session separately,
          | if you don't confirm enrollment into the session before it is started - there will be no partner for you during that session.
        """.stripMargin,

      "location" ->
        """
          |Please input your primary location :earth:
        """.stripMargin,

      "memo" ->
        """
          |Please tell me few words about yourself and your role in PayPal
          |
          |Know that your partner will see what you write
        """.stripMargin,

      "instruction" ->
        """
          |Great! :celebrate: Now your are registered for RandomCoffee meetings
          |
          |Here is a short instruction for you:
          | :one: 24 hour before the start of each session I will send you announcement and ask you whether you would like to participate or not
          |
          | :two: You will receive a match when the next RandomCoffee session is started if you have confirmed your participation before that moment
          |
          | :three: Choose the time and venue that works for you both. Eg, If you are based in San Jose, you can meet at the Oasis. If you are in different locations, you can set up a call.
          |
          | :four: If your partner doesn’t respond, send a message to this chat and we will pair you up with another person
          |
          | :five: To stop participating in Random Coffee meetings, slack `STOP`
          |
          | If you have any questions, just slack them here
        """.stripMargin

    ),

    transitions = Map(
      "welcome" -> List(ConversationTransition(default, Some("location"))),

      "location" -> List(ConversationTransition(default, Some("memo"), Seq(AddLocationBuilder))),

      "memo" -> List(ConversationTransition(default, Some("instruction"),
        Seq(AddMemoBuilder, CheckInitedSessionsBuilder))),
    )
  )

  val reminder_activation = ConversationScenario(
    id = "enforced_activation",
    first = "reminder",
    utterances = Map(
      "reminder" ->
        """
          | Hi, I have noticed that you register with RandomCoffee bot, but haven't finished activation process.
          | Before starting to participate in RandomCoffee meetings I need some information about your location and self introduction that your partner could see.
          |
          | Would you like to complete the activation right now ?
        """.stripMargin,

      "decline" ->
        """
          | That's fine, how would you like me to process further ?
          | :one: - send me a reminder sometime later
          | :two: - don't send me any messages
        """.stripMargin,

      "remind_me_again" ->
        """
          | Ok,  maybe in a week or so I will send you one more reminder, you also can trigger user activation scenario by sending `register` to this chat.
          |
          | Have a nice day!
        """.stripMargin,

      "remind_me_again_wtf" ->
        """
          | I am not sure I got your message, I will contact you later or you can send me any questions you have to this private channel
          |
          | Have a nice day!
        """.stripMargin,

      "bye" ->
        """
          | Good Bye, we will miss you :smile:, if you would like to become active again - just send me `register`
        """.stripMargin,
    ),

    transitions = Map(
      "reminder" -> List(
        ConversationTransition(NLPHelper.positive, None, Seq(StartActivationScenarioBuilder)),
        ConversationTransition(default, Some("decline"))
      ),

      "decline" -> List(
        ConversationTransition(NLPHelper.positive, None, Seq(ReminderDeclineActionBuilder)),
      )
    )
  )

  def buildSessionOnboardingScenario(session: RcfSessionDocument): ConversationScenario = ConversationScenario(
    id = SessionInviteService.SessionInviteScenario,
    first = "invite",
    utterances = Map(
      "invite" ->
        s"""
           |Hi!
           |
           |RandomCoffee Meetings keep going.
           |Would you like to participate in the next session (${sessToStr(session)}) ?
      """.stripMargin,

      "accept" ->
        """
          |Great! will send you your partner contacts when the session is started. Good Luck!
        """.stripMargin,

      "decline" ->
        """
          |Okay. We'll skip you this time. But will try you again when the next session is started.
        """.stripMargin,

      "closed" ->
        s"""
           |I am sorry,  the registration for the session (${sessToStr(session)}) is already closed. Will contact you when the next session is started
        """.stripMargin
    ),

    transitions = Map(
      "invite" -> List(
        ConversationTransition(NLPHelper.positive, None, Seq(new RegisterUserWithinSessionBuilder(session, true))),
        ConversationTransition(default, None, Seq(new RegisterUserWithinSessionBuilder(session, false)))
      )
    ),

    params = Map(SessionInviteService.SessionKey -> session.id)
  )

  def buildSessionReOnboarding(sn: RcfSessionDocument, so: RcfSessionDocument): ConversationScenario = ConversationScenario(
    id = SessionInviteService.SessionReInviteScenario,
    first = "invite",
    utterances = Map(
      "invite" ->
        s"""
          |Hi!
          |
          |RandomCoffee Meetings keep going.
          |Would you like to participate in the next session (${sessToStr(sn)}) ?
      """.stripMargin,

      "accept_n_met" ->
        s"""
          |Great! will send you your partner contacts when the session is started.
          |
          |What about previous RandomCoffee session (${sessToStr(so)}) which is finished, did you have a chance to meet your partner ?
        """.stripMargin,

      "decline_n_met" ->
        s"""
          |Okay. We'll skip you this time. But will try you again when the next session is started.
          |
          |What about previous RandomCoffee session (${sessToStr(so)}) which is finished, did you have a chance to meet your partner ?
        """.stripMargin,

      "closed_n_met" ->
        s"""
           |I am sorry,  the registration for the session (${sessToStr(sn)}) is already closed. Will contact you when the next session is started.
           |
           |What about previous RandomCoffee session (${sessToStr(so)}) which is finished, did you have a chance to meet your partner ?
        """.stripMargin,
      "met" ->
        s"""
           | Great! How was your meeting ?
           | :one: - awesome, :two: - good, :three: - fine (input a number)
         """.stripMargin,
      "not_met" ->
        s"""
           | Could you please provide more details ? We are trying to provide the best experience and your feedback is extremely important
         """.stripMargin,
      "gl" ->
        s"""
           | Thanks, good luck with future meetings !
       """.stripMargin
    ),

    transitions = Map(
      "invite" -> List(
        ConversationTransition(NLPHelper.positive, None, Seq(new ReRegisterUserWithinSessionBuilder(sn, so, true))),
        ConversationTransition(default, None, Seq(new ReRegisterUserWithinSessionBuilder(sn, so, false)))
      ),
      "accept_n_met" -> List(
        ConversationTransition(NLPHelper.positive, Some("met"), Seq(new AddMeetingConfirmationBuilder(so, true))),
        ConversationTransition(default, Some("not_met"), Seq(new AddMeetingConfirmationBuilder(so, false)))
      ),
      "decline_n_met" -> List(
        ConversationTransition(NLPHelper.positive, Some("met"), Seq(new AddMeetingConfirmationBuilder(so, true))),
        ConversationTransition(default, Some("not_met"), Seq(new AddMeetingConfirmationBuilder(so, false)))
      ),
      "closed_n_met" -> List(
        ConversationTransition(NLPHelper.positive, Some("met"), Seq(new AddMeetingConfirmationBuilder(so, true))),
        ConversationTransition(default, Some("not_met"), Seq(new AddMeetingConfirmationBuilder(so, false)))
      ),
      "met" -> List(
        ConversationTransition(default, Some("gl"), Seq(new AddMeetingFeedbackBuilder(so, true)))
      ),
      "not_met" -> List(
        ConversationTransition(default, Some("gl"), Seq(new AddMeetingFeedbackBuilder(so, false)))
      )
    ),

    params = Map(
      SessionInviteService.SessionKey -> sn.id,
      SessionInviteService.PrevSessionKey -> so.id
    )
  )

  def buildSessionClosureScenario(s: RcfSessionDocument): ConversationScenario = ConversationScenario(
    id = SessionInviteService.SessionClosureScenario,
    first = "inform",
    utterances = Map(
      "inform" ->
        s"""
           |RandomCoffee session (${sessToStr(s)}) is finished, did you have a chance to meet your partner ?
         """.stripMargin,
      "met" ->
        s"""
           | Great! How was your meeting ?
           | :one: - Awesome,    :two: - Good,    :three: - Fine   (input a number)
         """.stripMargin,
      "not_met" ->
        s"""
           | Could you please provide more details ? We are trying to provide the best experience and your feedback is extremely important
         """.stripMargin,
      "gl" ->
        s"""
           | Thanks, good luck with future meetings !
       """.stripMargin
    ),
    transitions = Map(
      "inform" -> List(
        ConversationTransition(NLPHelper.positive, Some("met"), Seq(new AddMeetingConfirmationBuilder(s, true))),
        ConversationTransition(default, Some("not_met"), Seq(new AddMeetingConfirmationBuilder(s, false)))
      ),
      "met" -> List(ConversationTransition(default, Some("gl"), Seq(new AddMeetingFeedbackBuilder(s, true)))),
      "not_met" -> List(ConversationTransition(default, Some("gl"), Seq(new AddMeetingFeedbackBuilder(s, false))))
    ),

    params = Map(
      SessionInviteService.SessionKey -> s.id,
    )
  )

  def buildInformPartnersScenario(session: RcfSessionDocument, partner: RcfUser): ConversationScenario = ConversationScenario(
    id = "know_your_partner",
    first = "partner",
    utterances = Map(
      "partner" ->
        s"""
         |Hi!
         |
         |For the session (${sessToStr(session)}) your RandomCoffee partner will be ${partner.fullname.getOrElse("[no_name]")} (<@${partner.id}>) from ${partner.location.getOrElse("[no_location]")} !
         |Self introduction: `${partner.memo.getOrElse("")}`
         |
         |If you have any questions just let me know in this channel. Good Luck!
       """.stripMargin
    ),
    transitions = Map()
  )

  def buildHalfSessionNotification(sess: RcfSessionDocument): ConversationScenario = ConversationScenario(
    id = "half_reminder",
    first = "half_session",
    utterances = Map(
      "half_session" ->
        s"""
           |Hey, just a gentle reminder the session (${sessToStr(sess)}) is close to its half, don't hesitate to contact your partner if haven't yet
       """.stripMargin
    ),
    transitions = Map()
  )

  def buildSinglePersonScenario(s: RcfSessionDocument, n: Int): ConversationScenario = ConversationScenario(
    id = "single",
    first = "single",
    utterances = Map(
      "single" ->
        s"""
           |Hi, in the session ${sessToStr(s)} we have odd number of participants and unfortunately we could not find a partner for you, would you like me to add you to the waiting list ?
      """.stripMargin,

      "waiting_list" ->
        s"""
           |Great! So if anybody could not reach out the partner we will contact you and you both could have a RandomCoffee meeting in this session
       """.stripMargin,

      "decline" ->
        s"""
           |Got your point! I will contact you when the next session is started
         """.stripMargin
    ),
    transitions = Map(
      "single" -> List(
        ConversationTransition(NLPHelper.positive, Some("waiting_list"), Seq(new AddToWaitingListBuilder(s, true))),
        ConversationTransition(default, Some("decline"), Seq(new AddToWaitingListBuilder(s, false)))
      )
    )
  )

  def customMessageScenario(text: String, params: Map[String, String]): ConversationScenario = ConversationScenario(
    id = "custom_message",
    first = "msg",
    utterances = Map("msg" -> text),
    transitions = Map(),
    params = params
  )

  val staticScenarios = Map(
    activation.id -> activation,
    reminder_activation.id -> reminder_activation
  )

}
