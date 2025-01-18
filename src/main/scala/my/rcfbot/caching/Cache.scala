/**
  *  Since Spray is not supported for scala 2.12.x the code is taken from
  *  https://github.com/spray/spray/tree/v1.2-M8/spray-caching/src/main/scala/spray/caching
  */

package my.rcfbot.caching

import scala.concurrent.{ Promise, Future, ExecutionContext }
import scala.util.control.NonFatal

/**
  * General interface implemented by all spray cache implementations.
  */
trait Cache[V] { cache ⇒

  /**
    * Selects the (potentially non-existing) cache entry with the given key.
    */
  def apply(key: Any) = new Keyed(key)

  class Keyed(key: Any) {
    /**
      * Returns either the cached Future for the key or evaluates the given call-by-name argument
      * which produces either a value instance of type `V` or a `Future[V]`.
      */
    def apply(magnet: ⇒ ValueMagnet[V])(implicit ec: ExecutionContext): Future[V] =
      cache.apply(key, () ⇒ try magnet.future catch { case NonFatal(e) ⇒ Future.failed(e) })

    /**
      * Returns either the cached Future for the key or evaluates the given function which
      * should lead to eventual completion of the promise.
      */
    def apply[U](f: Promise[V] ⇒ U)(implicit ec: ExecutionContext): Future[V] =
      cache.apply(key, () ⇒ { val p = Promise[V](); f(p); p.future })
  }

  /**
    * Returns either the cached Future for the given key or evaluates the given value generating
    * function producing a `Future[V]`.
    */
  def apply(key: Any, genValue: () ⇒ Future[V])(implicit ec: ExecutionContext): Future[V]

  /**
    * Retrieves the future instance that is currently in the cache for the given key.
    * Returns None if the key has no corresponding cache entry.
    */
  def get(key: Any): Option[Future[V]]

  /**
    * Removes the cache item for the given key. Returns the removed item if it was found (and removed).
    */
  def remove(key: Any): Option[Future[V]]

  /**
    * Clears the cache by removing all entries.
    */
  def clear()
}

class ValueMagnet[V](val future: Future[V])
object ValueMagnet {
  implicit def fromAny[V](block: V): ValueMagnet[V] = fromFuture(Future.successful(block))
  implicit def fromFuture[V](future: Future[V]): ValueMagnet[V] = new ValueMagnet(future)
}
