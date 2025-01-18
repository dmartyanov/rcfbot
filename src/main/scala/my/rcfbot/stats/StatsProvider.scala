package my.rcfbot.stats

import akka.actor.ActorLogging
import my.rcfbot.actors.CoordinatorActor
import my.rcfbot.domain.RcfSessionDocument
import my.rcfbot.service.{ParticipationComponent, RcfSessionComponent, SessionInviteComponent}
import my.rcfbot.util.SessionHelper

import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

trait StatsProvider {
  this: RcfSessionComponent with ParticipationComponent with SessionInviteComponent with ActorLogging with SessionHelper =>

  implicit val ec: ExecutionContextExecutor

  def buildStats(sessionId: String): Future[SessionStats] =
    (for {
      partDocs <- participationService.usersForSession(sessionId)
      invites <- sessionInviteService.getInvitesForSession(sessionId)
      prolongations <- participationService.usersFromPreviousSession(sessionId)
    } yield (partDocs, invites, prolongations)) map { case (partDocs, invites, prolongations) =>
      var acceptCount = 0
      var declineCount = 0
      var feedbackCount = 0
      val meetingsArranged = mutable.Map[String, Option[Boolean]]()
      val feedbackStats = mutable.Map[Int, Int]()
      val stringFeedback = mutable.Map[String, String]()
      val meetingsNotHappenedReasons = mutable.Map[String, Map[String, String]]()

      partDocs.foreach { doc =>
        //acceptance
        if (doc.accept) {
          acceptCount += 1
        } else {
          declineCount += 1
        }

        //partner
        if (doc.partnerId.isDefined && !doc.partnerId.contains(CoordinatorActor.EmptyPartner)) {
          val partnerId = doc.partnerId.get
          val meetingKey = buildPairKey(doc.userId, partnerId)
          val state = meetingsArranged.get(meetingKey).flatten
          (state, doc.success) match {
            case (None, success) => meetingsArranged.put(meetingKey, success)
            case (Some(false), Some(true)) => meetingsArranged.put(meetingKey, Some(true))
            case (_, _) => ()
          }

          if (doc.success.contains(false)) {
            if (!meetingsNotHappenedReasons.contains(meetingKey)) {
              meetingsNotHappenedReasons.put(
                meetingKey, Map(doc.userId -> doc.feedback.getOrElse("unkz"))
              )
            } else {
              val curr = meetingsNotHappenedReasons(meetingKey)
              meetingsNotHappenedReasons.put(
                meetingKey, curr + (doc.userId -> doc.feedback.getOrElse("unkz"))
              )
            }
          }

          doc.feedback.foreach { feedback =>
            feedbackCount += 1
            Try(feedback.toInt) match {
              case Success(f) if f >= 1 && f <= 3 =>
                feedbackStats.put(f, feedbackStats.getOrElse(f, 0) + 1)
              case Failure(err) =>
                if (doc.success.contains(true)) {
                  stringFeedback.put(doc.userId, feedback)
                }
            }
          }

        } else {
          if (doc.accept && doc.inTime.contains(true) && !doc.partnerId.contains(CoordinatorActor.EmptyPartner)) {
            val msg = s"There is no partner for ${doc.userId} during session $sessionId, This should not happen!!!"
            log.error(new IllegalStateException("No Partner"), msg)
          }
        }
      }

      val invitesNo =
        if(sessionId.equals("258d737d-1d5f-4171-b930-bbc1f5ef1afa")) {
          13
        } else {
          invites.size
        }

      val retainRate = prolongations.count(_.accept) / (meetingsArranged.size * 2).toDouble
      SessionStats(
        invitedNo = invitesNo,
        acceptNo = acceptCount,
        declineNo = declineCount,
        feedbackNo = feedbackCount,
        meetingsSetNo = meetingsArranged.size,
        meetingHappenedNo = meetingsArranged.count(e => e._2.contains(true)),
        meetingNotHappenedNo = meetingsArranged.count(e => e._2.contains(false)),
        retentionRate = retainRate,
        meetingNotHappenedReasons = meetingsNotHappenedReasons.toMap,
        feedbackDistribution = feedbackStats.toMap,
        otherFeedback = stringFeedback.values.toList
      )
    }


  def buildPairKey(u1: String, u2: String) = Seq(u1, u2).sorted.mkString("_")

  def statsToMessage(s: RcfSessionDocument, stats: SessionStats, public: Boolean) = {
    val fb = feedbackBlock(s, stats)
    s"""Hi, here are some facts about RandomCoffee session [${sessToStr(s)}]
       |
       | Invitations were sent to ${stats.invitedNo} persons:
       | accepted - ${f"${(stats.acceptNo.toFloat / stats.invitedNo.toFloat) * 100}%2.0f"} %, declined - ${f"${(stats.declineNo.toFloat / stats.invitedNo.toFloat) * 100}%2.0f"} %,  ignored - ${f"${((stats.invitedNo - stats.declineNo.toFloat - stats.acceptNo.toFloat) / stats.invitedNo.toFloat) * 100}%2.0f"} %
       |
       | We set up ${stats.meetingsSetNo} meetings
       |    ${stats.meetingHappenedNo} - successfully happened
       |    ${stats.meetingNotHappenedNo} - did not happen
       |    ${stats.meetingsSetNo - stats.meetingHappenedNo - stats.meetingNotHappenedNo} - no feedback provided
       | ${privateReport(stats, public)}
       | $fb
       |
       | ${f"${stats.retentionRate * 100}%2.0f"} % of [${sessToStr(s)}] session participants decided to enroll into the next session
       | :celebrate:
     """.stripMargin
  }

  def privateReport(stats: SessionStats, public: Boolean) = public match {
    case true => ""
    case false =>
      stats.meetingNotHappenedReasons.map {
        case (pair, replies) =>
          val personsReplies = replies.map {
            case (corpId, reply) =>
              s"$corpId: `$reply` \n"
          } mkString "\n"
          s"""
            |    `$pair`
            | $personsReplies
          """.stripMargin
      } mkString ""
  }

  def feedbackBlock(s: RcfSessionDocument, stats: SessionStats): String = {
    val total = (stats.feedbackDistribution.values.sum + stats.otherFeedback.length).toFloat
    if (total > 0) {
      s"""
         | Participants feedback:
         | Awesome - ${f"${(stats.feedbackDistribution.getOrElse(1, 0) / total) * 100}%2.0f"} %,    Good - ${f"${(stats.feedbackDistribution.getOrElse(2, 0) / total) * 100}%2.0f"} %,     Fine - ${f"${(stats.feedbackDistribution.getOrElse(3, 0) / total) * 100}%2.0f"} %, other - ${f"${stats.otherFeedback.length / total}%2.0f"} %
     """.stripMargin
    } else {
      "[There is no feedback provided for this session]"
    }
  }
}
