package specrest.testgen

import specrest.codegen.EmittedFile
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.expr_full
import specrest.ir.generated.SpecRestGenerated.span_t
import specrest.profile.ProfiledService

// Translates an IR expression to a target-language expression. Each backend owns
// its own recursion — Python comprehensions/quantifiers do not leaf-swap to TS
// `.every`/`.map` or Go loops — over the shared Translated result ADT and TestCtx.
// `stringLiteral` renders a Scala string as a target string literal.
trait ExprBackend:
  def translate(expr: expr_full, ctx: TestCtx): Translated
  def stringLiteral(s: String): String

// The language-specific scaffold + path layout. `scaffoldFiles` returns the
// static/derived harness files (client, runtime helpers, predicates, runner,
// config) in the target's natural layout — the file set differs per language,
// so a flat list is the right shape, not a fixed Python-shaped member list.
trait HarnessTemplates:
  def scaffoldFiles(ir: ServiceIRFull): List[EmittedFile]
  def strategiesPath: String
  def skipsPath: String
  def behavioralTestPath(serviceSnake: String): String
  def statefulTestPath(serviceSnake: String): String
  def structuralTestPath(serviceSnake: String): String

object PythonFastApiHarness extends HarnessTemplates:
  def scaffoldFiles(ir: ServiceIRFull): List[EmittedFile] =
    List(
      EmittedFile(FilePaths.TestsInitFile, ""),
      EmittedFile(FilePaths.ConftestFile, Templates.conftest),
      EmittedFile(FilePaths.PredicatesFile, Templates.predicates(ir)),
      EmittedFile(FilePaths.PytestIniFile, Templates.pytestIni),
      EmittedFile(FilePaths.RedactionFile, Templates.redaction),
      EmittedFile(FilePaths.StrategiesUserFile, Templates.strategiesUser, preserve = true),
      EmittedFile(FilePaths.RunConformanceFile, Templates.runConformance)
    )

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

// Skip-propagation algebra over Translated — pure, language-neutral. Shared by every
// ExprBackend so the recursion's short-circuit-on-Skip behaviour is identical
// across target languages; only the rendered tokens differ.
object ExprLift:
  def lift1(a: Translated)(f: String => Translated): Translated = a match
    case Translated.Emit(x)        => f(x)
    case s @ Translated.Skip(_, _) => s

  def lift2(a: Translated, b: Translated)(f: (String, String) => Translated): Translated =
    (a, b) match
      case (Translated.Emit(x), Translated.Emit(y)) => f(x, y)
      case (s @ Translated.Skip(_, _), _)           => s
      case (_, s @ Translated.Skip(_, _))           => s

  def lift3(a: Translated, b: Translated, c: Translated)(
      f: (String, String, String) => Translated
  ): Translated =
    (a, b, c) match
      case (Translated.Emit(x), Translated.Emit(y), Translated.Emit(z)) => f(x, y, z)
      case (s @ Translated.Skip(_, _), _, _)                            => s
      case (_, s @ Translated.Skip(_, _), _)                            => s
      case (_, _, s @ Translated.Skip(_, _))                            => s

  def liftAll(parts: List[Translated], span: Option[span_t])(
      f: List[String] => Translated
  ): Translated =
    parts.collectFirst { case s @ Translated.Skip(_, _) => s } match
      case Some(s) => s
      case None =>
        val texts = parts.collect { case Translated.Emit(t) => t }
        if texts.size == parts.size then f(texts)
        else Translated.Skip("internal: lift mismatch", span)

  // Shared dispatcher for `Builtins.byName` lookups. Each backend supplies only
  // `pickEmit` (e.g. `_.py` for Python, `_.ts` for TypeScript) — the arity check,
  // the unknown-name skip message, and the lift-from-translated-args plumbing
  // live here exactly once.
  def dispatchBuiltin(
      fname: String,
      args: List[Translated],
      span: Option[span_t],
      pickEmit: specrest.convention.Builtins.BuiltinSpec => List[String] => String
  ): Translated =
    specrest.convention.Builtins.byName.get(fname) match
      case Some(spec) if spec.arity == args.size =>
        liftAll(args, span)(rendered => Translated.Emit(pickEmit(spec)(rendered)))
      case Some(spec) =>
        Translated.Skip(
          s"$fname expects ${spec.arity} arg(s), got ${args.size}",
          span
        )
      case None =>
        Translated.Skip(s"unknown function '$fname/${args.size}' (see #138)", span)

object TsLit:
  def str(s: String): String =
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    s"\"$escaped\""

object TsFastCheckStrategy extends StrategyBackend:
  def string: String       = "fc.string()"
  def int: String          = "fc.integer()"
  def float: String        = "fc.double({ noNaN: true, noDefaultInfinity: true })"
  def bool: String         = "fc.boolean()"
  def datetime: String     = "fc.date()"
  def duration: String     = "fc.double({ noNaN: true, noDefaultInfinity: true, min: 0 })"
  def id: String           = "fc.uuid()"
  def jsonDatetime: String = "fc.date().map((d) => d.toISOString())"
  def jsonDuration: String = "fc.double({ noNaN: true, noDefaultInfinity: true, min: 0 })"
  def noneValue: String    = "fc.constant(null)"
  def nothing: String      = "fc.constant(undefined)"

  def call(fnName: String): String         = s"$fnName()"
  def option(inner: String): String        = s"fc.option($inner, { nil: null })"
  def set(inner: String): String           = s"fc.uniqueArray($inner, { maxLength: 5 })"
  def jsonSetUnique(inner: String): String = s"fc.uniqueArray($inner, { maxLength: 5 })"
  def seq(inner: String): String           = s"fc.array($inner, { maxLength: 5 })"
  def redactedPlaceholder: String =
    s"fc.constant(${TsLit.str(Strategies.RedactedPlaceholder)})"
  def redactWrap(inner: String): String = s"redact($inner)"

  def enumSampled(values: List[String]): String =
    s"fc.constantFrom(${values.map(TsLit.str).mkString(", ")})"

  def fixedDict(entries: List[(String, String)]): String =
    if entries.isEmpty then "fc.record({})"
    else
      val rows = entries.map((n, t) => s"        ${TsLit.str(n)}: $t")
      s"fc.record({\n${rows.mkString(",\n")},\n    })"

  def constrainedString(c: StringConstraint): String =
    val (primaryRegex, extraRegexes) = c.regexes match
      case head :: tail => (Some(head), tail)
      case Nil          => (None, Nil)
    val base = primaryRegex match
      case Some(p) => s"fc.stringMatching(new RegExp(${TsLit.str(s"^(?:$p)$$")}))"
      case None =>
        val args = List(
          c.minSize.map(n => s"minLength: $n"),
          c.maxSize.map(n => s"maxLength: $n")
        ).flatten.mkString(", ")
        if args.isEmpty then "fc.string()" else s"fc.string({ $args })"
    val withLenFilter = (primaryRegex, c.minSize, c.maxSize) match
      case (Some(_), Some(lo), Some(hi)) =>
        s"$base.filter((v) => $lo <= v.length && v.length <= $hi)"
      case (Some(_), Some(lo), None) => s"$base.filter((v) => v.length >= $lo)"
      case (Some(_), None, Some(hi)) => s"$base.filter((v) => v.length <= $hi)"
      case _                         => base
    val withExtraRegex = extraRegexes.foldLeft(withLenFilter): (acc, r) =>
      s"$acc.filter((v) => new RegExp(${TsLit.str(s"^(?:$r)$$")}).test(v))"
    c.predicateHelpers.foldLeft(withExtraRegex): (acc, h) =>
      s"$acc.filter((v) => $h(v))"

  def constrainedInt(c: IntConstraint): String =
    val args = List(
      c.minValue.map(n => s"min: $n"),
      c.maxValue.map(n => s"max: $n")
    ).flatten.mkString(", ")
    if args.isEmpty then "fc.integer()" else s"fc.integer({ $args })"

  def functionName(typeName: String): String =
    s"strategy$typeName"

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
