package my.rcfbot.slack.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.parboiled2.CharPredicate
import akka.stream.ActorMaterializer
import play.api.libs.json.{Format, JsValue, Json}

import scala.concurrent.duration._
import scala.concurrent.Future

object SlackApiClientUtil {
  private[api] implicit val rtmStartStateFmt = Json.format[RtmStartState]
  private[api] implicit val accessTokenFmt = Json.format[AccessToken]
  private[api] implicit val historyChunkFmt = Json.format[HistoryChunk]
  private[api] implicit val repliesChunkFmt = Json.format[RepliesChunk]
  private[api] implicit val pagingObjectFmt = Json.format[PagingObject]
  private[api] implicit val filesResponseFmt = Json.format[FilesResponse]
  private[api] implicit val fileInfoFmt = Json.format[FileInfo]
  private[api] implicit val reactionsResponseFmt = Json.format[ReactionsResponse]

  val defaultSlackApiBaseUri = Uri("https://slack.com/api/")

  /* TEMPORARY WORKAROUND - UrlEncode '?' in query string parameters */
  val charClassesClass = Class.forName("akka.http.impl.model.parser.CharacterClasses$")
  val charClassesObject = charClassesClass.getField("MODULE$").get(charClassesClass)
  //  strict-query-char-np
  val charPredicateField = charClassesObject.getClass.getDeclaredField("strict$minusquery$minuschar$minusnp")
  charPredicateField.setAccessible(true)
  val updatedCharPredicate = charPredicateField.get(charClassesObject).asInstanceOf[CharPredicate] -- '?'
  charPredicateField.set(charClassesObject, updatedCharPredicate)
  /* END TEMPORARY WORKAROUND */

  def buildClient(token: String, slackApiBaseUri: Uri = defaultSlackApiBaseUri): SlackApiClient = {
    new SlackApiClient(token, slackApiBaseUri)
  }

  def exchangeOauthForToken(
                             clientId: String,
                             clientSecret: String,
                             code: String,
                             redirectUri: Option[String] = None,
                             slackApiBaseUri: Uri = defaultSlackApiBaseUri
                           )(implicit system: ActorSystem): Future[AccessToken] = {
    val params =
      Seq("client_id" -> clientId, "client_secret" -> clientSecret, "code" -> code, "redirect_uri" -> redirectUri)
    val res = makeApiRequest(
      addQueryParams(addSegment(HttpRequest(uri = slackApiBaseUri), "oauth.access"), cleanParams(params))
    )
    res.map(_.as[AccessToken])(system.dispatcher)
  }

  def makeApiRequest(request: HttpRequest)(implicit system: ActorSystem): Future[JsValue] = {
    implicit val mat = ActorMaterializer()
    implicit val ec = system.dispatcher
    Http().singleRequest(request).flatMap {
      case response if response.status.intValue == 200 =>
        response.entity.toStrict(10.seconds).map { entity =>
          val dataStr = entity.data.decodeString("UTF-8")
          val parsed = Json.parse(dataStr)
          if ((parsed \ "ok").as[Boolean]) {
            parsed
          } else {
            throw ApiError((parsed \ "error").as[String])
          }
        }
      case response =>
        response.entity.toStrict(10.seconds).map { entity =>
          val dataStr = entity.data.decodeString("UTF-8")
          throw InvalidResponseError(response.status.intValue, dataStr)
        }
    }
  }

  def extract[T](jsFuture: Future[JsValue], field: String)(implicit system: ActorSystem,
                                                                   fmt: Format[T]): Future[T] = {
    jsFuture.map(js => (js \ field).as[T])(system.dispatcher)
  }

  def addQueryParams(request: HttpRequest, queryParams: Seq[(String, String)]): HttpRequest = {
    request.withUri(request.uri.withQuery(Uri.Query(request.uri.query() ++ queryParams: _*)))
  }

  def cleanParams(params: Seq[(String, Any)]): Seq[(String, String)] = {
    var paramList = Seq[(String, String)]()
    params.foreach {
      case (k, Some(v)) => paramList :+= (k -> v.toString)
      case (k, None) => // Nothing - Filter out none
      case (k, v) => paramList :+= (k -> v.toString)
    }
    paramList
  }

  def addSegment(request: HttpRequest, segment: String): HttpRequest = {
    request.withUri(request.uri.withPath(request.uri.path + segment))
  }
}
