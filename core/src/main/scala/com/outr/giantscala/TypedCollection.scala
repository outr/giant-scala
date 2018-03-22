package com.outr.giantscala

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

@compileTimeOnly("Enable Macros to expand annotation")
class TypedCollection[T <: ModelObject] extends StaticAnnotation {
  def macroTransform(annottees: Any*): AnyRef = macro TypedCollection.typed[T]
}

object TypedCollection {
  def typed[T](c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[AnyRef] = {
    import c.universe._

    val q"""new TypedCollection[$cls]().macroTransform($a)""" = c.macroApplication
    val tpe = c.typecheck(q"(??? : $cls)").tpe
    val fields = tpe.decls.foreach { d =>
      println(s"Field: $d / ${d.getClass}")
    }

    println(s"*** Macro Application? $tpe")

    c.abort(c.enclosingPosition, "Not finished!")
  }
}