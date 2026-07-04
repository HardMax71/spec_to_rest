package specrest.testgen

import specrest.codegen.EmittedFile
import specrest.ir.generated.SpecRestGenerated.IntConstraint
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.StringConstraint
import specrest.ir.generated.SpecRestGenerated.expr
import specrest.ir.generated.SpecRestGenerated.int_constraint
import specrest.ir.generated.SpecRestGenerated.span_t
import specrest.ir.generated.SpecRestGenerated.string_constraint
import specrest.profile.ProfiledService

import java.nio.charset.StandardCharsets

// Translates an IR expression to a target-language expression. Each backend owns
// its own recursion — Python comprehensions/quantifiers do not leaf-swap to TS
// `.every`/`.map` or Go loops — over the shared Translated result ADT and TestCtx.
// `stringLiteral` renders a Scala string as a target string literal.
trait ExprBackend:
  def translate(expr: expr, ctx: TestCtx): Translated
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

// Loads a bundled testgen template resource. A missing resource is a build
// packaging error, so it fails loudly.
private[testgen] object TemplateResources:
  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def load(root: String, relPath: String): String =
    val resourcePath = s"$root/$relPath"
    val is           = getClass.getClassLoader.getResourceAsStream(resourcePath)
    if is eq null then
      throw new RuntimeException(s"testgen template resource missing: $resourcePath")
    try new String(is.readAllBytes(), StandardCharsets.UTF_8)
    finally is.close()

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
  def constrainedInt(c: int_constraint): String
  def functionName(typeName: String): String

  def regexGen(pattern: String): String
  def boundedText(min: Option[Int], max: Option[Int]): String
  def lengthFilter(base: String, min: Option[Int], max: Option[Int]): String
  def regexFilter(base: String, pattern: String): String
  def predicateFilter(base: String, helper: String): String

  // One decision pipeline for string constraints across all backends: a
  // primary regex beats bounded text as the base, length bounds re-apply as a
  // filter only when a regex claimed the base, and the remaining regexes and
  // predicate helpers stack as filters. The five leaves above render.
  final def constrainedString(c: string_constraint): String = c match
    case StringConstraint(minOpt, maxOpt, regexes, predicateHelpers, _) =>
      val minSize = minOpt.map(_.toInt)
      val maxSize = maxOpt.map(_.toInt)
      val (primaryRegex, extraRegexes) = regexes match
        case head :: tail => (Some(head), tail)
        case Nil          => (None, Nil)
      val base = primaryRegex match
        case Some(p) => regexGen(p)
        case None    => boundedText(minSize, maxSize)
      val withLenFilter =
        if primaryRegex.isDefined && (minSize.isDefined || maxSize.isDefined) then
          lengthFilter(base, minSize, maxSize)
        else base
      val withExtraRegex = extraRegexes.foldLeft(withLenFilter)(regexFilter)
      predicateHelpers.foldLeft(withExtraRegex)(predicateFilter)

object PythonHypothesisStrategy extends StrategyBackend:
  // Postgres text rejects NUL outright (CharacterNotInRepertoireError), and
  // the spec's semantics carry no encoding model, so generated strings must
  // exclude what the storage layer categorically cannot hold. Same reality
  // as the preamble's isValidURI control-character bound.
  private val TextAlphabet =
    "alphabet=st.characters(exclude_characters=\"\\x00\")"

  def string: String       = s"st.text($TextAlphabet)"
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

  // Hypothesis's from_regex/fullmatch already whole-string-matches, so no
  // pattern anchoring here (unlike the JS and Go leaves).
  def regexGen(pattern: String): String =
    s"st.from_regex(${ExprToPython.pyString(pattern)}, fullmatch=True)" +
      ".filter(lambda v: \"\\x00\" not in v)"

  def boundedText(min: Option[Int], max: Option[Int]): String =
    val args = (TextAlphabet :: List(
      min.map(n => s"min_size=$n"),
      max.map(n => s"max_size=$n")
    ).flatten).mkString(", ")
    s"st.text($args)"

  def lengthFilter(base: String, min: Option[Int], max: Option[Int]): String =
    (min, max) match
      case (Some(lo), Some(hi)) => s"$base.filter(lambda v: $lo <= len(v) <= $hi)"
      case (Some(lo), None)     => s"$base.filter(lambda v: len(v) >= $lo)"
      case (None, Some(hi))     => s"$base.filter(lambda v: len(v) <= $hi)"
      case (None, None)         => base

  def regexFilter(base: String, pattern: String): String =
    s"$base.filter(lambda v: __import__('re').fullmatch(${ExprToPython.pyString(pattern)}, v) is not None)"

  def predicateFilter(base: String, helper: String): String =
    s"$base.filter(lambda v: $helper(v))"

  def constrainedInt(c: int_constraint): String = c match
    case IntConstraint(minOpt, maxOpt, _) =>
      val args = List(
        minOpt.map(n => s"min_value=${n}"),
        maxOpt.map(n => s"max_value=${n}")
      ).flatten.mkString(", ")
      if args.isEmpty then "st.integers()" else s"st.integers($args)"

  def functionName(typeName: String): String =
    s"strategy_${specrest.ir.Naming.toSnakeCase(typeName)}"

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
      pickEmit: specrest.ir.Builtins.BuiltinSpec => List[String] => String
  ): Translated =
    specrest.ir.Builtins.byName.get(fname) match
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

  def regexGen(pattern: String): String =
    s"fc.stringMatching(new RegExp(${TsLit.str(Strategies.fullMatchPattern(pattern))}))"

  def boundedText(min: Option[Int], max: Option[Int]): String =
    val args = List(
      min.map(n => s"minLength: $n"),
      max.map(n => s"maxLength: $n")
    ).flatten.mkString(", ")
    if args.isEmpty then "fc.string()" else s"fc.string({ $args })"

  def lengthFilter(base: String, min: Option[Int], max: Option[Int]): String =
    (min, max) match
      case (Some(lo), Some(hi)) => s"$base.filter((v) => $lo <= v.length && v.length <= $hi)"
      case (Some(lo), None)     => s"$base.filter((v) => v.length >= $lo)"
      case (None, Some(hi))     => s"$base.filter((v) => v.length <= $hi)"
      case (None, None)         => base

  def regexFilter(base: String, pattern: String): String =
    s"$base.filter((v) => new RegExp(${TsLit.str(Strategies.fullMatchPattern(pattern))}).test(v))"

  def predicateFilter(base: String, helper: String): String =
    s"$base.filter((v) => $helper(v))"

  def constrainedInt(c: int_constraint): String = c match
    case IntConstraint(minOpt, maxOpt, _) =>
      val args = List(
        minOpt.map(n => s"min: ${n}"),
        maxOpt.map(n => s"max: ${n}")
      ).flatten.mkString(", ")
      if args.isEmpty then "fc.integer()" else s"fc.integer({ $args })"

  def functionName(typeName: String): String =
    s"strategy$typeName"

object TestBackend:
  // Python-path backends only: TestEmit dispatches ts-express and go-chi to their own
  // emitter stacks, and Python stays byte-identical as the differential oracle.
  def harnessFor(profiled: ProfiledService): HarnessTemplates =
    PythonFastApiHarness

  def strategyFor(profiled: ProfiledService): StrategyBackend =
    PythonHypothesisStrategy

  def exprFor(profiled: ProfiledService): ExprBackend =
    ExprToPython
