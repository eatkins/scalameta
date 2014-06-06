package scala.reflect
package semantic

import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.reflect.core._

trait HostContext {
  def syntaxProfile: SyntaxProfile
  def semanticProfile: SemanticProfile

  def defns(ref: Ref): Seq[Tree]
  def attrs(tree: Tree): Seq[Attr]

  def owner(tree: Tree): Scope
  def members(scope: Scope): Seq[Tree]
  def members(scope: Scope, name: Name): Seq[Tree]

  def <:<(tpe1: Type, tpe2: Type): Boolean
  def supertypes(tpe: Type): Seq[Type]
  def supermembers(member: Member): Seq[Member]
  def subclasses(tpe: Type): Seq[Member.Template]
  def submembers(member: Member): Seq[Member]
  def linearization(tpes: Seq[Type]): Seq[Type]
  def self(tpe: Type): Aux.Self
  def lub(tpes: Seq[Type]): Type
  def glb(tpes: Seq[Type]): Type
  def widen(tpe: Type): Type
  def dealias(tpe: Type): Type
  def erasure(tpe: Type): Type
}

trait MacroContext extends HostContext {
  def application: Tree
  def warning(msg: String): Unit
  def error(msg: String): Unit
  def abort(msg: String): Nothing
  def resources: Seq[String]
  def resourceAsBytes(url: String): Array[Byte]
}
