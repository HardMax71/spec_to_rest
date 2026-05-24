package specrest.codegen.migration

import specrest.ir.generated.SpecRestGenerated.*

object TriggerSql:

  def functionBody(t: trigger_spec): String =
    val recompute = aggregateExpr(t)
    val source    = trigger_source_table(t)
    val parentFk  = trigger_source_foreign_key(t)
    val parentCol = trigger_target_column(t)
    val parentTbl = trigger_target_table(t)
    val funcName  = trigger_function_name(t)
    s"""CREATE OR REPLACE FUNCTION $funcName() RETURNS TRIGGER AS $$$$
       |BEGIN
       |    IF TG_OP = 'UPDATE' AND NEW.$parentFk IS DISTINCT FROM OLD.$parentFk THEN
       |        UPDATE $parentTbl
       |        SET $parentCol = (
       |            SELECT $recompute
       |            FROM $source
       |            WHERE $parentFk = OLD.$parentFk
       |        )
       |        WHERE id = OLD.$parentFk;
       |        UPDATE $parentTbl
       |        SET $parentCol = (
       |            SELECT $recompute
       |            FROM $source
       |            WHERE $parentFk = NEW.$parentFk
       |        )
       |        WHERE id = NEW.$parentFk;
       |    ELSE
       |        UPDATE $parentTbl
       |        SET $parentCol = (
       |            SELECT $recompute
       |            FROM $source
       |            WHERE $parentFk = COALESCE(NEW.$parentFk, OLD.$parentFk)
       |        )
       |        WHERE id = COALESCE(NEW.$parentFk, OLD.$parentFk);
       |    END IF;
       |    RETURN NULL;
       |END;
       |$$$$ LANGUAGE plpgsql;""".stripMargin

  def triggerStatement(t: trigger_spec): String =
    s"""CREATE TRIGGER ${trigger_name(t)}
       |    AFTER INSERT OR UPDATE OR DELETE ON ${trigger_source_table(t)}
       |    FOR EACH ROW EXECUTE FUNCTION ${trigger_function_name(t)}();""".stripMargin

  def dropStatements(t: trigger_spec): List[String] = List(
    s"DROP TRIGGER IF EXISTS ${trigger_name(t)} ON ${trigger_source_table(t)};",
    s"DROP FUNCTION IF EXISTS ${trigger_function_name(t)}();"
  )

  def aggregateExpr(t: trigger_spec): String =
    val col = trigger_source_column(t)
    (trigger_aggregate_of(t), col) match
      case (_: SumAgg, Some(c)) => s"COALESCE(SUM($c), 0)"
      case (_: MinAgg, Some(c)) => s"MIN($c)"
      case (_: MaxAgg, Some(c)) => s"MAX($c)"
      case (_: CountAgg, _)     => "COUNT(*)"
      case (_: SumAgg, None)    => "0"
      case (_: MinAgg, None)    => "NULL"
      case (_: MaxAgg, None)    => "NULL"
