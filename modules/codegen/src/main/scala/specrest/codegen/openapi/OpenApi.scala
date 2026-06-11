package specrest.codegen.openapi

import specrest.codegen.OperationContext
import specrest.convention.ParamSpec
import specrest.ir.Naming
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

private[openapi] object SchemaObjectAdapter:

  def fromLifted(s: schema_object): SchemaObject = s match
    case lifted: specrest.ir.generated.SpecRestGenerated.SchemaObject =>
      SchemaObject(
        `type` = lifted.a,
        format = lifted.b,
        minLength = lifted.c.map(asInt),
        maxLength = lifted.d.map(asInt),
        minimum = lifted.e.map(decimalToDouble),
        maximum = lifted.f.map(decimalToDouble),
        exclusiveMinimum = lifted.g.map(decimalToDouble),
        exclusiveMaximum = lifted.h.map(decimalToDouble),
        minItems = lifted.i.map(asInt),
        maxItems = lifted.j.map(asInt),
        pattern = lifted.k,
        enum_ = lifted.l,
        items = lifted.m.map(fromLifted),
        ref = lifted.n,
        required = lifted.o,
        properties = lifted.p.map(_.iterator.map((k, v) => k -> fromLifted(v)).toMap),
        additionalProperties = lifted.q.map(fromLiftedSOB),
        anyOf = lifted.r.map(_.map(fromLifted)),
        description = lifted.s,
        includeNullInEnum = lifted.t
      )

  private def fromLiftedSOB(sob: schema_object_or_bool): SchemaObjectOrBool = sob match
    case ls: specrest.ir.generated.SpecRestGenerated.SOBSchema => SOBSchema(fromLifted(ls.a))
    case lb: specrest.ir.generated.SpecRestGenerated.SOBBool   => SOBBool(lb.a)

  private def asInt(i: BigInt): Int = i.toInt

  private def decimalToDouble(d: decimal_lit): Double = d match
    case DecimalLit(m, e) =>
      BigDecimal(m.bigInteger, -e.toInt).doubleValue

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
    aliasMap: Map[String, type_alias_decl],
    enumMap: Map[String, enum_decl],
    entityDecls: Map[String, entity_decl],
    aliasAList: List[(String, type_alias_decl)],
    enumAList: List[(String, enum_decl)],
    entityNamesList: List[String]
)

// -- Schema generation ----------------------------------------

final case class FieldSchema(schema: SchemaObject, nullable: Boolean)

object Schema:

  def fieldToSchema(
      typeExpr: type_expr,
      constraint: Option[expr],
      ctx: BuildContext
  ): FieldSchema =
    val (lifted, nullable) = specrest.ir.generated.SpecRestGenerated.fieldToSchema(
      typeExpr,
      constraint,
      ctx.aliasAList,
      ctx.enumAList,
      ctx.entityNamesList
    )
    FieldSchema(SchemaObjectAdapter.fromLifted(lifted), nullable)

  def makeNullable(schema: SchemaObject): SchemaObject =
    decideNullable(schema.ref, schema.`type`) match
      case _: NdNoop => schema
      case _: NdWrapAnyOfNull =>
        SchemaObject(anyOf = Some(List(schema, SchemaObject(`type` = Some(List("null"))))))
      case _: NdAppendNull =>
        schema.copy(`type` = schema.`type`.map(_ :+ "null"))

// -- Components / Paths / Build / Serialize --------------------

object Components:

  def buildComponents(profiled: ProfiledService, ctx: BuildContext): ComponentsObject =
    val schemas = collection.mutable.LinkedHashMap.empty[String, SchemaObject]
    schemas("ErrorResponse") = errorResponseSchema
    for entity <- profiled.entities do
      ctx.entityDecls.get(entity.entityName).foreach: decl =>
        val decorated = decorateFields(entity, decl, ctx)
        val (createL, (readL, updateL)) =
          specrest.ir.generated.SpecRestGenerated.buildEntitySchemas(entity.entityName, decorated)
        schemas(entity.createSchemaName) = SchemaObjectAdapter.fromLifted(createL)
        schemas(entity.readSchemaName) = SchemaObjectAdapter.fromLifted(readL)
        schemas(entity.updateSchemaName) = SchemaObjectAdapter.fromLifted(updateL)
    ComponentsObject(schemas.toMap)

  private def decorateFields(
      entity: ProfiledEntity,
      decl: entity_decl,
      ctx: BuildContext
  ): List[(String, (schema_object, Boolean))] =
    val irFields = entFields(decl)
    entity.fields.zipWithIndex.map: (profiledField, idx) =>
      val irField = irFields(idx)
      val (lifted, nullable) = specrest.ir.generated.SpecRestGenerated.fieldToSchema(
        fldType(irField),
        fldDefault(irField),
        ctx.aliasAList,
        ctx.enumAList,
        ctx.entityNamesList
      )
      (profiledField.columnName, (lifted, nullable))

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
        title = svcName(profiled.ir),
        version = "0.1.0",
        description = Some(s"API for ${svcName(profiled.ir)}. Generated from formal specification.")
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
    val pairs = svcInvariants(profiled.ir).zipWithIndex.map:
      case (inv, idx) =>
        val name = invName(inv).getOrElse(anonInvariantName(Nata(BigInt(idx))))
        name -> prettyOneLine(invBody(inv))
    toStableListMap(disambiguateKeys(pairs))

  private def buildXTemporal(profiled: ProfiledService): Option[Map[String, TemporalAnnotation]] =
    val pairs = svcTemporals(profiled.ir).flatMap: t =>
      tmpBody(t) match
        case TbAlways(arg) => Some(tmpName(t) -> TemporalAnnotation("always", prettyOneLine(arg)))
        case TbEventually(arg) =>
          Some(tmpName(t) -> TemporalAnnotation("eventually", prettyOneLine(arg)))
        case TbFairness(arg) =>
          Some(tmpName(t) -> TemporalAnnotation("fairness", prettyOneLine(arg)))
        case TbInvalid(_) => None
    toStableListMap(disambiguateKeys(pairs))

  // ListMap preserves insertion order; the lifted disambiguateKeys already
  // returns a collision-free ordered list. Empty → None so the absent x-key
  // is omitted from the emitted YAML.
  private def toStableListMap[V](pairs: List[(String, V)]): Option[Map[String, V]] =
    if pairs.isEmpty then None
    else Some(scala.collection.immutable.ListMap.from(pairs))

  private def prettyOneLine(e: expr): String =
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
