package specrest.testgen

import specrest.codegen.go.GoLit
import specrest.ir.generated.SpecRestGenerated.IntConstraint
import specrest.ir.generated.SpecRestGenerated.int_constraint

object GoRapidStrategy extends StrategyBackend:
  def string: String       = "genString()"
  def int: String          = "genInt()"
  def float: String        = "genFloat()"
  def bool: String         = "genBool()"
  def datetime: String     = "genDatetime()"
  def duration: String     = "genDuration()"
  def id: String           = "genID()"
  def jsonDatetime: String = "genJSONDatetime()"
  def jsonDuration: String = "genJSONDuration()"
  def noneValue: String    = "genNone()"
  def nothing: String      = "genNothing()"

  def call(fnName: String): String         = s"$fnName()"
  def option(inner: String): String        = s"genOption($inner)"
  def set(inner: String): String           = s"genSet($inner)"
  def jsonSetUnique(inner: String): String = s"genSetUnique($inner)"
  def seq(inner: String): String           = s"genSeq($inner)"
  def redactedPlaceholder: String =
    s"genJust(${GoLit.str(Strategies.RedactedPlaceholder)})"
  def redactWrap(inner: String): String = s"genRedact($inner)"

  def enumSampled(values: List[String]): String =
    s"genSampled(${values.map(GoLit.str).mkString(", ")})"

  def fixedDict(entries: List[(String, String)]): String =
    if entries.isEmpty then "genDict()"
    else
      val rows = entries.map((n, t) => s"${GoLit.str(n)}, $t")
      s"genDict(${rows.mkString(", ")})"

  def regexGen(pattern: String): String =
    s"genStringMatching(${GoLit.str(Strategies.fullMatchPattern(pattern))})"

  // -1 is the harness's "no bound" sentinel for genStringBounded/genFilterLen.
  def boundedText(min: Option[Int], max: Option[Int]): String =
    val lo = min.getOrElse(-1)
    val hi = max.getOrElse(-1)
    if lo < 0 && hi < 0 then "genString()" else s"genStringBounded($lo, $hi)"

  def lengthFilter(base: String, min: Option[Int], max: Option[Int]): String =
    s"genFilterLen($base, ${min.getOrElse(-1)}, ${max.getOrElse(-1)})"

  def regexFilter(base: String, pattern: String): String =
    s"genFilterRegex($base, ${GoLit.str(Strategies.fullMatchPattern(pattern))})"

  def predicateFilter(base: String, helper: String): String =
    s"genFilterPred($base, $helper)"

  // The transports validate Int as int32 (pydantic conint on python,
  // fast-check's default integer range on ts, INTEGER columns underneath),
  // so open bounds clamp to the int32 window exactly like the python backend.
  def constrainedInt(c: int_constraint): String = c match
    case IntConstraint(minOpt, maxOpt, _) =>
      val lo = minOpt.map(n => BigInt(n.toString).max(BigInt(Int.MinValue))).getOrElse(BigInt(
        Int.MinValue
      ))
      val hi = maxOpt.map(n => BigInt(n.toString).min(BigInt(Int.MaxValue))).getOrElse(BigInt(
        Int.MaxValue
      ))
      s"genIntRange($lo, $hi)"

  def functionName(typeName: String): String =
    s"strategy$typeName"
