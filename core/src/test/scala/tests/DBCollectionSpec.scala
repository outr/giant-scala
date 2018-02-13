package tests

import com.matthicks.giantscala._
import org.scalatest.{Matchers, WordSpec}

class DBCollectionSpec extends WordSpec with Matchers {
  "DBCollection" should {
    "create successfully" in {
      Database.person shouldNot be(null)
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

  override def indexes: List[Index] = Nil
}

object Database extends MongoDatabase(name = "test") {
  val person: PersonCollection = new PersonCollection
}