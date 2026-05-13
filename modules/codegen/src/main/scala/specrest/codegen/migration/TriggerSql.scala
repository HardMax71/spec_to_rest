package specrest.codegen.migration

import specrest.convention.TriggerAggregate
import specrest.convention.TriggerSpec

object TriggerSql:

  def functionBody(t: TriggerSpec): String =
    val recompute = aggregateExpr(t)
    val source    = t.sourceTable
    val parentFk  = t.sourceForeignKey
    val parentCol = t.targetColumn
    val parentTbl = t.targetTable
    val funcName  = t.functionName
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

  def triggerStatement(t: TriggerSpec): String =
    s"""CREATE TRIGGER ${t.name}
       |    AFTER INSERT OR UPDATE OR DELETE ON ${t.sourceTable}
       |    FOR EACH ROW EXECUTE FUNCTION ${t.functionName}();""".stripMargin

  def dropStatements(t: TriggerSpec): List[String] = List(
    s"DROP TRIGGER IF EXISTS ${t.name} ON ${t.sourceTable};",
    s"DROP FUNCTION IF EXISTS ${t.functionName}();"
  )

  private def aggregateExpr(t: TriggerSpec): String =
    val col = t.sourceColumn
    (t.aggregate, col) match
      case (TriggerAggregate.Sum, Some(c)) => s"COALESCE(SUM($c), 0)"
      case (TriggerAggregate.Min, Some(c)) => s"MIN($c)"
      case (TriggerAggregate.Max, Some(c)) => s"MAX($c)"
      case (TriggerAggregate.Count, _)     => "COUNT(*)"
      case (TriggerAggregate.Sum, None)    => "0"
      case (TriggerAggregate.Min, None)    => "NULL"
      case (TriggerAggregate.Max, None)    => "NULL"
