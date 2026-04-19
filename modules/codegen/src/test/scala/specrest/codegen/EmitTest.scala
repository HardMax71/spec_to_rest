package specrest.codegen

import java.nio.file.{Files, Paths}
import specrest.parser.{Builder, Parse}
import specrest.profile.Annotate

class EmitTest extends munit.FunSuite:

  private def buildProfiled(name: String): specrest.profile.ProfiledService =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    Annotate.buildProfiledService(ir, "python-fastapi-postgres")

  test("emitProject runs without crashing on url_shortener"):
    val profiled = buildProfiled("url_shortener")
    val files    = Emit.emitProject(profiled)
    assert(files.nonEmpty, "no files emitted")

  test("url_shortener emits a known file set"):
    val profiled = buildProfiled("url_shortener")
    val files    = Emit.emitProject(profiled).map(_.path).toSet
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
    val files = Emit.emitProject(buildProfiled("url_shortener")).map(f => f.path -> f.content).toMap
    assert(
      files("app/main.py").contains("FastAPI"),
      s"main.py missing FastAPI: ${files("app/main.py").take(200)}"
    )
    assert(files("pyproject.toml").contains("[project]"), s"pyproject.toml missing [project]")
    assert(files("Dockerfile").contains("FROM"), "Dockerfile missing FROM")

  test("model file contains entity name"):
    val files = Emit.emitProject(buildProfiled("url_shortener")).map(f => f.path -> f.content).toMap
    val model = files("app/models/url_mapping.py")
    assert(
      model.contains("UrlMapping"),
      s"model missing UrlMapping class; content=${model.take(400)}"
    )

  test("empty __init__.py files are actually empty"):
    val files = Emit.emitProject(buildProfiled("url_shortener")).map(f => f.path -> f.content).toMap
    assertEquals(files("app/__init__.py"), "")
    assertEquals(files("app/db/__init__.py"), "")
