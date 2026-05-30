package specrest.convention
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.ir.idx

object Schema:

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

  final private case class ClassifierCtx(
      aliasAList: List[(String, type_alias_decl_full)],
      enumAList: List[(String, enum_decl_full)],
      entityNamesList: List[String]
  )

  def deriveSchema(ir: ServiceIRFull): database_schema =
    val ix          = ir.idx
    val entities    = ix.entities
    val entityNames = ix.entityNames
    val cctx = ClassifierCtx(
      aliasAList = ix.aliasAList,
      enumAList = ix.enumAList,
      entityNamesList = ix.entityNamesList
    )
    val entityRefs = buildEntityRefMap(ir, cctx)

    val tables = List.newBuilder[table_spec]
    for entity <- entities do
      tables += deriveTable(entity, ir, entityNames, cctx, entityRefs)

    svcState(ir) match
      case Some(sd) =>
        for sf <- stdFields(sd) do
          stfType(sf) match
            case RelationTypeF(_, _: (MultSome | MultSet), _, _) =>
              deriveJunctionTable(sf, entityNames).foreach(tables += _)
            case _ => ()
      case None => ()

    val builtTables        = tables.result()
    val withPartialIndexes = applyPartialIndexConventions(builtTables, entities, svcConventions(ir))
    val triggers           = detectAggregateTriggers(entities, withPartialIndexes)
    DatabaseSchema(withPartialIndexes, triggers)

  private def buildEntityRefMap(
      ir: ServiceIRFull,
      cctx: ClassifierCtx
  ): Map[String, EntityRef] =
    ir.idx.entities.map { entity =>
      val fields = entFields(entity)
      val tableName = Path
        .getConvention(svcConventions(ir), entName(entity), "db_table")
        .getOrElse(Naming.toTableName(entName(entity)))
      val idField = fields.find(f => fldName(f) == "id")
      val idFkSqlType = idField match
        case None => "BIGINT"
        case Some(f) =>
          val mapped = mapTypeToColumn("id", fldType(f), cctx)
          // A FK referencing the PK must match its widened type.
          columnSqlType(mapped.column) match
            case "BIGSERIAL" => "BIGINT"
            case other       => widenExplicitIdPkSqlType("id", other)
      entName(entity) -> EntityRef(tableName, idFkSqlType)
    }.toMap

  private def deriveTable(
      entity: entity_decl_full,
      ir: ServiceIRFull,
      entityNames: Set[String],
      cctx: ClassifierCtx,
      entityRefs: Map[String, EntityRef]
  ): table_spec =
    val fields = entFields(entity)
    val tableName = Path
      .getConvention(svcConventions(ir), entName(entity), "db_table")
      .getOrElse(Naming.toTableName(entName(entity)))

    val entityFieldNames = fields.map(fldName).toSet
    val columns          = scala.collection.mutable.ListBuffer.empty[column_spec]
    if !entityFieldNames.contains("id") then
      columns += ColumnSpec("id", "BIGSERIAL", false, None)

    val foreignKeys = scala.collection.mutable.ListBuffer.empty[foreign_key_spec]
    val checks      = scala.collection.mutable.ListBuffer.empty[String]
    val indexes     = scala.collection.mutable.ListBuffer.empty[index_spec]

    for field <- fields do
      val colName    = Naming.toColumnName(fldName(field))
      val mapped     = mapFieldToColumn(field, cctx, entityRefs)
      val widenedSql = widenExplicitIdPkSqlType(fldName(field), columnSqlType(mapped.column))
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
      fldDefault(field).foreach: c =>
        checks ++= extractChecks(colName, c)
      for refinement <- aliasRefinements(fldType(field), cctx.aliasAList) do
        checks ++= extractChecks(colName, refinement)

    for inv <- entInvariants(entity) do
      checks ++= extractInvariantChecks(inv, fields)

    svcState(ir) match
      case Some(sd) =>
        for sf <- stdFields(sd) do
          stfType(sf) match
            case RelationTypeF(from, mult, to, _)
                if typeName(to).contains(entName(entity)) && (mult match
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

    val tsOverride    = Path.getConvention(svcConventions(ir), entName(entity), "db_timestamps")
    val addTimestamps = !tsOverride.contains("false")
    if addTimestamps && !columns.exists(c => columnName(c) == "created_at") then
      columns += ColumnSpec("created_at", "TIMESTAMPTZ", false, Some("NOW()"))
    if addTimestamps && !columns.exists(c => columnName(c) == "updated_at") then
      columns += ColumnSpec("updated_at", "TIMESTAMPTZ", false, Some("NOW()"))

    TableSpec(
      tableName,
      entName(entity),
      columns.toList,
      "id",
      foreignKeys.toList,
      checks.toList,
      indexes.toList
    )

  private def deriveJunctionTable(
      stateField: state_field_decl_full,
      entityNames: Set[String]
  ): Option[table_spec] =
    stfType(stateField) match
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
      field: field_decl_full,
      cctx: ClassifierCtx,
      entityRefs: Map[String, EntityRef]
  ): MappedField =
    val colName = Naming.toColumnName(fldName(field))
    val mapped  = mapTypeToColumn(colName, fldType(field), cctx)
    if mapped.foreignKey.isEmpty && colName.endsWith("_id") then
      val prefix       = colName.dropRight("_id".length)
      val targetEntity = cctx.entityNamesList.find(n => Naming.toSnakeCase(n) == prefix)
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
      cctx: ClassifierCtx
  ): MappedField =
    val classified =
      classifyColumnType(typeExpr, cctx.aliasAList, cctx.enumAList, cctx.entityNamesList)
    val (kind, nullable) = classified match
      case ClassifiedColumn(k, n) => (k, n)
    kind match
      case CkPrim(sqlType) =>
        MappedField(ColumnSpec(colName, sqlType, nullable, None), None, None)
      case CkEnum(vs) =>
        val values = vs.map(v => s"'${escapeSqlString(v)}'").mkString(", ")
        MappedField(
          ColumnSpec(colName, "TEXT", nullable, None),
          None,
          Some(s"$colName IN ($values)")
        )
      case CkEntityRef(name) =>
        val refTable = Naming.toTableName(name)
        val fkCol    = colName + "_id"
        MappedField(
          ColumnSpec(fkCol, "BIGINT", nullable, None),
          Some(ForeignKeySpec(fkCol, refTable, "id", "CASCADE")),
          None
        )
      case _: CkJsonArray =>
        MappedField(ColumnSpec(colName, "JSONB", nullable, Some("'[]'::jsonb")), None, None)
      case _: CkJsonObject =>
        MappedField(ColumnSpec(colName, "JSONB", nullable, Some("'{}'::jsonb")), None, None)
      case _: CkRelation =>
        MappedField(ColumnSpec(colName, "BIGINT", nullable, None), None, None)
      case _: CkUnknown =>
        MappedField(ColumnSpec(colName, "TEXT", nullable, None), None, None)

  private def escapeSqlString(s: String): String = s.replace("'", "''")

  private def extractChecks(colName: String, constraint: expr_full): List[String] =
    flattenAnd(constraint).flatMap: atom =>
      classifyColumnCheckAtom(atom) match
        case _: CcSkip => Nil
        case CcRegexMatch(pat) =>
          List(s"$colName ~ '${escapeSqlString(pat)}'")
        case CcLenCompare(op, n) =>
          sqlOp(op).toList.map(o => s"length($colName) $o $n")
        case CcValueCompare(op, n) =>
          sqlOp(op).toList.map(o => s"$colName $o $n")
        case CcLenLitCompare(op, rhs) =>
          sqlOp(op).toList.map(o => s"length($colName) $o ${literalValue(rhs)}")
        case CcValueLitCompare(op, rhs) =>
          sqlOp(op).toList.map(o => s"$colName $o ${literalValue(rhs)}")

  private def literalValue(e: expr_full): String = e match
    case IntLitF(v, _)    => v.toString
    case FloatLitF(v, _)  => v
    case StringLitF(v, _) => s"'${escapeSqlString(v)}'"
    case _                => "NULL"

  private def extractInvariantChecks(
      inv: expr_full,
      @annotation.unused fields: List[field_decl_full]
  ): List[String] =
    flattenAnd(inv).flatMap: atom =>
      classifyInvariantAtom(atom) match
        case _: IcSkip => Nil
        case IcInClause(fieldName, elements) =>
          val colName = Naming.toColumnName(fieldName)
          val values = elements.collect {
            case StringLitF(v, _) => s"'${escapeSqlString(v)}'"
            case IntLitF(v, _)    => v.toString
          }
          // Lifted predicate already required all elements to be literals.
          // The Scala collect narrows further to String/Int (Float / Bool /
          // None aren't supported as IN-clause values), so an atom with a
          // non-(String|Int) literal element is dropped here.
          if values.length == elements.length && values.nonEmpty then
            List(s"$colName IN (${values.mkString(", ")})")
          else Nil
        case IcCompare(fieldName, op, rhs) =>
          val colName = Naming.toColumnName(fieldName)
          // sqlOp is total on the operators the lifted classifier admits
          // (Gt/Lt/Ge/Le/Eq/Neq) — see SchemaDerive.sqlOp_def.
          sqlOp(op).toList.map(o => s"$colName $o ${literalValue(rhs)}")

  private def applyPartialIndexConventions(
      tables: List[table_spec],
      entities: List[entity_decl_full],
      conv: Option[conventions_decl_full]
  ): List[table_spec] =
    val rules = extractPartialIndexRules(conv)
    if rules.isEmpty then tables
    else
      val entityByName = entities.map(e => entName(e) -> e).toMap
      // Resolve target entity name → table name (regex + convention lookup, stays in Scala).
      val rulesByTable: Map[String, List[(String, String)]] = rules
        .flatMap { case (target, (col, filt)) =>
          entityByName.get(target).map: e =>
            val tableNm =
              Path.getConvention(conv, entName(e), "db_table").getOrElse(
                Naming.toTableName(entName(e))
              )
            (tableNm, Naming.toColumnName(col), filt)
        }
        .groupBy(_._1)
        .view
        .mapValues(_.map((_, c, f) => (c, f)))
        .toMap
      tables.map: t =>
        rulesByTable.get(tableName(t)) match
          case None             => t
          case Some(colFilters) => appendPartialIndexes(t, colFilters)

  private def detectAggregateTriggers(
      entities: List[entity_decl_full],
      tables: List[table_spec]
  ): List[trigger_spec] =
    val tablesByEntity = tables.map(t => tableEntityName(t) -> t).toMap
    val entitiesByName = entities.map(e => entName(e) -> e).toMap
    val out            = List.newBuilder[trigger_spec]
    for
      parent    <- entities
      parentTbl <- tablesByEntity.get(entName(parent))
      inv       <- entInvariants(parent)
    do
      detectAggregateInvariant(inv) match
        case Some(DetectedAggregate(targetField, collFieldName, agg, sourceField)) =>
          val parentFields = entFields(parent)
          val triggerOpt: Option[trigger_candidate] =
            for
              collField   <- parentFields.find(f => fldName(f) == collFieldName)
              childName   <- collectionElementEntityName(fldType(collField))
              childEntity <- entitiesByName.get(childName)
              childTable  <- tablesByEntity.get(childName)
              validated <- validateTrigger(
                             parentTbl,
                             parentFields,
                             childTable,
                             childEntity,
                             targetField,
                             agg,
                             sourceField
                           )
            yield validated
          triggerOpt match
            case Some(TriggerCandidate(parentTable, _, childTable, fkCol, _, srcField)) =>
              val parentSnake = Naming.toSnakeCase(entName(parent))
              val funcName    = s"recalc_${parentSnake}_$targetField"
              out += TriggerSpec(
                s"trg_$funcName",
                funcName,
                parentTable,
                Naming.toColumnName(targetField),
                childTable,
                fkCol,
                agg,
                srcField.map(Naming.toColumnName)
              )
            case None => ()
        case None => ()
    out.result()
