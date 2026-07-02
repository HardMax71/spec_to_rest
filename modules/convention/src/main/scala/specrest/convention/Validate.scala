package specrest.convention

import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated.*

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object Validate:

  private type DiagBuilder = scala.collection.mutable.Builder[
    ConventionDiagnostic,
    List[ConventionDiagnostic]
  ]

  private enum ConventionTarget derives CanEqual:
    case Operation, Entity, AliasOrEnum

    def labelSingular: String = this match
      case Operation   => "operation"
      case Entity      => "entity"
      case AliasOrEnum => "type alias / enum"

  private object ConventionTarget:
    def describePlural(targets: Set[ConventionTarget]): String =
      val ordered = List(Operation, Entity, AliasOrEnum).filter(targets.contains)
      ordered match
        case List(Operation)              => "operations"
        case List(Entity)                 => "entities"
        case List(AliasOrEnum)            => "type aliases and enums"
        case List(Operation, Entity)      => "operations and entities"
        case List(Operation, AliasOrEnum) => "operations and type aliases / enums"
        case List(Entity, AliasOrEnum)    => "entities and type aliases / enums"
        case _                            => "operations, entities, and type aliases / enums"

  // Per-property metadata used by the cross-cutting validator. Each property
  // declares which target kinds it applies to and whether it requires a
  // qualifier (cvrQualifier, the second-dotted-ident or string-literal qualifier).
  // Value-shape validation lives in the lifted Isabelle parseConventionValue;
  // here we only deal with cross-cutting concerns (duplicates, target/
  // property matrix, IR-context checks for partial_index column / test_strategy
  // field existence).
  final private case class ConventionProperty(
      name: String,
      appliesTo: Set[ConventionTarget],
      qualifierIsIdentity: Boolean
  )

  private val Registry: List[ConventionProperty] = List(
    ConventionProperty("http_method", Set(ConventionTarget.Operation), false),
    ConventionProperty("http_path", Set(ConventionTarget.Operation), false),
    ConventionProperty("http_status_success", Set(ConventionTarget.Operation), false),
    ConventionProperty("http_header", Set(ConventionTarget.Operation), true),
    ConventionProperty("db_table", Set(ConventionTarget.Entity), false),
    ConventionProperty("db_timestamps", Set(ConventionTarget.Entity), false),
    ConventionProperty("plural", Set(ConventionTarget.Entity), false),
    ConventionProperty("partial_index", Set(ConventionTarget.Entity), true),
    ConventionProperty("strategy", Set(ConventionTarget.AliasOrEnum), false),
    ConventionProperty(
      "test_strategy",
      Set(ConventionTarget.Operation, ConventionTarget.Entity),
      true
    )
  )

  private val byName: Map[String, ConventionProperty] =
    Registry.map(p => p.name -> p).toMap

  private val ReservedRoutes: List[(String, String)] = List(
    "/health"  -> "the generated health endpoint",
    "/ready"   -> "the generated readiness endpoint",
    "/metrics" -> "the generated Prometheus endpoint",
    "/admin"   -> "the generated admin surface"
  )

  def validateRoutes(ir: ServiceIRFull): List[ConventionDiagnostic] =
    val endpoints = Path.deriveEndpoints(Classify.classifyOperations(ir), ir)
    val opSpans   = svcOperations(ir).map(op => operName(op) -> operSpan(op)).toMap
    endpoints.flatMap: ep =>
      ReservedRoutes.collectFirst:
        case (root, what) if ep.path == root || ep.path.startsWith(root + "/") =>
          ConventionDiagnostic(
            DiagnosticLevel.Error,
            s"operation '${ep.operationName}' resolves to route '${ep.path}', which is reserved for $what; rename the operation or set an http_path convention override",
            opSpans.getOrElse(ep.operationName, None),
            ep.operationName,
            "http_path"
          )

  def validateSecurity(ir: ServiceIRFull): List[ConventionDiagnostic] =
    val schemes  = svcSecurity(ir)
    val declared = schemes.map(ssdName).toSet
    val duplicates = schemes
      .groupBy(ssdName)
      .valuesIterator
      .filter(_.sizeIs > 1)
      .flatMap(_.drop(1))
      .map: s =>
        ConventionDiagnostic(
          DiagnosticLevel.Error,
          s"duplicate security scheme '${ssdName(s)}'",
          ssdSpan(s),
          ssdName(s),
          "security"
        )
      .toList
    val undeclaredRefs = svcOperations(ir).flatMap: op =>
      operRequiresAuth(op).getOrElse(Nil).filterNot(declared.contains).map: ref =>
        val hint =
          if declared.isEmpty then "no security block is declared"
          else s"declared schemes: ${declared.toList.sorted.mkString(", ")}"
        ConventionDiagnostic(
          DiagnosticLevel.Error,
          s"operation '${operName(op)}' requires_auth references undeclared security scheme '$ref'; $hint",
          operSpan(op),
          operName(op),
          "requires_auth"
        )
    duplicates ++ undeclaredRefs

  def validateConventions(
      conventions: Option[conventions_decl],
      ir: ServiceIRFull
  ): List[ConventionDiagnostic] =
    conventions match
      case None => Nil
      case Some(cd) =>
        val rules       = cvdRules(cd)
        val ix          = ir.idx
        val opNames     = svcOperations(ir).map(operName).toSet
        val entityNames = ix.entityNames
        val aliasNames  = ix.aliasNames
        val enumNames   = ix.enumNames
        val diagnostics = List.newBuilder[ConventionDiagnostic]
        val seen        = scala.collection.mutable.Map.empty[String, convention_rule]

        for rule <- rules do
          val propName = cvrProperty(rule)
          val propOpt  = byName.get(propName)

          val key = (propOpt, cvrQualifier(rule)) match
            case (Some(p), Some(q)) if p.qualifierIsIdentity => s"${cvrTarget(rule)}.$propName:$q"
            case _                                           => s"${cvrTarget(rule)}.$propName"

          seen.get(key) match
            case Some(existing) =>
              val loc = cvrSpan(existing)
                .map { case SpanT(sl, sc, _, _) =>
                  s" (first defined at $sl:$sc)"
                }
                .getOrElse("")
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"duplicate override for $key$loc",
                cvrSpan(rule),
                cvrTarget(rule),
                propName
              )
            case None => seen(key) = rule

          val targetKind: Option[ConventionTarget] =
            if opNames.contains(cvrTarget(rule)) then Some(ConventionTarget.Operation)
            else if entityNames.contains(cvrTarget(rule)) then Some(ConventionTarget.Entity)
            else if aliasNames.contains(cvrTarget(rule)) || enumNames.contains(cvrTarget(rule)) then
              Some(ConventionTarget.AliasOrEnum)
            else None

          (targetKind, propOpt) match
            case (None, _) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"no operation, entity, type alias, or enum named '${cvrTarget(rule)}'",
                cvrSpan(rule),
                cvrTarget(rule),
                propName
              )
            case (Some(_), None) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"unknown convention property '$propName'",
                cvrSpan(rule),
                cvrTarget(rule),
                propName
              )
            case (Some(target), Some(prop)) if !prop.appliesTo.contains(target) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                mismatchMessage(rule, prop, target),
                cvrSpan(rule),
                cvrTarget(rule),
                propName
              )
            case (Some(_), Some(prop)) =>
              // Value-shape diagnostic: surfaced by the parser as CvBad.
              cvrValue(rule) match
                case CvBad(failure, _) =>
                  err(rule, failureMsg(rule, failure), diagnostics)
                case _ => ()
              // Cross-cutting qualifier-required check.
              if prop.qualifierIsIdentity && cvrQualifier(rule).isEmpty then
                err(rule, qualifierMissingMsg(rule), diagnostics)
              // IR-context checks (need the full service IR — can't be parser-time).
              validateIrContext(rule, ir, diagnostics)

        detectEntityFieldCollisions(rules, ir, diagnostics)
        diagnostics.result()

  // Format a parser-emitted validation_failure into a diagnostic message keyed
  // to the rule's target + property name.
  private def failureMsg(rule: convention_rule, f: validation_failure): String = f match
    case _: ExpectedString =>
      s"invalid value for ${cvrTarget(rule)}.${cvrProperty(rule)} — expected a string"
    case _: ExpectedInteger =>
      s"invalid value for ${cvrTarget(rule)}.${cvrProperty(rule)} — expected an integer"
    case _: ExpectedBoolean =>
      s"invalid value for ${cvrTarget(rule)}.${cvrProperty(rule)} — expected true or false"
    case _: EmptyString =>
      s"invalid value for ${cvrTarget(rule)}.${cvrProperty(rule)} — cannot be empty"
    case BadHttpMethod(v) =>
      s"""invalid value for ${cvrTarget(
          rule
        )}.http_method — expected one of GET, POST, PUT, PATCH, DELETE, got "$v""""
    case HttpStatusOutOfRange(v) =>
      s"invalid value for ${cvrTarget(rule)}.http_status_success — expected integer between 100 and 599, got $v"
    case _: HttpPathMissingSlash =>
      s"invalid value for ${cvrTarget(rule)}.http_path — path must start with '/'"
    case BadTestStrategy(v) =>
      s"""invalid value for ${cvrTarget(
          rule
        )}.test_strategy — expected "live" or "redacted", got "$v""""
    case BadStrategyFormat(v) =>
      s"""invalid value for ${cvrTarget(
          rule
        )}.strategy — expected "module:symbol" (e.g., "tests.strategies_user:valid_url"), got "$v""""

  private def qualifierMissingMsg(rule: convention_rule): String = cvrProperty(rule) match
    case "http_header" =>
      s"""${cvrTarget(
          rule
        )}.http_header requires a header name qualifier (e.g., http_header "Location")"""
    case "partial_index" =>
      s"""${cvrTarget(rule)}.partial_index requires a column qualifier (e.g., ${cvrTarget(
          rule
        )}.partial_index "active" = "active = true")"""
    case "test_strategy" =>
      s"""${cvrTarget(rule)}.test_strategy requires a field qualifier (e.g., ${cvrTarget(
          rule
        )}.test_strategy "password" = "redacted" or ${cvrTarget(
          rule
        )}.password.test_strategy = "redacted")"""
    case _ =>
      s"${cvrTarget(rule)}.${cvrProperty(rule)} requires a qualifier"

  private def mismatchMessage(
      rule: convention_rule,
      prop: ConventionProperty,
      target: ConventionTarget
  ): String =
    target match
      case ConventionTarget.AliasOrEnum =>
        if prop.appliesTo.size > 1 then
          val applicable = ConventionTarget.describePlural(prop.appliesTo)
          s"property '${cvrProperty(rule)}' is not valid for type alias / enum '${cvrTarget(rule)}'; it applies to $applicable"
        else
          val aliasProps = Registry
            .filter(_.appliesTo.contains(ConventionTarget.AliasOrEnum))
            .map(p => s"'${p.name}'")
          val hint = aliasProps match
            case Nil     => "no properties apply"
            case List(p) => s"only $p applies"
            case ps      => s"only ${ps.mkString(", ")} apply"
          s"property '${cvrProperty(rule)}' is not valid for type alias / enum '${cvrTarget(rule)}'; $hint"
      case _ =>
        val applicable = ConventionTarget.describePlural(prop.appliesTo)
        s"property '${cvrProperty(rule)}' is not valid for ${target.labelSingular} '${cvrTarget(rule)}'; it applies to $applicable"

  private def validateIrContext(
      rule: convention_rule,
      ir: ServiceIRFull,
      diagnostics: DiagBuilder
  ): Unit =
    validateIrContextRule(rule, svcEntities(ir), svcOperations(ir)).foreach:
      case PartialIndexFieldMissing(target, field) =>
        err(
          rule,
          s"""$target.partial_index "$field" — no field named '$field' on entity '$target'""",
          diagnostics
        )
      case TestStrategyFieldMissing(target, field, kind) =>
        err(
          rule,
          s"""$target.test_strategy "$field" — no field named '$field' on $kind '$target'""",
          diagnostics
        )

  private def detectEntityFieldCollisions(
      rules: List[convention_rule],
      ir: ServiceIRFull,
      diagnostics: DiagBuilder
  ): Unit =
    val entityNames = ir.idx.entityNames.toList
    val tuples      = extractTsTuples(rules, entityNames)
    rules.foreach:
      case rule @ ConventionRuleFull(_, "test_strategy", Some(field), CvOk(PvBool(_)), _)
          if ir.idx.entityNames.contains(cvrTarget(rule)) =>
        val pairs = collisionsForRule(rule, tuples, entityNames)
        if pairs.nonEmpty then
          val others = pairs.map((t, v) => s"$t=$v").mkString(", ")
          diagnostics += ConventionDiagnostic(
            DiagnosticLevel.Error,
            s"conflicting test_strategy for field '$field' across entities ($others); operation inputs named '$field' would resolve ambiguously",
            cvrSpan(rule),
            cvrTarget(rule),
            "test_strategy"
          )
      case _ => ()

  private def err(rule: convention_rule, msg: String, diagnostics: DiagBuilder): Unit =
    diagnostics += ConventionDiagnostic(
      DiagnosticLevel.Error,
      msg,
      cvrSpan(rule),
      cvrTarget(rule),
      cvrProperty(rule)
    )
