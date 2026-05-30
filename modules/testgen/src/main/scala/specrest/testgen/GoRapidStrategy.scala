package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.IntConstraint
import specrest.ir.generated.SpecRestGenerated.StringConstraint
import specrest.ir.generated.SpecRestGenerated.int_constraint
import specrest.ir.generated.SpecRestGenerated.string_constraint

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

  def constrainedString(c: string_constraint): String = c match
    case StringConstraint(minOpt, maxOpt, regexes, predicateHelpers, _) =>
      val minSize = minOpt.map(_.toInt)
      val maxSize = maxOpt.map(_.toInt)
      val (primaryRegex, extraRegexes) = regexes match
        case head :: tail => (Some(head), tail)
        case Nil          => (None, Nil)
      val base = primaryRegex match
        case Some(p) => s"genStringMatching(${GoLit.str(s"^(?:$p)$$")})"
        case None =>
          val lo = minSize.getOrElse(-1)
          val hi = maxSize.getOrElse(-1)
          if lo < 0 && hi < 0 then "genString()" else s"genStringBounded($lo, $hi)"
      val withLenFilter = (primaryRegex, minSize, maxSize) match
        case (Some(_), Some(lo), Some(hi)) => s"genFilterLen($base, $lo, $hi)"
        case (Some(_), Some(lo), None)     => s"genFilterLen($base, $lo, -1)"
        case (Some(_), None, Some(hi))     => s"genFilterLen($base, -1, $hi)"
        case _                             => base
      val withExtraRegex = extraRegexes.foldLeft(withLenFilter): (acc, r) =>
        s"genFilterRegex($acc, ${GoLit.str(s"^(?:$r)$$")})"
      predicateHelpers.foldLeft(withExtraRegex): (acc, h) =>
        s"genFilterPred($acc, $h)"

  def constrainedInt(c: int_constraint): String = c match
    case IntConstraint(minOpt, maxOpt, _) =>
      (minOpt.map(_.toLong), maxOpt.map(_.toLong)) match
        case (Some(lo), Some(hi)) => s"genIntRange($lo, $hi)"
        case (Some(lo), None)     => s"genIntMin($lo)"
        case (None, Some(hi))     => s"genIntMax($hi)"
        case (None, None)         => "genInt()"

  def functionName(typeName: String): String =
    s"strategy$typeName"
