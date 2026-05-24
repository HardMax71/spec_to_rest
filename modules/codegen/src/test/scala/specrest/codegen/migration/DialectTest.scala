package specrest.codegen.migration

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class DialectTest extends CatsEffectSuite:

  test("forDatabase resolves the three registered dialects"):
    assertEquals(Dialect.forDatabase("postgres").id, "postgres")
    assertEquals(Dialect.forDatabase("sqlite").id, "sqlite")
    assertEquals(Dialect.forDatabase("mysql").id, "mysql")

  test("forDatabase rejects an unknown database"):
    interceptMessage[RuntimeException](
      "No SQL dialect registered for database 'oracle' (known: postgres, sqlite, mysql)"
    ):
      Dialect.forDatabase("oracle")

  private def saCases: List[(Dialect, CanonicalType, String)] = List(
    (Postgres, CanonicalType.Timestamptz, "sa.DateTime(timezone=True)"),
    (Sqlite, CanonicalType.Timestamptz, "sa.DateTime()"),
    (Mysql, CanonicalType.Timestamptz, "sa.DateTime()"),
    (Postgres, CanonicalType.Json, "postgresql.JSONB()"),
    (Sqlite, CanonicalType.Json, "sa.JSON()"),
    (Mysql, CanonicalType.Json, "sa.JSON()"),
    (Postgres, CanonicalType.Text, "sa.Text()"),
    (Sqlite, CanonicalType.Text, "sa.Text()"),
    (Mysql, CanonicalType.Text, "sa.String(length=255)")
  )

  saCases.foreach: (dialect, t, expected) =>
    test(s"${dialect.id} saType($t) = $expected"):
      assertEquals(dialect.saType(t).expr, expected)

  test("only Postgres JSON carries a dialect import module"):
    assertEquals(
      Postgres.saType(CanonicalType.Json).importModule,
      Some("sqlalchemy.dialects.postgresql")
    )
    assertEquals(Sqlite.saType(CanonicalType.Json).importModule, None)
    assertEquals(Mysql.saType(CanonicalType.Json).importModule, None)

  private def partialIx =
    IndexSpec("idx_orders_active", List("status"), false, Some("active"))

  test("Postgres partial index uses postgresql_where, no diagnostics"):
    val e = Postgres.partialIndex(partialIx)
    assertEquals(e.value, ", postgresql_where=sa.text('active')")
    assertEquals(e.diagnostics, Nil)

  test("SQLite partial index uses sqlite_where, no diagnostics"):
    val e = Sqlite.partialIndex(partialIx)
    assertEquals(e.value, ", sqlite_where=sa.text('active')")
    assertEquals(e.diagnostics, Nil)

  test("MySQL drops the partial filter and emits one warning diagnostic"):
    val e = Mysql.partialIndex(partialIx)
    assertEquals(e.value, "")
    assertEquals(e.diagnostics.length, 1)
    assertEquals(e.diagnostics.head.property, "partial_index")
    assert(e.diagnostics.head.message.contains("MySQL does not support partial indexes"))

  private def trigger = TriggerSpec(
    "trg_recalc_order_total",
    "recalc_order_total",
    "orders",
    "total",
    "line_items",
    "order_id",
    SumAgg(),
    Some("amount")
  )

  test("Postgres trigger emits a PL/pgSQL function plus a CREATE TRIGGER"):
    val e = Postgres.renderTrigger(trigger)
    assertEquals(e.upgrade.length, 2)
    assert(e.upgrade.mkString.contains("LANGUAGE plpgsql"))

  private def perEventDialects: List[Dialect] = List(Sqlite, Mysql)

  perEventDialects.foreach: dialect =>
    test(s"${dialect.id} trigger emits three single-statement row triggers, no PL/pgSQL"):
      val e = dialect.renderTrigger(trigger)
      assertEquals(e.upgrade.length, 3)
      assertEquals(e.downgrade.length, 3)
      val up = e.upgrade.mkString("\n")
      assert(!up.contains("plpgsql"), s"unexpected plpgsql in $up")
      assert(up.contains("CREATE TRIGGER trg_recalc_order_total_ins AFTER INSERT"))
      assert(up.contains("CREATE TRIGGER trg_recalc_order_total_upd AFTER UPDATE"))
      assert(up.contains("CREATE TRIGGER trg_recalc_order_total_del AFTER DELETE"))
      assert(up.contains("COALESCE(SUM(amount), 0)"))
      assert(e.downgrade.forall(_.contains("DROP TRIGGER IF EXISTS")))

  test("schemaDiagnostics aggregates per-dialect index degradations"):
    val tbl = TableSpec(
      "orders",
      "Order",
      List(ColumnSpec("id", "BIGSERIAL", false, None)),
      "id",
      Nil,
      Nil,
      List(partialIx)
    )
    val schema = DatabaseSchema(List(tbl), Nil)
    assertEquals(Postgres.schemaDiagnostics(schema), Nil)
    assertEquals(Sqlite.schemaDiagnostics(schema), Nil)
    assertEquals(Mysql.schemaDiagnostics(schema).length, 1)
