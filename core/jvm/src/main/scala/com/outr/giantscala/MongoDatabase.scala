package com.outr.giantscala

import java.util.concurrent.atomic.AtomicBoolean

import scala.language.experimental.macros
import com.outr.giantscala.oplog.OperationsLog
import com.outr.giantscala.upgrade.{CreateDatabase, DatabaseUpgrade}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoClient, MongoCollection}
import profig.JsonUtil

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scribe.Execution.global
import org.mongodb.scala
import org.mongodb.scala.model.ReplaceOptions

import _root_.scala.concurrent._
import _root_.scala.concurrent.duration.Duration

class MongoDatabase(url: String = "mongodb://localhost:27017", val name: String) {
  private lazy val client = MongoClient(url)
  protected lazy val db: scala.MongoDatabase = client.getDatabase(name)

  lazy val buildInfo: MongoBuildInfo = Await.result(db.runCommand(Document("buildinfo" -> "")).toFuture().map { j =>
    JsonUtil.fromJsonString[MongoBuildInfo](j.toJson())
  }, Duration.Inf)
  object version {
    lazy val string: String = buildInfo.version
    lazy val major: Int = buildInfo.versionArray.head
    lazy val minor: Int = buildInfo.versionArray(1)
  }

  private val _initialized = new AtomicBoolean(false)
  def initialized: Boolean = _initialized.get()

  private var _collections = Set.empty[DBCollection[_ <: ModelObject]]
  def collections: Set[DBCollection[_ <: ModelObject]] = _collections

  /**
    * Key/Value store functionality against MongoDB
    */
  object store {
    private lazy val info = db.getCollection("extraInfo")

    object string {
      def get(key: String): Future[Option[String]] = {
        info
          .find(Document("_id" -> key))
          .toFuture()
          .map(_.map(d => JsonUtil.fromJsonString[Stored](d.toJson()).value).headOption)
      }

      def set(key: String, value: String): Future[Unit] = {
        val json = JsonUtil.toJsonString(Stored(key, value))
        info
          .replaceOne(
            Document("_id" -> key),
            Document(json),
            new ReplaceOptions().upsert(true)
          )
          .toFuture()
          .map(_ => ())
      }
    }

    def typed[T](key: String): TypedStore[T] = macro Macros.storeTyped[T]

    case class Stored(_id: String, value: String)
  }

  private lazy val versionStore = store.typed[DatabaseVersion]("databaseVersion")

  private var versions = ListBuffer.empty[DatabaseUpgrade]

  lazy val oplog: OperationsLog = new OperationsLog(client)

  register(CreateDatabase)

  def register(upgrade: DatabaseUpgrade): Unit = synchronized {
    assert(!initialized, "Database is already initialized. Cannot register upgrades after initialization.")
    if (!versions.contains(upgrade)) {
      versions += upgrade
    }
    ()
  }

  def init(): Future[Unit] = scribe.async {
    if (_initialized.compareAndSet(false, true)) {
      versionStore(DatabaseVersion()).flatMap { version =>
        val upgrades = versions.toList.filterNot(v => version.upgrades.contains(v.label) && !v.alwaysRun)
        upgrade(version, upgrades, version.upgrades.isEmpty)
      }
    } else {
      Future.successful(())
    }
  }

  private def upgrade(version: DatabaseVersion,
                      upgrades: List[DatabaseUpgrade],
                      newDatabase: Boolean,
                      currentlyBlocking: Boolean = true): Future[Unit] = scribe.async {
    val blocking = upgrades.exists(_.blockStartup)
    val future = upgrades.headOption match {
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
      case None => Future.successful(())
    }

    if (currentlyBlocking && !blocking && upgrades.nonEmpty) {
      scribe.info("Additional upgrades do not require blocking. Allowing application to start...")
      Future.successful(())
    } else {
      future
    }
  }

  def drop(): Future[Unit] = scribe.async(db.drop().toFuture().map(_ => ()))

  def dispose(): Unit = {
    client.close()
  }

  case class DatabaseVersion(upgrades: Set[String] = Set.empty, _id: String = "databaseVersion")

  private[giantscala] def addCollection(collection: DBCollection[_ <: ModelObject]): Unit = synchronized {
    _collections += collection
  }

  private[giantscala] def getCollection(name: String): MongoCollection[Document] = db.getCollection(name)
}