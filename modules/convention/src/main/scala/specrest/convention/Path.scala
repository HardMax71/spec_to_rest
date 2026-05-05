package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

import specrest.ir.*

object Path:

  def deriveEndpoints(
      classifications: List[OperationClassification],
      ir: ServiceIRFull
  ): List[EndpointSpec] =
    classifications.map: c =>
      val op = ir.g.collectFirst {
        case o @ OperationDeclFull(n, _, _, _, _, _) if n == c.operationName => o
      }.getOrElse(
        throw new RuntimeException(s"operation not found: ${c.operationName}")
      )
      deriveEndpoint(c, op, ir)

  private def deriveEndpoint(
      classification: OperationClassification,
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

    val isGet = method == HttpMethod.GET
    EndpointSpec(
      operationName = classification.operationName,
      method = method,
      path = path,
      pathParams = pathParams.result(),
      queryParams = if isGet then other.result() else Nil,
      bodyParams = if isGet then Nil else other.result(),
      successStatus = successStatus
    )

  private def resolveMethod(
      c: OperationClassification,
      conv: Option[conventions_decl_full]
  ): HttpMethod =
    getConvention(conv, c.operationName, "http_method")
      .flatMap(HttpMethod.parse)
      .getOrElse(c.method)

  private def resolvePath(
      c: OperationClassification,
      op: OperationDeclFull,
      ir: ServiceIRFull
  ): String =
    getConvention(ir.n, op.a, "http_path")
      .getOrElse(autoDerivePath(c, op, ir))

  private def autoDerivePath(
      c: OperationClassification,
      op: OperationDeclFull,
      ir: ServiceIRFull
  ): String =
    val entity  = c.targetEntity
    val segment = entity.map(Naming.toPathSegment).getOrElse(Naming.toKebabCase(op.a))

    def segOrIdPath: String =
      findIdParam(op, ir) match
        case Some(id) => s"/$segment/{$id}"
        case None     => s"/$segment"

    c.kind match
      case OperationKind.Create => s"/$segment"
      case OperationKind.Read | OperationKind.FilteredRead | OperationKind.Replace |
          OperationKind.PartialUpdate | OperationKind.Delete =>
        segOrIdPath
      case OperationKind.Transition =>
        val action = extractActionVerb(op.a, entity)
        findIdParam(op, ir) match
          case Some(id) => s"/$segment/{$id}/$action"
          case None     => s"/$segment/$action"
      case OperationKind.BatchMutation => s"/$segment/batch"
      case OperationKind.SideEffect    => s"/${Naming.toKebabCase(op.a)}"
      case OperationKind.CreateChild   => s"/$segment"

  private def findIdParam(op: OperationDeclFull, ir: ServiceIRFull): Option[String] =
    ir.f match
      case None => None
      case Some(StateDeclFull(fs, _)) =>
        val keyTypeNames = fs.iterator.collect {
          case StateFieldDeclFull(_, RelationTypeF(from, _, _, _), _) => typeExprName(from)
        }.flatten.toSet
        op.b.iterator
          .collect { case ParamDeclFull(name, ty, _) => (name, ty) }
          .map { case (name, ty) =>
            typeExprName(ty) match
              case Some(n) if keyTypeNames.contains(n) => Some(name)
              case _ =>
                ty match
                  case NamedTypeF("Int", _) if name == "id" || name.endsWith("_id") =>
                    Some(name)
                  case _ => None
          }
          .collectFirst { case Some(name) => name }

  private def typeExprName(te: type_expr_full): Option[String] = te match
    case NamedTypeF(n, _) => Some(n)
    case _                => None

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
      c: OperationClassification,
      conv: Option[conventions_decl_full],
      effective: HttpMethod
  ): Int =
    getConvention(conv, c.operationName, "http_status_success") match
      case Some(s) => s.toInt
      case None =>
        if effective == HttpMethod.DELETE then 204
        else
          c.kind match
            case OperationKind.Create | OperationKind.CreateChild => 201
            case OperationKind.Delete                             => 204
            case _                                                => 200

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
    case StringLitF(v, _) => Some(v)
    case IntLitF(int_of_integer(v), _) => Some(v.toString)
    case FloatLitF(v, _)  => Some(v.toString)
    case BoolLitF(v, _)   => Some(v.toString)
    case _                => None
