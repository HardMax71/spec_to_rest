package specrest.profile

import specrest.convention.Classify
import specrest.convention.EndpointSpec
import specrest.convention.Path
import specrest.convention.Schema
import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated.*

@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
object Annotate:

  def buildProfiledService(ir: ServiceIRFull, profileName: String): ProfiledService =
    val profile         = Registry.getProfile(profileName)
    val classifications = Classify.classifyOperations(ir)
    val endpoints       = Path.deriveEndpoints(classifications, ir)
    val schema          = Schema.deriveSchema(ir)

    val ix = ir.idx
    val ctx = TypeContext(
      entityNames = ix.entityNames,
      enumNames = ix.enumNames,
      aliasMap = ix.aliases.map(a => talName(a) -> talType(a)).toMap
    )

    val classificationMap = classifications.map(c => classificationOperationName(c) -> c).toMap
    val endpointMap       = endpoints.map(e => e.operationName -> e).toMap
    val tableMap          = schemaTables(schema).map(t => tableEntityName(t) -> t).toMap

    val entities = ix.entities.map { entity =>
      val tableName = Path
        .getConvention(svcConventions(ir), entName(entity), "db_table")
        .getOrElse(Naming.toTableName(entName(entity)))
      profileEntity(
        entName(entity),
        tableName,
        entFields(entity),
        profile,
        ctx,
        tableMap.contains(entName(entity))
      )
    }

    val operations = svcOperations(ir).map { op =>
      val classification = classificationMap(operName(op))
      val endpoint       = endpointMap(operName(op))
      profileOperation(
        op,
        classificationKind(classification),
        classificationTargetEntity(classification),
        endpoint,
        profile,
        ctx
      )
    }

    ProfiledService(ir, profile, endpoints, schema, entities, operations)

  def attachDafnyMethods(
      profiled: ProfiledService,
      bindings: Map[String, String]
  ): ProfiledService =
    if bindings.isEmpty then profiled
    else
      profiled.copy(operations =
        profiled.operations.map: op =>
          bindings.get(op.operationName) match
            case Some(callable) => op.copy(dafnyMethod = Some(callable))
            case None           => op
      )

  private def profileEntity(
      entityName: String,
      tableName: String,
      fields: List[field_decl],
      profile: DeploymentProfile,
      ctx: TypeContext,
      hasTable: Boolean
  ): ProfiledEntity =
    val _            = hasTable
    val snakeName    = Naming.toSnakeCase(entityName)
    val pluralSnake  = Naming.toSnakeCase(Naming.pluralize(entityName))
    val profiledFlds = fields.map(f => profileField(fldName(f), fldType(f), profile, ctx))
    ProfiledEntity(
      entityName = entityName,
      tableName = tableName,
      modelClassName = entityName,
      createSchemaName = s"${entityName}Create",
      readSchemaName = s"${entityName}Read",
      updateSchemaName = s"${entityName}Update",
      modelFileName = s"$snakeName.py",
      schemaFileName = s"$snakeName.py",
      routerFileName = s"$pluralSnake.py",
      fields = profiledFlds
    )

  private def profileField(
      fieldName: String,
      typeExpr: type_expr,
      profile: DeploymentProfile,
      ctx: TypeContext
  ): ProfiledField =
    val mapped   = TypeMap.mapType(typeExpr, profile, ctx)
    val colName  = Naming.toColumnName(fieldName)
    val resolved = TypeMap.resolveTypeExpr(typeExpr, ctx.aliasMap)
    val nullable = resolved.isInstanceOf[OptionTypeF]
    val columnType =
      widenExplicitIdPkSqlType(fieldName, resolveColumnType(typeExpr, profile, ctx))
    ProfiledField(
      fieldName = fieldName,
      columnName = colName,
      domainType = mapped.domain,
      validationType = mapped.validation,
      ormFieldType = mapped.orm,
      ormColumnType = columnType,
      nullable = nullable,
      hasDefault = false
    )

  private def resolveColumnType(
      typeExpr: type_expr,
      profile: DeploymentProfile,
      ctx: TypeContext
  ): String =
    val defaults = ColumnTypeDefaults.forProfile(profile)
    typeExpr match
      case NamedTypeF(name, _) =>
        profile.typeMap.get(name) match
          case Some(m)                                => m.ormColumn
          case None if ctx.entityNames.contains(name) => defaults.relation
          case None if ctx.enumNames.contains(name)   => defaults.enum_
          case None =>
            ctx.aliasMap.get(name) match
              case Some(alias) => resolveColumnType(alias, profile, ctx)
              case None        => defaults.fallback
      case OptionTypeF(inner, _)                               => resolveColumnType(inner, profile, ctx)
      case SetTypeF(_, _) | SeqTypeF(_, _) | MapTypeF(_, _, _) => defaults.collection
      case RelationTypeF(_, _, _, _)                           => defaults.relation

  private case class ColumnTypeDefaults(
      relation: String,
      enum_ : String,
      fallback: String,
      collection: String
  )

  private object ColumnTypeDefaults:
    private val Python     = ColumnTypeDefaults("Integer", "String", "String", "JSONB")
    private val Go         = ColumnTypeDefaults("BIGINT", "TEXT", "TEXT", "JSONB")
    private val TypeScript = ColumnTypeDefaults("INTEGER", "TEXT", "TEXT", "JSONB")

    def forProfile(profile: DeploymentProfile): ColumnTypeDefaults =
      profile.language match
        case "go" => Go
        case "ts" => TypeScript
        case _    => Python

  private def profileOperation(
      op: operation_decl,
      kind: operation_kind,
      targetEntity: Option[String],
      endpoint: EndpointSpec,
      profile: DeploymentProfile,
      ctx: TypeContext
  ): ProfiledOperation =
    ProfiledOperation(
      operationName = operName(op),
      handlerName = Naming.toSnakeCase(operName(op)),
      endpoint = endpoint,
      kind = kind,
      targetEntity = targetEntity,
      requestBodyFields =
        operInputs(op).map(p => profileField(prmName(p), prmType(p), profile, ctx)),
      responseFields =
        operOutputs(op).map(p => profileField(prmName(p), prmType(p), profile, ctx)),
      requiresAuth = operRequiresAuth(op).getOrElse(Nil)
    )
