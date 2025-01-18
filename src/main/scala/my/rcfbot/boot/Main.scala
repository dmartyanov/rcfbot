package my.rcfbot.boot

import akka.actor.Props
import akka.http.scaladsl.Http
import my.rcfbot.actors.{AdminActor, CommandHandler, ConversationsHub, CoordinatorActor}
import my.rcfbot.api.ApiService
import my.rcfbot.boot.Wired._
import my.rcfbot.slack.api.{SlackApiClient, SlackApiClientUtil}
import my.rcfbot.slack.rtm.SlackRtmConnectionActor
import my.rcfbot.util.LoggerHelper

import scala.language.postfixOps

object Main extends App with ConfigurationImpl with RCFAkkaSystem with LoggerHelper {

  override val log = logger[Main.type]

  startActors()

  startServer()


  def startActors() = {
    val token = System.getenv().get("SL_RCF_BOT_TOKEN")

    val live = conf.get[Boolean]("rcf.live", false)

    if(live) {
      log.info("RCF Bot is started in PRODUCTION mode")
    } else {
      log.info("RCF Bot is started in TEST mode")
    }

    val lightweight = true

    val apiClient = SlackApiClient(token, SlackApiClientUtil.defaultSlackApiBaseUri)

    val chatHubActor = system.actorOf(
      Props(new ConversationsHub(apiClient) with RcfUserMongoComponent
        with ConfigurationImpl with TransitionMongoComponent
        with SessionInviteMongoComponent with RcfSessionMongoComponent), "hub")

    val adminActor = system.actorOf(
      Props(new AdminActor(apiClient) with ConfigurationImpl with AkkaExecutionContext
        with RcfUserMongoComponent with UserChangeEventMongoHandlerComponent), "admin"
    )

    val coordinatorActor = system.actorOf(
      Props(new CoordinatorActor(apiClient, live) with RcfUserMongoComponent with PurelyRandomPairingComponent
        with RcfSessionMongoComponent with RcfParticipationMongoComponent with ConfigurationImpl), "coordinator"
    )

    val connectionActor = SlackRtmConnectionActor(apiClient, lightweight, chatHubActor,  "rtm")

    val commandHandlerActor = system.actorOf(
      Props(new CommandHandler(apiClient) with RcfUserMongoComponent with RcfSessionMongoComponent
        with RcfParticipationMongoComponent with ConfigurationImpl
        with SlackMessagePersistenceMongoComponent with SessionInviteMongoComponent), "commands")
  }


  def startServer() = {

    val serviceActor = new ApiService(system) with LoggerHelper with AkkaExecutionContext with ConfigurationImpl

    Http().bindAndHandle(serviceActor.route,
      conf.getOpt[String]("http.interface").getOrElse("0.0.0.0"),
      conf.getOpt[Int]("http.port").getOrElse(9898))
  }

}
