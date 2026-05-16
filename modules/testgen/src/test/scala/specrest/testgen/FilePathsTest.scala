package specrest.testgen

import munit.CatsEffectSuite

class FilePathsTest extends CatsEffectSuite:

  test("behavioralTestFile uses snake-cased service name"):
    assertEquals(
      FilePaths.behavioralTestFile("safe_counter"),
      "tests/test_behavioral_safe_counter.py"
    )

  test("constants match the M5.4 contract"):
    assertEquals(FilePaths.ConftestFile, "tests/conftest.py")
    assertEquals(FilePaths.StrategiesFile, "tests/strategies.py")
    assertEquals(FilePaths.PredicatesFile, "tests/predicates.py")
    assertEquals(FilePaths.SkipsFile, "tests/_testgen_skips.json")
    assertEquals(FilePaths.AdminRouterFile, "app/routers/test_admin.py")

  test("testgen supports every fastapi dialect; go/ts have no test emitter"):
    assert(SupportedTargets.supports("python-fastapi-postgres"))
    assert(SupportedTargets.supports("python-fastapi-sqlite"))
    assert(SupportedTargets.supports("python-fastapi-mysql"))
    assert(!SupportedTargets.supports("go-chi-postgres"))
    assert(!SupportedTargets.supports("ts-express-postgres"))
    assertEquals(
      SupportedTargets.describe,
      "python-fastapi-mysql, python-fastapi-postgres, python-fastapi-sqlite"
    )

  test("supports rejects parseable-but-invalid axis combinations"):
    assert(!SupportedTargets.supports("ts-fastapi-postgres"))
    assert(!SupportedTargets.supports("go-fastapi-postgres"))
    assert(!SupportedTargets.supports("python-rails-postgres"))
    assert(!SupportedTargets.supports("not-a-target"))
