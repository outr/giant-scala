package com.outr.giantscala.oplog

import com.mongodb.client.model.changestream.OperationType
import com.outr.giantscala.{DBCollection, ModelObject}
import fabric._
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.Asable
import org.mongodb.scala
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonTimestamp
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.changestream.{ChangeStreamDocument, FullDocument}
import reactify.reaction.{Reaction, ReactionStatus}
import reactify._
import _root_.scala.util.Try

import _root_.scala.concurrent.duration._

class CollectionMonitor[T <: ModelObject[T]](collection: DBCollection[T],
                                             mongoCollection: MongoCollection[Document]) extends Reaction[Operation] {
  private lazy val ns: String = s"${collection.db.name}.${collection.collectionName}"

  /**
    * Receives all operations for this collection
    */
  lazy val operation: Channel[Operation] = Channel[Operation]

  /**
    * Only receives OpType.Insert records
    */
  lazy val insert: Channel[T] = operation.collect {
    case op if op.`type` == OpType.Insert => collection.converter.fromDocument(Document(JsonFormatter.Default(op.o)))
  }

  /**
    * Only receives OpType.Update records
    *
    * Note: this will not receive incomplete replacements. For example, &#36;set calls will be ignored as they apply to
    * multiple documents as well as not having a complete view of the object
    */
  lazy val update: Channel[T] = {
    val c = Channel[T]
    operation.attach { op =>
      if (op.`type` == OpType.Update) {
        try {
          c := collection.converter.fromDocument(Document(JsonFormatter.Default(op.o)))
        } catch {
          case _: Throwable => // Ignore records that can't be converted (covers situations like $set)
        }
      }
    }
    c
  }

  /**
    * Only receives OpType.Delete _ids
    */
  lazy val delete: Channel[Delete] = operation.collect {
    case op if op.`type` == OpType.Delete => op.o.as[Delete]
  }

  private lazy val watcher: scala.Observer[ChangeStreamDocument[Document]] = new scala.Observer[ChangeStreamDocument[Document]] {
    override def onNext(result: ChangeStreamDocument[Document]): Unit = {
      scribe.debug(s"Received document (${collection.collectionName}): ${result.getOperationType} for ${result.getNamespace}")
      val opChar = result.getOperationType match {
        case OperationType.INSERT => 'i'
        case OperationType.UPDATE | OperationType.REPLACE => 'u'
        case OperationType.DELETE => 'd'
        case OperationType.INVALIDATE => 'v'
        case OperationType.DROP => 'x'
        case opType => throw new RuntimeException(s"Unsupported OperationType: $opType / ${result.getFullDocument}")
      }
      val documentKey = Option(result.getDocumentKey).map(_.getFirstKey).getOrElse("")
      val op = Operation(
        ts = result.getClusterTime.getValue,
        t = 0,
        h = result.hashCode(),
        v = 0,
        op = opChar,
        ns = Option(result.getNamespace).map(_.getFullName).getOrElse(""),
        wall = result.getClusterTime.getValue,
        o = Option(result.getFullDocument)
          .map(d => JsonParser(d.toJson()))
          .getOrElse(obj("_id" -> str(documentKey)))
      )
      operation := op

      if (result.getOperationType == OperationType.INVALIDATE) {
        scribe.debug(s"Invalidated, restarting watcher for ${collection.collectionName}")
        val timestamp = new BsonTimestamp(result.getClusterTime.getTime, result.getClusterTime.getInc + 1)
        subscribe(Some(timestamp))
      }
    }

    override def onError(e: Throwable): Unit = {
      scribe.error(e)
    }

    override def onComplete(): Unit = {
      scribe.debug(s"Watcher on ${collection.collectionName} completed")
    }
  }

  /**
    * Starts the oplog monitor on the database if it's not already running and begins monitoring for operations relating
    * to this collection. This must be called before any operations can be received by #insert, #update, or #delete.
    */
  def start(): Unit = if (collection.db.useOplog) {
    collection.db.oplog.startIfNotRunning()
    collection.db.oplog.operations.reactions += this
  } else {
    subscribe()
  }

  private def subscribe(startAt: Option[BsonTimestamp] = None, awaitTime: Duration = 10.hours): Unit = {
    var w = mongoCollection.watch[Document]().maxAwaitTime(awaitTime).fullDocument(FullDocument.UPDATE_LOOKUP)
    startAt.foreach { timestamp =>
      w = w.startAtOperationTime(timestamp)
    }
    w.subscribe(watcher)
  }

  /**
    * Stops monitoring the oplog for operations related to this collection. Does not stop the oplog from running.
    */
  def stop(): Unit = if (collection.db.version.major >= 4) {
    // TODO: support stopping
  } else {
    collection.db.oplog.operations.reactions -= this
  }

  override def apply(op: Operation, previous: Option[Operation]): ReactionStatus = {
    if (op.ns == ns) {
      operation := op
    }
    ReactionStatus.Continue
  }
}