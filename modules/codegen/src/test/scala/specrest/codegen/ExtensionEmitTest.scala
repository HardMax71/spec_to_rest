package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures

class ExtensionEmitTest extends CatsEffectSuite:

  private val cases = List(
    ("python-fastapi-postgres", "app/extensions/__init__.py"),
    ("go-chi-postgres", "internal/extensions/extensions.go"),
    ("ts-express-postgres", "src/extensions/index.ts")
  )

  cases.foreach: (target, extPath) =>
    test(s"$target emits the extension scaffold with preserve=true"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val files = Emit.emitProject(profiled)
        val ext   = files.find(_.path == extPath).getOrElse(
          fail(s"$target did not emit $extPath; got: ${files.map(_.path).mkString(", ")}")
        )
        assert(ext.preserve, s"$extPath must be marked preserve=true")
        assert(ext.content.nonEmpty, s"$extPath stub should be non-empty")

  private def emittedContent(target: String, path: String): cats.effect.IO[String] =
    SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
      Emit
        .emitProject(profiled)
        .find(_.path == path)
        .map(_.content)
        .getOrElse(fail(s"$target did not emit $path"))

  test("python main.py imports register_extensions and calls it before include_router"):
    emittedContent("python-fastapi-postgres", "app/main.py").map: main =>
      assert(
        main.contains("from app.extensions import register as register_extensions"),
        s"main.py must import extension register; got:\n$main"
      )
      val registerIdx  = main.indexOf("register_extensions(app)")
      val firstInclude = main.indexOf("app.include_router(")
      assert(registerIdx >= 0, s"main.py must call register_extensions(app); got:\n$main")
      assert(firstInclude >= 0, s"main.py must call include_router; got:\n$main")
      assert(
        registerIdx < firstInclude,
        s"register_extensions must run BEFORE include_router (got register@$registerIdx vs include@$firstInclude):\n$main"
      )

  test("go main.go calls extensions.Register before any spec-derived route registration"):
    emittedContent("go-chi-postgres", "cmd/server/main.go").map: main =>
      assert(
        main.contains("/internal/extensions\""),
        s"main.go must import extensions package; got:\n$main"
      )
      val registerIdx = main.indexOf("extensions.Register(r, db)")
      val healthIdx   = main.indexOf("r.Get(\"/health\"")
      assert(registerIdx >= 0, s"main.go must call extensions.Register; got:\n$main")
      assert(healthIdx >= 0, s"main.go must register /health; got:\n$main")
      assert(
        registerIdx < healthIdx,
        s"extensions.Register must run BEFORE any route — chi panics on late r.Use (got register@$registerIdx vs /health@$healthIdx):\n$main"
      )

  test("ts app.ts calls registerExtensions before mountRoutes"):
    emittedContent("ts-express-postgres", "src/app.ts").map: app =>
      assert(
        app.contains("from './extensions/index.js'"),
        s"app.ts must import registerExtensions; got:\n$app"
      )
      val registerIdx = app.indexOf("registerExtensions(app)")
      val mountIdx    = app.indexOf("mountRoutes(app)")
      assert(registerIdx >= 0, s"app.ts must call registerExtensions(app); got:\n$app")
      assert(mountIdx >= 0, s"app.ts must call mountRoutes(app); got:\n$app")
      assert(
        registerIdx < mountIdx,
        s"registerExtensions must run BEFORE mountRoutes so user middleware wraps generated routes (got register@$registerIdx vs mount@$mountIdx):\n$app"
      )
