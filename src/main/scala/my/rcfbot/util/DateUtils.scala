package my.rcfbot.util

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import my.rcfbot.domain.RcfSlackMessage

import scala.util.Try

object DateUtils {

  val sessionDateFormatter = DateTimeFormatter.ofPattern("MMM dd")

  val messageDateFormatter = DateTimeFormatter.ofPattern("EEE, MM/dd HH:mm:ss")

  def formatMillis(ms: Long) = sessionDateFormatter.format(
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.of("America/Los_Angeles"))
  )

  def to11AM(ms: Long): Long = {
    val x = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.of("America/Los_Angeles"))
    val hour = x.getHour
    val delta = hour - 11
    x.minusHours(delta).toInstant.toEpochMilli
  }

  def to3PM(ms: Long): Long = {
    val x = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.of("America/Los_Angeles"))
    val hour = x.getHour
    val delta = hour - 15
    x.minusHours(delta).toInstant.toEpochMilli
  }

  def parseTimeCode(tc: String): Option[Long] = {
    val now = Instant.now().toEpochMilli
    val q = tc.takeWhile(_.isDigit)
    val unit = tc.drop(q.length).trim.take(1)

    Try {
      DateUtils.codeTUMap.get(unit).map(_.toMillis(q.toInt)).map(now - _)
    }.toOption.flatten
  }

  val codeTUMap = Map(
    "d" -> TimeUnit.DAYS,
    "h" -> TimeUnit.HOURS,
    "m" -> TimeUnit.MINUTES
  )

  val OneWeekInMillis = TimeUnit.DAYS.toMillis(7)

  def slackMessageDate(m: RcfSlackMessage): String =  (m match {
    case msg: RcfSlackMessage if msg.inMsg.isDefined =>
      Try(messageDateFormatter.format(
        ZonedDateTime.ofInstant(
          Instant.ofEpochSecond(msg.inMsg.get.ts.takeWhile(_.isDigit).toLong),
          ZoneId.of("America/Los_Angeles")
        ))).toOption

    case msg: RcfSlackMessage =>
      Try(messageDateFormatter.format(
        ZonedDateTime.ofInstant(
          Instant.ofEpochMilli(msg.toTs.get),
          ZoneId.of("America/Los_Angeles")
        ))).toOption
  }).getOrElse("")


  def getWeeksAgoTs(weeks: Int) = {
    val now = Instant.now().toEpochMilli
    val weeksInMillis = weeks * OneWeekInMillis
    now - weeksInMillis
  }
}
