package specrest.convention

import specrest.ir.idx
import specrest.ir.generated.SpecRestGenerated
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

  // An explicit integer `id` PK is widened to 64-bit. Spec integer semantics are
  // unbounded, so a 32-bit INTEGER overflows on ids >= 2^31 — surfacing as a 500
  // instead of a 4xx not-found — and it would also disagree with synthesized
  // BIGSERIAL PKs and BIGINT FKs. This is the single source of truth: both the
  // migration DDL (deriveTable) and the codegen renderer types (profile.Annotate,
  // which drives Prisma/SQLAlchemy/Go) must apply the *same* rule or the generated
  // client is mistyped against its own migration.

  final private case class EntityRef(tableName: String, idFkSqlType: String)

  final private case class MappedField(
      column: column_spec,
      foreignKey: Option[foreign_key_spec],
      check: Option[String]
  )

  def deriveSchema(ir: ServiceIRFull): database_schema =
    val ix          = ir.idx
    val entities    = ix.entities
    val entityNames = ix.entityNames
    val enumMap     = ix.enumByName
    val aliasMap    = ix.aliasByName
    val entityRefs  = buildEntityRefMap(ir, entityNames, enumMap, aliasMap)

    val tables = List.newBuilder[table_spec]
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

    val builtTables        = tables.result()
    val withPartialIndexes = applyPartialIndexConventions(builtTables, entities, ir.n)
    val triggers           = detectAggregateTriggers(entities, withPartialIndexes)
    DatabaseSchema(withPartialIndexes, triggers)

  private def buildEntityRefMap(
      ir: ServiceIRFull,
      entityNames: Set[String],
      enumMap: Map[String, EnumDeclFull],
      aliasMap: Map[String, TypeAliasDeclFull]
  ): Map[String, EntityRef] =
    ir.idx.entities.map { entity =>
      val fields = entity.c.collect { case f: FieldDeclFull => f }
      val tableName = Path
        .getConvention(ir.n, entity.a, "db_table")
        .getOrElse(Naming.toTableName(entity.a))
      val idField = fields.find(_.a == "id")
      val idFkSqlType = idField match
        case None => "BIGINT"
        case Some(f) =>
          val mapped = mapTypeToColumn("id", f.b, entityNames, enumMap, aliasMap)
          // A FK referencing the PK must match its widened type.
          columnSqlType(mapped.column) match
            case "BIGSERIAL" => "BIGINT"
            case other       => widenExplicitIdPkSqlType("id", other)
      entity.a -> EntityRef(tableName, idFkSqlType)
    }.toMap

  private def deriveTable(
      entity: EntityDeclFull,
      ir: ServiceIRFull,
      entityNames: Set[String],
      enumMap: Map[String, EnumDeclFull],
      aliasMap: Map[String, TypeAliasDeclFull],
      entityRefs: Map[String, EntityRef]
  ): table_spec =
    val fields = entity.c.collect { case f: FieldDeclFull => f }
    val tableName = Path
      .getConvention(ir.n, entity.a, "db_table")
      .getOrElse(Naming.toTableName(entity.a))

    val entityFieldNames = fields.map(_.a).toSet
    val columns          = scala.collection.mutable.ListBuffer.empty[column_spec]
    if !entityFieldNames.contains("id") then
      columns += ColumnSpec("id", "BIGSERIAL", false, None)

    val foreignKeys = scala.collection.mutable.ListBuffer.empty[foreign_key_spec]
    val checks      = scala.collection.mutable.ListBuffer.empty[String]
    val indexes     = scala.collection.mutable.ListBuffer.empty[index_spec]

    for field <- fields do
      val colName    = Naming.toColumnName(field.a)
      val mapped     = mapFieldToColumn(field, entityNames, enumMap, aliasMap, entityRefs)
      val widenedSql = widenExplicitIdPkSqlType(field.a, columnSqlType(mapped.column))
      val column = ColumnSpec(
        columnName(mapped.column),
        widenedSql,
        columnNullable(mapped.column),
        columnDefaultValue(mapped.column)
      )
      columns += column
      mapped.foreignKey.foreach: fk =>
        foreignKeys += fk
        val fkCol = fkColumn(fk)
        indexes += IndexSpec(s"idx_${tableName}_$fkCol", List(fkCol), false, None)
      mapped.check.foreach(checks += _)
      field.c.foreach: c =>
        checks ++= extractChecks(colName, c)
      for refinement <- SpecRestGenerated.aliasRefinements(field.b, aliasMap.toList) do
        checks ++= extractChecks(colName, refinement)

    for inv <- entity.d do
      checks ++= extractInvariantChecks(inv, fields)

    ir.f match
      case Some(StateDeclFull(fs, _)) =>
        for case StateFieldDeclFull(_, ty, _) <- fs do
          ty match
            case RelationTypeF(from, mult, to, _)
                if typeName(to).contains(entity.a) && (mult match
                  case _: (MultSome | MultSet) => false; case _ => true
                ) =>
              typeName(from).filter(entityNames.contains) match
                case Some(fromName) =>
                  val fkCol =
                    Naming.toColumnName(
                      fromName.replaceAll("([A-Z])", "_$1").toLowerCase.stripPrefix("_")
                    ) + "_id"
                  val fkRefTable = Naming.toTableName(fromName)
                  val nullable = mult match
                    case _: MultLone => true
                    case _           => false
                  if !columns.exists(c => columnName(c) == fkCol) then
                    columns += ColumnSpec(fkCol, "BIGINT", nullable, None)
                    foreignKeys += ForeignKeySpec(fkCol, fkRefTable, "id", "CASCADE")
                    indexes += IndexSpec(
                      s"idx_${tableName}_$fkCol",
                      List(fkCol),
                      false,
                      None
                    )
                case None => ()
            case _ => ()
      case None => ()

    val tsOverride    = Path.getConvention(ir.n, entity.a, "db_timestamps")
    val addTimestamps = !tsOverride.contains("false")
    if addTimestamps && !columns.exists(c => columnName(c) == "created_at") then
      columns += ColumnSpec("created_at", "TIMESTAMPTZ", false, Some("NOW()"))
    if addTimestamps && !columns.exists(c => columnName(c) == "updated_at") then
      columns += ColumnSpec("updated_at", "TIMESTAMPTZ", false, Some("NOW()"))

    TableSpec(
      tableName,
      entity.a,
      columns.toList,
      "id",
      foreignKeys.toList,
      checks.toList,
      indexes.toList
    )

  private def deriveJunctionTable(
      stateField: StateFieldDeclFull,
      entityNames: Set[String]
  ): Option[table_spec] =
    stateField.b match
      case RelationTypeF(from, _, to, _) =>
        for
          fromName <- typeName(from) if entityNames.contains(fromName)
          toName   <- typeName(to) if entityNames.contains(toName)
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
            tableName,
            s"${fromName}_$toName",
            List(
              ColumnSpec("id", "BIGSERIAL", false, None),
              ColumnSpec(fromCol, "BIGINT", false, None),
              ColumnSpec(toCol, "BIGINT", false, None),
              ColumnSpec("created_at", "TIMESTAMPTZ", false, Some("NOW()"))
            ),
            "id",
            List(
              ForeignKeySpec(fromCol, fromTable, "id", "CASCADE"),
              ForeignKeySpec(toCol, toTable, "id", "CASCADE")
            ),
            Nil,
            List(
              IndexSpec(
                s"idx_${tableName}_${fromCol}_$toCol",
                List(fromCol, toCol),
                true,
                None
              ),
              IndexSpec(s"idx_${tableName}_$fromCol", List(fromCol), false, None),
              IndexSpec(s"idx_${tableName}_$toCol", List(toCol), false, None)
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
          val widened = ColumnSpec(
            columnName(mapped.column),
            ref.idFkSqlType,
            columnNullable(mapped.column),
            columnDefaultValue(mapped.column)
          )
          MappedField(
            column = widened,
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
              ColumnSpec(colName, sqlType, false, None),
              None,
              None
            )
          case None =>
            enumMap.get(name) match
              case Some(EnumDeclFull(_, vs, _)) =>
                val values = vs.map(v => s"'${escapeSqlString(v)}'").mkString(", ")
                MappedField(
                  ColumnSpec(colName, "TEXT", false, None),
                  None,
                  Some(s"$colName IN ($values)")
                )
              case None if entityNames.contains(name) =>
                val refTable = Naming.toTableName(name)
                MappedField(
                  ColumnSpec(colName + "_id", "BIGINT", false, None),
                  Some(ForeignKeySpec(colName + "_id", refTable, "id", "CASCADE")),
                  None
                )
              case None =>
                aliasMap.get(name) match
                  case Some(TypeAliasDeclFull(_, t, _, _)) =>
                    mapTypeToColumn(colName, t, entityNames, enumMap, aliasMap)
                  case None =>
                    MappedField(
                      ColumnSpec(colName, "TEXT", false, None),
                      None,
                      None
                    )
      case OptionTypeF(inner, _) =>
        val innerMapped = mapTypeToColumn(colName, inner, entityNames, enumMap, aliasMap)
        val nullableCol = ColumnSpec(
          columnName(innerMapped.column),
          columnSqlType(innerMapped.column),
          true,
          columnDefaultValue(innerMapped.column)
        )
        MappedField(
          nullableCol,
          innerMapped.foreignKey,
          innerMapped.check
        )
      case SetTypeF(_, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", false, Some("'[]'::jsonb")),
          None,
          None
        )
      case SeqTypeF(_, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", false, Some("'[]'::jsonb")),
          None,
          None
        )
      case MapTypeF(_, _, _) =>
        MappedField(
          ColumnSpec(colName, "JSONB", false, Some("'{}'::jsonb")),
          None,
          None
        )
      case RelationTypeF(_, _, _, _) =>
        MappedField(
          ColumnSpec(colName, "BIGINT", false, None),
          None,
          None
        )

  private def escapeSqlString(s: String): String = s.replace("'", "''")

  private def extractChecks(colName: String, constraint: expr_full): List[String] =
    val checks = List.newBuilder[String]
    visitConstraint(constraint, colName, checks)
    checks.result()

  // A field typed by a refined alias (`type Email = String where value matches ...`) must carry
  // that alias's `where` predicate as a column CHECK, exactly as an inline field refinement does.
  // Walks the alias chain (and through Option), with a visited guard against cyclic aliases.
  private def visitConstraint(
      expr: expr_full,
      colName: String,
      checks: scala.collection.mutable.Builder[String, List[String]]
  ): Unit =
    flattenAnd(expr).foreach(atom => applyAtom(atom, colName, checks))

  private def applyAtom(
      expr: expr_full,
      colName: String,
      checks: scala.collection.mutable.Builder[String, List[String]]
  ): Unit =
    decomposeAtom(expr) match
      case RaMatches(pat) =>
        checks += s"$colName ~ '${escapeSqlString(pat)}'"
      case RaMatchesIdent(_, pat) =>
        checks += s"$colName ~ '${escapeSqlString(pat)}'"
      case RaLenCmp(op, int_of_integer(n)) =>
        sqlOp(op).foreach(o => checks += s"length($colName) $o $n")
      case RaValueCmp(op, int_of_integer(n)) =>
        sqlOp(op).foreach(o => checks += s"$colName $o $n")
      case _: RaPredCall => ()
      case _: RaUnknown  =>
        // Float / String literals not covered by decomposeAtom
        expr match
          case BinaryOpF(op, lhs, rhs, _) if isLiteral(rhs) =>
            sqlOp(op).foreach: o =>
              if isLenOfValue(lhs) then
                checks += s"length($colName) $o ${literalValue(rhs)}"
              else if isValueRef(lhs) then
                checks += s"$colName $o ${literalValue(rhs)}"
          case _ => ()

  private def literalValue(e: expr_full): String = e match
    case IntLitF(int_of_integer(v), _) => v.toString
    case FloatLitF(v, _)               => v
    case StringLitF(v, _)              => s"'${escapeSqlString(v)}'"
    case _                             => "NULL"

  private def extractInvariantChecks(inv: expr_full, fields: List[FieldDeclFull]): List[String] =
    flattenAnd(inv).flatMap:
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
      case b @ BinaryOpF(_, left, _, _) =>
        (extractFieldName(left), tryComparison(b, fields)) match
          case (Some(fieldName), Some(check)) =>
            List(check.replace("__COL__", Naming.toColumnName(fieldName)))
          case _ => Nil
      case _ => Nil

  private def tryComparison(
      b: BinaryOpF,
      @annotation.unused fields: List[FieldDeclFull]
  ): Option[String] =
    sqlOp(b.a).flatMap: op =>
      if isLiteral(b.c) then Some(s"__COL__ $op ${literalValue(b.c)}") else None

  private def applyPartialIndexConventions(
      tables: List[table_spec],
      entities: List[EntityDeclFull],
      conv: Option[conventions_decl_full]
  ): List[table_spec] =
    val rules = conv.toList.flatMap { case ConventionsDeclFull(rs, _) =>
      rs.collect {
        case ConventionRuleFull(target, "partial_index", Some(col), StringLitF(filt, _), _) =>
          (target, col, filt)
      }
    }
    if rules.isEmpty then tables
    else
      val entityByName = entities.map(e => e.a -> e).toMap
      val rulesByTable: Map[String, List[(String, String)]] = rules
        .flatMap { (target, col, filt) =>
          entityByName.get(target).map: e =>
            val tableName =
              Path.getConvention(conv, e.a, "db_table").getOrElse(Naming.toTableName(e.a))
            val colName = Naming.toColumnName(col)
            (tableName, colName, filt)
        }
        .groupBy(_._1)
        .view
        .mapValues(_.map((_, c, f) => (c, f)))
        .toMap
      tables.map: t =>
        rulesByTable.get(tableName(t)) match
          case None => t
          case Some(colFilters) =>
            val partials = colFilters.map: (col, filt) =>
              IndexSpec(
                s"idx_${tableName(t)}_${col}_partial",
                List(col),
                false,
                Some(filt)
              )
            TableSpec(
              tableName(t),
              tableEntityName(t),
              tableColumns(t),
              tablePrimaryKey(t),
              tableForeignKeys(t),
              tableChecks(t),
              tableIndexes(t) ++ partials
            )

  private def detectAggregateTriggers(
      entities: List[EntityDeclFull],
      tables: List[table_spec]
  ): List[trigger_spec] =
    val tablesByEntity = tables.map(t => tableEntityName(t) -> t).toMap
    val entityByName   = entities.map(e => e.a -> e).toMap
    val out            = List.newBuilder[trigger_spec]
    for parent <- entities do
      val parentTable  = tablesByEntity.get(parent.a)
      val parentFields = parent.c.collect { case f: FieldDeclFull => f }
      for inv <- parent.d do
        detectAggregateInvariant(inv) match
          case Some(DetectedAggregate(targetField, collectionFieldName, aggregate, sourceField)) =>
            val parentFieldOk   = parentFields.exists(_.a == targetField)
            val collectionField = parentFields.find(_.a == collectionFieldName)
            val childEntityName: Option[String] = collectionField.flatMap: f =>
              f.b match
                case SetTypeF(NamedTypeF(n, _), _) => Some(n)
                case SeqTypeF(NamedTypeF(n, _), _) => Some(n)
                case _                             => None
            // Find back-FK on child table to parent — must be unique (ambiguous
            // FKs to the same parent table can't be resolved without further
            // input; emit nothing rather than picking arbitrarily).
            val triggerOpt =
              for
                _           <- if parentFieldOk then Some(()) else None
                parentTbl   <- parentTable
                childName   <- childEntityName
                childEntity <- entityByName.get(childName)
                childTable  <- tablesByEntity.get(childName)
                matchingFks = tableForeignKeys(childTable).filter(fk =>
                                fkRefTable(fk) == tableName(parentTbl)
                              )
                fk             <- if matchingFks.size == 1 then matchingFks.headOption else None
                childFieldNames = childEntity.c.collect { case f: FieldDeclFull => f.a }.toSet
                _ <- sourceField match
                       case Some(sf) if !childFieldNames.contains(sf) => None
                       case _                                         => Some(())
              yield
                val parentSnake = Naming.toSnakeCase(parent.a)
                val funcName    = s"recalc_${parentSnake}_$targetField"
                TriggerSpec(
                  s"trg_$funcName",
                  funcName,
                  tableName(parentTbl),
                  Naming.toColumnName(targetField),
                  tableName(childTable),
                  fkColumn(fk),
                  aggregate,
                  sourceField.map(Naming.toColumnName)
                )
            triggerOpt.foreach(out += _)
          case None => ()
    out.result()
