import java.time.Instant
import java.util.concurrent.TimeUnit

import my.rcfbot.service.RcfUserService

scala.util.Random.shuffle(List(1,2,3,4))


val x = scala.util.Random.shuffle(1 to 11)
x
x.sliding(2, 2).toList

RcfUserService.parseEmail("dmartyanov@paypal.com")

val milliSecondsInDay = 60 * 60 * 24 * 1000
val now = Instant.now.toEpochMilli

now + (7 * milliSecondsInDay)

import scala.concurrent.duration._

val t1 = 5 minutes
val t2 = 3 minutes

t1 - t2
for (i <- 1 to 20) println(s"$i: ${scala.util.Random.nextInt(30)}")
val OneWeekInMillis = TimeUnit.DAYS.toMillis(7)

