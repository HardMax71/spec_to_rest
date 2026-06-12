package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures
import specrest.profile.Chi
import specrest.profile.DatabaseId
import specrest.profile.LanguageId
import specrest.profile.TargetKey

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EmitGoTest extends CatsEffectSuite:

  private def goldenRoot(db: DatabaseId): Path =
    Paths
      .get("fixtures/golden/codegen", TargetKey(LanguageId.Go, Chi.id, db).segments*)
      .resolve("url_shortener")

  test("emitProject for go-chi-postgres produces a valid Go project layout for url_shortener"):
    SpecFixtures.loadProfiled("url_shortener", "go-chi-postgres").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val expected = List(
        "go.mod",
        "cmd/server/main.go",
        "internal/config/config.go",
        "internal/database/database.go",
        "internal/handlers/common.go",
        "internal/services/common.go",
        "internal/models/common.go",
        "internal/models/url_mapping.go",
        "internal/handlers/url_mappings.go",
        "internal/services/url_mapping.go",
        "migrations/001_initial_schema.up.sql",
        "migrations/001_initial_schema.down.sql",
        "Dockerfile",
        "docker-compose.yml",
        ".env.example",
        "Makefile",
        ".gitignore",
        ".dockerignore",
        "README.md",
        ".github/workflows/ci.yml",
        "tests/health_test.go",
        "openapi.yaml",
        ".spec-snapshot.json"
      )
      expected.foreach: p =>
        assert(files.contains(p), s"missing file $p; emitted: ${files.keys.toList.sorted}")

      val goMod = files("go.mod")
      assert(goMod.contains("module github.com/generated/url-shortener"), goMod)
      assert(goMod.contains("github.com/go-chi/chi/v5"), goMod)
      assert(goMod.contains("github.com/uptrace/bun v1.2.18"), goMod)
      assert(goMod.contains("github.com/uptrace/bun/driver/pgdriver"), goMod)
      assert(!goMod.contains("jackc/pgx"), goMod)

      val mainGo = files("cmd/server/main.go")
      assert(mainGo.contains("package main"), mainGo)
      assert(mainGo.contains("chi.NewRouter()"), mainGo)
      assert(mainGo.contains("r.Get(\"/{code}\""), mainGo)
      assert(mainGo.contains("r.Post(\"/shorten\""), mainGo)
      assert(mainGo.contains("r.Delete(\"/{code}\""), mainGo)

      val model = files("internal/models/url_mapping.go")
      assert(model.contains("type UrlMapping struct"), model)
      assert(model.contains("bun.BaseModel `bun:\"table:url_mappings"), model)
      // struct fields are gofmt-aligned (text/tabwriter column padding)
      assert(
        model.contains("ID            int64     `bun:\"id,pk,autoincrement\" json:\"id\"`"),
        model
      )
      assert(model.contains("Code          string    `bun:\"code,notnull\" json:\"code\"`"), model)
      assert(model.contains("type UrlMappingCreate struct"), model)
      assert(!model.contains("type ErrorResponse struct"), model)
      assert(model.contains("\"github.com/uptrace/bun\""), model)
      assert(model.contains("\"time\""), model)

      val modelCommon = files("internal/models/common.go")
      assert(modelCommon.contains("type ErrorResponse struct"), modelCommon)
      assert(modelCommon.contains("package models"), modelCommon)

      val handler = files("internal/handlers/url_mappings.go")
      assert(handler.contains("type UrlMappingHandler struct"), handler)
      assert(handler.contains("func (h *UrlMappingHandler) Shorten("), handler)
      assert(handler.contains("func (h *UrlMappingHandler) Resolve("), handler)
      assert(handler.contains("http.Redirect"), handler)

      val svc = files("internal/services/url_mapping.go")
      assert(svc.contains("type UrlMappingService struct"), svc)
      assert(svc.contains("db  *bun.DB"), svc)
      assert(svc.contains("s.db.NewSelect().Model(&items).Order(\"id\").Scan(ctx)"), svc)
      assert(
        svc.contains("s.db.NewDelete().Model((*models.UrlMapping)(nil))"),
        svc
      )
      assert(svc.contains("bun.Ident(\"code\")"), svc)
      // Resolve carries a spec side-effect (click_count++) the engine can't derive, so it is a
      // fail-loud stub — not a silent findFirst that drops the increment (matches fastapi).
      assert(svc.contains("errors.New(\"Resolve not implemented\")"), svc)

      val migUp = files("migrations/001_initial_schema.up.sql")
      assert(migUp.contains("CREATE TABLE url_mappings"), migUp)
      assert(migUp.contains("id BIGSERIAL NOT NULL"), migUp)
      assert(migUp.contains("CONSTRAINT pk_url_mappings PRIMARY KEY (id)"), migUp)
      assert(migUp.contains("code TEXT NOT NULL"), migUp)
      assert(migUp.contains("updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL"), migUp)

      val migDown = files("migrations/001_initial_schema.down.sql")
      assert(migDown.contains("DROP TABLE url_mappings"), migDown)

      val snapshot = files(".spec-snapshot.json")
      assert(snapshot.contains("\"schemaVersion\""), snapshot)
      assert(snapshot.contains("url_mappings"), snapshot)

  private val dialectCases = List(
    DatabaseId.Postgres -> "go-chi-postgres",
    DatabaseId.Sqlite   -> "go-chi-sqlite",
    DatabaseId.Mysql    -> "go-chi-mysql"
  )

  dialectCases.foreach: (db, target) =>
    test(s"emitProject for $target matches the checked-in url_shortener golden"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val files           = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
        val expected        = walkGolden(goldenRoot(db))
        val missingInOutput = expected.keySet.diff(files.keySet)
        val extraInOutput   = files.keySet.diff(expected.keySet)
        assert(expected.nonEmpty, s"no golden tree at ${goldenRoot(db)}")
        assert(
          missingInOutput.isEmpty,
          s"emitter dropped golden files: ${missingInOutput.toList.sorted.mkString(", ")}"
        )
        assert(
          extraInOutput.isEmpty,
          s"emitter produced files not in golden: ${extraInOutput.toList.sorted.mkString(", ")}"
        )
        expected.toList.sortBy(_._1).foreach: (rel, want) =>
          val got = files(rel)
          if got != want then
            fail(
              s"$rel diverges from golden\n--- expected ---\n$want\n--- got ---\n$got\n--- end ---"
            )

  private def walkGolden(root: Path): Map[String, String] =
    if !Files.isDirectory(root) then Map.empty
    else
      val stream = Files.walk(root)
      try
        import scala.jdk.CollectionConverters.*
        stream.iterator.asScala
          .filter(Files.isRegularFile(_))
          .map(p => root.relativize(p).toString.replace('\\', '/') -> Files.readString(p))
          .toMap
      finally stream.close()
