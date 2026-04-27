package specrest.testgen

import specrest.ir.OperationDecl
import specrest.ir.ServiceIR
import specrest.ir.Span

object FilePaths:
  val TestsInitFile      = "tests/__init__.py"
  val ConftestFile       = "tests/conftest.py"
  val StrategiesFile     = "tests/strategies.py"
  val PredicatesFile     = "tests/predicates.py"
  val SkipsFile          = "tests/_testgen_skips.json"
  val AdminRouterFile    = "app/routers/test_admin.py"
  val PytestIniFile      = "pytest.ini"
  val RunConformanceFile = "tests/run_conformance.py"

  def behavioralTestFile(serviceSnake: String): String =
    s"tests/test_behavioral_$serviceSnake.py"

  def statefulTestFile(serviceSnake: String): String =
    s"tests/test_stateful_$serviceSnake.py"

  def structuralTestFile(serviceSnake: String): String =
    s"tests/test_structural_$serviceSnake.py"

object SupportedTargets:
  val PythonFastapiPostgres = "python-fastapi-postgres"
  val All: Set[String]      = Set(PythonFastapiPostgres)

enum ExprPy:
  case Py(text: String)
  case Skip(reason: String, span: Option[Span])

enum CaptureMode:
  case PostState
  case PreState

final case class TestCtx(
    inputs: Set[String],
    outputs: Set[String],
    stateFields: Set[String],
    enumValues: Map[String, Set[String]],
    knownPredicates: Set[String],
    boundVars: Set[String],
    capture: CaptureMode
):
  def withCapture(c: CaptureMode): TestCtx        = copy(capture = c)
  def withBound(names: Iterable[String]): TestCtx = copy(boundVars = boundVars ++ names)

object TestCtx:
  val DefaultPredicates: Set[String] =
    Set("isValidURI", "valid_uri", "is_valid_uri", "valid_email", "isValidEmail")

  def fromOperation(op: OperationDecl, ir: ServiceIR, capture: CaptureMode): TestCtx =
    val stateNames = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
    val enumVals   = ir.enums.map(e => e.name -> e.values.toSet).toMap
    TestCtx(
      inputs = op.inputs.map(_.name).toSet,
      outputs = op.outputs.map(_.name).toSet,
      stateFields = stateNames,
      enumValues = enumVals,
      knownPredicates = DefaultPredicates,
      boundVars = Set.empty,
      capture = capture
    )
