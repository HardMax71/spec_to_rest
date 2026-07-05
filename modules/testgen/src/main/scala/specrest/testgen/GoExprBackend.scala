package specrest.testgen

import specrest.codegen.go.GoLit
import specrest.ir.Builtins
import specrest.ir.generated.SpecRestGenerated.*

private[testgen] val GoReservedNames: Set[String] = Set(
  "break",
  "case",
  "chan",
  "const",
  "continue",
  "default",
  "defer",
  "else",
  "fallthrough",
  "for",
  "func",
  "go",
  "goto",
  "if",
  "import",
  "interface",
  "map",
  "package",
  "range",
  "return",
  "select",
  "struct",
  "switch",
  "type",
  "var",
  "true",
  "false",
  "nil",
  "iota",
  "any",
  "bool",
  "byte",
  "comparable",
  "complex64",
  "complex128",
  "error",
  "float32",
  "float64",
  "int",
  "int8",
  "int16",
  "int32",
  "int64",
  "rune",
  "string",
  "uint",
  "uintptr",
  "uint8",
  "uint16",
  "uint32",
  "uint64",
  "append",
  "cap",
  "clear",
  "close",
  "complex",
  "copy",
  "delete",
  "imag",
  "len",
  "make",
  "max",
  "min",
  "new",
  "panic",
  "print",
  "println",
  "real",
  "recover"
)

object GoIdent:
  def sanitize(s: String): String =
    val parts  = s.split("[^A-Za-z0-9]+").filter(_.nonEmpty).map(_.capitalize)
    val joined = parts.mkString
    if joined.isEmpty || !joined.head.isLetter then s"Svc$joined" else joined

// Go is statically typed: `any < any`, `any + any` and inline collection
// lambdas do not compile, so every numeric/comparison/logical/set operation is
// delegated to runtime helpers the go-test harness provides
// (_eq/_in/_lt/_add/_union/_len/_all/_filter/...). Function literals *are* Go
// expressions, so quantifiers/comprehensions/`the` compose as
// `_all(d, func(v any) bool { return _truthy(body) })`. State is read from the
// parsed /admin/state JSON via `_field(postState|preState, "x")` and
// the response body via `_field(responseData, "x")`.
object GoExprBackend extends ExprBackendBase:

  def languageName: String             = "Go"
  def reservedNames: Set[String]       = GoReservedNames
  def stringLiteral(s: String): String = GoLit.str(s)

  def builtinEmit: Builtins.BuiltinSpec => List[String] => String = _.go

  def boolLit(v: Boolean): String = if v then "true" else "false"
  def intLit(n: BigInt): String   = s"int64($n)"
  def noneLit: String             = "nil"

  def responseData: String = "responseData"

  def stateObject(mode: CaptureMode): String = mode match
    case CaptureMode.PostState => "postState"
    case CaptureMode.PreState  => "preState"

  def containerAccess(container: String, name: String): String =
    s"_field($container, ${GoLit.str(name)})"

  def indexAccess(base: String, idx: String): String = s"_index($base, $idx)"

  def userCallName(fname: String): String = fname

  def calleeCall(callee: String, args: List[String]): String =
    s"_call($callee${if args.isEmpty then "" else ", " + args.mkString(", ")})"

  def binOp(op: bin_op, l: String, r: String): String = op match
    case BAnd()       => s"(_truthy($l) && _truthy($r))"
    case BOr()        => s"(_truthy($l) || _truthy($r))"
    case BImplies()   => s"(!_truthy($l) || _truthy($r))"
    case BIff()       => s"_eq($l, $r)"
    case BEq()        => s"_eq($l, $r)"
    case BNeq()       => s"(!_eq($l, $r))"
    case BLt()        => s"_lt($l, $r)"
    case BGt()        => s"_gt($l, $r)"
    case BLe()        => s"_le($l, $r)"
    case BGe()        => s"_ge($l, $r)"
    case BIn()        => s"_in($l, $r)"
    case BNotIn()     => s"(!_in($l, $r))"
    case BAdd()       => s"_add($l, $r)"
    case BSub()       => s"_sub($l, $r)"
    case BMul()       => s"_mul($l, $r)"
    case BDiv()       => s"_div($l, $r)"
    case BUnion()     => s"_union($l, $r)"
    case BIntersect() => s"_inter($l, $r)"
    case BDiff()      => s"_diff($l, $r)"
    case BSubset()    => s"_subset($l, $r)"

  def unOp(op: un_op, x: String): String = op match
    case UNot()         => s"(!_truthy($x))"
    case UNegate()      => s"_neg($x)"
    case UCardinality() => s"_len($x)"
    case UPower()       => s"_powerset($x)"

  def mapMerge(l: String, r: String): String = s"_merge($l, $r)"

  def ifExpr(c: String, t: String, e: String): String =
    s"func() any { if _truthy($c) { return $t }; return $e }()"

  def letExpr(v: String, value: String, body: String): String =
    s"func($v any) any { return $body }($value)"

  def emptySet: String                   = "_set()"
  def setOf(elems: List[String]): String = s"_set(${elems.mkString(", ")})"
  def seqOf(elems: List[String]): String = s"[]any{${elems.mkString(", ")}}"

  def emptyMap: String                      = "map[string]any{}"
  def mapPair(k: String, v: String): String = s"$k, $v"
  def mapOf(pairs: List[String]): String    = s"_mapOf(${pairs.mkString(", ")})"

  def fieldPair(name: String, value: String): String = s"${GoLit.str(name)}, $value"
  def recordOf(pairs: List[String]): String          = s"_mapOf(${pairs.mkString(", ")})"

  def withRecord(base: String, pairs: List[String]): String =
    s"_with($base, ${pairs.mkString(", ")})"

  def comprehension(v: String, dom: String, isMapDomain: Boolean, pred: String): String =
    val iter = if isMapDomain then s"_values($dom)" else dom
    s"_setFilter($iter, func($v any) bool { return _truthy($pred) })"

  def quantifierExpr(kind: quant_kind, bound: List[(String, String)], body: String): String =
    val helper = kind match
      case QAll() => "_all"
      case _      => "_any"
    val nested = bound.foldRight(s"_truthy($body)"): (pair, acc) =>
      val (v, d) = pair
      s"$helper(_quantDomain($d), func($v any) bool { return $acc })"
    kind match
      case QNo() => s"(!($nested))"
      case _     => nested

  def theExpr(v: String, dom: String, body: String): String =
    s"_find(_quantDomain($dom), func($v any) bool { return _truthy($body) })"

  def lambdaExpr(param: String, body: String): String =
    s"func($param any) any { return $body }"

  def setEquals(l: String, r: String): String = s"_setEq($l, $r)"

  def negate(cond: String): String = s"!($cond)"

  def matchesExpr(target: String, pattern: String): String =
    s"_matches($target, ${GoLit.str(Strategies.fullMatchPattern(pattern))})"
