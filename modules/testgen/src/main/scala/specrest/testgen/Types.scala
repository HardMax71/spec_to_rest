package specrest.testgen

import specrest.codegen.AdminModel
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
    userFunctions: Map[String, function_decl],
    userPredicates: Map[String, predicate_decl],
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
    userFunctions.view.mapValues(f => BigInt(fncParams(f).size)).toList

  def predArities: List[(String, BigInt)] =
    userPredicates.view.mapValues(p => BigInt(prdParams(p).size)).toList

object TestCtx:
  def fromOperation(
      op: operation_decl,
      ir: ServiceIRFull,
      capture: CaptureMode,
      bareBodyOutput: Option[String] = None
  ): TestCtx =
    val stateNames    = irStateFieldNames(ir).toSet
    val mapStateNames = irStateFields(ir).filter(f => isMapType(stfType(f))).map(stfName).toSet
    val enumVals      = svcEnums(ir).map(e => enmName(e) -> enmVariants(e).toSet).toMap
    TestCtx(
      inputs = operInputs(op).map(prmName).toSet,
      outputs = operOutputs(op).map(prmName).toSet,
      stateFields = stateNames,
      mapStateFields = mapStateNames,
      enumValues = enumVals,
      userFunctions = svcFunctions(ir).map(f => fncName(f) -> f).toMap,
      userPredicates = svcPredicates(ir).map(p => prdName(p) -> p).toMap,
      boundVars = Set.empty,
      capture = capture,
      bareBodyOutput = bareBodyOutput,
      unbackedStateFields = AdminModel.unbackedStateFieldNames(ir)
    )

  def forInvariants(ir: ServiceIRFull): TestCtx =
    val stateFieldsAll = irStateFields(ir)
    TestCtx(
      inputs = Set.empty,
      outputs = Set.empty,
      stateFields = stateFieldsAll.map(stfName).toSet,
      mapStateFields = stateFieldsAll.filter(f => isMapType(stfType(f))).map(stfName).toSet,
      enumValues = svcEnums(ir).map(e => enmName(e) -> enmVariants(e).toSet).toMap,
      userFunctions = svcFunctions(ir).map(f => fncName(f) -> f).toMap,
      userPredicates = svcPredicates(ir).map(p => prdName(p) -> p).toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState,
      unbackedStateFields = AdminModel.unbackedStateFieldNames(ir)
    )

  def forPredicateBody(params: Set[String], ir: ServiceIRFull): TestCtx =
    TestCtx(
      inputs = params,
      outputs = Set.empty,
      stateFields = Set.empty,
      mapStateFields = Set.empty,
      enumValues = svcEnums(ir).map(e => enmName(e) -> enmVariants(e).toSet).toMap,
      userFunctions = svcFunctions(ir).map(f => fncName(f) -> f).toMap,
      userPredicates = svcPredicates(ir).map(p => prdName(p) -> p).toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState
    )

private[testgen] object UserDefs:
  def renderAll(ir: ServiceIRFull)(renderOne: (String, List[String], expr) => String): String =
    val parts =
      svcFunctions(ir).map(f => renderOne(fncName(f), fncParams(f).map(prmName), fncBody(f))) ++
        svcPredicates(ir).map(p => renderOne(prdName(p), prdParams(p).map(prmName), prdBody(p)))
    parts.mkString("")
