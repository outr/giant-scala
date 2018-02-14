package com.outr.giantscala

import com.outr.giantscala.oplog.OperationsLog
import com.outr.giantscala.upgrade.DatabaseUpgrade
import com.mongodb.client.model.UpdateOptions
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoClient, MongoCollection}
import profig.JsonUtil

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.mongodb.scala

class MongoDatabase(url: String = "mongodb://localhost:27017", val name: String) {
  private lazy val client = MongoClient(url)
  protected lazy val db: scala.MongoDatabase = client.getDatabase(name)

  private var _collections = Set.empty[DBCollection[_ <: ModelObject]]
  def collections: Set[DBCollection[_ <: ModelObject]] = _collections

  private lazy val info = db.getCollection("extraInfo")

  private var versions = ListBuffer.empty[DatabaseUpgrade]

  lazy val oplog: OperationsLog = new OperationsLog(client)

  def register(upgrade: DatabaseUpgrade): Unit = synchronized {
    if (!versions.contains(upgrade)) {
      versions += upgrade
    }
    ()
  }

  def init(): Future[Unit] = {
    info
      .find(Document("_id" -> "databaseVersion"))
      .toFuture()
      .map(docs => docs.headOption.map(d => JsonUtil.fromJsonString[DatabaseVersion](d.toJson())))
      .map(_.getOrElse(DatabaseVersion(Set.empty))).flatMap { version =>
      val upgrades = versions.toList.filterNot(v => version.upgrades.contains(v.label) && !v.alwaysRun)
      upgrade(version, upgrades, version.upgrades.isEmpty)
    }
  }

  private def upgrade(version: DatabaseVersion, upgrades: List[DatabaseUpgrade], newDatabase: Boolean, currentlyBlocking: Boolean = true): Future[Unit] = {
    val blocking = upgrades.exists(_.blockStartup)
    val future = upgrades.headOption match {
      case Some(u) => if (!newDatabase || u.applyToNew) {
        scribe.info(s"Upgrading with database upgrade: ${u.label} (${upgrades.length - 1} upgrades left)...")
        u.upgrade().flatMap { _ =>
          val versionUpdated = version.copy(upgrades = version.upgrades + u.label)
          info.replaceOne(Document("_id" -> "databaseVersion"), Document(JsonUtil.toJsonString(versionUpdated)), new UpdateOptions().upsert(true)).toFuture().flatMap { _ =>
            scribe.info(s"Completed database upgrade: ${u.label} successfully")
            upgrade(versionUpdated, upgrades.tail, newDatabase, blocking)
          }
        }
      } else {
        scribe.info(s"Skipping database upgrade: ${u.label} as it doesn't apply to new database")
        val versionUpdated = version.copy(upgrades = version.upgrades + u.label)
        info.replaceOne(Document("_id" -> "databaseVersion"), Document(JsonUtil.toJsonString(versionUpdated)), new UpdateOptions().upsert(true)).toFuture().flatMap { _ =>
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

  def drop(): Future[Unit] = db.drop().toFuture().map(_ => ())

  def dispose(): Unit = {
    client.close()
  }

  case class DatabaseVersion(upgrades: Set[String], _id: String = "databaseVersion")

  private[giantscala] def addCollection(collection: DBCollection[_ <: ModelObject]): Unit = synchronized {
    _collections += collection
  }

  private[giantscala] def getCollection(name: String): MongoCollection[Document] = db.getCollection(name)
}