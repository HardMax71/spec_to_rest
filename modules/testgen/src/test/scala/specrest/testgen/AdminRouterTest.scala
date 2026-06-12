package specrest.testgen

import munit.CatsEffectSuite
import specrest.codegen.python.AdminRouter
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate

class AdminRouterTest extends CatsEffectSuite:

  private def loadProfiled(path: String) =
    val src = scala.io.Source.fromFile(path).getLines.mkString("\n")
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => Annotate.buildProfiledService(ir, "python-fastapi-postgres")
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("router is bearer-guarded via the router-level require_admin dependency"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(src.contains("from app.security import require_admin"))
      assert(src.contains("dependencies=[Depends(require_admin)]"))
      assert(src.contains("prefix=\"/admin\""))
      assert(!src.contains("ENABLE_TEST_ADMIN"))

  test("router uses async session pattern matching codegen output"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(src.contains("from sqlalchemy.ext.asyncio import AsyncSession"))
      assert(src.contains("from app.database import get_session"))
      assert(src.contains("session: AsyncSession = Depends(get_session)"))

  test("reset endpoint emits one delete per entity"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(src.contains("await session.execute(delete(UrlMapping))"))
      assert(src.contains("@router.post(\"/reset\", status_code=204)"))

  test("state endpoint projects relations to dicts keyed by spec field names"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      // url_shortener: store: ShortCode -> lone LongURL → {row.code: row.url}
      assert(src.contains("\"store\": {row.code: row.url for row in rows}"), s"src=$src")
      // metadata: ShortCode -> lone UrlMapping → full row dict
      assert(
        src.contains("\"metadata\": {row.code: _row_to_dict(row) for row in rows}"),
        s"src=$src"
      )
      // base_url: scalar — None placeholder
      assert(src.contains("\"base_url\": None"), s"src=$src")

  test("safe_counter: reset zeroes the scalar state row, state projects count (#407)"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      // Reset restores the seeded defaults for the backed scalar
      assert(src.contains("sa_update(ServiceState).values(count=0)"), s"src=$src")
      // count is read from the singleton service_state row
      assert(src.contains("\"count\": state_row.count"), s"src=$src")
      assert(src.contains("from app.models.service_state import ServiceState"), s"src=$src")

  test("entity model imports use snake_case file names matching codegen"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(src.contains("from app.models.url_mapping import UrlMapping"))

  test("_row_to_dict helper is generated and handles datetime"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(src.contains("def _row_to_dict(row)"))
      assert(src.contains("isinstance(v, (datetime, date))"))
      assert(src.contains(".isoformat()"))

  test("conftest enabled-check fails on 5xx, only skips on other non-204"):
    val cf = Templates.conftest
    assert(cf.contains("if r.status_code >= 500"))
    assert(cf.contains("pytest.fail"))
    assert(cf.contains("atexit.register(client.close)"))

  // ---------- M5.9: per-entity seed endpoint emission ----------

  test("M5.9: todo_list emits POST /admin/seed/todo with DateTime coercion"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(src.contains("@router.post(\"/seed/todo\""), s"src=$src")
      assert(src.contains("async def seed_todo("), s"src=$src")
      assert(src.contains("Todo(**payload)"), s"src=$src")
      assert(src.contains("def _parse_iso(value)"), s"src=$src")
      assert(src.contains("payload[\"created_at\"] = _parse_iso"), s"src=$src")
      assert(src.contains("payload[\"updated_at\"] = _parse_iso"), s"src=$src")
      assert(src.contains("payload[\"completed_at\"] = _parse_iso"), s"src=$src")
      assert(src.contains("return {\"id\": obj.id}"), s"src=$src")

  test("M5.9: seed endpoint inherits the router-level require_admin guard"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(src.contains("async def seed_todo"), s"src=$src")
      assert(src.contains("dependencies=[Depends(require_admin)]"), s"src=$src")
      assert(!src.contains("_check_enabled"), "per-request checks were replaced by the dependency")

  test("M5.9: url_shortener (no transitions) emits NO seed endpoint"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(!src.contains("/seed/"), s"unexpected seed endpoint; src=$src")

  test("M5.9: safe_counter (no entities, no transitions) emits NO seed endpoint"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(!src.contains("/seed/"), s"unexpected seed endpoint; src=$src")

  // ---------- M5.9 PR #154 review round 2 ----------

  test("M5.9 fix G: aliased DateTime field gets _parse_iso() coercion"):
    val spec =
      """|service Demo {
         |  type CreatedAt = DateTime
         |  entity Foo {
         |    id: Int
         |    status: Status
         |    at: CreatedAt
         |    opt_at: Option[CreatedAt]
         |  }
         |  enum Status { ACTIVE, ARCHIVED }
         |  state {
         |    foos: Int -> lone Foo
         |  }
         |  transition FooLifecycle {
         |    entity: Foo
         |    field: status
         |    ACTIVE -> ARCHIVED via Archive
         |  }
         |  operation Archive {
         |    input: id: Int
         |    requires: id in foos
         |    ensures: foos'[id].status = ARCHIVED
         |  }
         |  conventions {
         |    Archive.http_method = "POST"
         |    Archive.http_path = "/foos/{id}/archive"
         |  }
         |}
         |""".stripMargin
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) =>
            val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
            val src      = AdminRouter.emit(profiled)
            assert(
              src.contains("payload[\"at\"] = _parse_iso(payload[\"at\"])"),
              s"alias of DateTime needs _parse_iso coercion; src=$src"
            )
            assert(
              src.contains("payload[\"opt_at\"] = _parse_iso(payload[\"opt_at\"])"),
              s"Option[alias of DateTime] needs _parse_iso coercion; src=$src"
            )
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")
