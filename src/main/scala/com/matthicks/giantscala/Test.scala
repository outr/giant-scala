package com.matthicks.giantscala

import com.mongodb.CursorType
import org.mongodb.scala.{MongoClient, Observer}
import org.mongodb.scala.bson.BsonTimestamp
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters._

object Test {
  def main(args: Array[String]): Unit = {
    val client = MongoClient()
    val db = client.getDatabase("local")
    val oplog = db.getCollection("oplog.rs")
    oplog.find(gte("ts", BsonTimestamp(math.floor(System.currentTimeMillis() / 1000.0).toInt, 0))).cursorType(CursorType.TailableAwait).noCursorTimeout(true).subscribe(new Observer[Document] {
      override def onNext(result: Document): Unit = {
        println(s"Next: $result")
      }

      override def onComplete(): Unit = {
        println("*** COMPLETE ***")
      }

      override def onError(e: Throwable): Unit = {
        println("*** ERROR ***")
        e.printStackTrace()
      }
    })

    println("subscribed!")
    Thread.sleep(Long.MaxValue)
  }
}
