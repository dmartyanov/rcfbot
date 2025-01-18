package my.rcfbot.stats

case class SessionStats(
                         invitedNo: Int,
                         acceptNo: Int,
                         declineNo: Int,
                         feedbackNo: Int,
                         meetingsSetNo: Int,
                         meetingHappenedNo: Int,
                         meetingNotHappenedNo: Int,
                         retentionRate: Double,
                         meetingNotHappenedReasons: Map[String, Map[String, String]],
                         feedbackDistribution: Map[Int, Int],
                         otherFeedback: Seq[String]
                       )