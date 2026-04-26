package specrest.testgen

import munit.CatsEffectSuite
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

  test("router is gated on ENABLE_TEST_ADMIN env var"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      assert(src.contains("ENABLE_TEST_ADMIN"))
      assert(src.contains("status_code=403"))
      assert(src.contains("prefix=\"/__test_admin__\""))

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

  test("safe_counter (no entities): reset is no-op, state returns empty"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val src = AdminRouter.emit(profiled)
      // Reset has nothing to delete
      assert(src.contains("    pass"))
      // count is scalar state, no entity backs it
      assert(src.contains("\"count\": None"))

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
