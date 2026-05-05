package specrest.profile

import specrest.ir.generated.SpecRestGenerated.*

import specrest.convention.Classify
import specrest.convention.EndpointSpec
import specrest.convention.Naming
import specrest.convention.OperationKind
import specrest.convention.Path
import specrest.convention.Schema
import specrest.ir.*

@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
object Annotate:

  def buildProfiledService(ir: service_ir_full, profileName: String): ProfiledService =
    val profile         = Registry.getProfile(profileName)
    val classifications = Classify.classifyOperations(ir)
    val endpoints       = Path.deriveEndpoints(classifications, ir)
    val schema          = Schema.deriveSchema(ir)

    val ctx = TypeContext(
      entityNames = ir.c.map(_.name).toSet,
      enumNames = ir.d.map(_.name).toSet,
      aliasMap = ir.e.map(a => a.name -> a.typeExpr).toMap
    )

    val classificationMap = classifications.map(c => c.operationName -> c).toMap
    val endpointMap       = endpoints.map(e => e.operationName -> e).toMap
    val tableMap          = schema.tables.map(t => t.b -> t).toMap

    val entities = ir.c.map: entity =>
      val tableName = Path
        .getConvention(ir.n, entity.name, "db_table")
        .getOrElse(Naming.toTableName(entity.name))
      profileEntity(
        entity.name,
        tableName,
        entity.fields,
        profile,
        ctx,
        tableMap.contains(entity.name)
      )

    val operations = ir.g.map: op =>
      val classification = classificationMap(op.name)
      val endpoint       = endpointMap(op.name)
      profileOperation(op, classification.kind, classification.targetEntity, endpoint, profile, ctx)

    ProfiledService(ir, profile, endpoints, schema, entities, operations)

  private def profileEntity(
      entityName: String,
      tableName: String,
      fields: List[field_decl_full],
      profile: DeploymentProfile,
      ctx: TypeContext,
      hasTable: Boolean
  ): ProfiledEntity =
    val _            = hasTable
    val snakeName    = Naming.toSnakeCase(entityName)
    val pluralSnake  = Naming.toSnakeCase(Naming.pluralize(entityName))
    val profiledFlds = fields.map(f => profileField(f.name, f.typeExpr, profile, ctx))
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
      typeExpr: type_expr_full,
      profile: DeploymentProfile,
      ctx: TypeContext
  ): ProfiledField =
    val mapped     = TypeMap.mapType(typeExpr, profile, ctx)
    val colName    = Naming.toColumnName(fieldName)
    val resolved   = TypeMap.resolveTypeExpr(typeExpr, ctx.aliasMap)
    val nullable   = resolved.isInstanceOf[OptionTypeF]
    val columnType = resolveColumnType(typeExpr, profile, ctx)
    ProfiledField(
      fieldName = fieldName,
      columnName = colName,
      pythonType = mapped.python,
      pydanticType = mapped.pydantic,
      sqlalchemyType = mapped.sqlalchemy,
      sqlalchemyColumnType = columnType,
      nullable = nullable,
      hasDefault = false
    )

  private def resolveColumnType(
      typeExpr: type_expr_full,
      profile: DeploymentProfile,
      ctx: TypeContext
  ): String = typeExpr match
    case NamedTypeF(name, _) =>
      profile.typeMap.get(name) match
        case Some(m)                                => m.sqlalchemyColumn
        case None if ctx.entityNames.contains(name) => "Integer"
        case None if ctx.enumNames.contains(name)   => "String"
        case None =>
          ctx.aliasMap.get(name) match
            case Some(alias) => resolveColumnType(alias, profile, ctx)
            case None        => "String"
    case OptionTypeF(inner, _)                               => resolveColumnType(inner, profile, ctx)
    case SetTypeF(_, _) | SeqTypeF(_, _) | MapTypeF(_, _, _) => "JSONB"
    case RelationTypeF(_, _, _, _)                           => "Integer"

  private def profileOperation(
      op: operation_decl_full,
      kind: OperationKind,
      targetEntity: Option[String],
      endpoint: EndpointSpec,
      profile: DeploymentProfile,
      ctx: TypeContext
  ): ProfiledOperation =
    ProfiledOperation(
      operationName = op.name,
      handlerName = Naming.toSnakeCase(op.name),
      endpoint = endpoint,
      kind = kind,
      targetEntity = targetEntity,
      requestBodyFields = op.b.map(p => profileField(p.name, p.typeExpr, profile, ctx)),
      responseFields = op.c.map(p => profileField(p.name, p.typeExpr, profile, ctx))
    )
