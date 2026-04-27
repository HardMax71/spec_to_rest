package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures

class EmitTest extends CatsEffectSuite:

  test("emitProject runs without crashing on url_shortener"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled)
      assert(files.nonEmpty, "no files emitted")

  test("url_shortener emits a known file set"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled).map(_.path).toSet
      val expected = Set(
        "app/__init__.py",
        "app/main.py",
        "app/config.py",
        "app/database.py",
        "app/db/__init__.py",
        "app/db/base.py",
        "app/models/__init__.py",
        "app/schemas/__init__.py",
        "app/routers/__init__.py",
        "app/services/__init__.py",
        "app/models/url_mapping.py",
        "app/schemas/url_mapping.py",
        "app/routers/url_mappings.py",
        "app/services/url_mapping.py",
        "openapi.yaml",
        "alembic.ini",
        "alembic/env.py",
        "alembic/versions/001_initial_schema.py",
        "pyproject.toml",
        "Dockerfile",
        "docker-compose.yml",
        ".env.example",
        "Makefile",
        ".gitignore",
        ".dockerignore",
        "README.md",
        ".github/workflows/ci.yml",
        "tests/test_health.py"
      )
      assertEquals(files, expected)

  test("main.py contains FastAPI / pyproject.toml contains [project]"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      assert(
        files("app/main.py").contains("FastAPI"),
        s"main.py missing FastAPI: ${files("app/main.py").take(200)}"
      )
      assert(files("pyproject.toml").contains("[project]"), s"pyproject.toml missing [project]")
      assert(files("Dockerfile").contains("FROM"), "Dockerfile missing FROM")

  test("model file contains entity name"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val model = files("app/models/url_mapping.py")
      assert(
        model.contains("UrlMapping"),
        s"model missing UrlMapping class; content=${model.take(400)}"
      )

  test("empty __init__.py files are actually empty"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      assertEquals(files("app/__init__.py"), "")
      assertEquals(files("app/db/__init__.py"), "")

  test(
    "ci.yml renders GitHub Actions ${{ ... }} expressions literally (not Handlebars-substituted)"
  ):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val ci    = files(".github/workflows/ci.yml")
      assert(
        ci.contains("${{ github.event_name == 'schedule' && 'exhaustive' || 'thorough' }}"),
        s"ci.yml profile expression not rendered as a literal GitHub expression:\n$ci"
      )
      assert(
        ci.contains("${{ env.SPEC_TEST_PROFILE }}"),
        s"ci.yml artifact-name expression not rendered as a literal GitHub expression:\n$ci"
      )
      assert(
        !ci.contains("$\\{{"),
        s"Handlebars escape leaked into rendered output:\n$ci"
      )
