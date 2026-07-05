package specrest.testgen

import specrest.ir.Builtins
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*

private[testgen] val PythonReservedNames: Set[String] = Set(
  "False",
  "None",
  "True",
  "and",
  "as",
  "assert",
  "async",
  "await",
  "break",
  "class",
  "continue",
  "def",
  "del",
  "elif",
  "else",
  "except",
  "finally",
  "for",
  "from",
  "global",
  "if",
  "import",
  "in",
  "is",
  "lambda",
  "nonlocal",
  "not",
  "or",
  "pass",
  "raise",
  "return",
  "try",
  "while",
  "with",
  "yield",
  "match",
  "case"
)

object ExprToPython extends ExprBackendBase:

  def languageName: String             = "Python"
  def reservedNames: Set[String]       = PythonReservedNames
  def stringLiteral(s: String): String = pyString(s)

  def builtinEmit: Builtins.BuiltinSpec => List[String] => String = _.py

  def boolLit(v: Boolean): String = if v then "True" else "False"
  def intLit(n: BigInt): String   = n.toString
  def noneLit: String             = "None"

  def responseData: String = "response_data"

  def stateObject(mode: CaptureMode): String = mode match
    case CaptureMode.PostState => "post_state"
    case CaptureMode.PreState  => "pre_state"

  def containerAccess(container: String, name: String): String =
    s"$container[${pyString(name)}]"

  def indexAccess(base: String, idx: String): String = s"$base[$idx]"

  def userCallName(fname: String): String = Naming.toSnakeCase(fname)

  def calleeCall(callee: String, args: List[String]): String =
    s"($callee)(${args.mkString(", ")})"

  def binOp(op: bin_op, l: String, r: String): String = op match
    case BAnd()       => s"(($l) and ($r))"
    case BOr()        => s"(($l) or ($r))"
    case BImplies()   => s"((not ($l)) or ($r))"
    case BIff()       => s"(($l) == ($r))"
    case BEq()        => s"(($l) == ($r))"
    case BNeq()       => s"(($l) != ($r))"
    case BLt()        => s"(($l) < ($r))"
    case BGt()        => s"(($l) > ($r))"
    case BLe()        => s"(($l) <= ($r))"
    case BGe()        => s"(($l) >= ($r))"
    case BIn()        => s"(($l) in ($r))"
    case BNotIn()     => s"(($l) not in ($r))"
    case BAdd()       => s"(($l) + ($r))"
    case BSub()       => s"(($l) - ($r))"
    case BMul()       => s"(($l) * ($r))"
    case BDiv()       => s"(($l) / ($r))"
    case BUnion()     => s"(($l) | ($r))"
    case BIntersect() => s"(($l) & ($r))"
    case BDiff()      => s"(($l) - ($r))"
    case BSubset()    => s"(($l) <= ($r))"

  def unOp(op: un_op, x: String): String = op match
    case UNot()         => s"(not ($x))"
    case UNegate()      => s"(-($x))"
    case UCardinality() => s"len($x)"
    case UPower()       => s"_powerset($x)"

  def mapMerge(l: String, r: String): String = s"{**($l), **($r)}"

  def ifExpr(c: String, t: String, e: String): String = s"(($t) if ($c) else ($e))"

  def letExpr(v: String, value: String, body: String): String =
    s"((lambda $v=($value): ($body))())"

  def emptySet: String                   = "set()"
  def setOf(elems: List[String]): String = s"{${elems.mkString(", ")}}"
  def seqOf(elems: List[String]): String = s"[${elems.mkString(", ")}]"

  def emptyMap: String                      = "{}"
  def mapPair(k: String, v: String): String = s"$k: $v"
  def mapOf(pairs: List[String]): String    = s"{${pairs.mkString(", ")}}"

  def fieldPair(name: String, value: String): String = s"${pyString(name)}: $value"
  def recordOf(pairs: List[String]): String          = s"{${pairs.mkString(", ")}}"

  def withRecord(base: String, pairs: List[String]): String =
    s"{**($base), ${pairs.mkString(", ")}}"

  def comprehension(v: String, dom: String, isMapDomain: Boolean, pred: String): String =
    val iter = if isMapDomain then s"($dom).values()" else s"($dom)"
    s"{$v for $v in $iter if ($pred)}"

  def quantifierExpr(kind: quant_kind, bound: List[(String, String)], body: String): String =
    val pairs   = bound.map((v, d) => s"$v in ($d)").mkString(" for ")
    val genExpr = s"($body for $pairs)"
    kind match
      case QAll()              => s"all$genExpr"
      case QSome() | QExists() => s"any$genExpr"
      case QNo()               => s"(not any$genExpr)"

  def theExpr(v: String, dom: String, body: String): String =
    s"next(($v for $v in ($dom) if ($body)), None)"

  def lambdaExpr(param: String, body: String): String = s"(lambda $param: ($body))"

  def setEquals(l: String, r: String): String =
    s"(sorted($l) == sorted($r))"

  def negate(cond: String): String = s"(not $cond)"

  def matchesExpr(target: String, pattern: String): String =
    s"(re.fullmatch(${pyString(pattern)}, $target) is not None)"

  private[testgen] def pyString(s: String): String =
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    s"\"$escaped\""
