package specrest.profile

import specrest.convention.{Classify, EndpointSpec, Naming, OperationKind, Path, Schema}
import specrest.ir.*

object Annotate:

  def buildProfiledService(ir: ServiceIR, profileName: String): ProfiledService =
    val profile         = Registry.getProfile(profileName)
    val classifications = Classify.classifyOperations(ir)
    val endpoints       = Path.deriveEndpoints(classifications, ir)
    val schema          = Schema.deriveSchema(ir)

    val ctx = TypeContext(
      entityNames = ir.entities.map(_.name).toSet,
      enumNames   = ir.enums.map(_.name).toSet,
      aliasMap    = ir.typeAliases.map(a => a.name -> a.typeExpr).toMap,
    )

    val classificationMap = classifications.map(c => c.operationName -> c).toMap
    val endpointMap       = endpoints.map(e => e.operationName -> e).toMap
    val tableMap          = schema.tables.map(t => t.entityName -> t).toMap

    val entities = ir.entities.map: entity =>
      val tableName = Path
        .getConvention(ir.conventions, entity.name, "db_table")
        .getOrElse(Naming.toTableName(entity.name))
      profileEntity(entity.name, tableName, entity.fields, profile, ctx, tableMap.contains(entity.name))

    val operations = ir.operations.map: op =>
      val classification = classificationMap(op.name)
      val endpoint       = endpointMap(op.name)
      profileOperation(op, classification.kind, classification.targetEntity, endpoint, profile, ctx)

    ProfiledService(ir, profile, endpoints, schema, entities, operations)

  private def profileEntity(
      entityName: String,
      tableName: String,
      fields: List[FieldDecl],
      profile: DeploymentProfile,
      ctx: TypeContext,
      hasTable: Boolean,
  ): ProfiledEntity =
    val _            = hasTable
    val snakeName    = Naming.toSnakeCase(entityName)
    val pluralSnake  = Naming.toSnakeCase(Naming.pluralize(entityName))
    val profiledFlds = fields.map(f => profileField(f.name, f.typeExpr, profile, ctx))
    ProfiledEntity(
      entityName       = entityName,
      tableName        = tableName,
      modelClassName   = entityName,
      createSchemaName = s"${entityName}Create",
      readSchemaName   = s"${entityName}Read",
      updateSchemaName = s"${entityName}Update",
      modelFileName    = s"$snakeName.py",
      schemaFileName   = s"$snakeName.py",
      routerFileName   = s"$pluralSnake.py",
      fields           = profiledFlds,
    )

  private def profileField(
      fieldName: String,
      typeExpr: TypeExpr,
      profile: DeploymentProfile,
      ctx: TypeContext,
  ): ProfiledField =
    val mapped     = TypeMap.mapType(typeExpr, profile, ctx)
    val colName    = Naming.toColumnName(fieldName)
    val resolved   = TypeMap.resolveTypeExpr(typeExpr, ctx.aliasMap)
    val nullable   = resolved.isInstanceOf[TypeExpr.OptionType]
    val columnType = resolveColumnType(typeExpr, profile, ctx)
    ProfiledField(
      fieldName            = fieldName,
      columnName           = colName,
      pythonType           = mapped.python,
      pydanticType         = mapped.pydantic,
      sqlalchemyType       = mapped.sqlalchemy,
      sqlalchemyColumnType = columnType,
      nullable             = nullable,
      hasDefault           = false,
    )

  private def resolveColumnType(
      typeExpr: TypeExpr,
      profile: DeploymentProfile,
      ctx: TypeContext,
  ): String = typeExpr match
    case TypeExpr.NamedType(name, _) =>
      profile.typeMap.get(name) match
        case Some(m) => m.sqlalchemyColumn
        case None if ctx.entityNames.contains(name) => "Integer"
        case None if ctx.enumNames.contains(name)    => "String"
        case None =>
          ctx.aliasMap.get(name) match
            case Some(alias) => resolveColumnType(alias, profile, ctx)
            case None        => "String"
    case TypeExpr.OptionType(inner, _)              => resolveColumnType(inner, profile, ctx)
    case TypeExpr.SetType(_, _) | TypeExpr.SeqType(_, _) | TypeExpr.MapType(_, _, _) => "JSONB"
    case TypeExpr.RelationType(_, _, _, _)           => "Integer"

  private def profileOperation(
      op: OperationDecl,
      kind: OperationKind,
      targetEntity: Option[String],
      endpoint: EndpointSpec,
      profile: DeploymentProfile,
      ctx: TypeContext,
  ): ProfiledOperation =
    ProfiledOperation(
      operationName     = op.name,
      handlerName       = Naming.toSnakeCase(op.name),
      endpoint          = endpoint,
      kind              = kind,
      targetEntity      = targetEntity,
      requestBodyFields = op.inputs.map(p => profileField(p.name, p.typeExpr, profile, ctx)),
      responseFields    = op.outputs.map(p => profileField(p.name, p.typeExpr, profile, ctx)),
    )
