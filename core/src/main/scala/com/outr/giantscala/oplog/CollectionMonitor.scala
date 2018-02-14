package com.outr.giantscala.oplog

import com.outr.giantscala.{DBCollection, ModelObject}
import org.mongodb.scala.bson.collection.immutable.Document
import reactify.{Channel, InvocationType, Observer}

class CollectionMonitor[T <: ModelObject](collection: DBCollection[T]) extends Observer[Operation] {
  private lazy val ns: String = s"${collection.db.name}.${collection.name}"

  /**
    * Unfiltered operations for this collection. Contains inserts, updates, and deletes. See #insert, #update, and
    * #delete for explicit monitoring.
    */
  lazy val operations: Channel[(OpType, T)] = Channel[(OpType, T)]

  /**
    * Filtered channel from #operations to only receive OpType.Insert records
    */
  lazy val insert: Channel[T] = operations.collect {
    case (opType, t) if opType == OpType.Insert => t
  }

  /**
    * Filtered channel from #operations to only receive OpType.Update records
    */
  lazy val update: Channel[T] = operations.collect {
    case (opType, t) if opType == OpType.Update => t
  }

  /**
    * Filtered channel from #operations to only receive OpType.Delete records
    */
  lazy val delete: Channel[T] = operations.collect {
    case (opType, t) if opType == OpType.Delete => t
  }

  /**
    * Starts the oplog monitor on the database if it's not already running and begins monitoring for operations relating
    * to this collection. This must be called before any operations can be received by #operations, #insert, #update, or
    * #delete.
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
    operations := (OpType(op.op) -> collection.converter.fromDocument(Document(op.o.spaces2)))
  }
}