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

  final private case class ConventionProperty(
      name: String,
      appliesTo: Set[ConventionTarget],
      qualifierIsIdentity: Boolean,
      validate: (ConventionRuleFull, ServiceIRFull, DiagBuilder) => Unit
  )

  private val Registry: List[ConventionProperty] = List(
    ConventionProperty(
      "http_method",
      Set(ConventionTarget.Operation),
      qualifierIsIdentity = false,
      (r, _, d) => validateHttpMethod(r, d)
    ),
    ConventionProperty(
      "http_path",
      Set(ConventionTarget.Operation),
      qualifierIsIdentity = false,
      (r, _, d) => validateHttpPath(r, d)
    ),
    ConventionProperty(
      "http_status_success",
      Set(ConventionTarget.Operation),
      qualifierIsIdentity = false,
      (r, _, d) => validateHttpStatus(r, d)
    ),
    ConventionProperty(
      "http_header",
      Set(ConventionTarget.Operation),
      qualifierIsIdentity = true,
      (r, _, d) => validateHttpHeader(r, d)
    ),
    ConventionProperty(
      "db_table",
      Set(ConventionTarget.Entity),
      qualifierIsIdentity = false,
      (r, _, d) => validateDbTable(r, d)
    ),
    ConventionProperty(
      "db_timestamps",
      Set(ConventionTarget.Entity),
      qualifierIsIdentity = false,
      (r, _, d) => validateDbTimestamps(r, d)
    ),
    ConventionProperty(
      "plural",
      Set(ConventionTarget.Entity),
      qualifierIsIdentity = false,
      (r, _, d) => validatePlural(r, d)
    ),
    ConventionProperty(
      "partial_index",
      Set(ConventionTarget.Entity),
      qualifierIsIdentity = true,
      (r, ir, d) => validatePartialIndex(r, ir, d)
    ),
    ConventionProperty(
      "strategy",
      Set(ConventionTarget.AliasOrEnum),
      qualifierIsIdentity = false,
      (r, _, d) => validateStrategy(r, d)
    ),
    ConventionProperty(
      "test_strategy",
      Set(ConventionTarget.Operation, ConventionTarget.Entity),
      qualifierIsIdentity = true,
      (r, ir, d) => validateTestStrategy(r, ir, d)
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
        val opNames     = ir.g.collect { case OperationDeclFull(n, _, _, _, _, _) => n }.toSet
        val entityNames = ir.c.collect { case EntityDeclFull(n, _, _, _, _) => n }.toSet
        val aliasNames  = ir.e.collect { case TypeAliasDeclFull(n, _, _, _) => n }.toSet
        val enumNames   = ir.d.collect { case EnumDeclFull(n, _, _) => n }.toSet
        val diagnostics = List.newBuilder[ConventionDiagnostic]
        val seen        = scala.collection.mutable.Map.empty[String, ConventionRuleFull]

        for case rule: ConventionRuleFull <- rules do
          val propOpt = byName.get(rule.b)

          val key = (propOpt, rule.c) match
            case (Some(p), Some(q)) if p.qualifierIsIdentity => s"${rule.a}.${rule.b}:$q"
            case _                                           => s"${rule.a}.${rule.b}"

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
                rule.b
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
                rule.b
              )
            case (Some(_), None) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"unknown convention property '${rule.b}'",
                rule.e,
                rule.a,
                rule.b
              )
            case (Some(target), Some(prop)) if !prop.appliesTo.contains(target) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                mismatchMessage(rule, prop, target),
                rule.e,
                rule.a,
                rule.b
              )
            case (Some(_), Some(prop)) =>
              prop.validate(rule, ir, diagnostics)

        detectEntityFieldCollisions(rules, ir, diagnostics)

        diagnostics.result()

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

  private def detectEntityFieldCollisions(
      rules: List[convention_rule_full],
      ir: ServiceIRFull,
      diagnostics: DiagBuilder
  ): Unit =
    val entityNames = ir.c.collect { case EntityDeclFull(n, _, _, _, _) => n }.toSet
    val grouped = rules
      .collect[(String, String, String, ConventionRuleFull)] {
        case r @ ConventionRuleFull(t, "test_strategy", Some(f), StringLitF(v, _), _)
            if entityNames.contains(t) =>
          (f, t, v, r)
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
            rule.b
          )

  private def err(rule: ConventionRuleFull, msg: String, diagnostics: DiagBuilder): Unit =
    diagnostics += ConventionDiagnostic(
      DiagnosticLevel.Error,
      msg,
      rule.e,
      rule.a,
      rule.b
    )

  private def warn(rule: ConventionRuleFull, msg: String, diagnostics: DiagBuilder): Unit =
    diagnostics += ConventionDiagnostic(
      DiagnosticLevel.Warning,
      msg,
      rule.e,
      rule.a,
      rule.b
    )

  private def validateHttpMethod(rule: ConventionRuleFull, diagnostics: DiagBuilder): Unit =
    rule.d match
      case StringLitF(v, _) if HttpMethod.parse(v).isEmpty =>
        err(
          rule,
          s"invalid value for ${rule.a}.http_method — expected one of GET, POST, PUT, PATCH, DELETE, got \"$v\"",
          diagnostics
        )
      case StringLitF(_, _) => ()
      case _ =>
        err(rule, s"invalid value for ${rule.a}.http_method — expected a string", diagnostics)

  private def validateHttpStatus(rule: ConventionRuleFull, diagnostics: DiagBuilder): Unit =
    rule.d match
      case IntLitF(int_of_integer(v), _) if v < 100 || v > 599 =>
        err(
          rule,
          s"invalid value for ${rule.a}.http_status_success — expected integer between 100 and 599, got $v",
          diagnostics
        )
      case IntLitF(_, _) => ()
      case _ =>
        err(
          rule,
          s"invalid value for ${rule.a}.http_status_success — expected an integer",
          diagnostics
        )

  private def validateHttpPath(rule: ConventionRuleFull, diagnostics: DiagBuilder): Unit =
    rule.d match
      case StringLitF(v, _) if !v.startsWith("/") =>
        err(
          rule,
          s"invalid value for ${rule.a}.http_path — path must start with '/'",
          diagnostics
        )
      case StringLitF(_, _) => ()
      case _ =>
        err(rule, s"invalid value for ${rule.a}.http_path — expected a string", diagnostics)

  private def validateHttpHeader(rule: ConventionRuleFull, diagnostics: DiagBuilder): Unit =
    if rule.c.isEmpty then
      err(
        rule,
        s"""${rule
            .a}.http_header requires a header name qualifier (e.g., http_header "Location")""",
        diagnostics
      )
    else
      rule.d match
        case StringLitF(_, _) | FieldAccessF(_, _, _) | IdentifierF(_, _) => ()
        case _ =>
          warn(
            rule,
            s"""${rule.a}.http_header "${rule.c
                .get}" value is a complex expression (not validated at compile time)""",
            diagnostics
          )

  private def validateDbTable(rule: ConventionRuleFull, diagnostics: DiagBuilder): Unit =
    rule.d match
      case StringLitF(v, _) if v.isEmpty =>
        err(rule, s"invalid value for ${rule.a}.db_table — cannot be empty", diagnostics)
      case StringLitF(_, _) => ()
      case _ =>
        err(rule, s"invalid value for ${rule.a}.db_table — expected a string", diagnostics)

  private def validateDbTimestamps(rule: ConventionRuleFull, diagnostics: DiagBuilder): Unit =
    rule.d match
      case BoolLitF(_, _) => ()
      case _ =>
        err(
          rule,
          s"invalid value for ${rule.a}.db_timestamps — expected true or false",
          diagnostics
        )

  private def validatePlural(rule: ConventionRuleFull, diagnostics: DiagBuilder): Unit =
    rule.d match
      case StringLitF(v, _) if v.isEmpty =>
        err(rule, s"invalid value for ${rule.a}.plural — cannot be empty", diagnostics)
      case StringLitF(_, _) => ()
      case _ =>
        err(rule, s"invalid value for ${rule.a}.plural — expected a string", diagnostics)

  private def validateStrategy(rule: ConventionRuleFull, diagnostics: DiagBuilder): Unit =
    rule.d match
      case StringLitF(v, _) =>
        v.split(':') match
          case Array(m, s) if m.nonEmpty && s.nonEmpty => ()
          case _ =>
            err(
              rule,
              s"""invalid value for ${rule.a}.strategy — expected "module:symbol" (e.g., "tests.strategies_user:valid_url"), got "$v"""",
              diagnostics
            )
      case _ =>
        err(rule, s"invalid value for ${rule.a}.strategy — expected a string", diagnostics)

  private def validatePartialIndex(
      rule: ConventionRuleFull,
      ir: ServiceIRFull,
      diagnostics: DiagBuilder
  ): Unit =
    rule.c match
      case None =>
        err(
          rule,
          s"""${rule.a}.partial_index requires a column qualifier (e.g., ${rule
              .a}.partial_index "active" = "active = true")""",
          diagnostics
        )
      case Some(field) =>
        val entityMatch = ir.c.collectFirst { case e: EntityDeclFull if e.a == rule.a => e }
        val knownField =
          entityMatch.exists(_.c.exists { case FieldDeclFull(n, _, _, _) => n == field })
        if entityMatch.isDefined && !knownField then
          err(
            rule,
            s"""${rule.a}.partial_index "$field" — no field named '$field' on entity '${rule.a}'""",
            diagnostics
          )

    rule.d match
      case StringLitF(v, _) if v.trim.isEmpty =>
        err(
          rule,
          s"""invalid value for ${rule.a}.partial_index — WHERE clause cannot be empty""",
          diagnostics
        )
      case StringLitF(_, _) => ()
      case _ =>
        err(
          rule,
          s"invalid value for ${rule.a}.partial_index — expected a string (the WHERE clause body, e.g. \"active = true\")",
          diagnostics
        )

  private def validateTestStrategy(
      rule: ConventionRuleFull,
      ir: ServiceIRFull,
      diagnostics: DiagBuilder
  ): Unit =
    rule.c match
      case None =>
        err(
          rule,
          s"""${rule.a}.test_strategy requires a field qualifier (e.g., ${rule
              .a}.test_strategy "password" = "redacted" or ${rule
              .a}.password.test_strategy = "redacted")""",
          diagnostics
        )
      case Some(field) =>
        val opMatch     = ir.g.collectFirst { case o: OperationDeclFull if o.a == rule.a => o }
        val entityMatch = ir.c.collectFirst { case e: EntityDeclFull if e.a == rule.a => e }
        val knownField =
          opMatch.exists(_.b.exists { case ParamDeclFull(n, _, _) => n == field }) ||
            entityMatch.exists(_.c.exists { case FieldDeclFull(n, _, _, _) => n == field })
        if !knownField then
          val targetKind =
            if opMatch.isDefined then "operation"
            else if entityMatch.isDefined then "entity"
            else "target"
          err(
            rule,
            s"""${rule.a}.test_strategy "$field" — no field named '$field' on $targetKind '${rule
                .a}'""",
            diagnostics
          )

    rule.d match
      case StringLitF("live", _) | StringLitF("redacted", _) => ()
      case StringLitF(v, _) =>
        err(
          rule,
          s"""invalid value for ${rule
              .a}.test_strategy — expected "live" or "redacted", got "$v"""",
          diagnostics
        )
      case _ =>
        err(
          rule,
          s"invalid value for ${rule.a}.test_strategy — expected a string (\"live\" or \"redacted\")",
          diagnostics
        )
