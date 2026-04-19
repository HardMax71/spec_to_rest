package specrest.convention

import specrest.ir.*

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

  def deriveSchema(ir: ServiceIR): DatabaseSchema =
    val entityNames = ir.entities.map(_.name).toSet
    val enumMap     = ir.enums.map(e => e.name -> e).toMap
    val aliasMap    = ir.typeAliases.map(a => a.name -> a).toMap
    val entityRefs  = buildEntityRefMap(ir, entityNames, enumMap, aliasMap)

    val tables = List.newBuilder[TableSpec]
    for entity <- ir.entities do
      tables += deriveTable(entity, ir, entityNames, enumMap, aliasMap, entityRefs)

    ir.state.foreach: state =>
      for field <- state.fields do
        field.typeExpr match
          case TypeExpr.RelationType(_, mult, _, _)
              if mult == Multiplicity.Some || mult == Multiplicity.Set =>
            deriveJunctionTable(field, entityNames).foreach(tables += _)
          case _ => ()

    DatabaseSchema(tables.result())

  private def buildEntityRefMap(
      ir: ServiceIR,
      entityNames: Set[String],
      enumMap: Map[String, EnumDecl],
      aliasMap: Map[String, TypeAliasDecl]
  ): Map[String, EntityRef] =
    ir.entities.map: entity =>
      val tableName = Path
        .getConvention(ir.conventions, entity.name, "db_table")
        .getOrElse(Naming.toTableName(entity.name))
      val idField = entity.fields.find(_.name == "id")
      val idFkSqlType = idField match
        case None => "BIGINT"
        case Some(f) =>
          val mapped = mapTypeToColumn("id", f.typeExpr, entityNames, enumMap, aliasMap)
          if mapped.column.sqlType == "BIGSERIAL" then "BIGINT" else mapped.column.sqlType
      entity.name -> EntityRef(tableName, idFkSqlType)
    .toMap

  private def deriveTable(
      entity: EntityDecl,
      ir: ServiceIR,
      entityNames: Set[String],
      enumMap: Map[String, EnumDecl],
      aliasMap: Map[String, TypeAliasDecl],
      entityRefs: Map[String, EntityRef]
  ): TableSpec =
    val tableName = Path
      .getConvention(ir.conventions, entity.name, "db_table")
      .getOrElse(Naming.toTableName(entity.name))

    val entityFieldNames = entity.fields.map(_.name).toSet
    val columns          = scala.collection.mutable.ListBuffer.empty[ColumnSpec]
    if !entityFieldNames.contains("id") then
      columns += ColumnSpec("id", "BIGSERIAL", nullable = false, defaultValue = None)

    val foreignKeys = scala.collection.mutable.ListBuffer.empty[ForeignKeySpec]
    val checks      = scala.collection.mutable.ListBuffer.empty[String]
    val indexes     = scala.collection.mutable.ListBuffer.empty[IndexSpec]

    for field <- entity.fields do
      val colName = Naming.toColumnName(field.name)
      val mapped  = mapFieldToColumn(field, entityNames, enumMap, aliasMap, entityRefs)
      columns += mapped.column
      mapped.foreignKey.foreach: fk =>
        foreignKeys += fk
        indexes += IndexSpec(s"idx_${tableName}_$colName", List(colName), unique = false)
      mapped.check.foreach(checks += _)
      field.constraint.foreach: c =>
        checks ++= extractChecks(colName, c)

    for inv <- entity.invariants do
      checks ++= extractInvariantChecks(inv, entity.fields)

    ir.state.foreach: state =>
      for sf <- state.fields do
        sf.typeExpr match
          case TypeExpr.RelationType(from, mult, to, _)
              if resolveTypeName(to).contains(entity.name) &&
                mult != Multiplicity.Some && mult != Multiplicity.Set =>
            resolveTypeName(from).filter(entityNames.contains) match
              case Some(fromName) =>
                val fkCol =
                  Naming.toColumnName(
                    fromName.replaceAll("([A-Z])", "_$1").toLowerCase.stripPrefix("_")
                  ) + "_id"
                val fkRefTable = Naming.toTableName(fromName)
                val nullable   = mult == Multiplicity.Lone
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

    val tsOverride    = Path.getConvention(ir.conventions, entity.name, "db_timestamps")
    val addTimestamps = !tsOverride.contains("false")
    if addTimestamps && !columns.exists(_.name == "created_at") then
      columns += ColumnSpec("created_at", "TIMESTAMPTZ", nullable = false, Some("NOW()"))
    if addTimestamps && !columns.exists(_.name == "updated_at") then
      columns += ColumnSpec("updated_at", "TIMESTAMPTZ", nullable = false, Some("NOW()"))

    TableSpec(
      name = tableName,
      entityName = entity.name,
      columns = columns.toList,
      primaryKey = "id",
      foreignKeys = foreignKeys.toList,
      checks = checks.toList,
      indexes = indexes.toList
    )

  private def deriveJunctionTable(
      stateField: StateFieldDecl,
      entityNames: Set[String]
  ): Option[TableSpec] =
    stateField.typeExpr match
      case TypeExpr.RelationType(from, _, to, _) =>
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
      field: FieldDecl,
      entityNames: Set[String],
      enumMap: Map[String, EnumDecl],
      aliasMap: Map[String, TypeAliasDecl],
      entityRefs: Map[String, EntityRef]
  ): MappedField =
    val colName = Naming.toColumnName(field.name)
    val mapped  = mapTypeToColumn(colName, field.typeExpr, entityNames, enumMap, aliasMap)
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
      typeExpr: TypeExpr,
      entityNames: Set[String],
      enumMap: Map[String, EnumDecl],
      aliasMap: Map[String, TypeAliasDecl]
  ): MappedField =
    typeExpr match
      case TypeExpr.NamedType(name, _) =>
        PrimitiveTypeMap.get(name) match
          case Some(sqlType) =>
            MappedField(
              ColumnSpec(colName, sqlType, nullable = false, None),
              None,
              None
            )
          case None =>
            enumMap.get(name) match
              case Some(enumDecl) =>
                val values = enumDecl.values.map(v => s"'${escapeSqlString(v)}'").mkString(", ")
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
                  case Some(alias) =>
                    mapTypeToColumn(colName, alias.typeExpr, entityNames, enumMap, aliasMap)
                  case None =>
                    MappedField(
                      ColumnSpec(colName, "TEXT", nullable = false, None),
                      None,
                      None
                    )
      case TypeExpr.OptionType(inner, _) =>
        val innerMapped = mapTypeToColumn(colName, inner, entityNames, enumMap, aliasMap)
        MappedField(
          innerMapped.column.copy(nullable = true),
          innerMapped.foreignKey,
          innerMapped.check
        )
      case TypeExpr.SetType(_, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", nullable = false, Some("'[]'::jsonb")),
          None,
          None
        )
      case TypeExpr.SeqType(_, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", nullable = false, Some("'[]'::jsonb")),
          None,
          None
        )
      case TypeExpr.MapType(_, _, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", nullable = false, Some("'{}'::jsonb")),
          None,
          None
        )
      case TypeExpr.RelationType(_, _, _, _) =>
        MappedField(
          ColumnSpec(colName, "BIGINT", nullable = false, None),
          None,
          None
        )

  private def escapeSqlString(s: String): String = s.replace("'", "''")

  private def resolveTypeName(typeExpr: TypeExpr): Option[String] = typeExpr match
    case TypeExpr.NamedType(n, _) => Some(n)
    case _                        => None

  private def extractChecks(colName: String, constraint: Expr): List[String] =
    val checks = List.newBuilder[String]
    visitConstraint(constraint, colName, checks)
    checks.result()

  private def visitConstraint(
      expr: Expr,
      colName: String,
      checks: scala.collection.mutable.Builder[String, List[String]]
  ): Unit = expr match
    case Expr.BinaryOp(BinOp.And, l, r, _) =>
      visitConstraint(l, colName, checks); visitConstraint(r, colName, checks)
    case b @ Expr.BinaryOp(_, _, _, _) =>
      tryMapComparison(b, colName).foreach(checks += _)
    case Expr.Matches(Expr.Identifier(_, _), pattern, _) =>
      checks += s"$colName ~ '${escapeSqlString(pattern)}'"
    case _ => ()

  private def sqlOp(op: BinOp): Option[String] = op match
    case BinOp.Gt  => Some(">")
    case BinOp.Lt  => Some("<")
    case BinOp.Ge  => Some(">=")
    case BinOp.Le  => Some("<=")
    case BinOp.Eq  => Some("=")
    case BinOp.Neq => Some("!=")
    case _         => None

  private def tryMapComparison(b: Expr.BinaryOp, colName: String): Option[String] =
    sqlOp(b.op).flatMap: op =>
      if isLenCall(b.left) && isLiteral(b.right) then
        Some(s"length($colName) $op ${literalValue(b.right)}")
      else if isValueRef(b.left) && isLiteral(b.right) then
        Some(s"$colName $op ${literalValue(b.right)}")
      else None

  private def isLenCall(e: Expr): Boolean = e match
    case Expr.Call(Expr.Identifier("len", _), _, _) => true
    case _                                          => false

  private def isValueRef(e: Expr): Boolean = e match
    case Expr.Identifier("value", _) => true
    case _                           => false

  private def isLiteral(e: Expr): Boolean = e match
    case Expr.IntLit(_, _) | Expr.FloatLit(_, _) | Expr.StringLit(_, _) => true
    case _                                                              => false

  private def literalValue(e: Expr): String = e match
    case Expr.IntLit(v, _)    => v.toString
    case Expr.FloatLit(v, _)  => v.toString
    case Expr.StringLit(v, _) => s"'${escapeSqlString(v)}'"
    case _                    => "NULL"

  private def extractInvariantChecks(inv: Expr, fields: List[FieldDecl]): List[String] =
    inv match
      case Expr.BinaryOp(BinOp.And, l, r, _) =>
        extractInvariantChecks(l, fields) ++ extractInvariantChecks(r, fields)
      case b @ Expr.BinaryOp(BinOp.In, left, Expr.SetLiteral(elements, _), _) =>
        extractFieldName(left) match
          case Some(fieldName) =>
            val values = elements.flatMap:
              case Expr.StringLit(v, _)     => Some(s"'${escapeSqlString(v)}'")
              case Expr.Identifier(n, _)    => Some(s"'${escapeSqlString(n)}'")
              case Expr.EnumAccess(_, m, _) => Some(s"'${escapeSqlString(m)}'")
              case _                        => None
            if values.nonEmpty then
              List(s"${Naming.toColumnName(fieldName)} IN (${values.mkString(", ")})")
            else Nil
          case None => Nil
      case b @ Expr.BinaryOp(_, _, _, _) =>
        tryMapInvariantComparison(b, fields).toList
      case Expr.Matches(inner, pattern, _) =>
        extractFieldName(inner)
          .map(n => s"${Naming.toColumnName(n)} ~ '${escapeSqlString(pattern)}'")
          .toList
      case _ => Nil

  private def tryMapInvariantComparison(
      b: Expr.BinaryOp,
      fields: List[FieldDecl]
  ): Option[String] =
    sqlOp(b.op).flatMap: op =>
      b.left match
        case Expr.Call(Expr.Identifier("len", _), args, _) if args.nonEmpty && isLiteral(b.right) =>
          extractFieldName(args.head).filter(n => fields.exists(_.name == n)).map: n =>
            s"length(${Naming.toColumnName(n)}) $op ${literalValue(b.right)}"
        case _ =>
          extractFieldName(b.left)
            .filter(n => isLiteral(b.right) && fields.exists(_.name == n))
            .map: n =>
              s"${Naming.toColumnName(n)} $op ${literalValue(b.right)}"

  private def extractFieldName(expr: Expr): Option[String] = expr match
    case Expr.Identifier(n, _)                         => Some(n)
    case Expr.FieldAccess(Expr.Identifier(_, _), f, _) => Some(f)
    case _                                             => None
