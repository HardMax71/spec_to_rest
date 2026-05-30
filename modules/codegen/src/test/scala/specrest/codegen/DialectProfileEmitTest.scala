package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.migration.CanonicalType
import specrest.codegen.migration.Dialect
import specrest.codegen.migration.Mysql
import specrest.codegen.migration.Postgres
import specrest.codegen.migration.Sqlite
import specrest.codegen.testutil.SpecFixtures
import specrest.ir.generated.SpecRestGenerated.IndexSpec

class DialectProfileEmitTest extends CatsEffectSuite:

  private def allTypes: List[CanonicalType] = List(
    CanonicalType.Text,
    CanonicalType.Varchar(50),
    CanonicalType.Int4,
    CanonicalType.Serial4,
    CanonicalType.Int8,
    CanonicalType.Serial8,
    CanonicalType.Float8,
    CanonicalType.Bool,
    CanonicalType.Timestamptz,
    CanonicalType.DateOnly,
    CanonicalType.Uuid,
    CanonicalType.Numeric(10, Some(2)),
    CanonicalType.Numeric(10, None),
    CanonicalType.Bytes,
    CanonicalType.Json
  )

  private def dialects: List[Dialect] = List(Postgres, Sqlite, Mysql)

  dialects.foreach: d =>
    test(s"${d.id}: every CanonicalType maps to a non-empty SaType"):
      allTypes.foreach: t =>
        assert(d.saType(t).expr.nonEmpty, s"${d.id} produced empty expr for $t")

  dialects.foreach: d =>
    test(s"${d.id}: partial-index diagnostic iff partial indexes unsupported"):
      val ix = IndexSpec(
        s"idx_${d.id}_x",
        List("status"),
        false,
        Some("active")
      )
      val hasDiag = d.partialIndex(ix).diagnostics.nonEmpty
      assertEquals(hasDiag, !d.caps.supportsPartialIndex)

  private def fileMap(target: String) =
    SpecFixtures
      .loadProfiled("url_shortener", target)
      .map(p => Emit.emitProject(p).map(f => f.path -> f.content).toMap)

  test("postgres profile keeps timezone-aware timestamps and asyncpg"):
    fileMap("python-fastapi-postgres").map: files =>
      val mig = files("alembic/versions/001_initial_schema.py")
      assert(mig.contains("sa.DateTime(timezone=True)"), mig)
      assert(files("pyproject.toml").contains("asyncpg"))
      assert(files(".env.example").contains("postgresql+asyncpg://"))
      assert(files("docker-compose.yml").contains("image: postgres:"))

  test("sqlite profile: no tz, no db service, FK pragma, aiosqlite"):
    fileMap("python-fastapi-sqlite").map: files =>
      val mig = files("alembic/versions/001_initial_schema.py")
      assert(!mig.contains("timezone=True"), mig)
      assert(!mig.contains("from sqlalchemy.dialects import postgresql"), mig)
      assert(mig.contains("sa.Text()"), mig)
      // SQLite autoincrements only INTEGER PRIMARY KEY; a serial PK must not be BIGINT.
      assert(!mig.contains("sa.BigInteger()"), mig)
      assert(
        mig.contains(
          """sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True"""
        ),
        mig
      )
      assert(files(".env.example").contains("sqlite+aiosqlite://"))
      assert(files("pyproject.toml").contains("aiosqlite"))
      assert(!files("pyproject.toml").contains("asyncpg"))
      assert(!files("docker-compose.yml").contains("image: postgres:"))
      assert(!files("docker-compose.yml").contains("image: mysql:"))
      assert(files("app/database.py").contains("PRAGMA foreign_keys=ON"))
      assert(files("alembic/env.py").contains("render_as_batch=True"))

  test("mysql profile: VARCHAR(255) strings, mysql service, aiomysql"):
    fileMap("python-fastapi-mysql").map: files =>
      val mig = files("alembic/versions/001_initial_schema.py")
      assert(!mig.contains("timezone=True"), mig)
      assert(!mig.contains("from sqlalchemy.dialects import postgresql"), mig)
      assert(mig.contains("sa.String(length=255)"), mig)
      assert(files(".env.example").contains("mysql+aiomysql://"))
      assert(files("pyproject.toml").contains("aiomysql"))
      assert(files("docker-compose.yml").contains("image: mysql:8.4"))

  private def fileMapOf(spec: String, target: String) =
    SpecFixtures
      .loadProfiled(spec, target)
      .map(p => Emit.emitProject(p).map(f => f.path -> f.content).toMap)

  // todo_list has a Set[String] `tags` column whose canonical default is the
  // Postgres-only `'[]'::jsonb`. It must not leak into sqlite/mysql migrations.
  test("collection default: Postgres ::jsonb cast does not leak into sqlite/mysql"):
    fileMapOf("todo_list", "python-fastapi-postgres").map: files =>
      val mig = files("alembic/versions/001_initial_schema.py")
      assert(mig.contains("""server_default=sa.text("'[]'::jsonb")"""), mig)

  test("python-fastapi-sqlite: tags default is bare '[]' (no ::jsonb)"):
    fileMapOf("todo_list", "python-fastapi-sqlite").map: files =>
      val mig = files("alembic/versions/001_initial_schema.py")
      assert(!mig.contains("::jsonb"), mig)
      assert(mig.contains("""server_default=sa.text("'[]'")"""), mig)

  test("python-fastapi-mysql: tags default is (JSON_ARRAY()) (no ::jsonb)"):
    fileMapOf("todo_list", "python-fastapi-mysql").map: files =>
      val mig = files("alembic/versions/001_initial_schema.py")
      assert(!mig.contains("::jsonb"), mig)
      assert(mig.contains("server_default=sa.text('(JSON_ARRAY())')"), mig)

  test("go-chi sqlite/mysql + ts-express sqlite: no ::jsonb in raw migration"):
    for
      goSqlite <- fileMapOf("todo_list", "go-chi-sqlite")
      goMysql  <- fileMapOf("todo_list", "go-chi-mysql")
      tsSqlite <- fileMapOf("todo_list", "ts-express-sqlite")
    yield
      val goUp = goSqlite("migrations/001_initial_schema.up.sql")
      assert(!goUp.contains("::jsonb"), goUp)
      assert(goUp.contains("DEFAULT '[]'"), goUp)
      val goUpMy = goMysql("migrations/001_initial_schema.up.sql")
      assert(!goUpMy.contains("::jsonb"), goUpMy)
      assert(goUpMy.contains("DEFAULT (JSON_ARRAY())"), goUpMy)
      val tsSql = tsSqlite("prisma/migrations/001_initial_schema/migration.sql")
      assert(!tsSql.contains("::jsonb"), tsSql)
      assert(tsSql.contains("DEFAULT '[]'"), tsSql)

  // P: todo_list `id: Int where value > 0` is an application-supplied (non-serial) integer PK,
  // widened to 64-bit (sa.BigInteger) per the spec's unbounded integer semantics. Alembic pins
  // it to `autoincrement=False`, so SQLAlchemy does NOT make it MySQL AUTO_INCREMENT — the
  // `id > 0` CHECK is therefore valid and retained on *every* dialect, matching the raw-SQL
  // renderer. (The MySQL-3818 drop only applies to synthesized serial PKs.)
  test("explicit integer PK CHECK is retained across all dialects (no MySQL-3818 drop)"):
    for
      pg     <- fileMapOf("todo_list", "python-fastapi-postgres")
      sqlite <- fileMapOf("todo_list", "python-fastapi-sqlite")
      mysql  <- fileMapOf("todo_list", "python-fastapi-mysql")
    yield
      val pgMig = pg("alembic/versions/001_initial_schema.py")
      val sqMig = sqlite("alembic/versions/001_initial_schema.py")
      val myMig = mysql("alembic/versions/001_initial_schema.py")
      for mig <- List(pgMig, sqMig, myMig) do
        assert(mig.contains("""sa.CheckConstraint('id > 0', name="ck_todos_0")"""), mig)
        assert(
          mig.contains(
            """sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=False"""
          ),
          mig
        )

  // Raw go-chi/ts SQL uses a bare `id INT` PK (no AUTO_INCREMENT keyword), so the CHECK
  // is valid on MySQL there and must be retained.
  test("go-chi mysql raw SQL keeps CHECK (id > 0): bare INT PK is not auto-increment"):
    fileMapOf("todo_list", "go-chi-mysql").map: files =>
      val up = files("migrations/001_initial_schema.up.sql")
      assert(up.contains("CHECK (id > 0)"), up)

  // P: the auto-increment decision is one canonical predicate honoured identically by all three
  // renderers. todo_list (single entity, explicit `id: Int`) is app-supplied -> no autoincrement
  // anywhere, and 64-bit-widened (BigInteger / BIGINT / Prisma BigInt) so the generated client
  // matches its own migration; url_shortener (synthesized BIGSERIAL) is DB-generated everywhere.
  test("explicit vs synthesized PK auto-increment is consistent across raw/alembic/prisma"):
    for
      tdAl <- fileMapOf("todo_list", "python-fastapi-postgres")
      tdGo <- fileMapOf("todo_list", "go-chi-postgres")
      tdTs <- fileMapOf("todo_list", "ts-express-postgres")
      usAl <- fileMapOf("url_shortener", "python-fastapi-postgres")
      usTs <- fileMapOf("url_shortener", "ts-express-postgres")
    yield
      val tdAlMig  = tdAl("alembic/versions/001_initial_schema.py")
      val tdGoSql  = tdGo("migrations/001_initial_schema.up.sql")
      val tdPrisma = tdTs("prisma/schema.prisma")
      // explicit Todo.id (Int where value > 0): application-supplied, 64-bit on every renderer
      assert(
        tdAlMig.contains(
          """sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=False"""
        ),
        tdAlMig
      )
      assert(tdGoSql.contains("id BIGINT NOT NULL") && !tdGoSql.contains("SERIAL"), tdGoSql)
      assert(tdPrisma.contains("id BigInt @id") && !tdPrisma.contains("autoincrement"), tdPrisma)
      // synthesized url_shortener.UrlMapping.id (BIGSERIAL): DB-generated everywhere (unchanged)
      val usAlMig  = usAl("alembic/versions/001_initial_schema.py")
      val usPrisma = usTs("prisma/schema.prisma")
      assert(usAlMig.contains("autoincrement=True"), usAlMig)
      assert(usPrisma.contains("@id @default(autoincrement())"), usPrisma)

  // The schema.prisma template owns nullability ({{#if nullable}}?{{/if}}); a second `?`
  // from prismaAttrs produced `String? ?` -> Prisma validation error P1012.
  test("ts-express: nullable Prisma fields render a single optional marker"):
    fileMapOf("todo_list", "ts-express-postgres").map: files =>
      val schema = files("prisma/schema.prisma")
      assert(!schema.contains("? ?"), schema)
      assert(schema.contains("description String? @db.Text"), schema)
      assert(schema.contains("""completedAt DateTime? @map("completed_at")"""), schema)

  // chi.URLParam returns a string; an integer PK path param must be parsed via strconv
  // before the service call. A string-keyed entity must NOT import strconv (unused import
  // is a Go build failure).
  test("go-chi: int path param parsed via strconv; string key untouched"):
    for
      todo <- fileMapOf("todo_list", "go-chi-postgres")
      url  <- fileMapOf("url_shortener", "go-chi-postgres")
    yield
      def handlers(files: Map[String, String]): String =
        files.collect {
          case (p, c) if p.startsWith("internal/handlers/") && p.endsWith(".go") => c
        }.mkString
      val todoHandler = handlers(todo)
      val urlHandler  = handlers(url)
      assert(todoHandler.contains("strconv.ParseInt("), todoHandler)
      assert(todoHandler.contains("\"strconv\""), todoHandler)
      assert(!urlHandler.contains("strconv"), urlHandler)
      assert(urlHandler.contains("""chi.URLParam(r, "code")"""), urlHandler)

  // express req.params is string; an integer PK route param must be coerced via Number()
  // before the service call (service signature is `number`). A string key is untouched.
  test("ts-express: int route param coerced via Number(); string key untouched"):
    for
      todo <- fileMapOf("todo_list", "ts-express-postgres")
      url  <- fileMapOf("url_shortener", "ts-express-postgres")
    yield
      def routes(files: Map[String, String]): String =
        files.collect {
          case (p, c) if p.startsWith("src/routes/") && p.endsWith(".ts") => c
        }.mkString
      val todoRoutes = routes(todo)
      val urlRoutes  = routes(url)
      assert(todoRoutes.contains("const id = Number(req.params['id'])"), todoRoutes)
      assert(todoRoutes.contains("if (!Number.isFinite(id))"), todoRoutes)
      assert(!urlRoutes.contains("Number(req.params"), urlRoutes)
      assert(urlRoutes.contains("req.params['code'] ?? ''"), urlRoutes)

  // Prisma types a Json column as JsonValue and a synthesized BIGSERIAL PK as `bigint`; both
  // diverge from the number-typed read DTO, so the service must cast the row. (The zero-churn
  // guard for genuinely plain entities is the byte-exact EmitTsTest golden itself.)
  test("ts-express: service casts Json-bearing and BigInt-PK rows"):
    for
      todo <- fileMapOf("todo_list", "ts-express-postgres")
      url  <- fileMapOf("url_shortener", "ts-express-postgres")
    yield
      def services(files: Map[String, String]): String =
        files.collect {
          case (p, c) if p.startsWith("src/services/") && p.endsWith(".ts") => c
        }.mkString
      val todoSvc = services(todo) // Todo.tags: Set -> Json column (getTodo read cast)
      val urlSvc  = services(url)  // UrlMapping: synthesized BIGSERIAL PK -> bigint (listAll cast)
      assert(todoSvc.contains("as unknown as Promise<TodoRead | null>"), todoSvc)
      assert(urlSvc.contains("as unknown as Promise<UrlMappingRead[]>"), urlSvc)

  // R: a list op that declares filter inputs cannot be the plain list-all template (which
  // ignores all inputs and would return every row with a 200). It must be the fail-loud stub,
  // consistent with the other synthesis ops. A genuine input-less list-all is unaffected.
  test("filtered list op is a fail-loud stub, not a silent unfiltered list-all"):
    for
      tsTodo <- fileMapOf("todo_list", "ts-express-postgres")
      goTodo <- fileMapOf("todo_list", "go-chi-postgres")
      faTodo <- fileMapOf("todo_list", "python-fastapi-postgres")
      tsUrl  <- fileMapOf("url_shortener", "ts-express-postgres")
    yield
      val tsSvc = tsTodo("src/services/todo.ts")
      val goSvc = goTodo("internal/services/todo.go")
      val faSvc = faTodo("app/services/todo.py")
      // ListTodos (status_filter/priority_filter/tag_filter) -> stub, never an unfiltered list
      assert(tsSvc.contains("throw new Error('listTodos not implemented')"), tsSvc)
      assert(!tsSvc.contains("listTodos = async (): Promise<TodoRead[]>"), tsSvc)
      assert(goSvc.contains("\"ListTodos not implemented\""), goSvc)
      assert(faSvc.contains("def list_todos") && faSvc.contains("NotImplementedError"), faSvc)
      // url_shortener ListAll has no filter inputs -> genuine list-all, still emitted
      val tsUrlSvc = tsUrl("src/services/urlMapping.ts")
      assert(tsUrlSvc.contains("prisma.urlMapping.findMany("), tsUrlSvc)

  // S: url_shortener.Resolve has a state-mutating side-effect (click_count++) the engine can't
  // derive, so all three targets must fail loud — not silently look up and redirect while
  // dropping the increment. fastapi already stubbed it; go/ts must now match.
  test("redirect op with a side-effect is a fail-loud stub across all three targets"):
    for
      ts <- fileMapOf("url_shortener", "ts-express-postgres")
      go <- fileMapOf("url_shortener", "go-chi-postgres")
      fa <- fileMapOf("url_shortener", "python-fastapi-postgres")
    yield
      val tsSvc = ts("src/services/urlMapping.ts")
      val goSvc = go("internal/services/url_mapping.go")
      val faSvc = fa("app/services/url_mapping.py")
      assert(tsSvc.contains("throw new Error('resolve not implemented')"), tsSvc)
      assert(!tsSvc.contains("prisma.urlMapping.findFirst"), tsSvc)
      assert(goSvc.contains("errors.New(\"Resolve not implemented\")"), goSvc)
      assert(!goSvc.contains("database/sql"), goSvc)
      assert(faSvc.contains("def resolve") && faSvc.contains("NotImplementedError"), faSvc)
      // the 302 redirect ROUTE is preserved in all three (spec-faithful API surface)
      assert(go("internal/handlers/url_mappings.go").contains("http.Redirect"), "go route")
      assert(ts("src/routes/urlMappings.ts").contains("res.redirect(302"), "ts route")

  // Q: every PK is 64-bit in the migration — synthesized as BIGSERIAL, explicit `id: Int`
  // widened to BIGINT. ts-express must declare both as Prisma `BigInt` so the generated client
  // matches its own migration.sql; the synthesized one alone is DB-generated
  // (`@default(autoincrement())`), the explicit one is application-supplied (bare `@id`).
  test("ts-express PK is Prisma BigInt; synthesized auto-increments, explicit does not"):
    for
      url  <- fileMapOf("url_shortener", "ts-express-postgres")
      todo <- fileMapOf("todo_list", "ts-express-postgres")
    yield
      val urlPrisma  = url("prisma/schema.prisma")
      val urlMig     = url("prisma/migrations/001_initial_schema/migration.sql")
      val todoPrisma = todo("prisma/schema.prisma")
      assert(urlMig.contains("id BIGSERIAL"), urlMig)
      assert(urlPrisma.contains("id BigInt @id @default(autoincrement()) @db.BigInt"), urlPrisma)
      assert(
        todoPrisma.contains("id BigInt @id") && !todoPrisma.contains("id BigInt @id @default"),
        todoPrisma
      )

  // F: ErrorResponse is a shared model type. Emitting it per-entity made any multi-entity
  // Go project fail to build ("ErrorResponse redeclared"). It must live once in common.go.
  test("go-chi: ErrorResponse declared once in common.go, not per entity"):
    fileMapOf("ecommerce", "go-chi-postgres").map: files =>
      val common = files("internal/models/common.go")
      assert(common.contains("type ErrorResponse struct"), common)
      val entityModels = files.collect {
        case (p, c)
            if p.startsWith("internal/models/") && p.endsWith(".go")
              && p != "internal/models/common.go" =>
          c
      }
      assert(entityModels.size >= 4, files.keys.toList.sorted.toString)
      entityModels.foreach(m => assert(!m.contains("type ErrorResponse struct"), m))

  // G: type aliases over Int (OrderId/CustomerId = Int) used as params must resolve to a
  // numeric type, not the `string` fallback. `orderId` resolves to the Order PK, which is a
  // 64-bit-widened BigInt, so the ts service coerces it to `bigint` for the Prisma `where`
  // (the #273 bridge); Go wants int64.
  test("alias-over-Int params resolve to numeric in ts + go"):
    for
      ts <- fileMapOf("ecommerce", "ts-express-postgres")
      go <- fileMapOf("ecommerce", "go-chi-postgres")
    yield
      val orderSvc = ts("src/services/order.ts")
      assert(orderSvc.contains("orderId: bigint"), orderSvc)
      assert(!orderSvc.contains("orderId: string"), orderSvc)
      val orderHandler = go.collect {
        case (p, c) if p.startsWith("internal/handlers/order") && p.endsWith(".go") => c
      }.mkString
      assert(orderHandler.contains("strconv.ParseInt"), orderHandler)
      assert(!orderHandler.contains("orderId string"), orderHandler)

  // H: a sub-entity with no operations must not emit a service file that imports context /
  // models (unused imports break `go build`).
  test("go-chi: operation-less entity service omits unused imports"):
    fileMapOf("ecommerce", "go-chi-postgres").map: files =>
      val svc = files("internal/services/inventory_entry.go")
      assert(!svc.contains("\"context\""), svc)
      assert(!svc.contains("internal/models\""), svc)
      assert(svc.contains("type InventoryEntryService struct"), svc)

  // I: the raw-SQL migration trigger must be dialect-aware. Postgres keeps PL/pgSQL
  // (byte-identical goldens); sqlite/mysql get portable per-event CREATE TRIGGER ...
  // BEGIN/END (no stored function — `CREATE OR REPLACE FUNCTION` is invalid there).
  test("raw-SQL trigger is dialect-aware (plpgsql for pg; per-event for sqlite/mysql)"):
    for
      pg     <- fileMapOf("ecommerce", "go-chi-postgres")
      sqlite <- fileMapOf("ecommerce", "go-chi-sqlite")
      mysql  <- fileMapOf("ecommerce", "go-chi-mysql")
    yield
      val pgUp = pg("migrations/001_initial_schema.up.sql")
      assert(pgUp.contains("CREATE OR REPLACE FUNCTION recalc_order_subtotal()"), pgUp)
      assert(pgUp.contains("LANGUAGE plpgsql"), pgUp)
      for db <- List(sqlite, mysql) do
        val up = db("migrations/001_initial_schema.up.sql")
        assert(!up.contains("CREATE OR REPLACE FUNCTION"), up)
        assert(!up.contains("plpgsql"), up)
        assert(!up.contains("EXECUTE FUNCTION"), up)
        assert(
          up.contains(
            "CREATE TRIGGER trg_recalc_order_subtotal_ins AFTER INSERT ON line_items " +
              "FOR EACH ROW BEGIN"
          ),
          up
        )
        val down = db("migrations/001_initial_schema.down.sql")
        assert(down.contains("DROP TRIGGER IF EXISTS trg_recalc_order_subtotal_del;"), down)
        assert(!down.contains("DROP FUNCTION"), down)

  // K: MySQL has no partial indexes. pg/sqlite keep the `WHERE` predicate; MySQL degrades
  // to a plain (full) index instead of emitting an invalid `WHERE` clause (error 1064).
  test("raw-SQL partial index: WHERE kept for pg/sqlite, dropped for mysql"):
    for
      pg     <- fileMapOf("ecommerce", "go-chi-postgres")
      sqlite <- fileMapOf("ecommerce", "go-chi-sqlite")
      mysql  <- fileMapOf("ecommerce", "go-chi-mysql")
    yield
      val pgUp = pg("migrations/001_initial_schema.up.sql")
      val sqUp = sqlite("migrations/001_initial_schema.up.sql")
      val myUp = mysql("migrations/001_initial_schema.up.sql")
      assert(pgUp.contains("ON products (active) WHERE active = true;"), pgUp)
      assert(sqUp.contains("ON products (active) WHERE active = true;"), sqUp)
      assert(myUp.contains("CREATE INDEX idx_products_active_partial ON products (active);"), myUp)
      assert(!myUp.contains("WHERE active = true"), myUp)

  // L: `DROP TABLE` already cascades indexes on every dialect; emitting an explicit
  // op.drop_index first breaks MySQL when the index backs a foreign key (error 1553).
  test("alembic downgrade drops tables only, no explicit op.drop_index"):
    for
      pg    <- fileMapOf("ecommerce", "python-fastapi-postgres")
      mysql <- fileMapOf("ecommerce", "python-fastapi-mysql")
    yield
      for files <- List(pg, mysql) do
        val mig = files("alembic/versions/001_initial_schema.py")
        assert(!mig.contains("op.drop_index("), mig)
        assert(mig.contains("op.drop_table("), mig)

  // N: a refined type alias (Email/Token/PasswordHash) must carry its `where` predicate as a
  // column CHECK — previously dropped entirely (no DB enforcement). Length checks are portable;
  // M makes the regex dialect-aware: Postgres `~`, MySQL `REGEXP`, SQLite drops it.
  test("type-alias refinements -> dialect-aware CHECKs (auth_service)"):
    for
      pg     <- fileMapOf("auth_service", "go-chi-postgres")
      sqlite <- fileMapOf("auth_service", "go-chi-sqlite")
      mysql  <- fileMapOf("auth_service", "go-chi-mysql")
    yield
      val pgUp = pg("migrations/001_initial_schema.up.sql")
      val sqUp = sqlite("migrations/001_initial_schema.up.sql")
      val myUp = mysql("migrations/001_initial_schema.up.sql")
      assert(pgUp.contains("""CHECK (email ~ '^[^@]+@[^@]+\.[^@]+$')"""), pgUp)
      assert(myUp.contains("""CHECK (email REGEXP '^[^@]+@[^@]+\.[^@]+$')"""), myUp)
      assert(!sqUp.contains("email ~"), sqUp)
      assert(!sqUp.contains("email REGEXP"), sqUp)
      // length refinement (Token=128 / PasswordHash=64) is portable -> present in every dialect
      for up <- List(pgUp, sqUp, myUp) do
        assert(up.contains("CHECK (length(password_hash) = 64)"), up)
        assert(up.contains("CHECK (length(access_token) = 128)"), up)

  // N: url_shortener's ShortCode alias (len + regex) previously produced no CHECK at all.
  test("url_shortener ShortCode alias now yields length + regex CHECKs"):
    for
      pg     <- fileMapOf("url_shortener", "go-chi-postgres")
      sqlite <- fileMapOf("url_shortener", "go-chi-sqlite")
    yield
      val pgUp = pg("migrations/001_initial_schema.up.sql")
      val sqUp = sqlite("migrations/001_initial_schema.up.sql")
      assert(pgUp.contains("CHECK (length(code) >= 6)"), pgUp)
      assert(pgUp.contains("""CHECK (code ~ '^[a-zA-Z0-9]+$')"""), pgUp)
      assert(sqUp.contains("CHECK (length(code) >= 6)"), sqUp)
      assert(!sqUp.contains("code ~"), sqUp)

  // O: `entity Child extends Base` was not flattened — Child's table dropped every inherited
  // column and its `where` refinement, and (because the parent declared `id`) fell through to a
  // synthesized BIGSERIAL id with no `id > 0` CHECK. Inheritance is now resolved in the IR, so
  // Child inherits Base's typed `id` (+ `id > 0`) and declared `created_at` across all dialects.
  test("entity `extends` flattens parent fields + refinements into the child table"):
    for
      pg     <- fileMapOf("edge_cases", "go-chi-postgres")
      sqlite <- fileMapOf("edge_cases", "go-chi-sqlite")
      mysql  <- fileMapOf("edge_cases", "go-chi-mysql")
    yield
      def children(files: Map[String, String]): String =
        val up = files("migrations/001_initial_schema.up.sql")
        up.linesIterator.dropWhile(!_.contains("CREATE TABLE children"))
          .takeWhile(!_.trim.startsWith(");"))
          .mkString("\n")
      for ch <- List(children(pg), children(sqlite), children(mysql)) do
        assert(ch.contains("CONSTRAINT ck_children_0 CHECK (id > 0)"), ch)
        assert(ch.contains("CONSTRAINT pk_children PRIMARY KEY (id)"), ch)
        assert(ch.contains("created_at"), ch)
        assert(!ch.contains("BIGSERIAL"), ch)
