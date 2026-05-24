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
        val ext = files.find(_.path == extPath).getOrElse(
          fail(s"$target did not emit $extPath; got: ${files.map(_.path).mkString(", ")}")
        )
        assert(ext.preserve, s"$extPath must be marked preserve=true")
        assert(ext.content.nonEmpty, s"$extPath stub should be non-empty")

  test("python main.py imports and calls register_extensions"):
    SpecFixtures.loadProfiled("url_shortener", "python-fastapi-postgres").map: profiled =>
      val main = Emit.emitProject(profiled).find(_.path == "app/main.py").get.content
      assert(
        main.contains("from app.extensions import register as register_extensions"),
        s"main.py must import extension register; got:\n$main"
      )
      assert(
        main.contains("register_extensions(app)"),
        s"main.py must call register_extensions(app); got:\n$main"
      )

  test("go main.go imports extensions package and calls extensions.Register"):
    SpecFixtures.loadProfiled("url_shortener", "go-chi-postgres").map: profiled =>
      val main = Emit.emitProject(profiled).find(_.path == "cmd/server/main.go").get.content
      assert(
        main.contains("/internal/extensions\""),
        s"main.go must import extensions package; got:\n$main"
      )
      assert(
        main.contains("extensions.Register(r, db)"),
        s"main.go must call extensions.Register; got:\n$main"
      )

  test("ts app.ts imports and calls registerExtensions"):
    SpecFixtures.loadProfiled("url_shortener", "ts-express-postgres").map: profiled =>
      val app = Emit.emitProject(profiled).find(_.path == "src/app.ts").get.content
      assert(
        app.contains("from './extensions/index.js'"),
        s"app.ts must import registerExtensions; got:\n$app"
      )
      assert(
        app.contains("registerExtensions(app)"),
        s"app.ts must call registerExtensions(app); got:\n$app"
      )
