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

  def buildProfiledService(ir: ServiceIRFull, profileName: String): ProfiledService =
    val profile         = Registry.getProfile(profileName)
    val classifications = Classify.classifyOperations(ir)
    val endpoints       = Path.deriveEndpoints(classifications, ir)
    val schema          = Schema.deriveSchema(ir)

    val ctx = TypeContext(
      entityNames = ir.c.collect { case e: EntityDeclFull => e.a }.toSet,
      enumNames = ir.d.collect { case e: EnumDeclFull => e.a }.toSet,
      aliasMap = ir.e.collect { case TypeAliasDeclFull(n, t, _, _) => n -> t }.toMap
    )

    val classificationMap = classifications.map(c => c.operationName -> c).toMap
    val endpointMap       = endpoints.map(e => e.operationName -> e).toMap
    val tableMap          = schema.tables.map(t => t.entityName -> t).toMap

    val entities = ir.c.collect { case entity: EntityDeclFull =>
      val tableName = Path
        .getConvention(ir.n, entity.a, "db_table")
        .getOrElse(Naming.toTableName(entity.a))
      profileEntity(
        entity.a,
        tableName,
        entity.c.collect { case f: FieldDeclFull => f },
        profile,
        ctx,
        tableMap.contains(entity.a)
      )
    }

    val operations = ir.g.collect { case op: OperationDeclFull =>
      val classification = classificationMap(op.a)
      val endpoint       = endpointMap(op.a)
      profileOperation(op, classification.kind, classification.targetEntity, endpoint, profile, ctx)
    }

    ProfiledService(ir, profile, endpoints, schema, entities, operations)

  private def profileEntity(
      entityName: String,
      tableName: String,
      fields: List[FieldDeclFull],
      profile: DeploymentProfile,
      ctx: TypeContext,
      hasTable: Boolean
  ): ProfiledEntity =
    val _            = hasTable
    val snakeName    = Naming.toSnakeCase(entityName)
    val pluralSnake  = Naming.toSnakeCase(Naming.pluralize(entityName))
    val profiledFlds = fields.map { case FieldDeclFull(n, t, _, _) => profileField(n, t, profile, ctx) }
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
      op: OperationDeclFull,
      kind: OperationKind,
      targetEntity: Option[String],
      endpoint: EndpointSpec,
      profile: DeploymentProfile,
      ctx: TypeContext
  ): ProfiledOperation =
    ProfiledOperation(
      operationName = op.a,
      handlerName = Naming.toSnakeCase(op.a),
      endpoint = endpoint,
      kind = kind,
      targetEntity = targetEntity,
      requestBodyFields = op.b.collect { case ParamDeclFull(n, t, _) => profileField(n, t, profile, ctx) },
      responseFields = op.c.collect { case ParamDeclFull(n, t, _) => profileField(n, t, profile, ctx) }
    )
