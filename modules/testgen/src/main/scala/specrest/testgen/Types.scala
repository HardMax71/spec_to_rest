package specrest.testgen

object FilePaths:
  val ConftestFile    = "tests/conftest.py"
  val StrategiesFile  = "tests/strategies.py"
  val PredicatesFile  = "tests/predicates.py"
  val SkipsFile       = "tests/_testgen_skips.json"
  val AdminRouterFile = "app/routers/test_admin.py"
  val PytestIniFile   = "pytest.ini"

  def behavioralTestFile(serviceSnake: String): String =
    s"tests/test_behavioral_$serviceSnake.py"

object SupportedTargets:
  val PythonFastapiPostgres = "python-fastapi-postgres"
  val All: Set[String]      = Set(PythonFastapiPostgres)
