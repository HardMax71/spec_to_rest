package specrest.testgen

import specrest.codegen.EmittedFile
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.profile.ProfiledService

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

object TestBackend:
  // Only the Python/FastAPI harness exists today; this is the dispatch point through
  // which the TypeScript (vitest+fast-check) and Go (test+rapid) harnesses plug in.
  // The conformance decision logic (Behavioral/Stateful/Structural) stays shared —
  // only the rendered target language differs.
  def harnessFor(profiled: ProfiledService): HarnessTemplates =
    PythonFastApiHarness
