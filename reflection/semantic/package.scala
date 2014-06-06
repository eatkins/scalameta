package scala.reflect

import org.scalareflect.adt._
import org.scalareflect.annotations._
import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.reflect.core._

package object semantic {
  @hosted def semanticProfile: SemanticProfile = delegate
  final case class SemanticProfile(dynamics: Boolean,
                                   postfixOps: Boolean,
                                   reflectiveCalls: Boolean,
                                   implicitConversions: Boolean,
                                   higherKinds: Boolean,
                                   existentials: Boolean,
                                   macros: Boolean)

  implicit class RichTree(tree: Tree) {
    @hosted def attrs: Seq[Attr] = delegate
    @hosted private[semantic] def internalTpe: Type = attrs.flatMap(_.collect{ case tpe: Attr.Type => tpe } match {
      case Attr.Type(tpe: Type) :: Nil => succeed(tpe)
      case _ => fail("typecheck has failed")
    })
    @hosted private[semantic] def internalParamTpe: Param.Type = attrs.flatMap(_.collect{ case tpe: Attr.Type => tpe } match {
      case Attr.Type(paramTpe) :: Nil => succeed(paramTpe)
      case _ => fail("typecheck has failed")
    })
  }

  implicit class SemanticTermOps(tree: Term) {
    @hosted def tpe: Type = tree.internalTpe
  }

  implicit class SemanticTypeOps(tree: Type) {
    @hosted def <:<(other: Type): Boolean = delegate
    @hosted def weak_<:<(other: Type): Boolean = ???
    @hosted def widen: Type = delegate
    @hosted def dealias: Type = delegate
    @hosted def erasure: Type = delegate
    @hosted def companion: Type.Ref = tree match {
      case ref: Type.Ref => ref.defns.flatMap {
        case Seq(t: Member.Template) => t.companion
        case _ => fail("companion not found")
      }.map(_.ref.toTypeRef)
      case _ => fail("companion not found")
    }
  }

  @hosted private[semantic] def supertypesToMembers(tpes: Seq[Type]): Seq[Member.Template] = {
    def extractTemplate(ref: Type.Ref) = {
      for {
        defns <- ref.defns
        result <- defns match {
          case Seq(t: Member.Template) => succeed(t)
          case d => fail(s"unexpected ref $ref to $d returned from supertypes")
        }
      } yield result
    }
    succeed(tpes) mmap {
      case ref: Type.Ref => extractTemplate(ref)
      case Type.Apply(ref: Type.Ref, _) => extractTemplate(ref)
      case tpe => fail(s"unexpected type $tpe returned from supertypes")
    }
  }
  implicit class SemanticTemplatesOps(val parents: Seq[Member.Template]) {
    @hosted def linearization: Seq[Member.Template] = {
      val linearization = parents.map(_.ref.toTypeRef).linearization
      linearization.flatMap(tpes => supertypesToMembers(tpes))
    }
  }

  implicit class SemanticTypesOps(val parents: Seq[Type]) {
    @hosted def linearization: Seq[Type] = wrapHosted(_.linearization(parents))
  }

  @hosted def lub(tpes: Seq[Type]): Type = delegate
  @hosted def glb(tpes: Seq[Type]): Type = delegate

  implicit class SemanticRefOps(tree: Ref) {
    private[semantic] def toTypeRef: Type.Ref = ??? // TODO: t"$tree"
    @hosted def defns: Seq[Member] = wrapHosted(_.defns(tree).collect{ case m: Member => m })
  }

  implicit class SemanticTypeRefOps(tree: Type.Ref) {
    @hosted def defns: Seq[Member.Type] = (tree: Ref).defns.flatMap(defns => {
      if (defns.exists(!_.isInstanceOf[Member.Type])) fail(s"unexpected $defns for ref $tree")
      else succeed(defns.asInstanceOf[Seq[Member.Type]])
    })
    @hosted def defn: Member.Type = defns.flatMap(_.findUnique)
  }

  implicit class SemanticTermRefOps(tree: Term.Ref) {
    @hosted def defns: Seq[Member.Term] = (tree: Ref).defns.flatMap(defns => {
      if (defns.exists(!_.isInstanceOf[Member.Term])) fail(s"unexpected $defns for ref $tree")
      else succeed(defns.asInstanceOf[Seq[Member.Term]])
    })
    @hosted def defn: Member.Term = defns.flatMap(_.findUnique)
  }

  implicit class SemanticMembers[A <: Member.Term](tree: Seq[A]) {
    def resolve(tpes: Seq[core.Type]): A = ??? // TODO: implement this in terms of Tree.attrs and Attr.Ref
  }

  implicit class SemanticMemberOps(tree: Member) {
    // TODO: expose type parameter instantiation facilities, e.g. `def foo[T]: T = ...` => `def foo: Int = ...`
    def ref: Ref = tree match {
      // TODO: this logic is not enough. if a member is a synthetic typeSignatureIn'd thing, we also need to remember its prefix
      case self: Aux.Self => self.name.getOrElse(Term.This(None))
      case named: Has.Name => named.name
    }
    @hosted def overrides: Seq[Member] = tree match {
      case mte: Member.Term => wrapHosted(_.supermembers(mte))
      case mty: Member.Type => wrapHosted(_.supermembers(mty))
    }
    def annots: Seq[Mod.Annot] = tree.mods.collect{ case annot: Mod.Annot => annot }
    def doc: Option[Mod.Doc] = tree.mods.collect{ case doc: Mod.Doc => doc }.headOption
    def isVal: Boolean = tree.isInstanceOf[Term.Name] && (tree.parent.map(parent => parent.isInstanceOf[Decl.Val] || parent.isInstanceOf[Defn.Val]).getOrElse(false))
    def isVar: Boolean = tree.isInstanceOf[Term.Name] && (tree.parent.map(parent => parent.isInstanceOf[Decl.Var] || parent.isInstanceOf[Defn.Var]).getOrElse(false))
    def isDef: Boolean = tree.isInstanceOf[Member.Def]
    def isType: Boolean = tree.isInstanceOf[Member.AbstractOrAliasType]
    def isClass: Boolean = tree.isInstanceOf[Defn.Class]
    def isTrait: Boolean = tree.isInstanceOf[Defn.Trait]
    def isObject: Boolean = tree.isInstanceOf[Defn.Object]
    def isPkg: Boolean = tree.isInstanceOf[Pkg]
    def isPkgObject: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Package])
    def isJava: Boolean = ??? // TODO: need special trees for Java artifacts
    def isPrivate: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Private])
    def isProtected: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Protected])
    def isPublic: Boolean = !tree.isPrivate && !tree.isProtected
    def isImplicit: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Implicit])
    def isFinal: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Final])
    def isSealed: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Sealed])
    @hosted def isOverride: Boolean = tree.overrides.map(_.nonEmpty)
    def isCase: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Case])
    def isAbstract: Boolean = (tree.mods.exists(_.isInstanceOf[Mod.Abstract]) || tree.isInstanceOf[Decl]) && !isAbstractOverride
    def isCovariant: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Covariant])
    def isContravariant: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Contravariant])
    def isLazy: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Lazy])
    def isAbstractOverride: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Abstract]) && tree.mods.exists(_.isInstanceOf[Mod.Override])
    def isMacro: Boolean = tree.mods.exists(_.isInstanceOf[Mod.Macro])
    def isByNameParam: Boolean = ???
    def isVarargParam: Boolean = ???
    def isValParam: Boolean = tree.mods.exists(_.isInstanceOf[Mod.ValParam])
    def isVarParam: Boolean = tree.mods.exists(_.isInstanceOf[Mod.VarParam])
  }

  implicit class SemanticTermMemberOps(tree: Member.Term) {
    def ref: Term.Ref = new SemanticMemberOps(tree).ref.asInstanceOf[Term.Ref]
    @hosted def overrides: Seq[Member.Term] = new SemanticMemberOps(tree).overrides.map(_.asInstanceOf[Seq[Member.Term]])
  }

  implicit class SemanticTypeMemberOps(tree: Member.Type) {
    def ref: Type.Ref = new SemanticMemberOps(tree).ref.asInstanceOf[Type.Ref]
    @hosted def overrides: Seq[Member.Type] = new SemanticMemberOps(tree).overrides.map(_.asInstanceOf[Seq[Member.Type]])
  }

  implicit class SemanticDefMemberOps(tree: Member.Def) {
    @hosted def tpe: core.Type = tree match {
      case x: Decl.Def => succeed(x.decltpe)
      case x: Decl.Procedure => ??? // TODO: t"Unit"
      case x: Defn.Def => x.body.tpe
      case x: Defn.Procedure => ??? // TODO: t"Unit"
    }
  }

  implicit class SemanticTemplateMemberOps(tree: Member.Template) {
    @hosted def superclasses: Seq[Member.Template] = tree.ref.toTypeRef.superclasses
    @hosted def supertypes: Seq[core.Type] = tree.ref.toTypeRef.supertypes
    @hosted def subclasses: Seq[Member.Template] = tree.ref.toTypeRef.subclasses
    @hosted def self: Aux.Self = succeed(tree.templ.self)
    @hosted def companion: Member.Template = tree match {
      case _: Defn.Class => findCompanion{ case x: Defn.Object => x }
      case _: Defn.Trait => findCompanion{ case x: Defn.Object => x }
      case _: Defn.Object => findCompanion{ case x: Defn.Class => x; case x: Defn.Trait => x }
    }
    @hosted private[semantic] def findCompanion[T <: Member.Template](f: PartialFunction[Member, T]): T = {
      val companionName = {
        if (tree.name.isInstanceOf[core.Term.Name]) core.Type.Name(tree.name.value, isBackquoted = false) else
        core.Term.Name(tree.name.value, isBackquoted = false)
      }
      val candidates = tree.owner.flatMap(_.members(companionName))
      candidates.flatMap{candidates =>
        val relevant = candidates.collect(f).headOption
        relevant.map(result => succeed(result)).getOrElse(fail("companion not found"))
      }
    }
  }

  implicit class SemanticDeclValOps(tree: Decl.Val) {
    @hosted def tpe: core.Type = succeed(tree.decltpe)
  }

  implicit class SemanticDeclVarOps(tree: Decl.Var) {
    @hosted def tpe: core.Type = succeed(tree.decltpe)
  }

  implicit class SemanticDefnValOps(tree: Defn.Val) {
    @hosted def tpe: core.Type = tree.rhs.tpe
  }

  implicit class SemanticDefnVarOps(tree: Defn.Var) {
    @hosted def tpe: core.Type = tree.rhs.map(_.tpe).getOrElse(succeed(tree.decltpe.get))
  }

  implicit class SemanticDefnClassOps(tree: Defn.Class) {
    @hosted def companion: Object = new SemanticTemplateMemberOps(tree).companion.map(_.asInstanceOf[Object])
  }

  implicit class SemanticDefnTraitOps(tree: Defn.Trait) {
    @hosted def companion: Object = new SemanticTemplateMemberOps(tree).companion.map(_.asInstanceOf[Object])
  }

  implicit class SemanticDefnObjectOps(tree: Defn.Object) {
    @hosted def companion: Member.Template with Member.Type = new SemanticTemplateMemberOps(tree).companion.map(_.asInstanceOf[Member.Template with Member.Type])
  }

  implicit class SemanticPkgObjectOps(tree: Defn.Object) {
    @hosted def companion: Member.Template with Member.Type = new SemanticTemplateMemberOps(tree).companion.map(_.asInstanceOf[Member.Template with Member.Type])
  }

  implicit class SemanticCtorOps(tree: Ctor) {
    @hosted def tpe: core.Type = tree.internalTpe
  }

  implicit class SemanticParentOps(tree: Param) {
    @hosted def ctor: Ctor = tree.attrs.flatMap(_.collect{ case defn: Attr.Defn => defn } match {
      case Attr.Defn(defn: Ctor) :: Nil => succeed(defn)
      case _ => fail("typecheck has failed")
    })
  }

  implicit class SemanticSelfOps(tree: Aux.Self) {
    def ref: Term.This = new SemanticMemberOps(tree).ref.asInstanceOf[Term.This]
    @hosted def tpe: Type = tree.internalTpe
  }

  implicit class SemanticParamOps(tree: Param) {
    @hosted def tpe: Param.Type = tree.internalParamTpe
  }

  implicit class SemanticTemplateOps(tree: Aux.Template) {
    @hosted def tpe: Type = tree.internalTpe
  }

  private[semantic] implicit class SemanticIterableOps[T](val members: Seq[T]) {
    @hosted def findUnique: T = members match {
      case Seq(unique) => succeed(unique)
      case Seq() => fail("no members found")
      case _ => fail("multiple members found")
    }
  }

  implicit class SemanticTreeOps(tree: Tree) {
    @hosted def owner: Scope = wrapHosted(_.owner(tree))
  }

  implicit class SemanticScopeOps(tree: Scope) {
    @hosted def members: Seq[Member] = wrapHosted(_.members(tree).collect{ case m: Member => m })
    @hosted def members(name: Name): Seq[Member] = wrapHosted(_.members(tree).collect{ case m: Member => m })
    @hosted private[semantic] def allMembers[T: ClassTag]: Seq[T] = {
      members.map(_.collect { case x: T => x })
    }
    @hosted private[semantic] def uniqueMember[T: ClassTag](s_name: String): T = {
      val isTerm = classOf[Member.Term].isAssignableFrom(classTag[T].runtimeClass)
      val name = if (isTerm) Term.Name(s_name, isBackquoted = false) else Type.Name(s_name, isBackquoted = false)
      members(name).map(_.collect { case x: T => x }).flatMap(_.findUnique)
    }
  }

  implicit class SemanticTopLevelScopeOps(tree: Scope.TopLevel) {
    @hosted def packages: Seq[Pkg] = tree.allMembers[Pkg]
    @hosted def packages(name: Name): Pkg = tree.uniqueMember[Pkg](name.toString)
    @hosted def packages(name: String): Pkg = tree.uniqueMember[Pkg](name.toString)
    @hosted def packages(name: scala.Symbol): Pkg = tree.uniqueMember[Pkg](name.toString)
    @hosted def pkgobject: Defn.Object = tree.allMembers[Defn.Object].map(_.filter(_.isPkgObject)).flatMap(_.findUnique)
  }

  implicit class SemanticTemplateScopeOps(tree: Scope.Template) {
    // TODO: directSuperclasses and others
    @hosted def superclasses: Seq[Member.Template] = tree match {
      case x: Aux.Template => x.tpe.flatMap(_.superclasses)
      case x: Member.Template => x.templ.superclasses
      case x: Type => x.supertypes.flatMap(tpes => supertypesToMembers(tpes))
    }
    @hosted def supertypes: Seq[Type] = tree match {
      case x: Aux.Template => x.tpe.flatMap(_.supertypes)
      case x: Member.Template => x.templ.supertypes
      case x: Type => wrapHosted(_.supertypes(x))
    }
    @hosted def self: Aux.Self = tree match {
      case x: Aux.Template => succeed(x.self)
      case x: Member.Template => succeed(x.templ.self)
      case x: Type => wrapHosted(_.self(x))
    }
    @hosted def subclasses: Seq[Member.Template] = tree match {
      case x: Aux.Template => x.tpe.flatMap(_.superclasses)
      case x: Member.Template => x.templ.subclasses
      case x: Type => wrapHosted(_.subclasses(x))
    }
    @hosted def ctor: Ctor.Primary = ctors.flatMap(_.collect { case prim: Ctor.Primary => prim }.findUnique)
    @hosted def ctors: Seq[Ctor] = wrapHosted(_.members(tree).collect{ case c: Ctor => c })
  }

  implicit class SemanticBlockScopeOps(tree: Scope.Block) {
    @hosted def classes: Seq[Defn.Class] = tree.allMembers[Defn.Class]
    @hosted def classes(name: Name): Defn.Class = tree.uniqueMember[Defn.Class](name.toString)
    @hosted def classes(name: String): Defn.Class = tree.uniqueMember[Defn.Class](name.toString)
    @hosted def classes(name: scala.Symbol): Defn.Class = tree.uniqueMember[Defn.Class](name.toString)
    @hosted def traits: Seq[Defn.Trait] = tree.allMembers[Defn.Trait]
    @hosted def traits(name: Name): Defn.Trait = tree.uniqueMember[Defn.Trait](name.toString)
    @hosted def traits(name: String): Defn.Trait = tree.uniqueMember[Defn.Trait](name.toString)
    @hosted def traits(name: scala.Symbol): Defn.Trait = tree.uniqueMember[Defn.Trait](name.toString)
    @hosted def objects: Seq[Defn.Object] = tree.allMembers[Defn.Object]
    @hosted def objects(name: Name): Defn.Object = tree.uniqueMember[Defn.Object](name.toString)
    @hosted def objects(name: String): Defn.Object = tree.uniqueMember[Defn.Object](name.toString)
    @hosted def objects(name: scala.Symbol): Defn.Object = tree.uniqueMember[Defn.Object](name.toString)
    @hosted def vars: Seq[Term.Name] = tree.allMembers[Term.Name]
    @hosted def vars(name: Name): Term.Name = tree.uniqueMember[Term.Name](name.toString)
    @hosted def vars(name: String): Term.Name = tree.uniqueMember[Term.Name](name.toString)
    @hosted def vars(name: scala.Symbol): Term.Name = tree.uniqueMember[Term.Name](name.toString)
  }

  implicit class SemanticRefineScopeOps(tree: Scope.Refine) {
    @hosted def defs: Seq[Member.Def] = tree.allMembers[Member.Def]
    @hosted def defs(name: Name): Member.Def = tree.uniqueMember[Member.Def](name.toString)
    @hosted def defs(name: String): Member.Def = tree.uniqueMember[Member.Def](name.toString)
    @hosted def defs(name: scala.Symbol): Member.Def = tree.uniqueMember[Member.Def](name.toString)
  }

  implicit class SemanticExistentialScopeOps(tree: Scope.Existential) {
    @hosted def vals: Seq[Term.Name] = tree.allMembers[Term.Name]
    @hosted def vals(name: Name): Term.Name = tree.uniqueMember[Term.Name](name.toString)
    @hosted def vals(name: String): Term.Name = tree.uniqueMember[Term.Name](name.toString)
    @hosted def vals(name: scala.Symbol): Term.Name = tree.uniqueMember[Term.Name](name.toString)
    @hosted def types: Seq[Member.AbstractOrAliasType] = tree.allMembers[Member.AbstractOrAliasType]
    @hosted def types(name: Name): Member.AbstractOrAliasType = tree.uniqueMember[Member.AbstractOrAliasType](name.toString)
    @hosted def types(name: String): Member.AbstractOrAliasType = tree.uniqueMember[Member.AbstractOrAliasType](name.toString)
    @hosted def types(name: scala.Symbol): Member.AbstractOrAliasType = tree.uniqueMember[Member.AbstractOrAliasType](name.toString)
  }

  implicit class SemanticParamsScopeOps(tree: Scope.Params) {
    @hosted def params: Seq[Param.Named] = tree.allMembers[Param.Named]
    @hosted def params(name: Name): Param.Named = tree.uniqueMember[Param.Named](name.toString)
    @hosted def params(name: String): Param.Named = tree.uniqueMember[Param.Named](name.toString)
    @hosted def params(name: scala.Symbol): Param.Named = tree.uniqueMember[Param.Named](name.toString)
    @hosted def tparams: Seq[TypeParam.Named] = tree.allMembers[TypeParam.Named]
    @hosted def tparams(name: Name): TypeParam.Named = tree.uniqueMember[TypeParam.Named](name.toString)
    @hosted def tparams(name: String): TypeParam.Named = tree.uniqueMember[TypeParam.Named](name.toString)
    @hosted def tparams(name: scala.Symbol): TypeParam.Named = tree.uniqueMember[TypeParam.Named](name.toString)
  }
}
