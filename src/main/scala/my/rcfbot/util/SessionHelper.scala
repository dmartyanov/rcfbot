package my.rcfbot.util

import my.rcfbot.domain.RcfSessionDocument

trait SessionHelper {

  def sessToStr(s: RcfSessionDocument) = s"${DateUtils.formatMillis(s.toStartTs)} - ${DateUtils.formatMillis(s.toEndTs)}"

}
