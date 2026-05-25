package specrest.codegen.openapi

import specrest.codegen.OperationContext
import specrest.codegen.SensitiveFields
import specrest.convention.Naming
import specrest.convention.ParamSpec
import specrest.ir.PrettyPrint
import specrest.ir.generated.SpecRestGenerated.*
import specrest.ir.idx
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

sealed trait SchemaObjectOrBool derives CanEqual
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

final case class TemporalAnnotation(kind: String, expr: String)

final case class OpenApiDocument(
    openapi: String,
    info: InfoObject,
    servers: List[ServerObject],
    paths: Map[String, PathItemObject],
    components: ComponentsObject,
    tags: List[TagObject],
    xInvariant: Option[Map[String, String]] = None,
    xTemporal: Option[Map[String, TemporalAnnotation]] = None
)

final case class BuildContext(
    aliasMap: Map[String, TypeAliasDeclFull],
    enumMap: Map[String, EnumDeclFull],
    entityDecls: Map[String, EntityDeclFull],
    aliasAList: List[(String, TypeAliasDeclFull)],
    enumAList: List[(String, EnumDeclFull)],
    entityNamesList: List[String]
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

@SuppressWarnings(Array("org.wartremover.warts.Var"))
object Constraints:

  def extractFieldConstraints(
      typeExpr: type_expr_full,
      constraint: Option[expr_full],
      aliasAList: List[(String, TypeAliasDeclFull)],
      enumAList: List[(String, EnumDeclFull)]
  ): JsonSchemaConstraints =
    var bounds = emptyOpenApiBounds
    for pred <- aliasRefinements(typeExpr, aliasAList) do
      bounds = visitConstraintOpenApi(pred, bounds)
    constraint match
      case Some(c) => bounds = visitConstraintOpenApi(c, bounds)
      case None    => ()
    val enum_ = findEnumValuesInType(typeExpr, aliasAList, enumAList)
    boundsToConstraints(bounds, enum_)

  private def boundsToConstraints(
      b: openapi_bounds,
      enum_ : Option[List[String]]
  ): JsonSchemaConstraints =
    b match
      case OpenApiBounds(nl, ml, mn, mx, emn, emx, pat) =>
        JsonSchemaConstraints(
          minLength = nl.map(asInt),
          maxLength = ml.map(asInt),
          minimum = mn.map(decimalToDouble),
          maximum = mx.map(decimalToDouble),
          exclusiveMinimum = emn.map(decimalToDouble),
          exclusiveMaximum = emx.map(decimalToDouble),
          pattern = pat,
          enum_ = enum_
        )

  private def asInt(i: int): Int = i match
    case int_of_integer(v) => v.toInt

  // decimal_lit DecimalLit(mantissa, exponent) represents mantissa * 10^exponent.
  // BigDecimal handles arbitrary precision; .doubleValue trims to IEEE 754.
  private def decimalToDouble(d: decimal_lit): Double = d match
    case DecimalLit(int_of_integer(m), int_of_integer(e)) =>
      BigDecimal(m.bigInteger, -e.toInt).doubleValue

// -- Schema generation ----------------------------------------

final case class FieldSchema(schema: SchemaObject, nullable: Boolean)

object Schema:

  private def primitiveDefToSchema(p: openapi_primitive_def): SchemaObject =
    p match
      case OpenApiPrimDef(types, fmt) => SchemaObject(`type` = Some(types), format = fmt)

  def fieldToSchema(
      typeExpr: type_expr_full,
      constraint: Option[expr_full],
      ctx: BuildContext
  ): FieldSchema =
    val nullable = typeExpr match
      case _: OptionTypeF => true
      case _              => false
    val effective = typeExpr match
      case OptionTypeF(inner, _) => inner
      case t                     => t
    val cs =
      Constraints.extractFieldConstraints(effective, constraint, ctx.aliasAList, ctx.enumAList)
    FieldSchema(typeExprToSchema(effective, cs, ctx), nullable)

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
      typeExpr: type_expr_full,
      constraints: JsonSchemaConstraints,
      ctx: BuildContext
  ): SchemaObject = typeExpr match
    case NamedTypeF(name, _) =>
      namedTypeSchema(name, constraints, ctx)
    case SetTypeF(inner, _) =>
      buildArraySchema(inner, constraints, ctx)
    case SeqTypeF(inner, _) =>
      buildArraySchema(inner, constraints, ctx)
    case MapTypeF(_, v, _) =>
      val value = fieldToSchema(v, None, ctx)
      val ap    = if value.nullable then makeNullable(value.schema) else value.schema
      SchemaObject(
        `type` = Some(List("object")),
        additionalProperties = Some(SOBSchema(ap))
      )
    case OptionTypeF(inner, _) =>
      typeExprToSchema(inner, constraints, ctx)
    case RelationTypeF(_, _, _, _) =>
      SchemaObject(`type` = Some(List("integer")))

  private def namedTypeSchema(
      name: String,
      c: JsonSchemaConstraints,
      ctx: BuildContext
  ): SchemaObject =
    classifyOpenApiNamedType(name, ctx.aliasAList, ctx.enumAList, ctx.entityNamesList) match
      case OntPrimitive(p) =>
        mergeConstraints(primitiveDefToSchema(p), c)
      case OntEnum(values) =>
        SchemaObject(`type` = Some(List("string")), enum_ = Some(values))
      case OntEntityRef(n) =>
        SchemaObject(ref = Some(s"#/components/schemas/${n}Read"))
      case OntAliasToType(base) =>
        typeExprToSchema(base, c, ctx)
      case _: OntUnknown =>
        mergeConstraints(SchemaObject(`type` = Some(List("string"))), c)

  private def buildArraySchema(
      inner: type_expr_full,
      c: JsonSchemaConstraints,
      ctx: BuildContext
  ): SchemaObject =
    val innerSchema = fieldToSchema(inner, None, ctx)
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
      decl: EntityDeclFull,
      ctx: BuildContext
  ): List[DecoratedField] =
    val irFields = decl.c.collect { case f: FieldDeclFull => f }
    entity.fields.zipWithIndex.map: (profiledField, idx) =>
      irFields(idx) match
        case FieldDeclFull(_, irType, irConstraint, _) =>
          val fs = Schema.fieldToSchema(irType, irConstraint, ctx)
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
      GET(),
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
      method: http_method,
      op: OperationObject
  ): PathItemObject = method match
    case _: GET    => item.copy(get = Some(op))
    case _: POST   => item.copy(post = Some(op))
    case _: PUT    => item.copy(put = Some(op))
    case _: PATCH  => item.copy(patch = Some(op))
    case _: DELETE => item.copy(delete = Some(op))

  private given CanEqual[route_kind, route_kind] = CanEqual.derived

  private def buildOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      ctx: BuildContext
  ): OperationObject =
    val routeKind   = OperationContext.from(op, entity).initialRouteKind
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
    val fs = Schema.fieldToSchema(p match { case ParamSpec(_, t, _) => t }, None, ctx)
    ParameterObject(
      name = p match { case ParamSpec(n, _, _) => n },
      in = location,
      required = if location == "path" then true else p.required,
      description = None,
      schema = if fs.nullable then Schema.makeNullable(fs.schema) else fs.schema
    )

  private def buildRequestBody(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      routeKind: route_kind,
      ctx: BuildContext
  ): Option[RequestBodyObject] =
    val isCreate = routeKind match
      case _: RkCreate => true
      case _           => false
    val isGetOrDelete = op.endpoint.method match
      case _: GET | _: DELETE => true
      case _                  => false
    val isKindCreate = op.kind match
      case _: Create => true
      case _         => false
    val isKindReplaceOrPartialUpdate = op.kind match
      case _: Replace | _: PartialUpdate => true
      case _                             => false
    if isGetOrDelete then None
    else if op.endpoint.bodyParams.isEmpty then None
    else if isCreate || isKindCreate then
      Some(componentBody(entity.createSchemaName))
    else if isKindReplaceOrPartialUpdate then
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
        val fs = Schema.fieldToSchema(p match { case ParamSpec(_, t, _) => t }, None, ctx)
        properties(p match { case ParamSpec(n, _, _) => n }) =
          if fs.nullable then Schema.makeNullable(fs.schema) else fs.schema
        if p.required then required += (p match { case ParamSpec(n, _, _) => n })
      val req = required.result()
      Some(SchemaObject(
        `type` = Some(List("object")),
        properties = Some(properties.toMap),
        required = if req.nonEmpty then Some(req) else None
      ))

  private def buildResponses(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      routeKind: route_kind
  ): Map[String, ResponseObject] =
    val status    = op.endpoint.successStatus.toString
    val success   = buildSuccessResponse(op, entity, routeKind)
    val responses = collection.mutable.LinkedHashMap.empty[String, ResponseObject]
    responses(status) = success

    val hasPathParam = op.endpoint.pathParams.nonEmpty
    val isKindNeeds404 = op.kind match
      case _: Read | _: Deletea | _: Replace | _: PartialUpdate | _: Transition | _: CreateChild =>
        true
      case _ => false
    val needs404 = hasPathParam && isKindNeeds404
    if needs404 then responses("404") = errorResponseRef("Resource not found")

    val acceptsInput = op.endpoint.bodyParams.nonEmpty ||
      op.endpoint.queryParams.nonEmpty || op.endpoint.pathParams.nonEmpty
    if acceptsInput then responses("422") = errorResponseRef("Validation error")

    responses.toMap

  private def buildSuccessResponse(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      routeKind: route_kind
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
    else
      routeKind match
        case _: RkList =>
          jsonResponse(
            "Successful response",
            SchemaObject(
              `type` = Some(List("array")),
              items =
                Some(SchemaObject(ref = Some(s"#/components/schemas/${entity.readSchemaName}")))
            )
          )
        case _: RkCreate =>
          jsonResponse(
            "Successful response",
            SchemaObject(ref = Some(s"#/components/schemas/${entity.readSchemaName}"))
          )
        case _: RkRead =>
          jsonResponse(
            "Successful response",
            SchemaObject(ref = Some(s"#/components/schemas/${entity.readSchemaName}"))
          )
        case _ =>
          val isReplaceOrPartial = op.kind match
            case _: Replace | _: PartialUpdate => true
            case _                             => false
          if isReplaceOrPartial then
            jsonResponse(
              "Successful response",
              SchemaObject(ref = Some(s"#/components/schemas/${entity.readSchemaName}"))
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

@SuppressWarnings(Array("org.wartremover.warts.Null"))
object OpenApi:

  def buildOpenApiDocument(profiled: ProfiledService): OpenApiDocument =
    val idx = profiled.ir.idx
    val ctx = BuildContext(
      aliasMap = idx.aliasByName,
      enumMap = idx.enumByName,
      entityDecls = idx.entityByName,
      aliasAList = idx.aliasAList,
      enumAList = idx.enumAList,
      entityNamesList = idx.entityNamesList
    )

    OpenApiDocument(
      openapi = "3.1.0",
      info = InfoObject(
        title = profiled.ir.a,
        version = "0.1.0",
        description = Some(s"API for ${profiled.ir.a}. Generated from formal specification.")
      ),
      servers = List(
        ServerObject(s"http://localhost:${profiled.profile.httpPort}", Some("Local development"))
      ),
      paths = Paths.buildPaths(profiled, ctx),
      components = Components.buildComponents(profiled, ctx),
      tags = buildTags(profiled),
      xInvariant = buildXInvariant(profiled),
      xTemporal = buildXTemporal(profiled)
    )

  private def buildXInvariant(profiled: ProfiledService): Option[Map[String, String]] =
    val pairs = profiled.ir.i.collect { case inv: InvariantDeclFull => inv }.zipWithIndex.map:
      case (inv, idx) =>
        val name = inv.a.getOrElse(anonInvariantName(Nata(BigInt(idx))))
        name -> prettyOneLine(inv.b)
    toStableListMap(disambiguateKeys(pairs))

  private def buildXTemporal(profiled: ProfiledService): Option[Map[String, TemporalAnnotation]] =
    val pairs = profiled.ir.j.collect { case t: TemporalDeclFull => t }.flatMap: t =>
      t.b match
        case TbAlways(arg)     => Some(t.a -> TemporalAnnotation("always", prettyOneLine(arg)))
        case TbEventually(arg) => Some(t.a -> TemporalAnnotation("eventually", prettyOneLine(arg)))
        case TbFairness(arg)   => Some(t.a -> TemporalAnnotation("fairness", prettyOneLine(arg)))
        case TbInvalid(_)      => None
    toStableListMap(disambiguateKeys(pairs))

  // ListMap preserves insertion order; the lifted disambiguateKeys already
  // returns a collision-free ordered list. Empty → None so the absent x-key
  // is omitted from the emitted YAML.
  private def toStableListMap[V](pairs: List[(String, V)]): Option[Map[String, V]] =
    if pairs.isEmpty then None
    else Some(scala.collection.immutable.ListMap.from(pairs))

  private def prettyOneLine(e: expr_full): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private def buildTags(profiled: ProfiledService): List[TagObject] =
    profiled.entities.map: e =>
      TagObject(Naming.toSnakeCase(e.entityName), Some(s"${e.entityName} operations"))
    :+ TagObject("infrastructure", Some("Health and metrics endpoints"))

  def serialize(doc: OpenApiDocument): String =
    val yaml = new org.yaml.snakeyaml.Yaml(customRepresenter, dumperOptions)
    yaml.dump(toJava(doc).orNull)

  private def dumperOptions: org.yaml.snakeyaml.DumperOptions =
    val opts = new org.yaml.snakeyaml.DumperOptions
    opts.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK)
    opts.setIndent(2)
    opts.setWidth(100)
    opts

  private def customRepresenter: org.yaml.snakeyaml.representer.Representer =
    new org.yaml.snakeyaml.representer.Representer(dumperOptions)

  private def toJava(v: Any): Option[AnyRef] = Option(v).flatMap {
    case _: None.type         => None
    case Some(x)              => toJava(x)
    case s: String            => Some(s)
    case b: Boolean           => Some(java.lang.Boolean.valueOf(b))
    case i: Int               => Some(java.lang.Integer.valueOf(i))
    case l: Long              => Some(java.lang.Long.valueOf(l))
    case d: Double            => Some(java.lang.Double.valueOf(d))
    case n: java.lang.Number  => Some(n)
    case b: java.lang.Boolean => Some(b)
    case m: Map[?, ?] =>
      val out = new java.util.LinkedHashMap[String, AnyRef]()
      m.foreach: (k, v) =>
        toJava(v).foreach(ja => out.put(k.toString, ja))
      Some(out)
    case xs: Iterable[?] => Some(xs.flatMap(toJava).toList.asJava)
    case SOBSchema(s)    => toJava(s)
    case SOBBool(b)      => Some(java.lang.Boolean.valueOf(b))
    case p: Product =>
      val out = new java.util.LinkedHashMap[String, AnyRef]()
      p.productElementNames.toList.zip(p.productIterator.toList).foreach: (k, value) =>
        if !shouldSkip(k) then toJava(value).foreach(ja => out.put(mapKeyName(k), ja))
      Some(out)
    case x: AnyRef => Some(x)
    case other     => Some(other.toString)
  }

  // SchemaObject has includeNullInEnum which is an internal flag — not a YAML field
  private def shouldSkip(key: String): Boolean =
    key == "includeNullInEnum"

  // Map internal Scala identifiers to proper OpenAPI YAML keys
  private def mapKeyName(scalaKey: String): String = scalaKey match
    case "enum_"      => "enum"
    case "ref"        => "$ref"
    case "type"       => "type"
    case "xInvariant" => "x-invariant"
    case "xTemporal"  => "x-temporal"
    case other        => other
