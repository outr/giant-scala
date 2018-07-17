package com.outr.giantscala.oplog

import com.mongodb.client.model.changestream.OperationType
import com.outr.giantscala.{DBCollection, ModelObject}
import io.circe.Json
import org.mongodb.scala
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.changestream.ChangeStreamDocument
import profig.JsonUtil
import reactify.{Channel, InvocationType, Observer}

class CollectionMonitor[T <: ModelObject](collection: DBCollection[T]) extends Observer[Operation] {
  private lazy val ns: String = s"${collection.db.name}.${collection.name}"

  /**
    * Receives all operations for this collection
    */
  lazy val operation: Channel[Operation] = Channel[Operation]

  /**
    * Only receives OpType.Insert records
    */
  lazy val insert: Channel[T] = operation.collect {
    case op if op.`type` == OpType.Insert => collection.converter.fromDocument(Document(op.o.spaces2))
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
          c := collection.converter.fromDocument(Document(op.o.spaces2))
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
    case op if op.`type` == OpType.Delete => JsonUtil.fromJsonString[Delete](op.o.spaces2)
  }

  /**
    * Starts the oplog monitor on the database if it's not already running and begins monitoring for operations relating
    * to this collection. This must be called before any operations can be received by #insert, #update, or #delete.
    */
  def start(): Unit = if (collection.db.version.major >= 4) {
    collection.collection.watch[Document]().subscribe(new scala.Observer[ChangeStreamDocument[Document]] {
      override def onNext(result: ChangeStreamDocument[Document]): Unit = {
        val opChar = result.getOperationType match {
          case OperationType.INSERT => 'i'
          case OperationType.UPDATE | OperationType.REPLACE => 'u'
          case OperationType.DELETE => 'd'
          case opType => throw new RuntimeException(s"Unsupported OperationType: $opType / ${result.getFullDocument}")
        }
        val op = Operation(
          ts = result.getClusterTime.getValue,
          t = 0,
          h = result.hashCode(),
          v = 0,
          op = opChar,
          ns = result.getNamespace.getFullName,
          wall = result.getClusterTime.getValue,
          o = Option(result.getFullDocument)
            .map(d => io.circe.parser.parse(d.toJson()))
            .flatMap {
              case Left(_) => None
              case Right(json) => Some(json)
            }
            .getOrElse(Json.obj("_id" -> Json.fromString(result.getDocumentKey.getFirstKey)))
        )
        operation := op
      }

      override def onError(e: Throwable): Unit = {
        scribe.error(e)
      }

      override def onComplete(): Unit = {}
    })
  } else {
    collection.db.oplog.startIfNotRunning()
    collection.db.oplog.operations.observe(this)
  }

  /**
    * Stops monitoring the oplog for operations related to this collection. Does not stop the oplog from running.
    */
  def stop(): Unit = {
    collection.db.oplog.operations.detach(this)
  }

  override def apply(op: Operation, `type`: InvocationType): Unit = if (op.ns == ns) {
    operation := op
  }
}