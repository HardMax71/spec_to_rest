package specrest.codegen.openapi

import specrest.codegen.RouteKind
import specrest.codegen.SensitiveFields
import specrest.convention.HttpMethod
import specrest.convention.Naming
import specrest.convention.OperationKind
import specrest.convention.ParamSpec
import specrest.ir.EntityDecl
import specrest.ir.EnumDecl
import specrest.ir.Expr
import specrest.ir.TypeAliasDecl
import specrest.ir.TypeExpr
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

import scala.jdk.CollectionConverters.*

final case class SchemaObject(
    `type`: Option[List[String]] = None,
    format: Option[String] = None,
    minLength: Option[Int] = None,
    maxLength: Option[Int] = None,
    minimum: Option[Double] = None,
    maximum: Option[Double] = None,
    exclusiveMinimum: Option[Double] = None,
    exclusiveMaximum: Option[Double] = None,
    minItems: Option[Int] = None,
    maxItems: Option[Int] = None,
    pattern: Option[String] = None,
    enum_ : Option[List[String]] = None,
    items: Option[SchemaObject] = None,
    ref: Option[String] = None,
    required: Option[List[String]] = None,
    properties: Option[Map[String, SchemaObject]] = None,
    additionalProperties: Option[SchemaObjectOrBool] = None,
    anyOf: Option[List[SchemaObject]] = None,
    description: Option[String] = None,
    includeNullInEnum: Boolean = false
)

sealed trait SchemaObjectOrBool
final case class SOBSchema(schema: SchemaObject) extends SchemaObjectOrBool
final case class SOBBool(v: Boolean)             extends SchemaObjectOrBool

final case class ParameterObject(
    name: String,
    in: String,
    required: Boolean,
    description: Option[String],
    schema: SchemaObject
)

final case class MediaTypeObject(schema: SchemaObject)
final case class HeaderObject(description: Option[String], schema: SchemaObject)

final case class RequestBodyObject(
    required: Boolean,
    description: Option[String],
    content: Map[String, MediaTypeObject]
)

final case class ResponseObject(
    description: String,
    headers: Option[Map[String, HeaderObject]],
    content: Option[Map[String, MediaTypeObject]]
)

final case class OperationObject(
    operationId: String,
    summary: Option[String],
    description: Option[String],
    tags: List[String],
    parameters: Option[List[ParameterObject]],
    requestBody: Option[RequestBodyObject],
    responses: Map[String, ResponseObject]
)

final case class PathItemObject(
    get: Option[OperationObject] = None,
    post: Option[OperationObject] = None,
    put: Option[OperationObject] = None,
    patch: Option[OperationObject] = None,
    delete: Option[OperationObject] = None
)

final case class ComponentsObject(schemas: Map[String, SchemaObject])
final case class InfoObject(title: String, version: String, description: Option[String])
final case class ServerObject(url: String, description: Option[String])
final case class TagObject(name: String, description: Option[String])

final case class OpenApiDocument(
    openapi: String,
    info: InfoObject,
    servers: List[ServerObject],
    paths: Map[String, PathItemObject],
    components: ComponentsObject,
    tags: List[TagObject]
)

final case class BuildContext(
    aliasMap: Map[String, TypeAliasDecl],
    enumMap: Map[String, EnumDecl],
    entityNames: Set[String],
    entityDecls: Map[String, EntityDecl]
)

// -- Constraints extraction ------------------------------------

final case class JsonSchemaConstraints(
    minLength: Option[Int] = None,
    maxLength: Option[Int] = None,
    minimum: Option[Double] = None,
    maximum: Option[Double] = None,
    exclusiveMinimum: Option[Double] = None,
    exclusiveMaximum: Option[Double] = None,
    pattern: Option[String] = None,
    enum_ : Option[List[String]] = None
)

object Constraints:

  def extractFieldConstraints(
      typeExpr: TypeExpr,
      constraint: Option[Expr],
      aliasMap: Map[String, TypeAliasDecl],
      enumMap: Map[String, EnumDecl]
  ): JsonSchemaConstraints =
    var out = JsonSchemaConstraints()
    out = collectFromType(typeExpr, aliasMap, enumMap, out)
    constraint match
      case Some(c) => visitConstraint(c, out)
      case None    => out

  private def collectFromType(
      typeExpr: TypeExpr,
      aliasMap: Map[String, TypeAliasDecl],
      enumMap: Map[String, EnumDecl],
      out: JsonSchemaConstraints
  ): JsonSchemaConstraints = typeExpr match
    case TypeExpr.OptionType(inner, _) =>
      collectFromType(inner, aliasMap, enumMap, out)
    case TypeExpr.NamedType(name, _) =>
      enumMap.get(name) match
        case Some(e) => out.copy(enum_ = Some(e.values))
        case None =>
          aliasMap.get(name) match
            case Some(alias) =>
              val afterType = collectFromType(alias.typeExpr, aliasMap, enumMap, out)
              alias.constraint match
                case Some(c) => visitConstraint(c, afterType)
                case None    => afterType
            case None => out
    case _ => out

  private def visitConstraint(
      expr: Expr,
      out: JsonSchemaConstraints
  ): JsonSchemaConstraints = expr match
    case Expr.BinaryOp(specrest.ir.BinOp.And, l, r, _) =>
      visitConstraint(r, visitConstraint(l, out))
    case Expr.Matches(inner, pattern, _) if isValueRef(inner) =>
      out.copy(pattern = Some(pattern))
    case b @ Expr.BinaryOp(_, _, _, _) =>
      applyComparison(b, out)
    case _ => out

  private def applyComparison(
      expr: Expr.BinaryOp,
      out: JsonSchemaConstraints
  ): JsonSchemaConstraints =
    literalNumber(expr.right) match
      case None => out
      case Some(n) =>
        if isLenCall(expr.left) then applyLengthBound(expr.op, n, out)
        else if isValueRef(expr.left) then applyNumericBound(expr.op, n, out)
        else out

  private def applyLengthBound(
      op: specrest.ir.BinOp,
      n: Double,
      out: JsonSchemaConstraints
  ): JsonSchemaConstraints =
    if n != n.toInt.toDouble || n < 0 then out
    else
      import specrest.ir.BinOp.*
      val ni = n.toInt
      op match
        case Ge => out.copy(minLength = tightenLower(out.minLength, ni))
        case Le => out.copy(maxLength = tightenUpper(out.maxLength, ni))
        case Gt => out.copy(minLength = tightenLower(out.minLength, ni + 1))
        case Lt =>
          if ni - 1 < 0 then out
          else out.copy(maxLength = tightenUpper(out.maxLength, ni - 1))
        case Eq =>
          out.copy(
            minLength = tightenLower(out.minLength, ni),
            maxLength = tightenUpper(out.maxLength, ni)
          )
        case _ => out

  private def applyNumericBound(
      op: specrest.ir.BinOp,
      n: Double,
      out: JsonSchemaConstraints
  ): JsonSchemaConstraints =
    import specrest.ir.BinOp.*
    op match
      case Ge => out.copy(minimum = tightenLowerD(out.minimum, n))
      case Le => out.copy(maximum = tightenUpperD(out.maximum, n))
      case Gt => out.copy(exclusiveMinimum = tightenLowerD(out.exclusiveMinimum, n))
      case Lt => out.copy(exclusiveMaximum = tightenUpperD(out.exclusiveMaximum, n))
      case Eq =>
        out.copy(
          minimum = tightenLowerD(out.minimum, n),
          maximum = tightenUpperD(out.maximum, n)
        )
      case _ => out

  private def tightenLower(cur: Option[Int], n: Int): Option[Int] =
    Some(cur.fold(n)(math.max(_, n)))
  private def tightenUpper(cur: Option[Int], n: Int): Option[Int] =
    Some(cur.fold(n)(math.min(_, n)))
  private def tightenLowerD(cur: Option[Double], n: Double): Option[Double] =
    Some(cur.fold(n)(math.max(_, n)))
  private def tightenUpperD(cur: Option[Double], n: Double): Option[Double] =
    Some(cur.fold(n)(math.min(_, n)))

  private def isLenCall(expr: Expr): Boolean = expr match
    case Expr.Call(Expr.Identifier("len", _), _, _) => true
    case _                                          => false

  private def isValueRef(expr: Expr): Boolean = expr match
    case Expr.Identifier("value", _) => true
    case _                           => false

  private def literalNumber(expr: Expr): Option[Double] = expr match
    case Expr.IntLit(v, _)   => Some(v.toDouble)
    case Expr.FloatLit(v, _) => Some(v)
    case _                   => None

// -- Schema generation ----------------------------------------

final case class FieldSchema(schema: SchemaObject, nullable: Boolean)

object Schema:

  private val PrimitiveSchemas: Map[String, SchemaObject] = Map(
    "String"   -> SchemaObject(`type` = Some(List("string"))),
    "Int"      -> SchemaObject(`type` = Some(List("integer"))),
    "Float"    -> SchemaObject(`type` = Some(List("number"))),
    "Bool"     -> SchemaObject(`type` = Some(List("boolean"))),
    "Boolean"  -> SchemaObject(`type` = Some(List("boolean"))),
    "DateTime" -> SchemaObject(`type` = Some(List("string")), format = Some("date-time")),
    "Date"     -> SchemaObject(`type` = Some(List("string")), format = Some("date")),
    "UUID"     -> SchemaObject(`type` = Some(List("string")), format = Some("uuid")),
    "Decimal"  -> SchemaObject(`type` = Some(List("string")), format = Some("decimal")),
    "Bytes"    -> SchemaObject(`type` = Some(List("string")), format = Some("byte")),
    "Money"    -> SchemaObject(`type` = Some(List("integer")))
  )

  def fieldToSchema(
      typeExpr: TypeExpr,
      constraint: Option[Expr],
      aliasMap: Map[String, TypeAliasDecl],
      enumMap: Map[String, EnumDecl],
      entityNames: Set[String]
  ): FieldSchema =
    val nullable = typeExpr match
      case _: TypeExpr.OptionType => true
      case _                      => false
    val effective = typeExpr match
      case TypeExpr.OptionType(inner, _) => inner
      case t                             => t
    val cs = Constraints.extractFieldConstraints(effective, constraint, aliasMap, enumMap)
    FieldSchema(typeExprToSchema(effective, cs, aliasMap, enumMap, entityNames), nullable)

  def makeNullable(schema: SchemaObject): SchemaObject =
    if schema.ref.isDefined then
      SchemaObject(anyOf = Some(List(schema, SchemaObject(`type` = Some(List("null"))))))
    else
      schema.`type` match
        case None =>
          SchemaObject(anyOf = Some(List(schema, SchemaObject(`type` = Some(List("null"))))))
        case Some(current) =>
          if current.contains("null") then schema
          else schema.copy(`type` = Some(current :+ "null"))

  private def typeExprToSchema(
      typeExpr: TypeExpr,
      constraints: JsonSchemaConstraints,
      aliasMap: Map[String, TypeAliasDecl],
      enumMap: Map[String, EnumDecl],
      entityNames: Set[String]
  ): SchemaObject = typeExpr match
    case TypeExpr.NamedType(name, _) =>
      namedTypeSchema(name, constraints, aliasMap, enumMap, entityNames)
    case TypeExpr.SetType(inner, _) =>
      buildArraySchema(inner, constraints, aliasMap, enumMap, entityNames)
    case TypeExpr.SeqType(inner, _) =>
      buildArraySchema(inner, constraints, aliasMap, enumMap, entityNames)
    case TypeExpr.MapType(_, v, _) =>
      val value = fieldToSchema(v, None, aliasMap, enumMap, entityNames)
      val ap    = if value.nullable then makeNullable(value.schema) else value.schema
      SchemaObject(
        `type` = Some(List("object")),
        additionalProperties = Some(SOBSchema(ap))
      )
    case TypeExpr.OptionType(inner, _) =>
      typeExprToSchema(inner, constraints, aliasMap, enumMap, entityNames)
    case TypeExpr.RelationType(_, _, _, _) =>
      SchemaObject(`type` = Some(List("integer")))

  private def namedTypeSchema(
      name: String,
      c: JsonSchemaConstraints,
      aliasMap: Map[String, TypeAliasDecl],
      enumMap: Map[String, EnumDecl],
      entityNames: Set[String]
  ): SchemaObject =
    PrimitiveSchemas.get(name) match
      case Some(p) => mergeConstraints(p, c)
      case None =>
        enumMap.get(name) match
          case Some(e) =>
            SchemaObject(`type` = Some(List("string")), enum_ = Some(e.values))
          case None =>
            if entityNames.contains(name) then
              SchemaObject(ref = Some(s"#/components/schemas/${name}Read"))
            else
              aliasMap.get(name) match
                case Some(alias) =>
                  typeExprToSchema(alias.typeExpr, c, aliasMap, enumMap, entityNames)
                case None =>
                  mergeConstraints(SchemaObject(`type` = Some(List("string"))), c)

  private def buildArraySchema(
      inner: TypeExpr,
      c: JsonSchemaConstraints,
      aliasMap: Map[String, TypeAliasDecl],
      enumMap: Map[String, EnumDecl],
      entityNames: Set[String]
  ): SchemaObject =
    val innerSchema = fieldToSchema(inner, None, aliasMap, enumMap, entityNames)
    val items =
      if innerSchema.nullable then makeNullable(innerSchema.schema) else innerSchema.schema
    SchemaObject(
      `type` = Some(List("array")),
      items = Some(items),
      minItems = c.minLength,
      maxItems = c.maxLength
    )

  private def mergeConstraints(base: SchemaObject, c: JsonSchemaConstraints): SchemaObject =
    base.copy(
      minLength = c.minLength.orElse(base.minLength),
      maxLength = c.maxLength.orElse(base.maxLength),
      minimum = c.minimum.orElse(base.minimum),
      maximum = c.maximum.orElse(base.maximum),
      exclusiveMinimum = c.exclusiveMinimum.orElse(base.exclusiveMinimum),
      exclusiveMaximum = c.exclusiveMaximum.orElse(base.exclusiveMaximum),
      pattern = c.pattern.orElse(base.pattern),
      enum_ = c.enum_.orElse(base.enum_)
    )

// -- Components / Paths / Build / Serialize --------------------

object Components:

  final private case class DecoratedField(name: String, schema: SchemaObject, nullable: Boolean)

  def buildComponents(profiled: ProfiledService, ctx: BuildContext): ComponentsObject =
    val schemas = collection.mutable.LinkedHashMap.empty[String, SchemaObject]
    schemas("ErrorResponse") = errorResponseSchema
    for entity <- profiled.entities do
      ctx.entityDecls.get(entity.entityName).foreach: decl =>
        val decorated = decorateFields(entity, decl, ctx)
        schemas(entity.createSchemaName) = createSchema(decorated, entity)
        schemas(entity.readSchemaName) = readSchema(decorated, entity)
        schemas(entity.updateSchemaName) = updateSchema(decorated, entity)
    ComponentsObject(schemas.toMap)

  private def decorateFields(
      entity: ProfiledEntity,
      decl: EntityDecl,
      ctx: BuildContext
  ): List[DecoratedField] =
    entity.fields.zipWithIndex.map: (profiledField, idx) =>
      val irField = decl.fields(idx)
      val fs = Schema.fieldToSchema(
        irField.typeExpr,
        irField.constraint,
        ctx.aliasMap,
        ctx.enumMap,
        ctx.entityNames
      )
      DecoratedField(profiledField.columnName, fs.schema, fs.nullable)

  private def nonIdFields(fs: List[DecoratedField]): List[DecoratedField] =
    fs.filterNot(_.name == "id")

  private def fieldProperty(f: DecoratedField): SchemaObject =
    if f.nullable then Schema.makeNullable(f.schema) else f.schema

  private def createSchema(fields: List[DecoratedField], entity: ProfiledEntity): SchemaObject =
    val fs = nonIdFields(fields)
    SchemaObject(
      `type` = Some(List("object")),
      description = Some(s"Create payload for ${entity.entityName}"),
      required = Some(fs.filterNot(_.nullable).map(_.name)),
      properties = Some(fs.map(f => f.name -> fieldProperty(f)).toMap)
    )

  private def readSchema(fields: List[DecoratedField], entity: ProfiledEntity): SchemaObject =
    val fs    = nonIdFields(fields).filterNot(f => SensitiveFields.isSensitive(f.name))
    val props = collection.mutable.LinkedHashMap.empty[String, SchemaObject]
    props("id") = SchemaObject(`type` = Some(List("integer")))
    for f <- fs do props(f.name) = fieldProperty(f)
    SchemaObject(
      `type` = Some(List("object")),
      description = Some(s"Read view for ${entity.entityName}"),
      required = Some("id" +: fs.filterNot(_.nullable).map(_.name)),
      properties = Some(props.toMap)
    )

  private def updateSchema(fields: List[DecoratedField], entity: ProfiledEntity): SchemaObject =
    val fs = nonIdFields(fields)
    SchemaObject(
      `type` = Some(List("object")),
      description = Some(s"Update payload for ${entity.entityName}"),
      properties = Some(fs.map(f => f.name -> Schema.makeNullable(f.schema)).toMap)
    )

  private def errorResponseSchema: SchemaObject =
    SchemaObject(
      `type` = Some(List("object")),
      description = Some("Standard error response body"),
      required = Some(List("detail")),
      properties = Some(
        Map(
          "detail" -> SchemaObject(
            `type` = Some(List("string")),
            description = Some("Human-readable error description")
          )
        )
      )
    )

object Paths:

  def buildPaths(
      profiled: ProfiledService,
      ctx: BuildContext
  ): Map[String, PathItemObject] =
    val paths        = collection.mutable.LinkedHashMap.empty[String, PathItemObject]
    val entityByName = profiled.entities.map(e => e.entityName -> e).toMap
    for op <- profiled.operations do
      op.targetEntity.flatMap(entityByName.get).foreach: entity =>
        val operation = buildOperation(op, entity, ctx)
        val existing  = paths.getOrElse(op.endpoint.path, PathItemObject())
        paths(op.endpoint.path) = setMethod(existing, op.endpoint.method, operation)
    paths("/health") = setMethod(
      paths.getOrElse("/health", PathItemObject()),
      HttpMethod.GET,
      healthOperation
    )
    paths.toMap

  private def healthOperation: OperationObject =
    OperationObject(
      operationId = "health_check",
      summary = Some("Health check"),
      description = Some("Returns 200 if the service is running."),
      tags = List("infrastructure"),
      parameters = None,
      requestBody = None,
      responses = Map(
        "200" -> ResponseObject(
          description = "Service is healthy",
          headers = None,
          content = Some(Map("application/json" -> MediaTypeObject(SchemaObject(
            `type` = Some(List("object")),
            required = Some(List("status")),
            properties = Some(Map(
              "status" -> SchemaObject(
                `type` = Some(List("string")),
                enum_ = Some(List("ok"))
              )
            ))
          ))))
        )
      )
    )

  private def setMethod(
      item: PathItemObject,
      method: HttpMethod,
      op: OperationObject
  ): PathItemObject = method match
    case HttpMethod.GET    => item.copy(get = Some(op))
    case HttpMethod.POST   => item.copy(post = Some(op))
    case HttpMethod.PUT    => item.copy(put = Some(op))
    case HttpMethod.PATCH  => item.copy(patch = Some(op))
    case HttpMethod.DELETE => item.copy(delete = Some(op))

  private def buildOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      ctx: BuildContext
  ): OperationObject =
    val routeKind   = RouteKind.classify(op)
    val parameters  = buildParameters(op, ctx)
    val requestBody = buildRequestBody(op, entity, routeKind, ctx)
    val responses   = buildResponses(op, entity, routeKind)
    OperationObject(
      operationId = op.handlerName,
      summary = Some(op.operationName),
      description = None,
      tags = List(Naming.toSnakeCase(entity.entityName)),
      parameters = if parameters.nonEmpty then Some(parameters) else None,
      requestBody = requestBody,
      responses = responses
    )

  private def buildParameters(op: ProfiledOperation, ctx: BuildContext): List[ParameterObject] =
    op.endpoint.pathParams.map(p => paramObject(p, "path", ctx)) ++
      op.endpoint.queryParams.map(p => paramObject(p, "query", ctx))

  private def paramObject(p: ParamSpec, location: String, ctx: BuildContext): ParameterObject =
    val fs = Schema.fieldToSchema(p.typeExpr, None, ctx.aliasMap, ctx.enumMap, ctx.entityNames)
    ParameterObject(
      name = p.name,
      in = location,
      required = if location == "path" then true else p.required,
      description = None,
      schema = if fs.nullable then Schema.makeNullable(fs.schema) else fs.schema
    )

  private def buildRequestBody(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      routeKind: RouteKind,
      ctx: BuildContext
  ): Option[RequestBodyObject] =
    if op.endpoint.method == HttpMethod.GET || op.endpoint.method == HttpMethod.DELETE then None
    else if op.endpoint.bodyParams.isEmpty then None
    else if routeKind == RouteKind.Create || op.kind == OperationKind.Create then
      Some(componentBody(entity.createSchemaName))
    else if op.kind == OperationKind.Replace || op.kind == OperationKind.PartialUpdate then
      Some(componentBody(entity.updateSchemaName))
    else
      inlineBodySchema(op, ctx).map: inline =>
        RequestBodyObject(
          required = true,
          description = None,
          content = Map("application/json" -> MediaTypeObject(inline))
        )

  private def componentBody(schemaName: String): RequestBodyObject =
    RequestBodyObject(
      required = true,
      description = None,
      content = Map("application/json" -> MediaTypeObject(
        SchemaObject(ref = Some(s"#/components/schemas/$schemaName"))
      ))
    )

  private def inlineBodySchema(
      op: ProfiledOperation,
      ctx: BuildContext
  ): Option[SchemaObject] =
    val params = op.endpoint.bodyParams
    if params.isEmpty then None
    else
      val properties = collection.mutable.LinkedHashMap.empty[String, SchemaObject]
      val required   = List.newBuilder[String]
      for p <- params do
        val fs = Schema.fieldToSchema(p.typeExpr, None, ctx.aliasMap, ctx.enumMap, ctx.entityNames)
        properties(p.name) = if fs.nullable then Schema.makeNullable(fs.schema) else fs.schema
        if p.required then required += p.name
      val req = required.result()
      Some(SchemaObject(
        `type` = Some(List("object")),
        properties = Some(properties.toMap),
        required = if req.nonEmpty then Some(req) else None
      ))

  private def buildResponses(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      routeKind: RouteKind
  ): Map[String, ResponseObject] =
    val status    = op.endpoint.successStatus.toString
    val success   = buildSuccessResponse(op, entity, routeKind)
    val responses = collection.mutable.LinkedHashMap.empty[String, ResponseObject]
    responses(status) = success

    val hasPathParam = op.endpoint.pathParams.nonEmpty
    val needs404 = hasPathParam && (
      op.kind == OperationKind.Read || op.kind == OperationKind.Delete ||
        op.kind == OperationKind.Replace || op.kind == OperationKind.PartialUpdate ||
        op.kind == OperationKind.Transition || op.kind == OperationKind.CreateChild
    )
    if needs404 then responses("404") = errorResponseRef("Resource not found")

    val acceptsInput = op.endpoint.bodyParams.nonEmpty ||
      op.endpoint.queryParams.nonEmpty || op.endpoint.pathParams.nonEmpty
    if acceptsInput then responses("422") = errorResponseRef("Validation error")

    responses.toMap

  private def buildSuccessResponse(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      routeKind: RouteKind
  ): ResponseObject =
    val status = op.endpoint.successStatus
    if status == 204 then ResponseObject("No content", None, None)
    else if status >= 300 && status < 400 then
      ResponseObject(
        description = "Redirect",
        headers = Some(Map(
          "Location" -> HeaderObject(
            description = Some("Target URL"),
            schema = SchemaObject(`type` = Some(List("string")), format = Some("uri"))
          )
        )),
        content = None
      )
    else if routeKind == RouteKind.List then
      jsonResponse(
        "Successful response",
        SchemaObject(
          `type` = Some(List("array")),
          items = Some(SchemaObject(ref = Some(s"#/components/schemas/${entity.readSchemaName}")))
        )
      )
    else if routeKind == RouteKind.Create || routeKind == RouteKind.Read ||
      op.kind == OperationKind.Replace || op.kind == OperationKind.PartialUpdate
    then
      jsonResponse(
        "Successful response",
        SchemaObject(
          ref = Some(s"#/components/schemas/${entity.readSchemaName}")
        )
      )
    else ResponseObject("Successful response", None, None)

  private def jsonResponse(description: String, schema: SchemaObject): ResponseObject =
    ResponseObject(
      description,
      headers = None,
      content = Some(Map("application/json" -> MediaTypeObject(schema)))
    )

  private def errorResponseRef(description: String): ResponseObject =
    ResponseObject(
      description,
      headers = None,
      content = Some(Map("application/json" -> MediaTypeObject(
        SchemaObject(ref = Some("#/components/schemas/ErrorResponse"))
      )))
    )

object OpenApi:

  def buildOpenApiDocument(profiled: ProfiledService): OpenApiDocument =
    val aliasMap    = profiled.ir.typeAliases.map(a => a.name -> a).toMap
    val enumMap     = profiled.ir.enums.map(e => e.name -> e).toMap
    val entityDecls = profiled.ir.entities.map(e => e.name -> e).toMap
    val entityNames = profiled.entities.map(_.entityName).toSet
    val ctx         = BuildContext(aliasMap, enumMap, entityNames, entityDecls)

    OpenApiDocument(
      openapi = "3.1.0",
      info = InfoObject(
        title = profiled.ir.name,
        version = "0.1.0",
        description = Some(s"API for ${profiled.ir.name}. Generated from formal specification.")
      ),
      servers = List(
        ServerObject("http://localhost:8000", Some("Local development"))
      ),
      paths = Paths.buildPaths(profiled, ctx),
      components = Components.buildComponents(profiled, ctx),
      tags = buildTags(profiled)
    )

  private def buildTags(profiled: ProfiledService): List[TagObject] =
    profiled.entities.map: e =>
      TagObject(Naming.toSnakeCase(e.entityName), Some(s"${e.entityName} operations"))
    :+ TagObject("infrastructure", Some("Health and metrics endpoints"))

  def serialize(doc: OpenApiDocument): String =
    val yaml = new org.yaml.snakeyaml.Yaml(customRepresenter, dumperOptions)
    yaml.dump(toJava(doc))

  private def dumperOptions: org.yaml.snakeyaml.DumperOptions =
    val opts = new org.yaml.snakeyaml.DumperOptions
    opts.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK)
    opts.setIndent(2)
    opts.setWidth(100)
    opts

  private def customRepresenter: org.yaml.snakeyaml.representer.Representer =
    new org.yaml.snakeyaml.representer.Representer(dumperOptions)

  private def toJava(v: Any): AnyRef = v match
    case null                 => null
    case None                 => null
    case Some(x)              => toJava(x)
    case s: String            => s
    case b: Boolean           => java.lang.Boolean.valueOf(b)
    case i: Int               => java.lang.Integer.valueOf(i)
    case l: Long              => java.lang.Long.valueOf(l)
    case d: Double            => java.lang.Double.valueOf(d)
    case n: java.lang.Number  => n
    case b: java.lang.Boolean => b
    case m: Map[?, ?] =>
      val out = new java.util.LinkedHashMap[String, AnyRef]()
      m.foreach: (k, v) =>
        val ja = toJava(v)
        if ja != null then
          val _ = out.put(k.toString, ja)
      out
    case xs: Iterable[?] =>
      xs.map(toJava).filter(_ != null).toList.asJava
    case SOBSchema(s) => toJava(s)
    case SOBBool(b)   => java.lang.Boolean.valueOf(b)
    case p: Product =>
      val out = new java.util.LinkedHashMap[String, AnyRef]()
      p.productElementNames.toList.zip(p.productIterator.toList).foreach: (k, value) =>
        if !shouldSkip(k) then
          val ja = toJava(value)
          if ja != null then
            val _ = out.put(mapKeyName(k), ja)
      out
    case x: AnyRef => x
    case other     => other.toString

  // SchemaObject has includeNullInEnum which is an internal flag — not a YAML field
  private def shouldSkip(key: String): Boolean =
    key == "includeNullInEnum"

  // Map internal Scala identifiers to proper OpenAPI YAML keys
  private def mapKeyName(scalaKey: String): String = scalaKey match
    case "enum_" => "enum"
    case "ref"   => "$ref"
    case "type"  => "type"
    case other   => other
