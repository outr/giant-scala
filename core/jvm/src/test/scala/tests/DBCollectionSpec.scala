package tests

import com.outr.giantscala._
import com.outr.giantscala.failure.FailureType
import com.outr.giantscala.oplog.Delete
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.{Assertion, AsyncWordSpec, Matchers}
import scribe.Logger
import scribe.format._
import scribe.modify.ClassNameFilter

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.language.implicitConversions

class DBCollectionSpec extends AsyncWordSpec with Matchers {
  private val testMonitoring = true

  "DBCollection" should {
    var inserts = ListBuffer.empty[Person]
    var deletes = ListBuffer.empty[Delete]

    "reconfigure logging" in {
      Logger.root.clearHandlers().withHandler(
        formatter = formatter"$date $levelPaddedRight $position - ${scribe.format.message}$newLine",
        modifiers = List(ClassNameFilter.startsWith("org.mongodb.driver.cluster", exclude = true))
      ).replace()
      Future.successful(succeed)
    }
    "verify the connection string" in {
      Database.connectionString should be("mongodb://localhost:27017/?waitQueueMultiple=100")
    }
    "drop the database so it's clean and ready" in {
      Database.drop().map(_ => true should be(true))
    }
    "initiate database upgrades" in {
      Database.init().map { _ =>
        succeed
      }
    }
    "verify the version" in {
      val version = Database.version.major
      version should be >= 3
    }
    "create successfully" in {
      Database.person shouldNot be(null)
    }
    if (testMonitoring) {
      "start monitoring people" in {
        Database.person.monitor.insert.attach { person =>
          inserts += person
        }
        Database.person.monitor.delete.attach { delete =>
          deletes += delete
        }
        noException should be thrownBy Database.person.monitor.start()
      }
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
    if (testMonitoring) {
      "verify the insert was monitored" in {
        waitFor(inserts.length should be(1)).map { _ =>
          val p = inserts.head
          p.name should be("John Doe")
          p.age should be(30)
          p._id should be("john.doe")
        }
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
    "query back by name" in {
      Database.person.byName("John Doe").map { people =>
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
    if (testMonitoring) {
      "verify the delete was monitored" in {
        waitFor(deletes.length should be(1)).map { _ =>
          deletes.length should be(1)
        }
      }
    }
    "do a batch insert" in {
      inserts.clear()
      Database.person.batch.insert(
        Person("Person A", 1, _id = "personA"),
        Person("Person B", 2, _id = "personB")
      ).execute().map { result =>
        result.getInsertedCount should be(2)
      }
    }
    if (testMonitoring) {
      "verify the batch insert was monitored" in {
        waitFor(inserts.length should be(2)).map { _ =>
          val p = inserts.head
          p.name should be("Person A")
          p.age should be(1)
          p._id should be("personA")
        }
      }
    }
    "do a batch update" in {
      Database.person.batch.update(
        Person("Person A", 123, _id = "personA"),
        Person("Person B", 234, _id = "personB")
      ).execute().map { result =>
        result.getModifiedCount should be(2)
      }
    }
    "query two people back" in {
      Database.person.all().map { people =>
        people.length should be(2)
        val p = people.head
        p.name should be("Person A")
        p.age should be(123)
        p._id should be("personA")
      }
    }
    "query Person A back in a raw aggregate query" in {
      Database.person.collection.aggregate(List(
        Document("""{$match: {name: "Person A"}}""")
      )).toFuture().map(_.map(Database.person.converter.fromDocument)).map { people =>
        people.map(_.name) should be(List("Person A"))
      }
    }
    "query Person A back in a aggregate DSL query" in {
      import Database.person._
      aggregate
        .`match`(name === "Person A")
        .toFuture
        .map { people =>
          people.map(_.name) should be(List("Person A"))
        }
    }
    "query Person A back in a aggregate DSL query with conversion" in {
      import Database.person._
      aggregate
        .project(name.include, _id.exclude)
        .`match`(name === "Person A")
        .as[PersonName]
        .toFuture.map { people =>
          people.map(_.name) should be(List("Person A"))
        }
    }
    "verify $group with $addToSet" in {
      import Database.person._
      val query = aggregate.group(_id.set("names"), name.addToSet("names")).toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$group":{"_id":"names","names":{"$addToSet":"$name"}}}])""")
    }
    "verify $objectToArray converts to proper query" in {
      import Database.person._
      val query = aggregate.project(name.objectToArray("names")).toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$project":{"name":{"$objectToArray":"$names"}}}])""")
    }
    "verify a complex query" in {
      import Database.person._
      val status = field[String]("status")
      val count = field[String]("count")
      val counts = field[String]("counts")
      val query = aggregate
        .project(status.objectToArray(status))
        .project(status.arrayElemAt(status.key, 0))
        .group(_id.set(status.op), sum("count"))
        .project(_id.exclude, status.set(name.set(_id.op), count.set(count.op)))
        .group(_id.set(counts), status.addToSet(counts))
        .toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$project":{"status":{"$objectToArray":"$status"}}},{"$project":{"status":{"$arrayElemAt":["$status.k",0]}}},{"$group":{"_id":"$status","count":{"$sum":1}}},{"$project":{"_id":0,"status":{"name":"$_id","count":"$count"}}},{"$group":{"_id":"counts","counts":{"$addToSet":"$status"}}}])""")
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

case class PersonName(name: String)

class PersonCollection extends DBCollection[Person]("person", Database) {
  import scribe.Execution.global

  val name: Field[String] = Field("name")
  val age: Field[Int] = Field("age")
  val created: Field[Long] = Field("created")
  val modified: Field[Long] = Field("modified")
  val _id: Field[String] = Field("_id")

  override val converter: Converter[Person] = Converter.auto[Person]

  override def indexes: List[Index] = List(
    name.index.ascending.unique
  )

  def byName(name: String): Future[List[Person]] = {
   aggregate.`match`(this.name === name).toFuture
  }
}

object Database extends MongoDatabase(
    name = "giant-scala-test",
    options = List(ConnectionOption.WaitQueueMultiple(100))) {
  val person: PersonCollection = new PersonCollection
}