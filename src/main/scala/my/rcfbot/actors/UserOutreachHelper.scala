package my.rcfbot.actors

import akka.actor.ActorSystem
import my.rcfbot.domain.RcfUser

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

trait UserOutreachHelper {

  implicit val ec: ExecutionContextExecutor
  val system: ActorSystem
  val testUserId: String

  def execDelayed(u: RcfUser, delayInMinutes: Int)(run: Runnable) =
    system.scheduler.scheduleOnce(userDelay(u, delayInMinutes) minutes, run)

  def runnable(proc: => Unit): Runnable = new Runnable {
    override def run(): Unit = proc
  }

  def userDelay(u: RcfUser, delayInMinutes: Int): Int =
    if (testUserId.equals(u.id)) {
      0
    } else {
      delayInMinutes + scala.util.Random.nextInt(delayInMinutes)
    }
}
