package my.rcfbot.api

import my.rcfbot.domain.{PlainResponse, ResponseModel}
import spray.json.DefaultJsonProtocol

object ApiProtocol extends DefaultJsonProtocol {

  implicit val responseFormat = jsonFormat3(ResponseModel)
  implicit val plainResponseFmt = jsonFormat1(PlainResponse)
}
