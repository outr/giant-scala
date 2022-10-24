package com.outr.giantscala

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.mongodb.{Block, MongoCredential, ServerAddress}

import scala.language.experimental.macros
import com.outr.giantscala.oplog.OperationsLog
import com.outr.giantscala.upgrade.{CreateDatabase, DatabaseUpgrade}
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.{Asable, Convertible, RW}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.connection.{ClusterSettings, ConnectionPoolSettings, ServerSettings, SocketSettings}
import org.mongodb.scala.connection.ClusterSettings.Builder
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCollection}
import profig.Profig

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scribe.Execution.global
import org.mongodb.scala.{MongoDatabase => ScalaMongoDatabase}
import org.mongodb.scala.model.ReplaceOptions

import scala.concurrent._
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class MongoDatabase(val name: String,
                    val urls: List[MongoDBServer] = MongoDatabase.urls,
                    val connectionPoolMinSize: Int = 0,
                    val connectionPoolMaxSize: Int = 100,
                    val connectionPoolMaxWaitQueue: Int = 500,
                    val connectionPoolMaxWaitTime: FiniteDuration = 2.minutes,
                    val heartbeatFrequency: FiniteDuration = 10.seconds,
                    val connectionTimeout: FiniteDuration = 10.seconds,
                    protected val credentials: Option[Credentials] = MongoDatabase.credentials) {
  assert(urls.nonEmpty, "At least one URL must be included")

  private val settings = {
    val builder = MongoClientSettings.builder()
    builder.applyToClusterSettings(new Block[ClusterSettings.Builder] {
      override def apply(b: Builder): Unit = {
        b.hosts(urls.map { url =>
          new ServerAddress(url.host, url.port)
        }.asJava)
      }
    })
    builder.applyToConnectionPoolSettings(new Block[ConnectionPoolSettings.Builder] {
      override def apply(b: ConnectionPoolSettings.Builder): Unit = {
        b.minSize(connectionPoolMinSize)
        b.maxSize(connectionPoolMaxSize)
        b.maxWaitTime(connectionPoolMaxWaitTime.toMillis, TimeUnit.MILLISECONDS)
      }
    })
    builder.applyToServerSettings(new Block[ServerSettings.Builder] {
      override def apply(b: ServerSettings.Builder): Unit = {
        b.heartbeatFrequency(heartbeatFrequency.toMillis, TimeUnit.MILLISECONDS)
      }
    })
    builder.applyToSocketSettings(new Block[SocketSettings.Builder] {
      override def apply(b: SocketSettings.Builder): Unit = {
        b.connectTimeout(connectionTimeout.toSeconds.toInt, TimeUnit.SECONDS)
      }
    })
    credentials.foreach { c =>
      builder.credential(MongoCredential.createCredential(c.username, c.authenticationDatabase, c.password.toCharArray))
    }
    builder.build()
  }
  private val client = MongoClient(settings)
  protected val db: ScalaMongoDatabase = client.getDatabase(name)

  // TODO: support client.startSession().toFuture().map(_.startTransaction()) for sessions and transactions on modifications (update, insert, and delete)

  val buildInfo: MongoBuildInfo = Await.result(db.runCommand(Document("buildinfo" -> "")).toFuture().map { j =>
    JsonParser(j.toJson()).as[MongoBuildInfo]
  }, Duration.Inf)
  object version {
    lazy val string: String = buildInfo.version
    lazy val major: Int = buildInfo.versionArray.head
    lazy val minor: Int = buildInfo.versionArray(1)
  }
  var useOplog: Boolean = version.major < 4

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
      def get(key: String): Future[Option[String]] = {
        info
          .find(Document("_id" -> key))
          .toFuture()
          .map(_.map(d => JsonParser(d.toJson()).as[Stored].value).headOption)
      }

      def set(key: String, value: String): Future[Unit] = {
        val json = JsonFormatter.Compact(Stored(key, value).json)
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

    def typed[T](key: String)(implicit rw: RW[T]): TypedStore[T] = new TypedStore[T] {
      override def get: Future[Option[T]] = s.string.get(key).map(_.map(json => JsonParser(json).as[T]))

      override def apply(default: => T): Future[T] = get.map(_.getOrElse(default))

      override def set(value: T): Future[Unit] = s.string.set(key, JsonFormatter.Compact(value.json))
    }

    case class Stored(_id: String, value: String)

    object Stored {
      implicit val rw: RW[Stored] = RW.gen
    }
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

  def init(): Future[Unit] = {
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
                      currentlyBlocking: Boolean = true): Future[Unit] = {
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
      future.failed.map { throwable =>
        scribe.error("Database upgrade failure", throwable)
      }
      Future.successful(())
    } else {
      future
    }
  }

  def drop(): Future[Unit] = db.drop().toFuture().map(_ => ())

  def dispose(): Unit = {
    client.close()
  }

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