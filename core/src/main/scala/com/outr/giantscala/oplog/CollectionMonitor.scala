package com.outr.giantscala.oplog

import com.outr.giantscala.{DBCollection, ModelObject}
import org.mongodb.scala.bson.collection.immutable.Document
import profig.JsonUtil
import reactify.{Channel, InvocationType, Observer}

class CollectionMonitor[T <: ModelObject](collection: DBCollection[T]) extends Observer[Operation] {
  private lazy val ns: String = s"${collection.db.name}.${collection.name}"

  /**
    * Only receives OpType.Insert records
    */
  lazy val insert: Channel[T] = Channel[T]

  /**
    * Only receives OpType.Update records
    */
  lazy val update: Channel[T] = Channel[T]

  /**
    * Only receives OpType.Delete _ids
    */
  lazy val delete: Channel[Delete] = Channel[Delete]

  /**
    * Starts the oplog monitor on the database if it's not already running and begins monitoring for operations relating
    * to this collection. This must be called before any operations can be received by #insert, #update, or #delete.
    */
  def start(): Unit = {
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
    OpType(op.op) match {
      case OpType.Insert => insert := collection.converter.fromDocument(Document(op.o.spaces2))
      case OpType.Update => update := collection.converter.fromDocument(Document(op.o.spaces2))
      case OpType.Delete => delete := JsonUtil.fromJsonString[Delete](op.o.spaces2)
      case _ => // Ignore others
    }
  }
}