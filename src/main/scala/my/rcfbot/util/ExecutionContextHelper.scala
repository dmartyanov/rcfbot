package my.rcfbot.util

import scala.concurrent.ExecutionContext

/**
  * Created by dmartyanov on 11/9/16.
  */
trait ExecutionContextHelper {
  implicit def ec: ExecutionContext
}
