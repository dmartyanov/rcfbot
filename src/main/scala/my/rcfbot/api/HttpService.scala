package my.rcfbot.api

import my.rcfbot.domain.ResponseModel
import my.rcfbot.util.{ExecutionContextHelper, LoggerHelper}
import spray.json.RootJsonFormat

import scala.concurrent.Future

trait HttpService {
  this: ExecutionContextHelper with LoggerHelper =>

  def writeResponse[T](result: => Future[List[T]])(implicit jf: RootJsonFormat[T]) = result map {
    result => ResponseModel(false, result.map(m => jf.write(m)))
  } recover {
    case e: Exception =>
      log.error(s"Error in response processing: [${e.getMessage}]", e)
      ResponseModel(true, List(), Option(e.getMessage))
  }

}
