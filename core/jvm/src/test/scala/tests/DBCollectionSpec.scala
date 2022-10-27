package tests

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.outr.giantscala._
import com.outr.giantscala.dsl.SortField
import com.outr.giantscala.failure.FailureType
import fabric._
import fabric.rw.RW
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions

class DBCollectionSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {
  "DBCollection" should {
//    val inserts = ListBuffer.empty[Person]
//    val deletes = ListBuffer.empty[Delete]

    "drop the database so it's clean and ready" in {
      DBCollectionDatabase.drop().map(_ => true should be(true))
    }
    "initiate database upgrades" in {
      DBCollectionDatabase.init().map { _ =>
        succeed
      }
    }
    "verify the version" in {
      DBCollectionDatabase.buildInfo.map { buildInfo =>
        buildInfo.major should be >= 3
      }
    }
    "create successfully" in {
      DBCollectionDatabase.person shouldNot be(null)
    }
//    "start monitoring people" in {
//      DBCollectionDatabase.person.monitor.insert.attach { person =>
//        inserts += person
//      }
//      DBCollectionDatabase.person.monitor.delete.attach { delete =>
//        deletes += delete
//      }
//      noException should be thrownBy DBCollectionDatabase.person.monitor.start()
//    }
//    "validate a field value" in {
//      val f = Field[String]("MyField")
//      val value = f("test")
//      value should be("""{"MyField":"test"}""")
//    }
    "insert a person" in {
      DBCollectionDatabase.person.insert(Person(
        name = "John Doe",
        age = 30,
        _id = DBCollectionDatabase.person.id("john.doe")
      )).map { result =>
        result.isRight should be(true)
        val p = result.toOption.get
        p.name should be("John Doe")
        p.age should be(30)
        p._id should be(DBCollectionDatabase.person.id("john.doe"))
      }
    }
    // TODO: uncomment when monitoring consistently works (only works sometimes)
//    "verify the insert was monitored" in {
//      waitFor(inserts.length should be(1)).map { _ =>
//        val p = inserts.head
//        p.name should be("John Doe")
//        p.age should be(30)
//        p._id should be("john.doe")
//      }
//    }
    "query one person back" in {
      DBCollectionDatabase.person.all().map { people =>
        people.length should be(1)
        val p = people.head
        p.name should be("John Doe")
        p.age should be(30)
        p._id should be(DBCollectionDatabase.person.id("john.doe"))
      }
    }
    "query back by name" in {
      DBCollectionDatabase.person.byName("John Doe").map { people =>
        people.length should be(1)
        val p = people.head
        p.name should be("John Doe")
        p.age should be(30)
        p._id should be(DBCollectionDatabase.person.id("john.doe"))
      }
    }
    "trigger constraint violation inserting the same name twice" in {
      DBCollectionDatabase.person.insert(Person(
        name = "John Doe",
        age = 31,
        _id = DBCollectionDatabase.person.id("john.doe2")
      )).map { result =>
        result.isLeft should be(true)
        val failure = result.swap.toOption.get
        failure.`type` should be(FailureType.DuplicateKey)
      }
    }
    "delete one person" in {
      DBCollectionDatabase.person.all().flatMap { people =>
        val p = people.head
        DBCollectionDatabase.person.delete(p._id).map { _ =>
          people.length should be(1)
        }
      }
    }
//    "verify the delete was monitored" in {
//      waitFor(deletes.length should be(1)).map { _ =>
//        deletes.length should be(1)
//      }
//    }
    "do a batch insert" in {
//      inserts.clear()
      DBCollectionDatabase.person.batch.insert(
        Person("Person A", 1, _id = DBCollectionDatabase.person.id("personA")),
        Person("Person B", 2, _id = DBCollectionDatabase.person.id("personB"))
      ).execute().map { result =>
        result.getInsertedCount should be(2)
      }
    }
//    "verify the batch insert was monitored" in {
//      waitFor(inserts.length should be(2)).map { _ =>
//        val p = inserts.head
//        p.name should be("Person A")
//        p.age should be(1)
//        p._id should be(DBCollectionDatabase.person.id("personA"))
//      }
//    }
    "do a batch update" in {
      DBCollectionDatabase.person.batch.update(
        Person("Person A", 123, _id = DBCollectionDatabase.person.id("personA")),
        Person("Person B", 234, _id = DBCollectionDatabase.person.id("personB"))
      ).execute().map { result =>
        result.getModifiedCount should be(2)
      }
    }
    "query two people back" in {
      DBCollectionDatabase.person.all().map { people =>
        people.length should be(2)
        val p = people.head
        p.name should be("Person A")
        p.age should be(123)
        p._id should be(DBCollectionDatabase.person.id("personA"))
      }
    }
    "query Person A back in a aggregate DSL query" in {
      import DBCollectionDatabase.person._
      aggregate
        .`match`(name === "Person A")
        .toList
        .map { people =>
          people.map(_.name) should be(List("Person A"))
        }
    }
    "query Person A back in an aggregate DSL query using toStream" in {
      import DBCollectionDatabase.person._
      aggregate
        .sort(SortField.Descending(name))
        .toList
        .map { people =>
          people.map(_.name).toSet should be(Set("Person A", "Person B"))
        }
    }
    "aggregate count" in {
      import DBCollectionDatabase.person._
      aggregate.count().toList.map { results =>
        results should be(List(2))
      }
    }
    "aggregate sort by name ascending" in {
      import DBCollectionDatabase.person._
      aggregate
        .sort(SortField.Ascending(name))
        .toList
        .map { people =>
          val names = people.map(_.name)
          names should be(List("Person A", "Person B"))
        }
    }
    "aggregate sort by name descending" in {
      import DBCollectionDatabase.person._
      aggregate
        .sort(SortField.Descending(name))
        .toList
        .map { people =>
          val names = people.map(_.name)
          names should be(List("Person B", "Person A"))
        }
    }
    "aggregate skip" in {
      import DBCollectionDatabase.person._
      aggregate
        .sort(SortField.Ascending(name))
        .skip(1)
        .toList
        .map { people =>
          val names = people.map(_.name)
          names should be(List("Person B"))
        }
    }
    "aggregate limit" in {
      import DBCollectionDatabase.person._
      aggregate
        .sort(SortField.Ascending(name))
        .limit(1)
        .toList
        .map { people =>
          val names = people.map(_.name)
          names should be(List("Person A"))
        }
    }
    "query Person A back in a aggregate DSL query with conversion" in {
      import DBCollectionDatabase.person._
      aggregate
        .project(name.include, _id.exclude)
        .`match`(name === "Person A")
        .as[PersonName]
        .toList.map { people =>
          people.map(_.name) should be(List("Person A"))
        }
    }
    "verify $group with $addToSet" in {
      import DBCollectionDatabase.person._
      val query = aggregate.group(_id.set("names"), name.addToSet("names")).toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$group":{"_id":"names","names":{"$addToSet":"$name"}}}])""")
    }
    "verify $or" in {
      import DBCollectionDatabase.person._
      val query = aggregate.`match`(name === "Person A" || name === "Person B").toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$match":{"$or":[{"name":"Person A"},{"name":"Person B"}]}}])""")
    }
    "verify $and" in {
      import DBCollectionDatabase.person._
      val query = aggregate.`match`(name === "Person A" && name === "Person B").toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$match":{"$and":[{"name":"Person A"},{"name":"Person B"}]}}])""")
    }
    "verify $in" in {
      import DBCollectionDatabase.person._
      val query = aggregate.`match`(name.in("Person A", "Person B")).toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$match":{"name":{"$in":["Person A","Person B"]}}}])""")
    }
    "verify $size" in {
      import DBCollectionDatabase.person._
      val query = aggregate.`match`(name.size(10)).toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$match":{"name":{"$size":10}}}])""")
    }
    "verify aggregate $addFields" in {
      import DBCollectionDatabase.person._
      val query = aggregate.addFields(Field[Person]("person").arrayElemAt("$people", 0)).toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$addFields":{"person":{"$arrayElemAt":["$people",0]}}}])""")
    }
    "verify $objectToArray converts to proper query" in {
      import DBCollectionDatabase.person._
      val query = aggregate.project(name.objectToArray("names")).toQuery(includeSpaces = false)
      query should be("""db.person.aggregate([{"$project":{"name":{"$objectToArray":"$names"}}}])""")
    }
    "verify a complex query" in {
      import DBCollectionDatabase.person._
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
    "verify findOneAndSet sets a new value" in {
      import DBCollectionDatabase.person._
      findAndUpdate
        .`match`(name === "Person A")
        .set(age(31337))
        .toIO
        .map { option =>
          option.map(_.age) should be(Some(31337))
        }
    }
    "verify findOneAndSet returns None on invalid filter" in {
      import DBCollectionDatabase.person._
      findAndUpdate
        .`match`(name === "Person A", age === 123)
        .set(age(321))
        .toIO
        .map { option =>
          option should be(None)
        }
    }
    "lock on a field" in {
      import DBCollectionDatabase.person._
      val start = System.currentTimeMillis()
      var io1Finished = false
      val io1 = fieldLock(Id[Person]("personA"), lock) {
        IO.sleep(2.seconds).map { _ =>
          io1Finished = true
        }
      }
      val io2 = IO.sleep(100.millis).flatMap { _ =>
        fieldLock(Id[Person]("personA"), lock, delay = 250.millis) {
          io1Finished should be(true)
          IO.pure(System.currentTimeMillis() - start)
        }
      }
      for {
        fiber1 <- io1.start
        fiber2 <- io2.start
        _ <- fiber1.joinWithNever
        delay <- fiber2.joinWithNever
      } yield {
        delay should be >= 2000L
      }
    }
    "stop the oplog" in {
      noException should be thrownBy DBCollectionDatabase.oplog.stop()
    }
  }

  def waitFor(condition: => Assertion,
              time: Long = 15000L,
              startTime: Long = System.currentTimeMillis()): IO[Assertion] = {
    try {
      val result: Assertion = condition
      IO.pure(result)
    } catch {
      case t: Throwable => if (System.currentTimeMillis() - startTime > time) {
        IO {
          throw t
        }
      } else {
        IO.sleep(10.millis).flatMap { _ =>
          waitFor(condition, time, startTime)
        }
      }
    }
  }
}

case class Person(name: String,
                  age: Int,
                  lock: Boolean = false,
                  created: Long = System.currentTimeMillis(),
                  modified: Long = System.currentTimeMillis(),
                  _id: Id[Person] = Id()) extends ModelObject[Person]

object Person {
  implicit val rw: RW[Person] = RW.gen
}

case class PersonName(name: String)

object PersonName {
  implicit val rw: RW[PersonName] = RW.gen
}

class PersonCollection extends DBCollection[Person]("person", DBCollectionDatabase) {
  val name: Field[String] = field("name")
  val age: Field[Int] = field("age")
  val lock: Field[Boolean] = field("lock")
  val created: Field[Long] = field("created")
  val modified: Field[Long] = field("modified")

  override val converter: Converter[Person] = Converter[Person]

  override def indexes: List[Index] = List(
    name.index.ascending.unique
  )

  def byName(name: String): IO[List[Person]] = {
    aggregate.`match`(this.name === name).toList
  }
}

object DBCollectionDatabase extends MongoDatabase(name = "giant-scala-test") {
  val person: PersonCollection = new PersonCollection
}