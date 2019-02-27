package my.rcfbot.util

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

/**
  * Created by dmartyanov on 2/11/16.
  */
trait AkkaComponent {
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
}
