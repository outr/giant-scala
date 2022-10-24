package com.outr.giantscala.oplog

import com.outr.giantscala.Converter
import com.mongodb.CursorType
import fabric.io.JsonParser
import fabric.rw.Asable
import org.mongodb.scala.bson.BsonTimestamp
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.{MongoClient, Observer, Subscription}
import reactify.Channel

class OperationsLog(client: MongoClient) extends Observer[Document] {
  private lazy val db = client.getDatabase("local")
  private lazy val oplog = db.getCollection("oplog.rs")
  private var subscription: Option[Subscription] = None

  /**
    * Channel receiving original Documents coming over the oplog before transformation.
    */
  lazy val documents: Channel[Document] = Channel[Document]

  /**
    * Channel receiving transformed oplog Documents into Operation instances.
    */
  lazy val operations: Channel[Operation] = Channel[Operation]

  /**
    * Starts the oplog monitor. Will begin firing Document instances to `documents` and Operation instances to
    * `operations`. Will throw an error if already running. See #startIfNotRunning for a safe ignorant start.
    */
  def start(): Unit = synchronized {
    assert(subscription.isEmpty, "Already started!")
    val start = BsonTimestamp(math.floor(System.currentTimeMillis() / 1000.0).toInt, 0)
    oplog
      .find(gte("ts", start))
      .cursorType(CursorType.TailableAwait)
      .noCursorTimeout(true)
      .subscribe(this)
  }

  /**
    * Works like #start, but does not throw an error if already started.
    */
  def startIfNotRunning(): Unit = synchronized {
    if (!isRunning) start()
  }

  /**
    * True if OperationsLog has been started but has not been stopped.
    */
  def isRunning: Boolean = subscription.nonEmpty

  /**
    * Stops the monitor on the oplog.
    */
  def stop(): Unit = synchronized {
    if (isRunning) {
      subscription match {
        case Some(s) => {
          s.unsubscribe()
          subscription = None
        }
        case None => throw new RuntimeException("Not running!")
      }
    }
  }

  override def onSubscribe(subscription: Subscription): Unit = synchronized {
    this.subscription = Some(subscription)
    subscription.request(Long.MaxValue)
  }

  override def onNext(result: Document): Unit = {
    documents := result
    val op = JsonParser(result.toJson(Converter.settings)).as[Operation]
    operations := op
  }

  override def onError(e: Throwable): Unit = scribe.error(s"OperationsLog error", e)

  override def onComplete(): Unit = {}
}
