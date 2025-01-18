package my.rcfbot.domain

case class RcfSessionDocument(
                             id: String,
                             series: Boolean,
                             status: String,
                             toStartTs: Long,
                             halfTs: Option[Long] = None,
                             toEndTs: Long,
                             createTs: Long,
                             duration: Long,
                             updateTs: Option[Long] = None,
                             memo: Option[String] = None
                             ) extends JsonModel