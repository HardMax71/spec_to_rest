package specrest.codegen

import specrest.convention.ScalarState
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

final case class ScalarStateFieldView(specName: String, columnName: String, camelName: String)

final case class ScalarUpdateView(specName: String, columnName: String, rhs: scalar_rhs)

final case class ScalarGuardView(
    specName: String,
    columnName: String,
    cmp: scalar_cmp,
    lit: BigInt
)

final case class ScalarOpView(
    operation: ProfiledOperation,
    updates: List[ScalarUpdateView],
    guards: List[ScalarGuardView]
):
  def guardPretty: String =
    if guards.isEmpty then "true"
    else
      guards
        .map(g => s"${g.specName} ${ScalarOps.specCmp(g.cmp)} ${g.lit}")
        .mkString(" and ")

object ScalarOps:

  val TableName: String = ScalarState.TableName

  def stateFields(p: ProfiledService): List[ScalarStateFieldView] =
    ScalarState.fields(p.ir).map: sf =>
      val col = ScalarState.columnName(stfName(sf))
      ScalarStateFieldView(stfName(sf), col, specrest.ir.Naming.toCamelCase(col))

  def views(p: ProfiledService): List[ScalarOpView] =
    val scalarNames = ScalarState.fieldNames(p.ir)
    if scalarNames.isEmpty then Nil
    else
      val profiledByName = p.operations.map(o => o.operationName -> o).toMap
      svcOperations(p.ir).flatMap: op =>
        val clauses = flattenEnsures(operEnsures(op))
        val updates = clauses.map(c => scalarUpdateOf(scalarNames, c))
        val guards  = flattenEnsures(operRequires(op)).map(r => scalarGuardOf(scalarNames, r))
        if clauses.nonEmpty && updates.forall(_.isDefined) && guards.forall(_.isDefined) then
          profiledByName.get(operName(op)).map: po =>
            // conjunctive ensures repeating a field collapse to the last clause
            val ups = updates.flatten
              .map((n, rhs) => ScalarUpdateView(n, ScalarState.columnName(n), rhs))
              .groupBy(_.columnName)
              .values
              .map(_.last)
              .toList
              .sortBy(_.columnName)
            val gs = guards.flatten.collect:
              case SgCmp(n, c, k) => ScalarGuardView(n, ScalarState.columnName(n), c, k)
            ScalarOpView(po, ups, gs)
        else None

  def renderRhs(r: scalar_rhs, selfRef: String): String = r match
    case SrLit(k)    => k.toString
    case SrSelf()    => selfRef
    case SrAdd(a, b) => s"(${renderRhs(a, selfRef)} + ${renderRhs(b, selfRef)})"
    case SrSub(a, b) => s"(${renderRhs(a, selfRef)} - ${renderRhs(b, selfRef)})"
    case SrMul(a, b) => s"(${renderRhs(a, selfRef)} * ${renderRhs(b, selfRef)})"

  def sqlCmp(c: scalar_cmp): String = c match
    case _: ScGt  => ">"
    case _: ScGe  => ">="
    case _: ScLt  => "<"
    case _: ScLe  => "<="
    case _: ScEq  => "="
    case _: ScNeq => "<>"

  def pyCmp(c: scalar_cmp): String = c match
    case _: ScEq  => "=="
    case _: ScNeq => "!="
    case other    => sqlCmp(other)

  def specCmp(c: scalar_cmp): String = c match
    case _: ScNeq => "!="
    case _: ScEq  => "="
    case other    => sqlCmp(other)

  def seedSqlFor(t: table_spec): String =
    val cols = tableColumns(t).map(columnName)
    val vals = cols.map(c => if c == "id" then "1" else "0")
    s"INSERT INTO ${tableName(t)} (${cols.mkString(", ")}) VALUES (${vals.mkString(", ")})"

  def isStateTable(t: table_spec): Boolean =
    tableName(t) == TableName && tableEntityName(t).isEmpty
