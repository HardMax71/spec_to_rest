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
    assertEquals(FilePaths.AdminRouterFileTs, "src/routes/testAdmin.ts")

  test("testgen supports every fastapi, ts-express and go-chi dialect"):
    List(
      "python-fastapi-postgres",
      "python-fastapi-sqlite",
      "python-fastapi-mysql",
      "ts-express-postgres",
      "ts-express-sqlite",
      "ts-express-mysql",
      "go-chi-postgres",
      "go-chi-sqlite",
      "go-chi-mysql"
    ).foreach(t => assert(SupportedTargets.supports(t), t))
    assertEquals(
      SupportedTargets.describe,
      "go-chi-mysql, go-chi-postgres, go-chi-sqlite, " +
        "python-fastapi-mysql, python-fastapi-postgres, python-fastapi-sqlite, " +
        "ts-express-mysql, ts-express-postgres, ts-express-sqlite"
    )

  test("supports rejects parseable-but-invalid axis combinations"):
    assert(!SupportedTargets.supports("ts-fastapi-postgres"))
    assert(!SupportedTargets.supports("go-fastapi-postgres"))
    assert(!SupportedTargets.supports("python-rails-postgres"))
    assert(!SupportedTargets.supports("not-a-target"))
