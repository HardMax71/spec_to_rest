package specrest.testgen

import specrest.ir.Builtins
import specrest.ir.generated.SpecRestGenerated.*

private[testgen] val TsReservedNames: Set[String] = Set(
  "break",
  "case",
  "catch",
  "class",
  "const",
  "continue",
  "debugger",
  "default",
  "delete",
  "do",
  "else",
  "enum",
  "export",
  "extends",
  "false",
  "finally",
  "for",
  "function",
  "if",
  "import",
  "in",
  "instanceof",
  "new",
  "null",
  "return",
  "super",
  "switch",
  "this",
  "throw",
  "true",
  "try",
  "typeof",
  "var",
  "void",
  "while",
  "with",
  "yield",
  "let",
  "static",
  "await",
  "async",
  "arguments",
  "eval"
)

// Set/relation semantics that Python gets from built-ins (`in`, `==` on sets,
// `len`, `|`/`&`/`-`) are delegated to runtime helpers the vitest harness module
// provides: _len, _in, _eq, _union, _inter, _diff, _subset, _powerset. State is
// read from the parsed /admin/state JSON objects `preState`/`postState`
// and the response body object `responseData`.
object TsExprBackend extends ExprBackendBase:

  def languageName: String             = "TypeScript"
  def reservedNames: Set[String]       = TsReservedNames
  def stringLiteral(s: String): String = TsLit.str(s)

  def builtinEmit: Builtins.BuiltinSpec => List[String] => String = _.ts

  def boolLit(v: Boolean): String = if v then "true" else "false"
  def intLit(n: BigInt): String   = n.toString
  def noneLit: String             = "null"

  def responseData: String = "responseData"

  def stateObject(mode: CaptureMode): String = mode match
    case CaptureMode.PostState => "postState"
    case CaptureMode.PreState  => "preState"

  def containerAccess(container: String, name: String): String =
    s"$container[${TsLit.str(name)}]"

  def indexAccess(base: String, idx: String): String = s"$base[$idx]"

  def userCallName(fname: String): String = fname

  def calleeCall(callee: String, args: List[String]): String =
    s"($callee)(${args.mkString(", ")})"

  def binOp(op: bin_op, l: String, r: String): String = op match
    case BAnd()       => s"(($l) && ($r))"
    case BOr()        => s"(($l) || ($r))"
    case BImplies()   => s"((!($l)) || ($r))"
    case BIff()       => s"_eq(($l), ($r))"
    case BEq()        => s"_eq(($l), ($r))"
    case BNeq()       => s"(!_eq(($l), ($r)))"
    case BLt()        => s"(($l) < ($r))"
    case BGt()        => s"(($l) > ($r))"
    case BLe()        => s"(($l) <= ($r))"
    case BGe()        => s"(($l) >= ($r))"
    case BIn()        => s"_in(($l), ($r))"
    case BNotIn()     => s"(!_in(($l), ($r)))"
    case BAdd()       => s"(($l) + ($r))"
    case BSub()       => s"(($l) - ($r))"
    case BMul()       => s"(($l) * ($r))"
    case BDiv()       => s"(($l) / ($r))"
    case BUnion()     => s"_union(($l), ($r))"
    case BIntersect() => s"_inter(($l), ($r))"
    case BDiff()      => s"_diff(($l), ($r))"
    case BSubset()    => s"_subset(($l), ($r))"

  def unOp(op: un_op, x: String): String = op match
    case UNot()         => s"(!($x))"
    case UNegate()      => s"(-($x))"
    case UCardinality() => s"_len($x)"
    case UPower()       => s"_powerset($x)"

  def mapMerge(l: String, r: String): String = s"{ ...($l), ...($r) }"

  def ifExpr(c: String, t: String, e: String): String = s"(($c) ? ($t) : ($e))"

  def letExpr(v: String, value: String, body: String): String =
    s"(($v) => ($body))($value)"

  def emptySet: String                   = "new Set()"
  def setOf(elems: List[String]): String = s"new Set([${elems.mkString(", ")}])"
  def seqOf(elems: List[String]): String = s"[${elems.mkString(", ")}]"

  def emptyMap: String                      = "{}"
  def mapPair(k: String, v: String): String = s"[$k, $v]"
  def mapOf(pairs: List[String]): String    = s"Object.fromEntries([${pairs.mkString(", ")}])"

  def fieldPair(name: String, value: String): String = s"${TsLit.str(name)}: $value"
  def recordOf(pairs: List[String]): String          = s"{ ${pairs.mkString(", ")} }"

  def withRecord(base: String, pairs: List[String]): String =
    s"{ ...($base), ${pairs.mkString(", ")} }"

  def comprehension(v: String, dom: String, isMapDomain: Boolean, pred: String): String =
    val iter = if isMapDomain then s"Object.values($dom)" else dom
    s"new Set(Array.from($iter).filter(($v) => ($pred)))"

  def quantifierExpr(kind: quant_kind, bound: List[(String, String)], body: String): String =
    val method = kind match
      case QAll() => "every"
      case _      => "some"
    val nested = bound.foldRight(body): (pair, acc) =>
      val (v, d) = pair
      s"Array.from($d).$method(($v) => ($acc))"
    kind match
      case QNo() => s"(!($nested))"
      case _     => nested

  def theExpr(v: String, dom: String, body: String): String =
    s"(Array.from($dom).find(($v) => ($body)) ?? null)"

  def lambdaExpr(param: String, body: String): String = s"(($param) => ($body))"

  def matchesExpr(target: String, pattern: String): String =
    s"(new RegExp(${TsLit.str(Strategies.fullMatchPattern(pattern))}).test($target))"
