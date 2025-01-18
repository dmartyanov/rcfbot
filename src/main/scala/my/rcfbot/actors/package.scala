package my.rcfbot

import scala.concurrent.{ExecutionContext, Future}

package object actors {

  implicit def optionFuture2FutureOption[A](f: Option[Future[A]] )(implicit ec: ExecutionContext): Future[Option[A]] =
    f match {
      case Some(f) => f.map(Some(_))
      case None => Future.successful(Option.empty[A])
    }

}
