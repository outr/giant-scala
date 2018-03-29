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
    val fields = fieldNamesAndTypes(c)(tpe).toList.map {
      case (n, _) => q"val ${n.toTermName}: String = ${n.toString}"
    }

    def modifiedObject(objectDef: ModuleDef): c.Expr[AnyRef] = {
      val ModuleDef(_, objectName, template) = objectDef
      val body = template.body.tail     // Drop the init method
      val ret = q"""
        object $objectName {
          ..$fields
          ..$body
        }
      """
      c.Expr[AnyRef](ret)
    }

    annottees.map(_.tree) match {
      case (objectDecl: ModuleDef) :: _ => modifiedObject(objectDecl)
      case x => c.abort(c.enclosingPosition, s"@table can only be applied to an object, not to $x")
    }
  }

  private def fieldNamesAndTypes(c: whitebox.Context)
                                (tpe: c.universe.Type): Iterable[(c.universe.Name, c.universe.Type)] = {
    import c.universe._

    object CaseField {
      def unapply(trmSym: TermSymbol): Option[(Name, Type)] = {
        if (trmSym.isVal && trmSym.isCaseAccessor) Some((TermName(trmSym.name.toString.trim), trmSym.typeSignature))
        else None
      }
    }

    tpe.decls.collect {
      case CaseField(n, t) => (n, t)
    }
  }
}