package specrest.convention

import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated.*

object Path:

  private given CanEqual[http_method, http_method] = CanEqual.derived

  def deriveEndpoints(
      classifications: List[operation_classification],
      ir: ServiceIRFull
  ): List[EndpointSpec] =
    classifications.map: c =>
      val opName = classification_operation_name(c)
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

    val isGet = method match
      case _: GET => true
      case _      => false
    EndpointSpec(
      operationName = classification_operation_name(classification),
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
    getConvention(conv, classification_operation_name(c), "http_method")
      .flatMap(parseHttpMethod)
      .getOrElse(classification_method(c))

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
    val entity  = classification_target_entity(c)
    val segment = entity.map(Naming.toPathSegment).getOrElse(Naming.toKebabCase(op.a))

    def segOrIdPath: String =
      findIdParam(op, ir) match
        case Some(id) => s"/$segment/{$id}"
        case None     => s"/$segment"

    classification_kind(c) match
      case _: Create => s"/$segment"
      case _: Read | _: FilteredRead | _: Replace | _: PartialUpdate | _: Deletea =>
        segOrIdPath
      case _: Transition =>
        val action = extractActionVerb(op.a, entity)
        findIdParam(op, ir) match
          case Some(id) => s"/$segment/{$id}/$action"
          case None     => s"/$segment/$action"
      case _: BatchMutation => s"/$segment/batch"
      case _: SideEffect    => s"/${Naming.toKebabCase(op.a)}"
      case _: CreateChild   => s"/$segment"

  private def findIdParam(op: OperationDeclFull, ir: ServiceIRFull): Option[String] =
    ir.f match
      case None => None
      case Some(StateDeclFull(fs, _)) =>
        val keyTypeNames = fs.iterator.collect {
          case StateFieldDeclFull(_, RelationTypeF(from, _, _, _), _) => typeName(from)
        }.flatten.toSet
        op.b.iterator
          .collect { case ParamDeclFull(name, ty, _) => (name, ty) }
          .map { case (name, ty) =>
            typeName(ty) match
              case Some(n) if keyTypeNames.contains(n) => Some(name)
              case _ =>
                ty match
                  case NamedTypeF("Int", _) if name == "id" || name.endsWith("_id") =>
                    Some(name)
                  case _ => None
          }
          .collectFirst { case Some(name) => name }

  private def extractActionVerb(opName: String, entityName: Option[String]): String =
    entityName match
      case None => Naming.toKebabCase(opName)
      case Some(en) =>
        if opName.endsWith(en) then
          val verb = opName.dropRight(en.length)
          if verb.nonEmpty then Naming.toKebabCase(verb) else Naming.toKebabCase(opName)
        else if opName.startsWith(en) then
          val verb = opName.drop(en.length)
          if verb.nonEmpty then Naming.toKebabCase(verb) else Naming.toKebabCase(opName)
        else Naming.toKebabCase(opName)

  private val PathParamRegex = """\{(\w+)\}""".r

  private def extractPathParamNames(path: String): Set[String] =
    PathParamRegex.findAllMatchIn(path).map(_.group(1)).toSet

  private def resolveStatus(
      c: operation_classification,
      conv: Option[conventions_decl_full],
      effective: http_method
  ): Int =
    getConvention(conv, classification_operation_name(c), "http_status_success") match
      case Some(s) => s.toInt
      case None =>
        val isDelete = effective match
          case _: DELETE => true
          case _         => false
        if isDelete then 204
        else
          classification_kind(c) match
            case _: Create | _: CreateChild => 201
            case _: Deletea                 => 204
            case _                          => 200

  def getConvention(
      conv: Option[conventions_decl_full],
      target: String,
      property: String
  ): Option[String] =
    conv.flatMap { case ConventionsDeclFull(rules, _) =>
      rules.collectFirst {
        case ConventionRuleFull(t, p, _, v, _) if t == target && p == property =>
          exprToString(v)
      }.flatten
    }

  private def exprToString(expr: expr_full): Option[String] = expr match
    case StringLitF(v, _)              => Some(v)
    case IntLitF(int_of_integer(v), _) => Some(v.toString)
    case FloatLitF(v, _)               => Some(v.toString)
    case BoolLitF(v, _)                => Some(v.toString)
    case _                             => None
