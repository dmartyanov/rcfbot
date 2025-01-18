package my.rcfbot.domain

case class SessionInviteDoc(id: String,
                            sessionId: String,
                            userId: String,
                            createTs: Long,
                            updateTs: Option[Long] = None) extends JsonModel
