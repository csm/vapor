package vapor.util.test

import org.scalatest.{Matchers, FlatSpec}
import vapor.util.CachedValue

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
 * Created by cmarshall on 6/17/15.
 */
class CachedValueSpec extends FlatSpec with Matchers {
  import scala.concurrent.ExecutionContext.Implicits.global
  def result[T](f:Future[T]):T = Await.result(f, 1.second)

  "getting a value" should "return that value" in {
    val v = CachedValue(Future.successful(42), 1.hour)
    result(v.get) should be (42)
  }

  "getting a dynamic value" should "return the updated value" in {
    var x = 1
    val v = CachedValue(Future {
      x += 1
      x
    }, 1.hour)
    result(v.get) should be (2)
  }

  "getting an expired dynamic value" should "return the updated value" in {
    var x = 1
    val v = CachedValue(Future {
      x += 1
      x
    }, 5.seconds)
    result(v.get) should be (2)
    Thread.sleep(5 * 1000 + 100)
    result(v.get) should be (3)
  }

  "getting an invalidated dynamic value" should "return the updated value" in {
    var x = 1
    val v = CachedValue(Future {
      x += 1
      x
    }, 1.hour)
    result(v.get) should be (2)
    v.invalidate()
    result(v.get) should be (3)
  }

  "extending a cached value" should "return the same value" in {
    var x = 1
    val v = CachedValue(Future {
      x += 1
      x
    }, 5.seconds)
    result(v.get) should be (2)
    Thread.sleep(4 * 1000)
    v.extend()
    Thread.sleep(2 * 1000)
    result(v.get) should be (2)
  }
}
