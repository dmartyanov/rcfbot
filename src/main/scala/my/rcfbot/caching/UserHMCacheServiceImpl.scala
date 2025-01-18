package my.rcfbot.caching

import java.util.concurrent.locks.ReentrantLock

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import my.rcfbot.domain.RcfUser
import my.rcfbot.util.{CacheService, ConfigHelper, ExecutionContextHelper}


abstract class UserHMCacheServiceImpl extends CacheService[RcfUser] {
  this: ConfigHelper with ExecutionContextHelper =>

  val lock = new ReentrantLock()

  val cache = new ConcurrentLinkedHashMap.Builder[Any, RcfUser]
    .initialCapacity(50)
    .maximumWeightedCapacity(500)
    .build()

  override def put(key: String, value: RcfUser): RcfUser =
    try {
      lock.lock()
      cache.put(key, value)
      value
    } finally {
      lock.unlock()
    }

  override def get(key: String): Option[RcfUser] =
    try {
      lock.lock()
      Option(cache.get(key))
    } finally {
      lock.unlock()
    }

  override def remove(key: String): Option[RcfUser] =
    try {
      lock.lock()
      Option(cache.remove(key))
    } finally {
      lock.unlock()
    }
}
