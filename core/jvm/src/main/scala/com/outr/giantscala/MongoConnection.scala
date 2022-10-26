package com.outr.giantscala

import com.mongodb.{Block, MongoCredential, ServerAddress}
import org.mongodb.scala.connection.ClusterSettings.Builder
import org.mongodb.scala.connection.{ClusterSettings, ConnectionPoolSettings, ServerSettings, SocketSettings}
import org.mongodb.scala.{MongoClient, MongoClientSettings}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

class MongoConnection(val urls: List[MongoDBServer] = MongoDatabase.urls,
                      val connectionPoolMinSize: Int = 0,
                      val connectionPoolMaxSize: Int = 100,
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
  private[giantscala] val client = MongoClient(settings)
}
