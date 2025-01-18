package my.rcfbot.slack.rtm

import my.rcfbot.slack.api.RtmStartState
import my.rcfbot.slack.models._
import play.api.libs.json._

object RtmState {
  def apply(initial: RtmStartState): RtmState = {
    new RtmState(initial)
  }
}

class RtmState(start: RtmStartState) {
  private val _url: String = start.url
  private val _self = start.self
  private val _team = start.team
  private val _users = start.users
  private val _channels = start.channels
  private val _groups = start.groups
  private val _ims = start.ims
  private val _bots = start.bots

  def url: String = _url
  def self: User = _self
  def team: Team = _team
  def users: Option[Seq[User]] = _users
  def channels: Option[Seq[Channel]] = _channels
  def groups: Option[Seq[Group]] = _groups
  def ims: Option[Seq[Im]] = _ims
  def bots: Option[Seq[JsValue]] = _bots

  def getUserIdForName(name: String): Option[String] = {
    _users.flatMap(_.find(_.name == name).map(_.id))
  }

  def getChannelIdForName(name: String): Option[String] = {
    _channels.flatMap(_.find(_.name == name).map(_.id))
  }

  def getUserById(id: String): Option[User] = {
    _users.flatMap(_.find(_.id == id))
  }

//  // TODO: Add remaining update events
//  private[rtm] def update(event: SlackEvent) {
//    event match {
//      case e: ChannelCreated =>
//        addReplaceChannel(e.channel)
//      case e: ChannelDeleted =>
//        removeChannel(e.channel)
//      case e: ChannelRename =>
//        addReplaceChannel(e.channel)
//      case e: ImCreated =>
//        addReplaceIm(e.channel)
//      case e: ImClose =>
//        removeIm(e.channel)
//      case e: UserChange =>
//        addReplaceUser(e.user)
//      case e: TeamJoin =>
//        addReplaceUser(e.user)
//      case _ =>
//    }
//  }

//  private def addReplaceChannel(chan: Channel) {
//    removeChannel(chan.id)
//    _channels :+= chan
//  }
//
//  private def removeChannel(chanId: String) {
//    _channels = _channels.filterNot(_.id == chanId)
//  }
//
//  private def addReplaceIm(im: Im) {
//    removeIm(im.id)
//    _ims :+= im
//  }
//
//  private def removeIm(imId: String) {
//    _ims = _ims.filterNot(_.id == imId)
//  }
//
//  private def addReplaceUser(user: User) {
//    removeUser(user.id)
//    _users :+= user
//  }
//
//  private def removeUser(userId: String) {
//    _users = _users.filterNot(_.id == userId)
//  }
}