package tests

import com.outr.giantscala._
import com.outr.giantscala.failure.{DBFailure, FailureType}
import com.outr.giantscala.oplog.Delete
import org.mongodb.scala.MongoException
import org.scalatest.{Assertion, AsyncWordSpec, Matchers}
import scribe.Logger
import scribe.format._
import scribe.modify.ClassNameFilter

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions
import scala.util.Success

class DBCollectionSpec extends AsyncWordSpec with Matchers {
  "DBCollection" should {
    var inserts = ListBuffer.empty[Person]
    var deletes = ListBuffer.empty[Delete]

    "drop the database so it's clean and ready" in {
      Logger.update(Logger.rootName) { l =>
        val formatter = formatter"$date $levelPaddedRight $position - ${scribe.format.message}$newLine"
        val filter = ClassNameFilter.startsWith("org.mongodb.driver.cluster", exclude = true)
        l.clearHandlers().withHandler(formatter, modifiers = List(filter))
      }
      Database.drop().map(_ => true should be(true))
    }
    "initiate database upgrades" in {
      Database.init().map { _ =>
        succeed
      }
    }
    "create successfully" in {
      Database.person shouldNot be(null)
    }
    "start monitoring people" in {
      Database.person.monitor.insert.attach { person =>
        inserts += person
      }
      Database.person.monitor.delete.attach { delete =>
        deletes += delete
      }
      noException should be thrownBy Database.person.monitor.start()
    }
    "insert a person" in {
      Database.person.insert(Person(name = "John Doe", age = 30, _id = "john.doe")).map { result =>
        result.isRight should be(true)
        val p = result.right.get
        p.name should be("John Doe")
        p.age should be(30)
        p._id should be("john.doe")
      }
    }
    "verify the insert was monitored" in {
      waitFor(inserts.length should be(1)).map { _ =>
        val p = inserts.head
        p.name should be("John Doe")
        p.age should be(30)
        p._id should be("john.doe")
      }
    }
    "query one person back" in {
      Database.person.all().map { people =>
        people.length should be(1)
        val p = people.head
        p.name should be("John Doe")
        p.age should be(30)
        p._id should be("john.doe")
      }
    }
    "trigger constraint violation inserting the same name twice" in {
      Database.person.insert(Person(name = "John Doe", age = 31, _id = "john.doe2")).map { result =>
        result.isLeft should be(true)
        val failure = result.left.get
        failure.`type` should be(FailureType.DuplicateKey)
      }
    }
    "delete one person" in {
      Database.person.all().flatMap { people =>
        val p = people.head
        Database.person.delete(p._id).map { _ =>
          people.length should be(1)
        }
      }
    }
    "verify the delete was monitored" in {
      waitFor(deletes.length should be(1)).map { _ =>
        deletes.length should be(1)
      }
    }
    "stop the oplog" in {
      noException should be thrownBy Database.oplog.stop()
    }
  }

  def waitFor(condition: => Assertion,
              time: Long = 15000L,
              startTime: Long = System.currentTimeMillis()): Future[Assertion] = {
    try {
      val result: Assertion = condition
      Future.successful(result)
    } catch {
      case t: Throwable => if (System.currentTimeMillis() - startTime > time) {
        Future.failed(t)
      } else {
        Future {
          Thread.sleep(10L)
        }.flatMap { _ =>
          waitFor(condition, time, startTime)
        }
      }
    }
  }
}

case class Person(name: String,
                  age: Int,
                  created: Long = System.currentTimeMillis(),
                  modified: Long = System.currentTimeMillis(),
                  _id: String) extends ModelObject

class PersonCollection extends DBCollection[Person]("person", Database) {
  override val converter: Converter[Person] = Converter.auto[Person]

  override def indexes: List[Index] = List(
    Index.Ascending("name").unique
  )
}

object Database extends MongoDatabase(name = "giant-scala-test") {
  val person: PersonCollection = new PersonCollection   // TODO: typed[PersonCollection]
}