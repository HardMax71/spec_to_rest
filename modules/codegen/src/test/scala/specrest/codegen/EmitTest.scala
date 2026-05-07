package specrest.codegen

import cats.effect.IO
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
        "app/redaction.py",
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
        "tests/test_health.py",
        "tests/test_log_redaction.py"
      )
      assertEquals(files, expected)

  test("main.py contains FastAPI / pyproject.toml contains [project]"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      assert(
        files("app/main.py").contains("FastAPI"),
        s"main.py missing FastAPI: ${files("app/main.py").take(200)}"
      )
      assert(files("pyproject.toml").contains("[project]"), "pyproject.toml missing [project]")
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

  test("schema with sensitive field imports SecretStr and types it as SecretStr"):
    SpecFixtures.loadProfiled("auth_service").map: profiled =>
      val files  = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val schema = files("app/schemas/user.py")
      assert(
        schema.contains("from pydantic import BaseModel, ConfigDict, SecretStr"),
        s"user schema should import SecretStr; got:\n$schema"
      )
      assert(
        schema.contains("password_hash: SecretStr"),
        s"UserCreate should declare password_hash: SecretStr; got:\n$schema"
      )
      assert(
        !schema.contains("password_hash: str"),
        s"UserCreate should not have a plain-str password_hash; got:\n$schema"
      )

  test("read schema continues to exclude sensitive fields"):
    SpecFixtures.loadProfiled("auth_service").map: profiled =>
      val files  = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val schema = files("app/schemas/user.py")
      val readBlock =
        schema.linesIterator
          .dropWhile(!_.contains("class UserRead"))
          .takeWhile(!_.startsWith("class UserUpdate"))
          .mkString("\n")
      assert(
        !readBlock.contains("password_hash"),
        s"UserRead must not surface password_hash; got:\n$readBlock"
      )

  test("model emits typed __init__ taking the Create schema"):
    SpecFixtures.loadProfiled("auth_service").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val model = files("app/models/user.py")
      assert(
        model.contains("from app.schemas.user import UserCreate"),
        s"user model should import UserCreate; got:\n$model"
      )
      assert(
        model.contains("def __init__(self, body: UserCreate) -> None:"),
        s"User.__init__ should take a typed UserCreate; got:\n$model"
      )
      assert(
        model.contains("password_hash=body.password_hash.get_secret_value()"),
        s"User.__init__ should unwrap password_hash via get_secret_value(); got:\n$model"
      )
      assert(
        model.contains("email=body.email"),
        s"User.__init__ should pass email through; got:\n$model"
      )

  test("no service handler splats model_dump() into the ORM constructor"):
    val cases = List("secret_create", "auth_service", "url_shortener", "todo_list", "ecommerce")
    cases.foldLeft(IO.unit): (acc, name) =>
      acc.flatMap: _ =>
        SpecFixtures.loadProfiled(name).map: profiled =>
          val offenders = Emit.emitProject(profiled).filter: f =>
            f.path.startsWith("app/services/") && f.content.contains("model_dump()")
          assert(
            offenders.isEmpty,
            s"$name: services still call model_dump(): ${offenders.map(_.path)}"
          )

  test("matching create-shape op renders typed row = Model(body) handler"):
    SpecFixtures.loadProfiled("secret_create").map: profiled =>
      val files   = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val service = files("app/services/account.py")
      assert(
        service.contains("row = Account(body)"),
        s"create handler should call Account(body); got:\n$service"
      )
      assert(
        !service.contains("NotImplementedError"),
        s"create handler should not be a stub; got:\n$service"
      )

  test("secret_create — schema, model, service line up across the boundary"):
    SpecFixtures.loadProfiled("secret_create").map: profiled =>
      val files  = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val schema = files("app/schemas/account.py")
      val model  = files("app/models/account.py")
      assert(
        schema.contains("password_hash: SecretStr"),
        s"AccountCreate should use SecretStr; got:\n$schema"
      )
      assert(
        model.contains("password_hash=body.password_hash.get_secret_value()"),
        s"Account.__init__ should unwrap SecretStr; got:\n$model"
      )
      assert(
        model.contains("email=body.email") && model.contains("display_name=body.display_name"),
        s"Account.__init__ should pass plain fields through; got:\n$model"
      )

  test("nullable sensitive field unwraps via guarded get_secret_value"):
    SpecFixtures.loadProfiled("secret_create").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val model = files("app/models/account.py")
      assert(
        model.contains(
          "reset_token=body.reset_token.get_secret_value() " +
            "if body.reset_token is not None else None"
        ),
        s"nullable sensitive field should guard get_secret_value() against None; got:\n$model"
      )

  test("every entity model file emits a typed __init__ regardless of field count"):
    val cases = List("secret_create", "auth_service", "url_shortener", "todo_list", "ecommerce")
    cases.foldLeft(IO.unit): (acc, name) =>
      acc.flatMap: _ =>
        SpecFixtures.loadProfiled(name).map: profiled =>
          val offenders = Emit.emitProject(profiled).filter: f =>
            f.path.startsWith("app/models/") &&
              f.path.endsWith(".py") &&
              !f.path.endsWith("__init__.py") &&
              !f.content.contains("def __init__(self, body:")
          assert(
            offenders.isEmpty,
            s"$name: model files missing typed __init__: ${offenders.map(_.path)}"
          )

  test("schema without sensitive fields does not import SecretStr"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files  = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val schema = files("app/schemas/url_mapping.py")
      assert(
        !schema.contains("SecretStr"),
        s"url_mapping schema should not import SecretStr; got:\n$schema"
      )

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
