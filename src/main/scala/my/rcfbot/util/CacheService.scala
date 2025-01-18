package my.rcfbot.util

trait CacheService[T] {
  def put(key: String, value: T): T

  def get(key: String): Option[T]

  def remove(key: String): Option[T]
}

trait CacheComponent[T] {
  def cacheService: CacheService[T]
}
