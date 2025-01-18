package my.rcfbot.domain

case class UserParticipationDoc(
                                 id: String,
                                 userId: String,
                                 sessionId: String,
                                 createTs: Long,
                                 accept: Boolean,
                                 inTime: Option[Boolean] = None,
                                 feedback: Option[String] = None,
                                 partnerId: Option[String] = None,
                                 success: Option[Boolean] = None,
                                 waitingList: Option[Boolean] = None,
                                 prevSessionId: Option[String] = None
                               ) extends JsonModel


case class SessionParticipationAgg(
                                  id: String,
                                  count: Int
                                  ) extends JsonModel