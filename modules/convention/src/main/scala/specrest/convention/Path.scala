package specrest.convention

import specrest.ir.*
import specrest.ir.generated.*

object Path:

  private given CanEqual[http_method, http_method] = CanEqual.derived

  def deriveEndpoints(
      classifications: List[operation_classification],
      ir: ServiceIRFull
  ): List[EndpointSpec] =
    classifications.map: c =>
      val opName = classificationOperationName(c)
      val op = ir.g.collectFirst {
        case o @ OperationDeclFull(n, _, _, _, _, _) if n == opName => o
      }.getOrElse(
        throw new RuntimeException(s"operation not found: $opName")
      )
      deriveEndpoint(c, op, ir)

  private def deriveEndpoint(
      classification: operation_classification,
      op: OperationDeclFull,
      ir: ServiceIRFull
  ): EndpointSpec =
    val method        = resolveMethod(classification, ir.n)
    val path          = resolvePath(classification, op, ir)
    val successStatus = resolveStatus(classification, ir.n, method)

    val pathParamNames = extractPathParamNames(path)
    val pathParams     = List.newBuilder[ParamSpec]
    val other          = List.newBuilder[ParamSpec]
    for case ParamDeclFull(name, ty, _) <- op.b do
      if pathParamNames.contains(name) then
        pathParams += ParamSpec(name, ty, required = true)
      else
        val required: Boolean =
          ty match
            case _: OptionTypeF => false
            case _              => true
        other += ParamSpec(name, ty, required)

    val isGet = isGetMethod(method)
    EndpointSpec(
      operationName = classificationOperationName(classification),
      method = method,
      path = path,
      pathParams = pathParams.result(),
      queryParams = if isGet then other.result() else Nil,
      bodyParams = if isGet then Nil else other.result(),
      successStatus = successStatus
    )

  private def resolveMethod(
      c: operation_classification,
      conv: Option[conventions_decl_full]
  ): http_method =
    val override_ = getConvention(conv, classificationOperationName(c), "http_method")
      .flatMap(parseHttpMethod)
    resolveMethod(override_, classificationMethod(c))

  private def resolvePath(
      c: operation_classification,
      op: OperationDeclFull,
      ir: ServiceIRFull
  ): String =
    getConvention(ir.n, op.a, "http_path")
      .getOrElse(autoDerivePath(c, op, ir))

  private def autoDerivePath(
      c: operation_classification,
      op: OperationDeclFull,
      ir: ServiceIRFull
  ): String =
    val entity  = classificationTargetEntity(c)
    val opKebab = Naming.toKebabCase(op.a)
    val segment = entity.map(Naming.toPathSegment).getOrElse(opKebab)
    val action  = extractActionVerb(op.a, entity)
    val idOpt   = findIdParam(op, ir)
    derivePathPattern(classificationKind(c), segment, idOpt, action, opKebab)

  private def findIdParam(op: OperationDeclFull, ir: ServiceIRFull): Option[String] =
    findIdParam(op.b, ir.f)

  private def extractActionVerb(opName: String, entityName: Option[String]): String =
    Naming.toKebabCase(extractVerbBeforeKebab(opName, entityName))

  private val PathParamRegex = """\{(\w+)\}""".r

  private def extractPathParamNames(path: String): Set[String] =
    PathParamRegex.findAllMatchIn(path).map(_.group(1)).toSet

  private def resolveStatus(
      c: operation_classification,
      conv: Option[conventions_decl_full],
      effective: http_method
  ): Int =
    val overrideStr = getConvention(conv, classificationOperationName(c), "http_status_success")
    resolveStatus(overrideStr, effective, classificationKind(c)).toInt

  def getConvention(
      conv: Option[conventions_decl_full],
      target: String,
      property: String
  ): Option[String] =
    conv.flatMap { case ConventionsDeclFull(rules, _) =>
      rules.collectFirst {
        case ConventionRuleFull(t, p, _, CvOk(pv), _) if t == target && p == property =>
          parsedValueToString(pv)
      }.flatten
    }
