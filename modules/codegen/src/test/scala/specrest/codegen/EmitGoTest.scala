package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures
import specrest.profile.Annotate
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
      // list endpoint is paginated: handler reads limit/offset via the shared helper
      assert(handler.contains("limit, offset := listPagination(r)"), handler)
      assert(handler.contains("h.svc.ListAll(r.Context(), limit, offset)"), handler)
      assert(files("internal/handlers/common.go").contains("func listPagination("), files)
      assert(files("internal/handlers/common.go").contains("pageLimitDefault  = 50"), files)

      val svc = files("internal/services/url_mapping.go")
      assert(svc.contains("type UrlMappingService struct"), svc)
      assert(svc.contains("db  *bun.DB"), svc)
      assert(
        svc.contains(
          "s.db.NewSelect().Model(&items).Order(\"id\").Limit(limit).Offset(offset).Scan(ctx)"
        ),
        svc
      )
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

  test("kernel-routed go service hydrates and persists per-op relation scopes"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val profiled = Annotate.attachDafnyMethods(
        Annotate.buildProfiledService(ir, "go-chi-sqlite"),
        Map("Shorten" -> "Shorten", "Resolve" -> "Resolve")
      )
      val kernel = DafnyKernel(
        packagePath = DafnyKernel.GoDefaultPackagePath,
        files = Map("kernel.go" -> "package kernel\n"),
        bindings =
          List(OperationBinding("Shorten", "Shorten"), OperationBinding("Resolve", "Resolve"))
      )
      val files = Emit
        .emitProject(profiled, EmitOptions(dafnyKernel = Some(kernel)))
        .map(f => f.path -> f.content)
        .toMap
      val service = files.getOrElse(
        "internal/services/url_mapping.go",
        fail("no url_mapping service emitted")
      )
      val bridge = files.getOrElse(
        "internal/services/state_bridge.go",
        fail("no state bridge emitted")
      )
      assert(
        service.contains("state, err := hydrateState(ctx, tx, scope)"),
        s"kernel ops must hydrate state with the op's scope — got:\n$service"
      )
      assert(
        service.contains("if err := persistState(ctx, tx, state, scope); err != nil {"),
        s"kernel ops must persist the mutated state under the same scope — got:\n$service"
      )
      // Resolve reads both relations at the path param's key; Shorten's
      // candidate-freshness check forces both whole.
      assert(
        service.contains(
          "\t\tscope := map[string]hydrationSel{\n" +
            "\t\t\t\"metadata\": {keys: []any{code}},\n" +
            "\t\t\t\"store\":    {keys: []any{code}},\n" +
            "\t\t}"
        ),
        s"resolve should hydrate both relations keyed by code — got:\n$service"
      )
      assert(
        service.contains(
          "\t\tscope := map[string]hydrationSel{\n" +
            "\t\t\t\"metadata\": {full: true},\n" +
            "\t\t\t\"store\":    {full: true},\n" +
            "\t\t}"
        ),
        s"shorten should hydrate both relations whole — got:\n$service"
      )
      assert(
        bridge.contains(
          "func hydrateState(ctx context.Context, db bun.IDB, scope map[string]hydrationSel) (*dafnykernel.ServiceState, error) {"
        ),
        s"hydrateState must take the scope map — got:\n$bridge"
      )
      assert(
        bridge.contains(
          "func persistState(ctx context.Context, db bun.IDB, st *dafnykernel.ServiceState, scope map[string]hydrationSel) error {"
        ),
        s"persistState must take the scope map — got:\n$bridge"
      )
      // A keyed load confines the select, and the persist delete scan only
      // sees rows fetched through the same scoped select.
      assert(
        bridge.contains("} else if len(sel.keys) > 0 {"),
        s"bridge must load keyed scopes through an IN filter — got:\n$bridge"
      )
      assert(
        bridge.contains("bun.In(sel.keys)"),
        s"bridge must bind scope keys into the query — got:\n$bridge"
      )

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
