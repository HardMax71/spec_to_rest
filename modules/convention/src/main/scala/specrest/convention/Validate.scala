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
  // qualifier (rule.b, the second-dotted-ident or string-literal qualifier).
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

  def validateConventions(
      conventions: Option[conventions_decl_full],
      ir: ServiceIRFull
  ): List[ConventionDiagnostic] =
    conventions match
      case None => Nil
      case Some(ConventionsDeclFull(rules, _)) =>
        val ix          = ir.idx
        val opNames     = ir.g.collect { case OperationDeclFull(n, _, _, _, _, _) => n }.toSet
        val entityNames = ix.entityNames
        val aliasNames  = ix.aliasNames
        val enumNames   = ix.enumNames
        val diagnostics = List.newBuilder[ConventionDiagnostic]
        val seen        = scala.collection.mutable.Map.empty[String, ConventionRuleFull]

        for case rule: ConventionRuleFull <- rules do
          val propName = rule.b
          val propOpt  = byName.get(propName)

          val key = (propOpt, rule.c) match
            case (Some(p), Some(q)) if p.qualifierIsIdentity => s"${rule.a}.$propName:$q"
            case _                                           => s"${rule.a}.$propName"

          seen.get(key) match
            case Some(existing) =>
              val loc = existing.e
                .map { case SpanT(int_of_integer(sl), int_of_integer(sc), _, _) =>
                  s" (first defined at $sl:$sc)"
                }
                .getOrElse("")
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"duplicate override for $key$loc",
                rule.e,
                rule.a,
                propName
              )
            case None => seen(key) = rule

          val targetKind: Option[ConventionTarget] =
            if opNames.contains(rule.a) then Some(ConventionTarget.Operation)
            else if entityNames.contains(rule.a) then Some(ConventionTarget.Entity)
            else if aliasNames.contains(rule.a) || enumNames.contains(rule.a) then
              Some(ConventionTarget.AliasOrEnum)
            else None

          (targetKind, propOpt) match
            case (None, _) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"no operation, entity, type alias, or enum named '${rule.a}'",
                rule.e,
                rule.a,
                propName
              )
            case (Some(_), None) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"unknown convention property '$propName'",
                rule.e,
                rule.a,
                propName
              )
            case (Some(target), Some(prop)) if !prop.appliesTo.contains(target) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                mismatchMessage(rule, prop, target),
                rule.e,
                rule.a,
                propName
              )
            case (Some(_), Some(prop)) =>
              // Value-shape diagnostic: surfaced by the parser as CvBad.
              rule.d match
                case CvBad(failure, _) =>
                  err(rule, failureMsg(rule, failure), diagnostics)
                case _ => ()
              // Cross-cutting qualifier-required check.
              if prop.qualifierIsIdentity && rule.c.isEmpty then
                err(rule, qualifierMissingMsg(rule), diagnostics)
              // IR-context checks (need the full service IR — can't be parser-time).
              validateIrContext(rule, ir, diagnostics)

        detectEntityFieldCollisions(rules, ir, diagnostics)
        diagnostics.result()

  // Format a parser-emitted validation_failure into a diagnostic message keyed
  // to the rule's target + property name.
  private def failureMsg(rule: ConventionRuleFull, f: validation_failure): String = f match
    case _: ExpectedString =>
      s"invalid value for ${rule.a}.${rule.b} — expected a string"
    case _: ExpectedInteger =>
      s"invalid value for ${rule.a}.${rule.b} — expected an integer"
    case _: ExpectedBoolean =>
      s"invalid value for ${rule.a}.${rule.b} — expected true or false"
    case _: EmptyString =>
      s"invalid value for ${rule.a}.${rule.b} — cannot be empty"
    case BadHttpMethod(v) =>
      s"""invalid value for ${rule
          .a}.http_method — expected one of GET, POST, PUT, PATCH, DELETE, got "$v""""
    case HttpStatusOutOfRange(int_of_integer(v)) =>
      s"invalid value for ${rule.a}.http_status_success — expected integer between 100 and 599, got $v"
    case _: HttpPathMissingSlash =>
      s"invalid value for ${rule.a}.http_path — path must start with '/'"
    case BadTestStrategy(v) =>
      s"""invalid value for ${rule.a}.test_strategy — expected "live" or "redacted", got "$v""""
    case BadStrategyFormat(v) =>
      s"""invalid value for ${rule
          .a}.strategy — expected "module:symbol" (e.g., "tests.strategies_user:valid_url"), got "$v""""

  private def qualifierMissingMsg(rule: ConventionRuleFull): String = rule.b match
    case "http_header" =>
      s"""${rule.a}.http_header requires a header name qualifier (e.g., http_header "Location")"""
    case "partial_index" =>
      s"""${rule.a}.partial_index requires a column qualifier (e.g., ${rule.a}.partial_index "active" = "active = true")"""
    case "test_strategy" =>
      s"""${rule.a}.test_strategy requires a field qualifier (e.g., ${rule
          .a}.test_strategy "password" = "redacted" or ${rule.a}.password.test_strategy = "redacted")"""
    case _ =>
      s"${rule.a}.${rule.b} requires a qualifier"

  private def mismatchMessage(
      rule: ConventionRuleFull,
      prop: ConventionProperty,
      target: ConventionTarget
  ): String =
    target match
      case ConventionTarget.AliasOrEnum =>
        if prop.appliesTo.size > 1 then
          val applicable = ConventionTarget.describePlural(prop.appliesTo)
          s"property '${rule.b}' is not valid for type alias / enum '${rule.a}'; it applies to $applicable"
        else
          val aliasProps = Registry
            .filter(_.appliesTo.contains(ConventionTarget.AliasOrEnum))
            .map(p => s"'${p.name}'")
          val hint = aliasProps match
            case Nil     => "no properties apply"
            case List(p) => s"only $p applies"
            case ps      => s"only ${ps.mkString(", ")} apply"
          s"property '${rule.b}' is not valid for type alias / enum '${rule.a}'; $hint"
      case _ =>
        val applicable = ConventionTarget.describePlural(prop.appliesTo)
        s"property '${rule.b}' is not valid for ${target.labelSingular} '${rule.a}'; it applies to $applicable"

  private def validateIrContext(
      rule: ConventionRuleFull,
      ir: ServiceIRFull,
      diagnostics: DiagBuilder
  ): Unit =
    validateIrContextRule(rule, ir.c, ir.g).foreach:
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
      rules: List[convention_rule_full],
      ir: ServiceIRFull,
      diagnostics: DiagBuilder
  ): Unit =
    val entityNames = ir.idx.entityNames
    val grouped = rules
      .collect[(String, String, String, ConventionRuleFull)] {
        case r @ ConventionRuleFull(t, "test_strategy", Some(f), CvOk(PvBool(live)), _)
            if entityNames.contains(t) =>
          (f, t, if live then "live" else "redacted", r)
      }
      .groupBy { case (field, _, _, _) => field }
    grouped.foreach: (field, entries) =>
      val distinctEntities = entries.map((_, t, _, _) => t).distinct
      val distinctValues   = entries.map((_, _, v, _) => v).distinct
      if distinctEntities.size > 1 && distinctValues.size > 1 then
        entries.foreach: (_, target, _, rule) =>
          val others = entries
            .collect { case (_, t, v, _) if t != target => s"$t=$v" }
            .distinct
            .mkString(", ")
          diagnostics += ConventionDiagnostic(
            DiagnosticLevel.Error,
            s"conflicting test_strategy for field '$field' across entities ($others); operation inputs named '$field' would resolve ambiguously",
            rule.e,
            rule.a,
            "test_strategy"
          )

  private def err(rule: ConventionRuleFull, msg: String, diagnostics: DiagBuilder): Unit =
    diagnostics += ConventionDiagnostic(
      DiagnosticLevel.Error,
      msg,
      rule.e,
      rule.a,
      rule.b
    )
