package specrest.convention

import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.ir.idx

object ScalarState:

  val TableName   = "service_state"
  val SingletonId = 1

  def fields(ir: ServiceIRFull): List[state_field_decl] =
    svcState(ir) match
      case None => Nil
      case Some(sd) =>
        val scalars = stdFields(sd).filter: sf =>
          stfType(sf) match
            case NamedTypeF("Int", _) => true
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
