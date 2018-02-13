package com.matthicks.giantscala

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macros to expand")
object Macros {
  def auto[T](c: blackbox.Context)(implicit t: c.WeakTypeTag[T]): c.Tree = {
    import c.universe._

    q"""
       import org.mongodb.scala.bson.collection.immutable.Document
       import profig.JsonUtil

       new Converter[$t] {
         override def toDocument(t: $t): Document = {
           Document(JsonUtil.toJsonString(t))
         }

         override def fromDocument(document: Document): $t = {
           JsonUtil.fromJsonString[$t](document.toJson())
         }
       }
     """
  }
}
