package my.rcfbot.boot

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.ConfigFactory
import my.rcfbot.util.{AkkaComponent, ConfigHelper, ExecutionContextHelper}

import scala.concurrent.ExecutionContext

object Wired {

  lazy val actorSystem = ActorSystem("rcfbot")
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

}
