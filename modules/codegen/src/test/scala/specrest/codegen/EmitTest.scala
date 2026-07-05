package specrest.codegen

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures
import specrest.profile.Annotate

class EmitTest extends CatsEffectSuite:

  test("emitProject runs without crashing on url_shortener"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled)
      assert(files.nonEmpty, "no files emitted")

  test("emitProject lays kernel files under app/dafny_kernel/ when DafnyKernel attached (#27)"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val kernel = DafnyKernel(
        packagePath = DafnyKernel.PythonDefaultPackagePath,
        files = Map(
          "module_.py"          -> "# kernel body\n",
          "_dafny/__init__.py"  -> "# runtime\n",
          "System_/__init__.py" -> "# system\n"
        ),
        bindings = List(
          OperationBinding("Shorten", "app.dafny_kernel.module_.default__.Shorten")
        )
      )
      val files = Emit
        .emitProject(profiled, EmitOptions(dafnyKernel = Some(kernel)))
        .map(f => f.path -> f.content)
        .toMap
      assertEquals(files.get("app/dafny_kernel/module_.py"), Some("# kernel body\n"))
      assertEquals(files.get("app/dafny_kernel/_dafny/__init__.py"), Some("# runtime\n"))
      assertEquals(files.get("app/dafny_kernel/System_/__init__.py"), Some("# system\n"))
      assert(
        files.get("app/dafny_kernel/__init__.py").exists(_.contains("from . import module_")),
        s"missing kernel package marker: ${files.get("app/dafny_kernel/__init__.py")}"
      )
      assert(
        files.contains("app/services/_dafny_adapter.py"),
        "missing dafny adapter file"
      )
      assert(
        files.contains("app/services/_synth.py"),
        "missing synth marker file"
      )

  test("DafnyKernel.rewritePythonImports uses depth-aware dots"):
    val rootFile =
      """import sys
        |
        |import module_ as module_
        |import _dafny as _dafny
        |""".stripMargin
    val nestedFile =
      """import sys
        |
        |import _dafny as _dafny
        |""".stripMargin
    val out = DafnyKernel.rewritePythonImports(
      Map(
        "module_.py"          -> rootFile,
        "System_/__init__.py" -> nestedFile
      )
    )
    assert(
      out("module_.py").contains("from . import module_ as module_"),
      s"root file should use single-dot:\n${out("module_.py")}"
    )
    assert(
      out("module_.py").contains("from . import _dafny as _dafny"),
      s"root file should use single-dot:\n${out("module_.py")}"
    )
    assert(
      out("System_/__init__.py").contains("from .. import _dafny as _dafny"),
      s"nested file should use double-dot:\n${out("System_/__init__.py")}"
    )
    assert(out("module_.py").contains("import sys"), "stdlib import preserved")

  test("DafnyKernel.rewriteGoImports strips src/ and module-qualifies the runtime imports"):
    val kernelFile =
      """package kernel
        |
        |import (
        |  os "os"
        |  _dafny "dafny"
        |  m__System "System_"
        |)
        |""".stripMargin
    val runtimeFile =
      """package dafny
        |
        |import "fmt"
        |""".stripMargin
    val out = DafnyKernel.rewriteGoImports(
      Map("src/kernel.go" -> kernelFile, "src/dafny/dafny.go" -> runtimeFile),
      "github.com/generated/svc",
      "internal/dafnykernel"
    )
    assertEquals(out.keySet, Set("kernel.go", "dafny/dafny.go"))
    val kernel = out("kernel.go")
    assert(
      kernel.contains("_dafny \"github.com/generated/svc/internal/dafnykernel/dafny\""),
      s"runtime import should be module-qualified:\n$kernel"
    )
    assert(
      kernel.contains("m__System \"github.com/generated/svc/internal/dafnykernel/System_\""),
      s"System_ import should be module-qualified:\n$kernel"
    )
    assert(!kernel.contains("\"dafny\""), s"bare runtime import must be rewritten:\n$kernel")
    assert(kernel.contains("os \"os\""), "stdlib import preserved")
    assert(out("dafny/dafny.go").contains("import \"fmt\""), "runtime file content preserved")

  test(
    "DafnyKernel.rewriteJsKernel renames .js to .cjs + appends exports, leaving others untouched"
  ):
    val out = DafnyKernel.rewriteJsKernel(
      Map(
        "kernel.js"   -> "let _module = {};\nlet _dafny = {};\n",
        "runtime.txt" -> "raw\n"
      )
    )
    assertEquals(out.keySet, Set("kernel.cjs", "runtime.txt"))
    assert(
      out("kernel.cjs").endsWith("module.exports = { _module, _dafny };\n"),
      s"kernel.cjs should re-export the module + runtime:\n${out("kernel.cjs")}"
    )
    // Non-JS artifacts must pass through verbatim (no rename, no appended exports).
    assertEquals(out("runtime.txt"), "raw\n")

  test("kernel routing falls back to the route-kind body for non-scalar inputs"):
    SpecFixtures.loadIR("todo_list").map: ir =>
      val profiledBase = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      val profiled     = Annotate.attachDafnyMethods(profiledBase, Map("CreateTodo" -> "CreateTodo"))
      val kernel = DafnyKernel(
        packagePath = DafnyKernel.PythonDefaultPackagePath,
        files = Map("module_.py" -> "# kernel\n"),
        bindings = List(OperationBinding("CreateTodo", "CreateTodo"))
      )
      val files = Emit.emitProject(profiled, EmitOptions(dafnyKernel = Some(kernel)))
      val todoService = files
        .find(_.path == "app/services/todo.py")
        .map(_.content)
        .getOrElse(fail("no todo service emitted"))
      // CreateTodo's Option-, enum-, and set-typed inputs convert at the
      // kernel boundary now; the call must carry those conversions.
      assert(
        todoService.contains("enum_to_dafny(\"Priority\", body.priority)"),
        s"enum input should convert through the datatype constructor — got:\n$todoService"
      )
      assert(
        todoService.contains("to_dafny_set(to_dafny_str(_x) for _x in body.tags)"),
        "set input should convert through to_dafny_set"
      )
      assert(
        todoService.contains("some_or_none(body.description, lambda _v: to_dafny_str(_v))"),
        "optional input should wrap through some_or_none"
      )

  test("kernel-routed router call args match kernel handler signature (#27 review)"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val profiledBase = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      val profiled =
        Annotate.attachDafnyMethods(
          profiledBase,
          Map("Shorten" -> "Shorten", "Resolve" -> "Resolve")
        )
      val kernel = DafnyKernel(
        packagePath = DafnyKernel.PythonDefaultPackagePath,
        files = Map("module_.py" -> "# kernel\n"),
        bindings =
          List(OperationBinding("Shorten", "Shorten"), OperationBinding("Resolve", "Resolve"))
      )
      val files = Emit.emitProject(profiled, EmitOptions(dafnyKernel = Some(kernel)))
      val router = files
        .find(_.path == "app/routers/url_mappings.py")
        .map(_.content)
        .getOrElse(fail("no url_mappings router emitted"))
      // Resolve service signature is `code: str` — router must pass `code` (it's a Read route
      // so the prior route-kind logic happened to align, but kernel-routing is the source of truth).
      assert(
        router.contains("svc.resolve(code)"),
        s"router should call svc.resolve(code) — got:\n$router"
      )
      // Shorten service signature is `body: ShortenRequest` — router must pass `body`.
      assert(
        router.contains("svc.shorten(body)"),
        s"router should call svc.shorten(body) — got:\n$router"
      )

  test("kernel-routed handler signature matches dafnyCallArgs for path+body ops (#27 review)"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val profiledBase = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      val profiled =
        Annotate.attachDafnyMethods(
          profiledBase,
          Map("Shorten" -> "Shorten", "Resolve" -> "Resolve")
        )
      val kernel = DafnyKernel(
        packagePath = DafnyKernel.PythonDefaultPackagePath,
        files = Map("module_.py" -> "# kernel\n"),
        bindings =
          List(OperationBinding("Shorten", "Shorten"), OperationBinding("Resolve", "Resolve"))
      )
      val files = Emit.emitProject(profiled, EmitOptions(dafnyKernel = Some(kernel)))
      val service = files
        .find(_.path == "app/services/url_mapping.py")
        .map(_.content)
        .getOrElse(fail("no url_mapping service emitted"))
      // Shorten: body-only op. Signature must include body; the call converts
      // the string across the Dafny boundary and is preceded by the compiled
      // requires guard on the hydrated state.
      assert(
        service.contains("async def shorten(self, body: ShortenRequest)"),
        s"shorten handler should accept body: ShortenRequest — got:\n$service"
      )
      assert(
        service.contains("_dafny_kernel.RequiresShorten(") &&
          service.contains("to_dafny_str(body.url),"),
        s"shorten must guard with RequiresShorten on converted args — got:\n$service"
      )
      assert(
        service.contains("_dafny_kernel.Shorten(") &&
          service.contains("to_dafny_str(body.url),"),
        s"shorten kernel call should pass the converted body.url — got:\n$service"
      )
      assert(
        service.contains("state = await hydrate_state(self._session)"),
        s"kernel ops must hydrate state from the session — got:\n$service"
      )
      assert(
        service.contains("await persist_state(self._session, state)"),
        s"kernel ops must persist the mutated state — got:\n$service"
      )
      // Resolve: path param `code: str`, converted at the boundary.
      assert(
        service.contains("async def resolve(self, code: str)"),
        s"resolve handler should accept code: str — got:\n$service"
      )
      assert(
        service.contains("_dafny_kernel.Resolve(") && service.contains("to_dafny_str(code),"),
        s"resolve kernel call should pass the converted code — got:\n$service"
      )

  test("go validators use package-unique pattern names across entities"):
    SpecFixtures.loadIR("auth_service").map: ir =>
      val profiled = Annotate.buildProfiledService(ir, "go-chi-sqlite")
      val files    = Emit.emitProject(profiled)
      val declared = files
        .filter(f => f.path.startsWith("internal/models/"))
        .flatMap(_.content.linesIterator.filter(_.startsWith("var ")).map(_.split(" ")(1)))
      assertEquals(
        declared.diff(declared.distinct),
        List.empty[String],
        s"duplicate package-level var names across model files: $declared"
      )

  test("Go kernel-routed service marshals scalar in/out via the dafnykernel adapter"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val profiledBase = Annotate.buildProfiledService(ir, "go-chi-postgres")
      val profiled =
        Annotate.attachDafnyMethods(
          profiledBase,
          Map("Shorten" -> "Shorten", "Resolve" -> "Resolve")
        )
      val kernel = DafnyKernel(
        packagePath = DafnyKernel.GoDefaultPackagePath,
        files = Map("src/kernel.go" -> "package kernel\n\nimport (\n  _dafny \"dafny\"\n)\n"),
        bindings =
          List(OperationBinding("Shorten", "Shorten"), OperationBinding("Resolve", "Resolve"))
      )
      val files  = Emit.emitProject(profiled, EmitOptions(dafnyKernel = Some(kernel)))
      val byPath = files.map(f => f.path -> f.content).toMap
      val service =
        byPath.getOrElse("internal/services/url_mapping.go", fail("no go service emitted"))
      // Shorten: body op with a two-value (code, short_url) return -> map[string]any,
      // hydrated, guarded, and persisted inside one transaction.
      assert(
        service.contains(
          "outCode, outShortURL := dafnykernel.Companion_Default___.Shorten(state, dafnykernel.StringToDafny(body.URL))"
        ),
        s"Shorten should marshal body.URL and capture both outputs — got:\n$service"
      )
      assert(
        service.contains("state, err := hydrateState(ctx, tx)"),
        s"kernel ops must hydrate state inside the transaction — got:\n$service"
      )
      assert(
        service.contains(
          "dafnykernel.Companion_Default___.RequiresShorten(state, dafnykernel.StringToDafny(body.URL))"
        ),
        s"kernel ops must check the compiled requires twin — got:\n$service"
      )
      assert(
        service.contains("persistState(ctx, tx, state)"),
        s"kernel ops must persist the mutated state — got:\n$service"
      )
      assert(
        service.contains("\"code\":") && service.contains(
          "dafnykernel.StringFromDafny(outShortURL)"
        ),
        s"Shorten should marshal outputs back into a result map — got:\n$service"
      )
      // Resolve: single scalar in/out.
      assert(
        service.contains(
          "outURL := dafnykernel.Companion_Default___.Resolve(state, dafnykernel.StringToDafny(code))"
        ),
        s"Resolve should marshal the path param — got:\n$service"
      )
      assert(
        service.contains("dafnykernel \"github.com/generated/url-shortener/internal/dafnykernel\""),
        s"service should import the kernel package — got:\n$service"
      )
      // Adapter emitted, and the runtime import in the kernel files is module-qualified.
      val adapter = byPath.getOrElse("internal/dafnykernel/adapter.go", fail("no adapter emitted"))
      assert(
        adapter.contains("func MakeState() *ServiceState"),
        s"adapter missing MakeState:\n$adapter"
      )
      val kernelGo =
        byPath.getOrElse("internal/dafnykernel/kernel.go", fail("kernel src/ not stripped"))
      assert(
        kernelGo.contains("github.com/generated/url-shortener/internal/dafnykernel/dafny"),
        s"kernel runtime import should be rewritten — got:\n$kernelGo"
      )

  test("emitProject without kernel does not emit dafny_kernel files"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val paths = Emit.emitProject(profiled).map(_.path).toSet
      assert(paths.forall(p => !p.startsWith("app/dafny_kernel/")), "kernel files leaked")
      assert(!paths.contains("app/services/_dafny_adapter.py"), "adapter leaked")
      assert(!paths.contains("app/services/_synth.py"), "synth marker leaked")

  test("Go emitProject without kernel emits no dafnykernel files or adapter"):
    SpecFixtures.loadProfiled("url_shortener", "go-chi-postgres").map: profiled =>
      val paths = Emit.emitProject(profiled).map(_.path).toSet
      assert(paths.forall(p => !p.startsWith("internal/dafnykernel/")), "go kernel files leaked")

  test("TS kernel-routed service marshals scalar in/out via the dafnyKernel adapter"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val profiledBase = Annotate.buildProfiledService(ir, "ts-express-postgres")
      val profiled =
        Annotate.attachDafnyMethods(
          profiledBase,
          Map("Shorten" -> "Shorten", "Resolve" -> "Resolve")
        )
      val kernel = DafnyKernel(
        packagePath = DafnyKernel.JsDefaultPackagePath,
        files = Map("kernel.js" -> "let _module = {};\nlet _dafny = {};\n"),
        bindings =
          List(OperationBinding("Shorten", "Shorten"), OperationBinding("Resolve", "Resolve"))
      )
      val files   = Emit.emitProject(profiled, EmitOptions(dafnyKernel = Some(kernel)))
      val byPath  = files.map(f => f.path -> f.content).toMap
      val service = byPath.getOrElse("src/services/urlMapping.ts", fail("no ts service emitted"))
      // Shorten: body op, two-value (code, short_url) return -> object literal.
      assert(
        service.contains("companion.Shorten(state, stringToDafny(body.url))"),
        s"Shorten should marshal body.url through the companion — got:\n$service"
      )
      assert(
        service.contains(
          "return { code: stringFromDafny(outCode), shortUrl: stringFromDafny(outShortUrl) };"
        ),
        s"Shorten should marshal both outputs into a camelCase result object - got:\n$service"
      )
      // Resolve: single scalar in/out.
      assert(
        service.contains("companion.Resolve(state, stringToDafny(code))"),
        s"Resolve should marshal the path param — got:\n$service"
      )
      assert(
        service.contains("from '../dafnyKernel/adapter.js'"),
        s"service should import the kernel adapter — got:\n$service"
      )
      // Kernel emitted as .cjs with appended exports; adapter + copy script present.
      assert(byPath.contains("src/dafnyKernel/adapter.ts"), "adapter not emitted")
      assert(!byPath.contains("src/dafnyKernel/kernel.js"), "kernel.js should be renamed to .cjs")
      val kernelCjs = byPath.getOrElse("src/dafnyKernel/kernel.cjs", fail("kernel.cjs not emitted"))
      assert(
        kernelCjs.contains("module.exports = { _module, _dafny };"),
        s"kernel.cjs should re-export the module + runtime — got:\n$kernelCjs"
      )
      assert(byPath.contains("scripts/copyKernel.mjs"), "build-time kernel copy script not emitted")

  test("TS emitProject without kernel emits no dafnyKernel files or adapter"):
    SpecFixtures.loadProfiled("url_shortener", "ts-express-postgres").map: profiled =>
      val paths = Emit.emitProject(profiled).map(_.path).toSet
      assert(paths.forall(p => !p.startsWith("src/dafnyKernel/")), "ts kernel files leaked")
      assert(!paths.contains("scripts/copyKernel.mjs"), "copy script leaked without a kernel")

  test("url_shortener emits a known file set"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files = Emit.emitProject(profiled).map(_.path).toSet
      val expected = Set(
        "app/__init__.py",
        "app/main.py",
        "app/config.py",
        "app/security.py",
        "app/database.py",
        "app/redaction.py",
        "app/pagination.py",
        "app/routers/admin.py",
        "app/db/__init__.py",
        "app/db/base.py",
        "app/models/__init__.py",
        "app/schemas/__init__.py",
        "app/routers/__init__.py",
        "app/services/__init__.py",
        "app/extensions/__init__.py",
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
        "docker-compose.override.yml.example",
        "docker-compose.staging.yml",
        "docker-compose.prod.yml",
        ".env.example",
        "Makefile",
        ".gitignore",
        ".dockerignore",
        "README.md",
        ".github/workflows/ci.yml",
        "tests/test_health.py",
        "tests/test_log_redaction.py",
        ".spec-snapshot.json"
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
        schema.contains("from pydantic import BaseModel, ConfigDict, Field, SecretStr"),
        s"user schema should import Field and SecretStr; got:\n$schema"
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

  test("db model is pure persistence: no schema import, no custom __init__"):
    SpecFixtures.loadProfiled("auth_service").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val model = files("app/models/user.py")
      assert(
        !model.contains("from app.schemas"),
        s"db model must not import api schemas; got:\n$model"
      )
      assert(
        !model.contains("def __init__"),
        s"db model must not define a custom __init__; got:\n$model"
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

  test("create-shape op constructs the model with explicit fields in the service"):
    SpecFixtures.loadProfiled("secret_create").map: profiled =>
      val files   = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val service = files("app/services/account.py")
      assert(
        service.contains("row = Account("),
        s"create handler should construct Account(...); got:\n$service"
      )
      assert(
        !service.contains("Account(body)"),
        s"create handler should not pass the schema to the constructor; got:\n$service"
      )
      assert(
        !service.contains("NotImplementedError"),
        s"create handler should not be a stub; got:\n$service"
      )

  test("secret_create — schema, model, service line up across the boundary"):
    SpecFixtures.loadProfiled("secret_create").map: profiled =>
      val files   = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val schema  = files("app/schemas/account.py")
      val model   = files("app/models/account.py")
      val service = files("app/services/account.py")
      assert(
        schema.contains("password_hash: SecretStr"),
        s"AccountCreate should use SecretStr; got:\n$schema"
      )
      assert(
        !model.contains("from app.schemas"),
        s"Account model must stay pure persistence; got:\n$model"
      )
      assert(
        service.contains("password_hash=body.password_hash.get_secret_value()"),
        s"create handler should unwrap SecretStr; got:\n$service"
      )
      assert(
        service.contains("email=body.email") && service.contains("display_name=body.display_name"),
        s"create handler should pass plain fields through; got:\n$service"
      )

  test("nullable sensitive field unwraps via guarded get_secret_value"):
    SpecFixtures.loadProfiled("secret_create").map: profiled =>
      val files   = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val service = files("app/services/account.py")
      assert(
        service.contains(
          "reset_token=body.reset_token.get_secret_value() " +
            "if body.reset_token is not None else None"
        ),
        s"nullable sensitive field should guard get_secret_value() against None; got:\n$service"
      )

  test("every entity model file is pure persistence (no __init__, no schema import)"):
    val cases = List("secret_create", "auth_service", "url_shortener", "todo_list", "ecommerce")
    cases.foldLeft(IO.unit): (acc, name) =>
      acc.flatMap: _ =>
        SpecFixtures.loadProfiled(name).map: profiled =>
          val offenders = Emit.emitProject(profiled).filter: f =>
            f.path.startsWith("app/models/") &&
              f.path.endsWith(".py") &&
              !f.path.endsWith("__init__.py") &&
              (f.content.contains("def __init__") || f.content.contains("from app.schemas"))
          assert(
            offenders.isEmpty,
            s"$name: model files still coupled to schemas: ${offenders.map(_.path)}"
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
