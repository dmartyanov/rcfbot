package my.rcfbot.domain

import spray.json.JsValue

/**
  * Created by dmartyanov on 10/9/16.
  */
case class ResponseModel(
                          error: Boolean,
                          payload: List[JsValue],
                          errorMsg: Option[String] = None
                        )