package specrest.verify.z3

import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable
import scala.util.boundary

private[z3] given CanEqual[smt_term, smt_term] = CanEqual.derived
private[z3] given CanEqual[expr, expr]         = CanEqual.derived

private[z3] type TranslateBoundary =
  boundary.Label[Either[VerifyError.Translator, Z3Script]]

private[z3] def fail(ctx: TranslateCtx, msg: String): Nothing =
  boundary.break(Left(VerifyError.Translator(msg)))(using ctx.bnd)

private[z3] enum StateMode derives CanEqual:
  case Pre, Post

final private[z3] case class EntityInfo(
    sort: Z3Sort,
    fields: mutable.LinkedHashMap[String, (Z3Sort, String)]
)

final private[z3] case class EnumInfo(sort: Z3Sort, members: List[String])

final private[z3] case class PrimitiveAliasInfo(underlyingSort: Z3Sort, constraint: expr)

final private[z3] case class TypeAliasInfo(sort: Z3Sort)

sealed private[z3] trait StateEntry derives CanEqual

final private[z3] case class StateRelationInfo(
    keySort: Z3Sort,
    valueSort: Z3Sort,
    domFunc: String,
    mapFunc: String,
    domFuncPost: String,
    mapFuncPost: String,
    isTotal: Boolean
) extends StateEntry

final private[z3] case class StateConstInfo(
    sort: Z3Sort,
    funcName: String,
    funcNamePost: String
) extends StateEntry

@SuppressWarnings(Array("org.wartremover.warts.Var"))
final private[z3] class TranslateCtx(val bnd: TranslateBoundary):
  val sorts: mutable.LinkedHashMap[String, Z3Sort]              = mutable.LinkedHashMap.empty
  val funcs: mutable.LinkedHashMap[String, Z3FunctionDecl]      = mutable.LinkedHashMap.empty
  val assertions: mutable.ArrayBuffer[Z3Expr]                   = mutable.ArrayBuffer.empty
  val entities: mutable.LinkedHashMap[String, EntityInfo]       = mutable.LinkedHashMap.empty
  val enums: mutable.LinkedHashMap[String, EnumInfo]            = mutable.LinkedHashMap.empty
  val typeAliases: mutable.LinkedHashMap[String, TypeAliasInfo] = mutable.LinkedHashMap.empty
  val primitiveAliases: mutable.LinkedHashMap[String, PrimitiveAliasInfo] =
    mutable.LinkedHashMap.empty
  val state: mutable.LinkedHashMap[String, StateEntry]        = mutable.LinkedHashMap.empty
  val matchesIds: mutable.LinkedHashMap[String, Int]          = mutable.LinkedHashMap.empty
  val stringLitIds: mutable.LinkedHashMap[String, Int]        = mutable.LinkedHashMap.empty
  val aggSumIds: mutable.LinkedHashMap[String, Int]           = mutable.LinkedHashMap.empty
  val cardinalityNames: mutable.LinkedHashMap[String, String] = mutable.LinkedHashMap.empty
  val skolemIds: mutable.LinkedHashMap[String, Int]           = mutable.LinkedHashMap.empty
  val inputs: mutable.ArrayBuffer[ArtifactBinding]            = mutable.ArrayBuffer.empty
  val outputs: mutable.ArrayBuffer[ArtifactBinding]           = mutable.ArrayBuffer.empty
  val predicateNames: mutable.Set[String]                     = mutable.Set.empty
  var hasPostState: Boolean                                   = false
  var stateMode: StateMode                                    = StateMode.Pre

  def declareSort(sort: Z3Sort): Unit = sort match
    case Z3Sort.Uninterp(_) =>
      val k = Z3Sort.key(sort)
      if !sorts.contains(k) then sorts(k) = sort
    case Z3Sort.SetOf(elem)    => declareSort(elem)
    case Z3Sort.OptionOf(elem) => declareSort(elem)
    case _                     => ()

  def declareFunc(decl: Z3FunctionDecl): Unit =
    if !funcs.contains(decl.name) then
      funcs(decl.name) = decl
      decl.argSorts.foreach(declareSort)
      declareSort(decl.resultSort)

  def matchesNameFor(pattern: String): String =
    matchesIds.get(pattern) match
      case Some(id) => s"matches_$id"
      case None =>
        val id = matchesIds.size
        matchesIds(pattern) = id
        s"matches_$id"

  def stringLitNameFor(value: String): String =
    stringLitIds.get(value) match
      case Some(id) => s"str_$id"
      case None =>
        val id = stringLitIds.size
        stringLitIds(value) = id
        s"str_$id"

  def stringLitCount: Int = stringLitIds.size

  // A sum aggregate's uninterpreted function is keyed by its lambda body. The full term `toString`
  // is the registry key (injective: distinct terms render distinctly), so distinct bodies always
  // get distinct ids -- never the collisions a lossy name-sanitiser would risk.
  def aggSumKeyFor(bodyRendered: String): String =
    aggSumIds.get(bodyRendered) match
      case Some(id) => s"b$id"
      case None =>
        val id = aggSumIds.size
        aggSumIds(bodyRendered) = id
        s"b$id"

  def cardinalityNameFor(targetName: String, mode: StateMode = StateMode.Pre): String =
    val key = if mode == StateMode.Post then s"${targetName}__post" else targetName
    cardinalityNames.get(key) match
      case Some(name) => name
      case None =>
        val name =
          if mode == StateMode.Post then s"card_${targetName}_post" else s"card_$targetName"
        cardinalityNames(key) = name
        name

  def freshSkolem(prefix: String): String =
    val count = skolemIds.getOrElse(prefix, 0)
    skolemIds(prefix) = count + 1
    s"${prefix}_$count"
