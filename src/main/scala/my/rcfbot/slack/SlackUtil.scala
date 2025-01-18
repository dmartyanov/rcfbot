package my.rcfbot.slack

import my.rcfbot.slack.models.SlackMessage

object SlackUtil {

  private val mentionrx = """<@(\w+)>""".r

  def extractMentionedIds(text: String): Seq[String] = {
    mentionrx.findAllMatchIn(text).toVector.map(_.subgroups.head)
  }

  def mentionsId(text: String, id: String): Boolean = {
    mentionrx.findAllMatchIn(text).toVector.map(_.subgroups.head).contains(id)
  }

  def isDirectMsg(m: SlackMessage): Boolean = {
    m.channel.startsWith("D")
  }
}
