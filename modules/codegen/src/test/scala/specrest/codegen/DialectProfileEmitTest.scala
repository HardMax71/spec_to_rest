package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.migration.CanonicalType
import specrest.codegen.migration.Dialect
import specrest.codegen.migration.Mysql
import specrest.codegen.migration.Postgres
import specrest.codegen.migration.Sqlite
import specrest.codegen.testutil.SpecFixtures

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
      val ix = specrest.convention.IndexSpec(
        s"idx_${d.id}_x",
        List("status"),
        unique = false,
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
