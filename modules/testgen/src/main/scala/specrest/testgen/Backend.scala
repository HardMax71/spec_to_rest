package specrest.testgen

import specrest.codegen.EmittedFile
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.expr_full
import specrest.profile.ProfiledService

// Translates an IR expression to a target-language expression. Each backend owns
// its own recursion — Python comprehensions/quantifiers do not leaf-swap to TS
// `.every`/`.map` or Go loops — over the shared ExprPy result ADT and TestCtx.
// `stringLiteral` renders a Scala string as a target string literal.
trait ExprBackend:
  def translate(expr: expr_full, ctx: TestCtx): ExprPy
  def stringLiteral(s: String): String

trait HarnessTemplates:
  def testsInit: EmittedFile
  def conftest: EmittedFile
  def predicates(ir: ServiceIRFull): EmittedFile
  def pytestIni: EmittedFile
  def redaction: EmittedFile
  def strategiesUser: EmittedFile
  def runConformance: EmittedFile
  def strategiesPath: String
  def skipsPath: String
  def behavioralTestPath(serviceSnake: String): String
  def statefulTestPath(serviceSnake: String): String
  def structuralTestPath(serviceSnake: String): String

object PythonFastApiHarness extends HarnessTemplates:
  def testsInit: EmittedFile = EmittedFile(FilePaths.TestsInitFile, "")
  def conftest: EmittedFile  = EmittedFile(FilePaths.ConftestFile, Templates.conftest)
  def pytestIni: EmittedFile = EmittedFile(FilePaths.PytestIniFile, Templates.pytestIni)
  def redaction: EmittedFile = EmittedFile(FilePaths.RedactionFile, Templates.redaction)
  def strategiesUser: EmittedFile =
    EmittedFile(FilePaths.StrategiesUserFile, Templates.strategiesUser)
  def runConformance: EmittedFile =
    EmittedFile(FilePaths.RunConformanceFile, Templates.runConformance)

  def predicates(ir: ServiceIRFull): EmittedFile =
    EmittedFile(FilePaths.PredicatesFile, Templates.predicates(ir))

  def strategiesPath: String = FilePaths.StrategiesFile
  def skipsPath: String      = FilePaths.SkipsFile

  def behavioralTestPath(serviceSnake: String): String =
    FilePaths.behavioralTestFile(serviceSnake)
  def statefulTestPath(serviceSnake: String): String =
    FilePaths.statefulTestFile(serviceSnake)
  def structuralTestPath(serviceSnake: String): String =
    FilePaths.structuralTestFile(serviceSnake)

// Constructs target-language value generators. The IR analysis that decides *what*
// to generate (type traversal, constraint collection in Strategies) stays shared;
// only the rendered generator syntax differs (Hypothesis `st.*` vs fast-check `fc.*`
// vs rapid). Methods return generator-expression text.
trait StrategyBackend:
  def string: String
  def int: String
  def float: String
  def bool: String
  def datetime: String
  def duration: String
  def id: String
  def jsonDatetime: String
  def jsonDuration: String
  def noneValue: String
  def call(fnName: String): String
  def option(inner: String): String
  def set(inner: String): String
  def jsonSetUnique(inner: String): String
  def seq(inner: String): String
  def nothing: String
  def redactedPlaceholder: String
  def redactWrap(inner: String): String
  def enumSampled(values: List[String]): String
  def fixedDict(entries: List[(String, String)]): String
  def constrainedString(c: StringConstraint): String
  def constrainedInt(c: IntConstraint): String
  def functionName(typeName: String): String

object PythonHypothesisStrategy extends StrategyBackend:
  def string: String       = "st.text()"
  def int: String          = "st.integers()"
  def float: String        = "st.floats(allow_nan=False, allow_infinity=False)"
  def bool: String         = "st.booleans()"
  def datetime: String     = "st.datetimes()"
  def duration: String     = "st.timedeltas()"
  def id: String           = "st.uuids().map(str)"
  def jsonDatetime: String = "st.datetimes().map(lambda d: d.isoformat())"
  def jsonDuration: String = "st.timedeltas().map(lambda d: d.total_seconds())"
  def noneValue: String    = "st.none()"
  def nothing: String      = "st.nothing()"

  def call(fnName: String): String         = s"$fnName()"
  def option(inner: String): String        = s"st.one_of(st.none(), $inner)"
  def set(inner: String): String           = s"st.sets($inner, max_size=5)"
  def jsonSetUnique(inner: String): String = s"st.lists($inner, unique=True, max_size=5)"
  def seq(inner: String): String           = s"st.lists($inner, max_size=5)"
  def redactedPlaceholder: String          = s"""st.just("${Strategies.RedactedPlaceholder}")"""
  def redactWrap(inner: String): String    = s"redact($inner)"

  def enumSampled(values: List[String]): String =
    s"st.sampled_from([${values.map(ExprToPython.pyString).mkString(", ")}])"

  def fixedDict(entries: List[(String, String)]): String =
    if entries.isEmpty then "st.fixed_dictionaries({})"
    else
      val rows = entries.map((n, t) => s"        ${ExprToPython.pyString(n)}: $t")
      s"st.fixed_dictionaries({\n${rows.mkString(",\n")},\n    })"

  def constrainedString(c: StringConstraint): String =
    val (primaryRegex, extraRegexes) = c.regexes match
      case head :: tail => (Some(head), tail)
      case Nil          => (None, Nil)
    val base = primaryRegex match
      case Some(p) => s"st.from_regex(${ExprToPython.pyString(p)}, fullmatch=True)"
      case None =>
        val args = List(
          c.minSize.map(n => s"min_size=$n"),
          c.maxSize.map(n => s"max_size=$n")
        ).flatten.mkString(", ")
        if args.isEmpty then "st.text()" else s"st.text($args)"
    val withLenFilter = (primaryRegex, c.minSize, c.maxSize) match
      case (Some(_), Some(lo), Some(hi)) => s"$base.filter(lambda v: $lo <= len(v) <= $hi)"
      case (Some(_), Some(lo), None)     => s"$base.filter(lambda v: len(v) >= $lo)"
      case (Some(_), None, Some(hi))     => s"$base.filter(lambda v: len(v) <= $hi)"
      case _                             => base
    val withExtraRegex = extraRegexes.foldLeft(withLenFilter): (acc, r) =>
      s"$acc.filter(lambda v: __import__('re').fullmatch(${ExprToPython.pyString(r)}, v) is not None)"
    c.predicateHelpers.foldLeft(withExtraRegex): (acc, h) =>
      s"$acc.filter(lambda v: $h(v))"

  def constrainedInt(c: IntConstraint): String =
    val args = List(
      c.minValue.map(n => s"min_value=$n"),
      c.maxValue.map(n => s"max_value=$n")
    ).flatten.mkString(", ")
    if args.isEmpty then "st.integers()" else s"st.integers($args)"

  def functionName(typeName: String): String =
    s"strategy_${specrest.convention.Naming.toSnakeCase(typeName)}"

object TestBackend:
  // Only the Python/FastAPI backend exists today; this is the dispatch point through
  // which the TypeScript (vitest+fast-check) and Go (test+rapid) backends plug in.
  // The conformance decision logic (Behavioral/Stateful/Structural) stays shared —
  // only the rendered target language differs.
  def harnessFor(profiled: ProfiledService): HarnessTemplates =
    PythonFastApiHarness

  def strategyFor(profiled: ProfiledService): StrategyBackend =
    PythonHypothesisStrategy

  def exprFor(profiled: ProfiledService): ExprBackend =
    ExprToPython
