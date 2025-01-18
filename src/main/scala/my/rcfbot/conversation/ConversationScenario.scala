package my.rcfbot.conversation

import my.rcfbot.actions.ActionBuilder

object ConversationHelper {
//  val LAST = "last"

  def transition(scn: ConversationScenario, current: String, text: String): Option[ConversationTransition] =
    scn.transitions.get(current).flatMap { trs =>
      trs.find(_.check(text))
    }

}

case class ConversationScenario(
                                id: String,
                                first: String,
                                utterances: Map[String, String],
                                transitions: Map[String, Seq[ConversationTransition]],
                                params: Map[String, String] = Map()
                               )

case class ConversationTransition(
                                   check: String => Boolean,
                                   next: Option[String],
                                   actionBuilders: Seq[ActionBuilder[_]] = Seq()
                                 )
