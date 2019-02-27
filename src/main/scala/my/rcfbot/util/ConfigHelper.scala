package my.rcfbot.util

import com.typesafe.config.Config

import scala.reflect.runtime.universe._
import scala.collection.JavaConverters._

import scala.util.Try
import scala.language.{implicitConversions, postfixOps}

/**
  * Created by dmartyanov on 11/9/16.
  */
trait ConfigHelper {
  val conf: Config

  implicit def config(c: Config): ExtendedConfiguration = new ExtendedConfiguration(c)
}

class ExtendedConfiguration(config: Config) {
  private val StringTag = typeTag[String]
  private val ScalaDurationTag = typeTag[scala.concurrent.duration.Duration]
  private val StringListTag = typeTag[List[String]]
  private val BooleanTag = typeTag[Boolean]

  def getOpt[T](path: String)(implicit tag: TypeTag[T]): Option[T] = Try {(tag match {
    case StringTag => config.getString(path)
    case ScalaDurationTag => config.getDuration(path, scala.concurrent.duration.MILLISECONDS)
    case TypeTag.Int => config.getInt(path)
    case TypeTag.Double => config.getDouble(path)
    case TypeTag.Long => config.getLong(path)
    case StringListTag => config.getStringList(path).asScala.toList
    case BooleanTag => config.getBoolean(path)
    case _ => throw new IllegalArgumentException(s"Configuration option type $tag not implemented")
  }).asInstanceOf[T]} toOption

  def get[T](path: String, default: => T)(implicit tag: TypeTag[T]) = getOpt(path).getOrElse(default)

  def get[T](path: String)(implicit tag: TypeTag[T]) = getOpt(path)
    .getOrElse(throw new RuntimeException(s"Configuration value at path $path not found"))
}