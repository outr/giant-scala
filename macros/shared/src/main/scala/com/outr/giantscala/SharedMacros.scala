package com.outr.giantscala

import scala.annotation.compileTimeOnly
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macros to expand")
object SharedMacros {
  def fieldValue[T](c: blackbox.Context)(value: c.Expr[T])(implicit t: c.WeakTypeTag[T]): c.Tree = {
    import c.universe._

    q"""
       import io.circe.Json
       import profig.JsonUtil

       Json.obj(${c.prefix}.name -> JsonUtil.toJson($value))
     """
  }
}
