package specrest.testgen

import specrest.ir.FunctionDecl
import specrest.ir.OperationDecl
import specrest.ir.PredicateDecl
import specrest.ir.ServiceIR
import specrest.ir.Span
import specrest.ir.TypeExpr

object FilePaths:
  val TestsInitFile      = "tests/__init__.py"
  val ConftestFile       = "tests/conftest.py"
  val StrategiesFile     = "tests/strategies.py"
  val StrategiesUserFile = "tests/strategies_user.py"
  val RedactionFile      = "tests/redaction.py"
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

enum ExprPy derives CanEqual:
  case Py(text: String)
  case Skip(reason: String, span: Option[Span])

enum CaptureMode derives CanEqual:
  case PostState
  case PreState

final case class TestCtx(
    inputs: Set[String],
    outputs: Set[String],
    stateFields: Set[String],
    mapStateFields: Set[String],
    enumValues: Map[String, Set[String]],
    userFunctions: Map[String, FunctionDecl],
    userPredicates: Map[String, PredicateDecl],
    boundVars: Set[String],
    capture: CaptureMode
):
  def withCapture(c: CaptureMode): TestCtx        = copy(capture = c)
  def withBound(names: Iterable[String]): TestCtx = copy(boundVars = boundVars ++ names)

object TestCtx:
  def fromOperation(op: OperationDecl, ir: ServiceIR, capture: CaptureMode): TestCtx =
    val stateNames = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
    val mapStateNames = ir.state.toList.flatMap(_.fields).collect {
      case f if isMapType(f.typeExpr) => f.name
    }.toSet
    val enumVals = ir.enums.map(e => e.name -> e.values.toSet).toMap
    TestCtx(
      inputs = op.inputs.map(_.name).toSet,
      outputs = op.outputs.map(_.name).toSet,
      stateFields = stateNames,
      mapStateFields = mapStateNames,
      enumValues = enumVals,
      userFunctions = ir.functions.map(f => f.name -> f).toMap,
      userPredicates = ir.predicates.map(p => p.name -> p).toMap,
      boundVars = Set.empty,
      capture = capture
    )

  private def isMapType(t: TypeExpr): Boolean = t match
    case _: TypeExpr.MapType => true
    case _                   => false
