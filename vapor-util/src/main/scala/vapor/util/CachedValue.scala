package vapor.util

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

object CachedValue {
  def apply[T](loader: => Future[T], timeout: FiniteDuration) = new CachedValue[T](loader, timeout)

  def withValue[T](value: T, loader: => Future[T], timeout: FiniteDuration) = {
    val ret = new CachedValue[T](loader, timeout)
    ret.set(value)
    ret
  }
}

/**
 * A lazy value that is cached for a specified timeout.
 */
class CachedValue[T](loader: => Future[T], timeout: FiniteDuration) {
  @volatile private var _value:Option[T] = None
  @volatile private var _expires = System.currentTimeMillis()

  def get(implicit executor: ExecutionContext):Future[T] = {
    if (_value.isDefined) {
      if (_expires > System.currentTimeMillis()) {
        return Future.successful(_value.get)
      }
    }
    loader.map(v => {
      set(v)
      v
    })
  }

  def set(value: T): Unit = {
    _value = Some(value)
    extend()
  }

  def peek:Option[T] = {
    _value.flatMap(v => if (_expires > System.currentTimeMillis()) Some(v) else None)
  }

  def extend():Unit = {
    _expires = System.currentTimeMillis() + timeout.toMillis
  }

  def invalidate(): Unit = {
    _value = None
  }
}
