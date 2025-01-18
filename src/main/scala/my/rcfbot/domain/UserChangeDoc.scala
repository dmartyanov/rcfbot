package my.rcfbot.domain

import my.rcfbot.slack.models.User

case class UserChangeDoc(id: String,
                         user: User,
                         createTs: Long) extends JsonModel
