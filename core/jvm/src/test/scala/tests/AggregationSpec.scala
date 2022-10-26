package tests

import cats.effect.testing.scalatest.AsyncIOSpec

import java.util.concurrent.atomic.AtomicLong
import com.outr.giantscala.{Converter, DBCollection, Field, Id, Index, ModelObject, MongoDatabase}
import com.outr.giantscala.dsl._
import fabric.rw.RW
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class AggregationSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {
  "Aggregation" should {
    val Apple1 = Order("Apple", 5)
    val Banana1 = Order("Banana", 8)
    val Cherry1 = Order("Cherry", 12)
    val Apple2 = Order("Apple", 24)
    val Banana2 = Order("Banana", 2)
    val Cherry2 = Order("Cherry", 11)
    val Apple3 = Order("Apple", 8)

    "drop the database so it's clean and ready" in {
      AggregationDatabase.drop().map(_ => true should be(true))
    }
    "initiate database upgrades" in {
      AggregationDatabase.init().map { _ =>
        succeed
      }
    }
    "verify the version" in {
      AggregationDatabase.buildInfo.map { buildInfo =>
        buildInfo.major should be >= 3
      }
    }
    "create successfully" in {
      AggregationDatabase.orders shouldNot be(null)
    }
    "add several orders" in {
      AggregationDatabase.orders.insert(List(
        Apple1,
        Banana1,
        Cherry1,
        Apple2,
        Banana2,
        Cherry2,
        Apple3
      )).map { result =>
        result.isRight should be(true)
      }
    }
    "query back" in {
      AggregationDatabase.orders.all().map { orders =>
        orders.length should be(7)
      }
    }
    "aggregate and group largest orders by item" in {
      import AggregationDatabase._
      orders
        .aggregate
        .sort(SortField.Descending(orders.qty))
        .group(
          orders._id.from(orders.item),
          field[String]("qty").first(orders.qty)
        )
        .sort(SortField.Descending(orders.qty))
        .as[LargestOrder]
        .toList
        .map { orders =>
          orders should be(List(
            LargestOrder("Apple", 24),
            LargestOrder("Cherry", 12),
            LargestOrder("Banana", 8)
          ))
        }
    }
    "aggregate largest orders and replace root" in {
      import AggregationDatabase._
      orders
        .aggregate
        .sort(SortField.Descending(orders.qty))
        .group(
          orders._id.from(orders.item),
          field[String]("order").first(Field.Root)
        )
        .replaceRoot(Field[Order]("$order"))
        .sort(SortField.Descending(orders.qty))
        .toList
        .map { orders =>
          orders.map(o => o.item -> o.qty) should be(List(
            ("Apple", 24),
            ("Cherry", 12),
            ("Banana", 8)
          ))
        }
    }
  }

  case class LargestOrder(_id: String, qty: Int)

  object LargestOrder {
    implicit val rw: RW[LargestOrder] = RW.gen
  }
}

case class Order(item: String,
                 qty: Int,
                 created: Long = AggregationDatabase.now,
                 modified: Long = AggregationDatabase.now,
                 _id: Id[Order] = Id[Order]()) extends ModelObject[Order]

object Order {
  implicit val rw: RW[Order] = RW.gen
}

class OrderCollection extends DBCollection[Order]("order", AggregationDatabase) {
  val item: Field[String] = Field("item")
  val qty: Field[Int] = Field("qty")
  val created: Field[Long] = Field("created")
  val modified: Field[Long] = Field("modified")

  override val converter: Converter[Order] = Converter.apply

  override def indexes: List[Index] = List(
    qty.index.ascending,
    created.index.ascending
  )
}

object AggregationDatabase extends MongoDatabase("giant-scala-aggregation") {
  private lazy val counter = new AtomicLong(System.currentTimeMillis())
  def now: Long = counter.getAndIncrement()

  val orders: OrderCollection = new OrderCollection
}