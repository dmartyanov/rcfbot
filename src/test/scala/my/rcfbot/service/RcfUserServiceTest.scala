package my.rcfbot.service

import my.rcfbot.boot.Wired.{AkkaExecutionContext, ConfigurationImpl, LruUserCacheComponent, RCFAkkaSystem}
import my.rcfbot.domain.RcfUser
import my.rcfbot.mongo.RcfUserServiceImpl
import my.rcfbot.util.LoggerHelper
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

class RcfUserServiceTest extends FlatSpec with Matchers {

  implicit def await[T](f: Future[T]): T = Await.result(f, 20 seconds)

//  lazy val rcfUserServiceImpl = new RcfUserServiceImpl with AkkaExecutionContext with LruUserCacheComponent
//    with ConfigurationImpl with LoggerHelper with RCFAkkaSystem


//  "User Service" should "update user document" in {
//    val user: RcfUser = rcfUserServiceImpl.getUser("dmartyanov")
//    user.im should be (Some("DH813B91B"))
//
//    val updateUser = rcfUserServiceImpl.update(user.copy(im=Some("X")))
//    updateUser.im should be (Some("X"))
//
//    val user2: RcfUser = rcfUserServiceImpl.getUser("dmartyanov")
//    user2.im should be (Some("X"))
//
//    val finalUser = rcfUserServiceImpl.update(user)
//  }
}
