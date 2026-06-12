package specrest.convention

import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.ir.idx

object ScalarState:

  val TableName   = "service_state"
  val SingletonId = 1

  // Bare `Int` only: a type alias to Int may carry a `where` refinement the
  // state table would not enforce (no derived CHECK yet), so alias-typed
  // scalars deliberately stay on the LLM path. A field named `id` would
  // collide with the singleton PK column. An entity mapped to the reserved
  // table name disables the feature for the service - those ops then route
  // to LLM synthesis, exactly the pre-#407 behaviour, rather than failing
  // the compile of a previously-valid spec.
  def fields(ir: ServiceIRFull): List[state_field_decl] =
    svcState(ir) match
      case None => Nil
      case Some(sd) =>
        val scalars = stdFields(sd).filter: sf =>
          stfType(sf) match
            case NamedTypeF("Int", _) => columnName(stfName(sf)) != "id"
            case _                    => false
        if scalars.isEmpty || entityTableNames(ir).contains(TableName) then Nil
        else scalars

  def fieldNames(ir: ServiceIRFull): List[String] = fields(ir).map(stfName)

  def columnName(specName: String): String = Naming.toColumnName(specName)

  def stateTable(ir: ServiceIRFull): Option[table_spec] =
    val scalars = fields(ir)
    if scalars.isEmpty then None
    else
      val idCol = ColumnSpec("id", "BIGINT", false, None)
      val cols = scalars.map: sf =>
        ColumnSpec(columnName(stfName(sf)), "BIGINT", false, Some("0"))
      Some(
        TableSpec(
          TableName,
          "",
          idCol :: cols,
          "id",
          Nil,
          List(s"id = $SingletonId"),
          Nil
        )
      )

  private def entityTableNames(ir: ServiceIRFull): Set[String] =
    ir.idx.entities.map { entity =>
      Path
        .getConvention(svcConventions(ir), entName(entity), "db_table")
        .getOrElse(Naming.toTableName(entName(entity)))
    }.toSet
