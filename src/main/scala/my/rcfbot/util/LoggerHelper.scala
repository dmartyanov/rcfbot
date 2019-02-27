package my.rcfbot.util

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import scala.reflect.ClassTag

/**
 * Created by d.a.martyanov on 24.12.14.
 */
trait LoggerHelper {
  this: LoggerHelper =>

  def logger[T](implicit cls: ClassTag[T]) = com.typesafe.scalalogging.Logger(LoggerFactory.getLogger(cls.runtimeClass))

  val log: Logger
}
