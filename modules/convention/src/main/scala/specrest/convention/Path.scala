package specrest.convention

import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

object Path:

  private given CanEqual[http_method, http_method] = CanEqual.derived

  def deriveEndpoints(
      classifications: List[operation_classification],
      ir: ServiceIRFull
  ): List[EndpointSpec] =
    classifications.map: c =>
      val opName = classificationOperationName(c)
      val op = svcOperations(ir)
        .find(o => operName(o) == opName)
        .getOrElse(
          throw new RuntimeException(s"operation not found: $opName")
        )
      deriveEndpoint(c, op, ir)

  private def deriveEndpoint(
      classification: operation_classification,
      op: operation_decl_full,
      ir: ServiceIRFull
  ): EndpointSpec =
    val method        = resolveMethod(classification, svcConventions(ir))
    val path          = resolvePath(classification, op, ir)
    val successStatus = resolveStatus(classification, svcConventions(ir), method)

    val pathParamNames = extractPathParamNames(path)
    val pathParams     = List.newBuilder[ParamSpec]
    val other          = List.newBuilder[ParamSpec]
    for p <- operInputs(op) do
      val name = prmName(p)
      val ty   = prmType(p)
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
    SpecRestGenerated.resolveMethod(override_, classificationMethod(c))

  private def resolvePath(
      c: operation_classification,
      op: operation_decl_full,
      ir: ServiceIRFull
  ): String =
    getConvention(svcConventions(ir), operName(op), "http_path")
      .getOrElse(autoDerivePath(c, op, ir))

  private def autoDerivePath(
      c: operation_classification,
      op: operation_decl_full,
      ir: ServiceIRFull
  ): String =
    val entity  = classificationTargetEntity(c)
    val opKebab = Naming.toKebabCase(operName(op))
    val segment = entity.map(Naming.toPathSegment).getOrElse(opKebab)
    val action  = extractActionVerb(operName(op), entity)
    val idOpt   = findIdParam(op, ir)
    derivePathPattern(classificationKind(c), segment, idOpt, action, opKebab)

  private def findIdParam(op: operation_decl_full, ir: ServiceIRFull): Option[String] =
    SpecRestGenerated.findIdParam(operInputs(op), svcState(ir))

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
    SpecRestGenerated.resolveStatus(overrideStr, effective, classificationKind(c)).toInt

  def getConvention(
      conv: Option[conventions_decl_full],
      target: String,
      property: String
  ): Option[String] =
    conv.flatMap { c =>
      cvdRules(c).collectFirst {
        case ConventionRuleFull(t, p, _, CvOk(pv), _) if t == target && p == property =>
          parsedValueToString(pv)
      }.flatten
    }
