# giant-scala

[![CI](https://github.com/outr/giant-scala/actions/workflows/ci.yml/badge.svg)](https://github.com/outr/giant-scala/actions/workflows/ci.yml)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/outr/giantscala)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.outr/giant-scala_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.outr/giant-scala_2.12)
[![Scala version support](https://index.scala-lang.org/outr/giant-scala/giant-scala/latest.svg)](https://index.scala-lang.org/outr/giant-scala/giant-scala)
[![javadoc](https://javadoc.io/badge2/com.outr/giant-scala_2.13/javadoc.svg)](https://javadoc.io/doc/com.outr/giant-scala_2.13)

GiantScala is wrapper on top of the Scala MongoDB driver to provide higher level functionality and better type-safety.

## Quick Start

For people that want to skip the explanations and see it action, this is the place to start!

### Dependency Configuration

```scala
libraryDependencies += "com.outr" %% "giant-scala" % "1.5.0"
```

If you are developing a cross-project with Scala.js support:

```scala
libraryDependencies += "com.outr" %%% "giant-scala" % "1.5.0"
```

Note: while MongoDB obviously doesn't work in the browser, the purpose of the Scala.js functionality is to allow shared
model objects that extend from the base `ModelObject`.

### Case Classes

Most of the functionality in GiantScala is accessible via the import:

```scala
import com.outr.giantscala._
```

In order to represent our object, we begin with a case class:

```scala
case class Person(_id: String,
                  name: String,
                  age: Int,
                  created: Long = System.currentTimeMillis(),
                  modified: Long = System.currentTimeMillis()) extends ModelObject
```

Notice that this case class extends from `ModelObject`. This is a simple trait that defines three required fields for all
model objects:
* `_id: String`: A unique identifier in the database
* `created: Long`: The creation date of this document
* `modified: Long`: The last modified date of this document

### Database

In order to represent the tie-in to an actual MongoDB instance, we must create a database object:

```scala
object Database extends MongoDatabase(name = "giant-scala-tutorial") {
    ... collections listed here ...
}
```

### DBCollection

Now that we have a `Person` case class, we need to tie it to a database collection. To do this, we use the `DBCollection`
object:

```scala
class PersonCollection extends DBCollection[Person]("person", Database) {
  override val converter: Converter[Person] = Converter.auto[Person]

  val name: Field[String] = Field("name")
  val age: Field[Int] = Field("age")
  val created: Field[Long] = Field("created")
  val modified: Field[Long] = Field("modified")
  val _id: Field[String] = Field("_id")

  override def indexes: List[Index] = List(
    name.index.ascending.unique
  )
}
```

There's a lot of boilerplate code in this class, but we can simplify this setup if we use the GiantScala SBT plugin. Add
the following line to your `project/plugins.sbt` file in your project:

```scala
addSbtPlugin("com.outr" % "giant-scala-plugin" % "1.2.0")
```

Now you can run `sbt generateDBModels` and a `PersonModel` class will be generated in your source directory. This simplifies
set up by changing the signature of the `PersonCollection` to:

```scala
class PersonCollection extends PersonModel("person", Database) {
  override def indexes: List[Index] = List(
    name.index.ascending.unique
  )
}
```

The fields and converter are all defined in the `PersonModel` class. The only things left to define are indexes.

Now, we need to update our `Database` to include the collection:

```scala
object Database extends MongoDatabase(name = "giant-scala-tutorial") {
    val person: PersonCollection = new PersonCollection
}
```

### Initialize the Database

GiantScala supports advanced features like database upgrades that are handled during the initialization phase:

```scala
Database.init()
```

Note that `init()` returns a `IO[Unit]` that must complete before the database is fully usable. We'll talk about
database upgrades later in this tutorial.

### Inserting a Person

Now that our database is fully defined we can easily insert a person:

```scala
Database.person.insert(Person(_id = "john.doe", name = "John Doe", age = 30))
```

Note that this returns a `IO[Either[DBFailure, Person]]` representing the success or failure of the operation.

### Querying a Person

Now that there's a person in our database, we can query them back with:

```scala
Database.person.all()
```

This will return a `IO[List[Person]]`

For a more advanced query, GiantScala offers a type-safe implementation of aggregation:

```scala
import Database.person._

aggregate
  .`match`(name === "John Doe")
  .toList
```

Note that this will return a `IO[List[Person]]`.

If we wanted a more simplistic, limited type to result from aggregation we could do:

```scala
import Database.person._

case class SimplePerson(_id: String, name: String)

aggregate
  .project(name.include)
  .`match`(name === "John Doe")
  .as[SimplePerson]
  .toList
```

This will return a `IO[List[SimplePerson]]`. This can be extremely useful for complex queries to avoid being bound to
the original type. It is also worth noting that instead of calling `toList` you can call `toQuery()` and it will generate
a `String` representation of the query that can be directly pasted into the `mongo` REPL. This allows for much easier testing
of complex queries.

### Advanced Features (TODO: Give examples for each of these)
* Database Upgrades: Extend from `DatabaseUpgrade` and call `Database.register(upgrade)` before calling `Database.init()`
* Batch Operations: GiantScala provides several features for batch operations. The typical path is to simply call `collection.batch` to get started
* Key/Value Store: `Database.store` provides lots of functionality for storing key/value data into the database
* Realtime Monitoring: GiantScala provides advanced functionality to monitor changes happening in real-time to the database (see `collection.monitor`)