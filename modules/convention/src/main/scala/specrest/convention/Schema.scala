package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

object Schema:

  private val PrimitiveTypeMap: Map[String, String] = Map(
    "String"   -> "TEXT",
    "Int"      -> "INTEGER",
    "Float"    -> "DOUBLE PRECISION",
    "Bool"     -> "BOOLEAN",
    "Boolean"  -> "BOOLEAN",
    "DateTime" -> "TIMESTAMPTZ",
    "Date"     -> "DATE",
    "UUID"     -> "UUID",
    "Decimal"  -> "NUMERIC(19,4)",
    "Bytes"    -> "BYTEA",
    "Money"    -> "INTEGER"
  )

  final private case class EntityRef(tableName: String, idFkSqlType: String)

  final private case class MappedField(
      column: ColumnSpec,
      foreignKey: Option[ForeignKeySpec],
      check: Option[String]
  )

  def deriveSchema(ir: ServiceIRFull): DatabaseSchema =
    val entities    = ir.c.collect { case e: EntityDeclFull => e }
    val enums       = ir.d.collect { case e: EnumDeclFull => e }
    val aliases     = ir.e.collect { case a: TypeAliasDeclFull => a }
    val entityNames = entities.map(_.a).toSet
    val enumMap     = enums.map(e => e.a -> e).toMap
    val aliasMap    = aliases.map(a => a.a -> a).toMap
    val entityRefs  = buildEntityRefMap(ir, entityNames, enumMap, aliasMap)

    val tables = List.newBuilder[TableSpec]
    for entity <- entities do
      tables += deriveTable(entity, ir, entityNames, enumMap, aliasMap, entityRefs)

    ir.f match
      case Some(StateDeclFull(fs, _)) =>
        for case sf @ StateFieldDeclFull(_, ty, _) <- fs do
          ty match
            case RelationTypeF(_, _: (MultSome | MultSet), _, _) =>
              deriveJunctionTable(sf, entityNames).foreach(tables += _)
            case _ => ()
      case None => ()

    DatabaseSchema(tables.result())

  private def buildEntityRefMap(
      ir: ServiceIRFull,
      entityNames: Set[String],
      enumMap: Map[String, EnumDeclFull],
      aliasMap: Map[String, TypeAliasDeclFull]
  ): Map[String, EntityRef] =
    ir.c.collect { case entity: EntityDeclFull => entity }.map { entity =>
      val fields    = entity.c.collect { case f: FieldDeclFull => f }
      val tableName = Path
        .getConvention(ir.n, entity.a, "db_table")
        .getOrElse(Naming.toTableName(entity.a))
      val idField = fields.find(_.a == "id")
      val idFkSqlType = idField match
        case None => "BIGINT"
        case Some(f) =>
          val mapped = mapTypeToColumn("id", f.b, entityNames, enumMap, aliasMap)
          if mapped.column.sqlType == "BIGSERIAL" then "BIGINT" else mapped.column.sqlType
      entity.a -> EntityRef(tableName, idFkSqlType)
    }.toMap

  private def deriveTable(
      entity: EntityDeclFull,
      ir: ServiceIRFull,
      entityNames: Set[String],
      enumMap: Map[String, EnumDeclFull],
      aliasMap: Map[String, TypeAliasDeclFull],
      entityRefs: Map[String, EntityRef]
  ): TableSpec =
    val fields = entity.c.collect { case f: FieldDeclFull => f }
    val tableName = Path
      .getConvention(ir.n, entity.a, "db_table")
      .getOrElse(Naming.toTableName(entity.a))

    val entityFieldNames = fields.map(_.a).toSet
    val columns          = scala.collection.mutable.ListBuffer.empty[ColumnSpec]
    if !entityFieldNames.contains("id") then
      columns += ColumnSpec("id", "BIGSERIAL", nullable = false, defaultValue = None)

    val foreignKeys = scala.collection.mutable.ListBuffer.empty[ForeignKeySpec]
    val checks      = scala.collection.mutable.ListBuffer.empty[String]
    val indexes     = scala.collection.mutable.ListBuffer.empty[IndexSpec]

    for field <- fields do
      val colName = Naming.toColumnName(field.a)
      val mapped  = mapFieldToColumn(field, entityNames, enumMap, aliasMap, entityRefs)
      columns += mapped.column
      mapped.foreignKey.foreach: fk =>
        foreignKeys += fk
        indexes += IndexSpec(s"idx_${tableName}_$colName", List(colName), unique = false)
      mapped.check.foreach(checks += _)
      field.c.foreach: c =>
        checks ++= extractChecks(colName, c)

    for inv <- entity.d do
      checks ++= extractInvariantChecks(inv, fields)

    ir.f match
      case Some(StateDeclFull(fs, _)) =>
        for case StateFieldDeclFull(_, ty, _) <- fs do
          ty match
            case RelationTypeF(from, mult, to, _)
                if resolveTypeName(to).contains(entity.a) && (mult match
                  case _: (MultSome | MultSet) => false; case _ => true
                ) =>
              resolveTypeName(from).filter(entityNames.contains) match
                case Some(fromName) =>
                  val fkCol =
                    Naming.toColumnName(
                      fromName.replaceAll("([A-Z])", "_$1").toLowerCase.stripPrefix("_")
                    ) + "_id"
                  val fkRefTable = Naming.toTableName(fromName)
                  val nullable = mult match
                    case _: MultLone => true
                    case _           => false
                  if !columns.exists(_.name == fkCol) then
                    columns += ColumnSpec(fkCol, "BIGINT", nullable, None)
                    foreignKeys += ForeignKeySpec(fkCol, fkRefTable, "id", "CASCADE")
                    indexes += IndexSpec(
                      s"idx_${tableName}_$fkCol",
                      List(fkCol),
                      unique = false
                    )
                case None => ()
            case _ => ()
      case None => ()

    val tsOverride    = Path.getConvention(ir.n, entity.a, "db_timestamps")
    val addTimestamps = !tsOverride.contains("false")
    if addTimestamps && !columns.exists(_.name == "created_at") then
      columns += ColumnSpec("created_at", "TIMESTAMPTZ", nullable = false, Some("NOW()"))
    if addTimestamps && !columns.exists(_.name == "updated_at") then
      columns += ColumnSpec("updated_at", "TIMESTAMPTZ", nullable = false, Some("NOW()"))

    TableSpec(
      name = tableName,
      entityName = entity.a,
      columns = columns.toList,
      primaryKey = "id",
      foreignKeys = foreignKeys.toList,
      checks = checks.toList,
      indexes = indexes.toList
    )

  private def deriveJunctionTable(
      stateField: StateFieldDeclFull,
      entityNames: Set[String]
  ): Option[TableSpec] =
    stateField.b match
      case RelationTypeF(from, _, to, _) =>
        for
          fromName <- resolveTypeName(from) if entityNames.contains(fromName)
          toName   <- resolveTypeName(to) if entityNames.contains(toName)
        yield
          val fromTable = Naming.toTableName(fromName)
          val toTable   = Naming.toTableName(toName)
          val tableName = s"${fromTable}_$toTable"
          val fromCol = Naming.toColumnName(
            fromName.replaceAll("([A-Z])", "_$1").toLowerCase.stripPrefix("_")
          ) + "_id"
          val toCol = Naming.toColumnName(
            toName.replaceAll("([A-Z])", "_$1").toLowerCase.stripPrefix("_")
          ) + "_id"
          TableSpec(
            name = tableName,
            entityName = s"${fromName}_$toName",
            columns = List(
              ColumnSpec("id", "BIGSERIAL", nullable = false, None),
              ColumnSpec(fromCol, "BIGINT", nullable = false, None),
              ColumnSpec(toCol, "BIGINT", nullable = false, None),
              ColumnSpec("created_at", "TIMESTAMPTZ", nullable = false, Some("NOW()"))
            ),
            primaryKey = "id",
            foreignKeys = List(
              ForeignKeySpec(fromCol, fromTable, "id", "CASCADE"),
              ForeignKeySpec(toCol, toTable, "id", "CASCADE")
            ),
            checks = Nil,
            indexes = List(
              IndexSpec(
                s"idx_${tableName}_${fromCol}_$toCol",
                List(fromCol, toCol),
                unique = true
              ),
              IndexSpec(s"idx_${tableName}_$fromCol", List(fromCol), unique = false),
              IndexSpec(s"idx_${tableName}_$toCol", List(toCol), unique = false)
            )
          )
      case _ => None

  private def mapFieldToColumn(
      field: FieldDeclFull,
      entityNames: Set[String],
      enumMap: Map[String, EnumDeclFull],
      aliasMap: Map[String, TypeAliasDeclFull],
      entityRefs: Map[String, EntityRef]
  ): MappedField =
    val colName = Naming.toColumnName(field.a)
    val mapped  = mapTypeToColumn(colName, field.b, entityNames, enumMap, aliasMap)
    if mapped.foreignKey.isEmpty && colName.endsWith("_id") then
      val prefix       = colName.dropRight("_id".length)
      val targetEntity = entityNames.find(n => Naming.toSnakeCase(n) == prefix)
      targetEntity.flatMap(entityRefs.get) match
        case Some(ref) =>
          MappedField(
            column = mapped.column.copy(sqlType = ref.idFkSqlType),
            foreignKey = Some(ForeignKeySpec(colName, ref.tableName, "id", "CASCADE")),
            check = mapped.check
          )
        case None => mapped
    else mapped

  private def mapTypeToColumn(
      colName: String,
      typeExpr: type_expr_full,
      entityNames: Set[String],
      enumMap: Map[String, EnumDeclFull],
      aliasMap: Map[String, TypeAliasDeclFull]
  ): MappedField =
    typeExpr match
      case NamedTypeF(name, _) =>
        PrimitiveTypeMap.get(name) match
          case Some(sqlType) =>
            MappedField(
              ColumnSpec(colName, sqlType, nullable = false, None),
              None,
              None
            )
          case None =>
            enumMap.get(name) match
              case Some(EnumDeclFull(_, vs, _)) =>
                val values = vs.map(v => s"'${escapeSqlString(v)}'").mkString(", ")
                MappedField(
                  ColumnSpec(colName, "TEXT", nullable = false, None),
                  None,
                  Some(s"$colName IN ($values)")
                )
              case None if entityNames.contains(name) =>
                val refTable = Naming.toTableName(name)
                MappedField(
                  ColumnSpec(colName + "_id", "BIGINT", nullable = false, None),
                  Some(ForeignKeySpec(colName + "_id", refTable, "id", "CASCADE")),
                  None
                )
              case None =>
                aliasMap.get(name) match
                  case Some(TypeAliasDeclFull(_, t, _, _)) =>
                    mapTypeToColumn(colName, t, entityNames, enumMap, aliasMap)
                  case None =>
                    MappedField(
                      ColumnSpec(colName, "TEXT", nullable = false, None),
                      None,
                      None
                    )
      case OptionTypeF(inner, _) =>
        val innerMapped = mapTypeToColumn(colName, inner, entityNames, enumMap, aliasMap)
        MappedField(
          innerMapped.column.copy(nullable = true),
          innerMapped.foreignKey,
          innerMapped.check
        )
      case SetTypeF(_, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", nullable = false, Some("'[]'::jsonb")),
          None,
          None
        )
      case SeqTypeF(_, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", nullable = false, Some("'[]'::jsonb")),
          None,
          None
        )
      case MapTypeF(_, _, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", nullable = false, Some("'{}'::jsonb")),
          None,
          None
        )
      case RelationTypeF(_, _, _, _) =>
        MappedField(
          ColumnSpec(colName, "BIGINT", nullable = false, None),
          None,
          None
        )

  private def escapeSqlString(s: String): String = s.replace("'", "''")

  private def resolveTypeName(typeExpr: type_expr_full): Option[String] = typeExpr match
    case NamedTypeF(n, _) => Some(n)
    case _                => None

  private def extractChecks(colName: String, constraint: expr_full): List[String] =
    val checks = List.newBuilder[String]
    visitConstraint(constraint, colName, checks)
    checks.result()

  private def visitConstraint(
      expr: expr_full,
      colName: String,
      checks: scala.collection.mutable.Builder[String, List[String]]
  ): Unit = expr match
    case BinaryOpF(BAnd(), l, r, _) =>
      visitConstraint(l, colName, checks); visitConstraint(r, colName, checks)
    case b @ BinaryOpF(_, _, _, _) =>
      tryMapComparison(b, colName).foreach(checks += _)
    case MatchesF(IdentifierF(_, _), pattern, _) =>
      checks += s"$colName ~ '${escapeSqlString(pattern)}'"
    case _ => ()

  private def sqlOp(op: bin_op_full): Option[String] = op match
    case BGt()  => Some(">")
    case BLt()  => Some("<")
    case BGe()  => Some(">=")
    case BLe()  => Some("<=")
    case BEq()  => Some("=")
    case BNeq() => Some("!=")
    case _      => None

  private def tryMapComparison(b: BinaryOpF, colName: String): Option[String] =
    sqlOp(b.a).flatMap: op =>
      if isLenCall(b.b) && isLiteral(b.c) then
        Some(s"length($colName) $op ${literalValue(b.c)}")
      else if isValueRef(b.b) && isLiteral(b.c) then
        Some(s"$colName $op ${literalValue(b.c)}")
      else None

  private def isLenCall(e: expr_full): Boolean = e match
    case CallF(IdentifierF("len", _), _, _) => true
    case _                                  => false

  private def isValueRef(e: expr_full): Boolean = e match
    case IdentifierF("value", _) => true
    case _                       => false

  private def isLiteral(e: expr_full): Boolean = e match
    case IntLitF(_, _) | FloatLitF(_, _) | StringLitF(_, _) => true
    case _                                                  => false

  private def literalValue(e: expr_full): String = e match
    case IntLitF(int_of_integer(v), _) => v.toString
    case FloatLitF(v, _)               => v
    case StringLitF(v, _)              => s"'${escapeSqlString(v)}'"
    case _                             => "NULL"

  private def extractInvariantChecks(inv: expr_full, fields: List[FieldDeclFull]): List[String] =
    inv match
      case BinaryOpF(BAnd(), l, r, _) =>
        extractInvariantChecks(l, fields) ++ extractInvariantChecks(r, fields)
      case BinaryOpF(BIn(), left, SetLiteralF(elements, _), _) =>
        extractFieldName(left) match
          case Some(fieldName) =>
            val colName = Naming.toColumnName(fieldName)
            val values = elements.collect {
              case StringLitF(v, _)              => s"'${escapeSqlString(v)}'"
              case IntLitF(int_of_integer(v), _) => v.toString
            }
            if values.length == elements.length && values.nonEmpty then
              List(s"$colName IN (${values.mkString(", ")})")
            else Nil
          case None => Nil
      case b @ BinaryOpF(_, left, right, _) =>
        (extractFieldName(left), tryComparison(b, fields)) match
          case (Some(fieldName), Some(check)) =>
            List(check.replace("__COL__", Naming.toColumnName(fieldName)))
          case _ => Nil
      case _ => Nil

  private def extractFieldName(expr: expr_full): Option[String] = expr match
    case FieldAccessF(IdentifierF("self", _), name, _) => Some(name)
    case IdentifierF(name, _)                          => Some(name)
    case _                                             => None

  private def tryComparison(b: BinaryOpF, fields: List[FieldDeclFull]): Option[String] =
    sqlOp(b.a).flatMap: op =>
      if isLiteral(b.c) then Some(s"__COL__ $op ${literalValue(b.c)}") else None
