package my.rcfbot.boot

import my.rcfbot.boot.Wired.{ConfigurationImpl, RCFAkkaSystem}

object Main extends App with ConfigurationImpl with RCFAkkaSystem {

  println("Main is started")
}
