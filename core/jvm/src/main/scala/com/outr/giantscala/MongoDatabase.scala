package com.outr.giantscala

import cats.effect.IO
import cats.implicits.toTraverseOps

import java.util.concurrent.atomic.AtomicBoolean

import scala.language.experimental.macros
import com.outr.giantscala.oplog.OperationsLog
import com.outr.giantscala.upgrade.{CreateDatabase, DatabaseUpgrade}
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.{Asable, Convertible, RW}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.MongoCollection
import profig.Profig

import scala.collection.mutable.ListBuffer
import org.mongodb.scala.{MongoDatabase => ScalaMongoDatabase}
import org.mongodb.scala.model.ReplaceOptions

class MongoDatabase(val name: String,
                    val connection: MongoConnection = new MongoConnection()) extends StreamSupport {
  protected val db: ScalaMongoDatabase = connection.client.getDatabase(name)

  // TODO: support client.startSession().toFuture().map(_.startTransaction()) for sessions and transactions on modifications (update, insert, and delete)

  def buildInfo: IO[MongoBuildInfo] = db.runCommand(Document("buildinfo" -> "")).one.map { doc =>
    JsonParser(doc.toJson()).as[MongoBuildInfo]
  }

  private val _initialized = new AtomicBoolean(false)
  def initialized: Boolean = _initialized.get()

  private var _collections = Set.empty[DBCollection[_ <: ModelObject[_]]]
  def collections: Set[DBCollection[_ <: ModelObject[_]]] = _collections

  /**
    * Key/Value store functionality against MongoDB
    */
  object store { s =>
    private lazy val info = db.getCollection("extraInfo")

    object string {
      def get(key: String): IO[Option[String]] = {
        info
          .find(Document("_id" -> key))
          .stream
          .first
          .map(_.map(d => JsonParser(d.toJson()).as[Stored].value))
      }

      def set(key: String, value: String): IO[Unit] = {
        val json = JsonFormatter.Compact(Stored(key, value).json)
        info
          .replaceOne(
            Document("_id" -> key),
            Document(json),
            new ReplaceOptions().upsert(true)
          )
          .first
          .map(_ => ())
      }
    }

    def typed[T](key: String)(implicit rw: RW[T]): TypedStore[T] = new TypedStore[T] {
      override def get: IO[Option[T]] = s.string.get(key).map(_.map(json => JsonParser(json).as[T]))

      override def apply(default: => T): IO[T] = get.map(_.getOrElse(default))

      override def set(value: T): IO[Unit] = s.string.set(key, JsonFormatter.Compact(value.json))
    }

    case class Stored(_id: String, value: String)

    object Stored {
      implicit val rw: RW[Stored] = RW.gen
    }
  }

  private lazy val versionStore = store.typed[DatabaseVersion]("databaseVersion")

  private val versions = ListBuffer.empty[DatabaseUpgrade]

  lazy val oplog: OperationsLog = new OperationsLog(connection.client)

  register(CreateDatabase)

  def register(upgrade: DatabaseUpgrade): Unit = synchronized {
    assert(!initialized, "Database is already initialized. Cannot register upgrades after initialization.")
    if (!versions.contains(upgrade)) {
      versions += upgrade
    }
    ()
  }

  def init(managed: Boolean = true): IO[Unit] = {
    if (_initialized.compareAndSet(false, true) && managed) {
      versionStore(DatabaseVersion()).flatMap { version =>
        val upgrades = versions.toList.filterNot(v => version.upgrades.contains(v.label) && !v.alwaysRun)
        upgrade(version, upgrades, version.upgrades.isEmpty)
      }
    } else {
      IO.unit
    }
  }

  def truncate(): IO[Int] = collections.toList.map(_.truncate()).sequence.map(deleted => deleted.sum)

  private def upgrade(version: DatabaseVersion,
                      upgrades: List[DatabaseUpgrade],
                      newDatabase: Boolean,
                      currentlyBlocking: Boolean = true): IO[Unit] = {
    val blocking = upgrades.exists(_.blockStartup)
    val io: IO[Unit] = upgrades.headOption match {
      case Some(u) => if (!newDatabase || u.applyToNew) {
        scribe.info(s"Upgrading with database upgrade: ${u.label} (${upgrades.length - 1} upgrades left)...")
        u.upgrade(this).flatMap { _ =>
          val versionUpdated = version.copy(upgrades = version.upgrades + u.label)
          versionStore.set(versionUpdated).flatMap { _ =>
            scribe.info(s"Completed database upgrade: ${u.label} successfully")
            upgrade(versionUpdated, upgrades.tail, newDatabase, blocking)
          }
        }
      } else {
        scribe.info(s"Skipping database upgrade: ${u.label} as it doesn't apply to new database")
        val versionUpdated = version.copy(upgrades = version.upgrades + u.label)
        versionStore.set(versionUpdated).flatMap { _ =>
          upgrade(versionUpdated, upgrades.tail, newDatabase, blocking)
        }
      }
      case None => IO.unit
    }

    if (currentlyBlocking && !blocking && upgrades.nonEmpty) {
      scribe.info("Additional upgrades do not require blocking. Allowing application to start...")
      io.redeem(
        recover = (throwable: Throwable) => {
          scribe.error("Database upgrade failure", throwable)
          throw throwable
        },
        map = identity
      ).unsafeRunAndForget()(cats.effect.unsafe.implicits.global)
      IO.unit
    } else {
      io
    }
  }

  def drop(): IO[Unit] = db.drop().first.map(_ => ())

  def dispose(): IO[Unit] = IO(connection.client.close())

  case class DatabaseVersion(upgrades: Set[String] = Set.empty, _id: String = "databaseVersion")

  object DatabaseVersion {
    implicit val rw: RW[DatabaseVersion] = RW.gen
  }

  private[giantscala] def addCollection(collection: DBCollection[_ <: ModelObject[_]]): Unit = synchronized {
    _collections += collection
  }

  private[giantscala] def getCollection(name: String): MongoCollection[Document] = db.getCollection(name)
}

object MongoDatabase {
  def urls: List[MongoDBServer] = Profig("giantscala.MongoDatabase.urls")
    .opt[List[MongoDBServer]]
    .getOrElse(List(MongoDBServer.default))

  def credentials: Option[Credentials] = {
    val config = Profig("giantscala.MongoDatabase.credentials")
    if (config.exists()) {
      Some(config.as[Credentials])
    } else {
      None
    }
  }
}