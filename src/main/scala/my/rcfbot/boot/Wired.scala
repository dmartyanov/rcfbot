package my.rcfbot.boot

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.ConfigFactory
import my.rcfbot.caching.UserHMCacheServiceImpl
import my.rcfbot.domain.RcfUser
import my.rcfbot.matching.{MatchingComponent, MatchingService, PurelyRandomMatchingServiceImpl}
import my.rcfbot.mongo._
import my.rcfbot.service._
import my.rcfbot.util._

import scala.concurrent.ExecutionContext

object Wired {

  lazy val actorSystem = ActorSystem("rcf")
  lazy val actorMaterializer = ActorMaterializer(ActorMaterializerSettings(actorSystem))(actorSystem)

  trait RCFAkkaSystem extends AkkaComponent {
    override implicit val system: ActorSystem = actorSystem
    override implicit val materializer: ActorMaterializer = actorMaterializer
  }

  trait AkkaExecutionContext extends ExecutionContextHelper {
    override implicit def ec: ExecutionContext = actorSystem.dispatcher
  }

  lazy val fixedThreadPool = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(12))

  trait FixedPoolExecutionContext extends ExecutionContextHelper {
    override implicit def ec: ExecutionContext = fixedThreadPool
  }

  private lazy val configImpl = ConfigFactory.load()

  trait ConfigurationImpl extends ConfigHelper {
    override lazy val conf = configImpl
  }

  private lazy val lruUserCahcheService = new UserHMCacheServiceImpl with ConfigurationImpl with AkkaExecutionContext

  trait LruUserCacheComponent extends CacheComponent[RcfUser] {
    override def cacheService: CacheService[RcfUser] = lruUserCahcheService
  }


  lazy val rcfUserServiceImpl = new RcfUserServiceImpl with AkkaExecutionContext with LruUserCacheComponent
    with ConfigurationImpl with LoggerHelper with RCFAkkaSystem

  trait RcfUserMongoComponent extends RcfUserComponent {
    override def rcfUserService: RcfUserService = rcfUserServiceImpl
  }

  lazy val rcfSessionServiceImpl = new RcfSessionManagerImpl with ConfigurationImpl
    with LoggerHelper with AkkaExecutionContext

  trait RcfSessionMongoComponent extends RcfSessionComponent {
    override def sessionManager: RcfSessionManager = rcfSessionServiceImpl
  }

  lazy val rcfParticipationImpl = new ParticipationServiceImpl with ConfigurationImpl
    with LoggerHelper with AkkaExecutionContext

  trait RcfParticipationMongoComponent extends ParticipationComponent {
    override def participationService: ParticipationService = rcfParticipationImpl
  }

  lazy val rcfMessageServiceImpl = new RcfMessageServiceImpl with ConfigurationImpl
    with LoggerHelper with AkkaExecutionContext

  trait SlackMessagePersistenceMongoComponent extends SlackMessagePersistenceComponent {
    override def messageService: SlackMessagePersistenceService = rcfMessageServiceImpl
  }

  lazy val rcfUserChangeEventHandlerImpl = new UserChangeEventMongoHandler with ConfigurationImpl
    with LoggerHelper with AkkaExecutionContext

  trait UserChangeEventMongoHandlerComponent extends UserChangeEventHandlerComponent {
    override def userChangeEventHandler: UserChangeEventHandler = rcfUserChangeEventHandlerImpl
  }

  lazy val transitionMongoService = new TransitionServiceImpl with ConfigurationImpl
    with LoggerHelper with AkkaExecutionContext

  trait TransitionMongoComponent extends TransitionComponent {
    override def transitionService: TransitionService = transitionMongoService
  }

  lazy val sessionInviteMongoService = new SessionInviteServiceImpl with ConfigurationImpl
    with LoggerHelper with AkkaExecutionContext

  trait SessionInviteMongoComponent extends SessionInviteComponent {
    override def sessionInviteService: SessionInviteService = sessionInviteMongoService
  }

  lazy val purelyRandomPairingService = new PurelyRandomMatchingServiceImpl
  trait PurelyRandomPairingComponent extends MatchingComponent {
    override def matchingService: MatchingService = purelyRandomPairingService
  }

}
