package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.Registry
import specrest.profile.TargetKey

object FilePaths:
  val TestsInitFile      = "tests/__init__.py"
  val ConftestFile       = "tests/conftest.py"
  val StrategiesFile     = "tests/strategies.py"
  val StrategiesUserFile = "tests/strategies_user.py"
  val RedactionFile      = "tests/redaction.py"
  val PredicatesFile     = "tests/predicates.py"
  val SkipsFile          = "tests/_testgen_skips.json"
  val AdminRouterFile    = "app/routers/test_admin.py"
  val AdminRouterFileTs  = "src/routes/testAdmin.ts"
  val AdminRouterFileGo  = "internal/testadmin/admin_conformance.go"
  val PytestIniFile      = "pytest.ini"
  val RunConformanceFile = "tests/run_conformance.py"

  def behavioralTestFile(serviceSnake: String): String =
    s"tests/test_behavioral_$serviceSnake.py"

  def statefulTestFile(serviceSnake: String): String =
    s"tests/test_stateful_$serviceSnake.py"

  def structuralTestFile(serviceSnake: String): String =
    s"tests/test_structural_$serviceSnake.py"

object SupportedTargets:
  def supports(target: String): Boolean =
    TargetKey.parse(target).toOption.exists: k =>
      Registry.resolve(k).isRight &&
        Registry.framework(k.framework).exists(_.supportsTestgen(k.database))

  def describe: String =
    Registry.frameworkIds
      .flatMap(Registry.framework)
      .flatMap: fw =>
        for
          l <- fw.supportedLanguages.toList
          d <- fw.supportedDialects.toList
          if fw.supportsTestgen(d)
        yield TargetKey(l, fw.id, d).slug
      .distinct
      .sorted
      .mkString(", ")

// Result type for any expression-translation backend. The payload is an opaque
// target-language string (Python, TS, Go, …); the type itself is language-
// agnostic, despite its old name `ExprPy.Py` which leaked the Python-was-first
// history. Renamed to Translated.Emit so a `TsExprBackend.translate(...)` call
// no longer reads as "TS backend returns Python".
enum Translated derives CanEqual:
  case Emit(text: String)
  case Skip(reason: String, span: Option[span_t])

enum CaptureMode derives CanEqual:
  case PostState
  case PreState

final case class TestCtx(
    inputs: Set[String],
    outputs: Set[String],
    stateFields: Set[String],
    mapStateFields: Set[String],
    enumValues: Map[String, Set[String]],
    userFunctions: Map[String, FunctionDeclFull],
    userPredicates: Map[String, PredicateDeclFull],
    boundVars: Set[String],
    capture: CaptureMode,
    // The single output an endpoint returns as the bare response body (e.g. a `list`
    // route returns the array itself, not `{"<name>": [...]}`). Such an output must
    // translate to `response_data`, not `response_data["<name>"]`.
    bareBodyOutput: Option[String] = None,
    // State fields the test-admin `/state` endpoint cannot project (no backing entity
    // table) — it emits them as `null`, so any assertion referencing them would compare
    // against `None` and crash. Such expressions are honest-skipped, not emitted.
    unbackedStateFields: Set[String] = Set.empty
):
  def withCapture(c: CaptureMode): TestCtx        = copy(capture = c)
  def withBound(names: Iterable[String]): TestCtx = copy(boundVars = boundVars ++ names)

  def identCtx(reserved: List[String]): ident_ctx =
    IdentCtx(
      reserved,
      boundVars.toList,
      bareBodyOutput,
      outputs.toList,
      inputs.toList,
      stateFields.toList,
      unbackedStateFields.toList,
      enumValues.keys.toList,
      enumValues.values.flatten.toList
    )

  def fnArities: List[(String, BigInt)] =
    userFunctions.view.mapValues(f => BigInt(f.b.size)).toList

  def predArities: List[(String, BigInt)] =
    userPredicates.view.mapValues(p => BigInt(p.b.size)).toList

object TestCtx:
  def fromOperation(
      op: OperationDeclFull,
      ir: ServiceIRFull,
      capture: CaptureMode,
      bareBodyOutput: Option[String] = None
  ): TestCtx =
    val stateNames = ir.f.toList.flatMap {
      case StateDeclFull(fs, _) => fs.collect { case StateFieldDeclFull(n, _, _) => n }
    }.toSet
    val mapStateNames = ir.f.toList.flatMap {
      case StateDeclFull(fs, _) =>
        fs.collect { case StateFieldDeclFull(n, t, _) if isMapType(t) => n }
    }.toSet
    val enumVals = ir.d.collect { case e: EnumDeclFull => e.a -> e.b.toSet }.toMap
    TestCtx(
      inputs = op.b.collect { case ParamDeclFull(n, _, _) => n }.toSet,
      outputs = op.c.collect { case ParamDeclFull(n, _, _) => n }.toSet,
      stateFields = stateNames,
      mapStateFields = mapStateNames,
      enumValues = enumVals,
      userFunctions = ir.l.collect { case f: FunctionDeclFull => f.a -> f }.toMap,
      userPredicates = ir.m.collect { case p: PredicateDeclFull => p.a -> p }.toMap,
      boundVars = Set.empty,
      capture = capture,
      bareBodyOutput = bareBodyOutput,
      unbackedStateFields = AdminModel.unbackedStateFieldNames(ir)
    )
