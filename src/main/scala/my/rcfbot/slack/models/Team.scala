package my.rcfbot.slack.models

import play.api.libs.json._

case class Team(id: String,
                name: String,
                domain: String,
                enterprise_id: Option[String],
                enterprise_name: Option[String],
                email_domain: Option[String],
                msg_edit_window_mins: Option[Int],
                over_storage_limit: Option[Boolean],
                prefs: Option[JsValue],
                plan: Option[String])
