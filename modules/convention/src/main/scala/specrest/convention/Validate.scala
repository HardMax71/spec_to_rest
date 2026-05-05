package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

import specrest.ir.*

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object Validate:

  private val OperationProperties: Set[String] = Set(
    "http_method",
    "http_path",
    "http_status_success",
    "http_header"
  )

  private val EntityProperties: Set[String] = Set(
    "db_table",
    "db_timestamps",
    "plural"
  )

  private val AliasOrEnumProperties: Set[String] = Set("strategy")

  private val FieldQualifiedProperties: Set[String] = Set("test_strategy")

  private val QualifierUsingProperties: Set[String] = Set("http_header", "test_strategy")

  def validateConventions(
      conventions: Option[conventions_decl_full],
      ir: service_ir_full
  ): List[ConventionDiagnostic] =
    conventions match
      case None => Nil
      case Some(c) =>
        val opNames     = ir.g.map(_.name).toSet
        val entityNames = ir.c.map(_.name).toSet
        val aliasNames  = ir.e.map(_.name).toSet
        val enumNames   = ir.d.map(_.name).toSet
        val diagnostics = List.newBuilder[ConventionDiagnostic]
        val seen        = scala.collection.mutable.Map.empty[String, convention_rule_full]

        for rule <- c.rules do
          val key = rule.c match
            case Some(q) if QualifierUsingProperties.contains(rule.b) =>
              s"${rule.a}.${rule.b}:$q"
            case _ => s"${rule.a}.${rule.b}"

          seen.get(key) match
            case Some(existing) =>
              val loc = existing.span
                .map(s => s" (first defined at ${s.a}:${s.b})")
                .getOrElse("")
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"duplicate override for $key$loc",
                rule.span,
                rule.a,
                rule.b
              )
            case None => seen(key) = rule

          val targetKind =
            if opNames.contains(rule.a) then Some("operation")
            else if entityNames.contains(rule.a) then Some("entity")
            else if aliasNames.contains(rule.a) then Some("alias")
            else if enumNames.contains(rule.a) then Some("enum")
            else None

          targetKind match
            case None =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"no operation, entity, type alias, or enum named '${rule.a}'",
                rule.span,
                rule.a,
                rule.b
              )
            case Some("operation") if EntityProperties.contains(rule.b) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.b}' is not valid for operation '${rule.a}'; it applies to entities",
                rule.span,
                rule.a,
                rule.b
              )
            case Some("operation") if AliasOrEnumProperties.contains(rule.b) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.b}' is not valid for operation '${rule.a}'; it applies to type aliases and enums",
                rule.span,
                rule.a,
                rule.b
              )
            case Some("entity") if OperationProperties.contains(rule.b) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.b}' is not valid for entity '${rule.a}'; it applies to operations",
                rule.span,
                rule.a,
                rule.b
              )
            case Some("entity") if AliasOrEnumProperties.contains(rule.b) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.b}' is not valid for entity '${rule.a}'; it applies to type aliases and enums",
                rule.span,
                rule.a,
                rule.b
              )
            case Some("alias" | "enum") if FieldQualifiedProperties.contains(rule.b) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.b}' is not valid for type alias / enum '${rule.a}'; it applies to operations and entities",
                rule.span,
                rule.a,
                rule.b
              )
            case Some("alias" | "enum") if !AliasOrEnumProperties.contains(rule.b) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.b}' is not valid for type alias / enum '${rule.a}'; only 'strategy' applies",
                rule.span,
                rule.a,
                rule.b
              )
            case Some(_)
                if !OperationProperties.contains(rule.b) &&
                  !EntityProperties.contains(rule.b) &&
                  !AliasOrEnumProperties.contains(rule.b) &&
                  !FieldQualifiedProperties.contains(rule.b) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"unknown convention property '${rule.b}'",
                rule.span,
                rule.a,
                rule.b
              )
            case Some(_) =>
              validateValue(rule, ir, diagnostics)

        detectEntityFieldCollisions(c.rules, ir, diagnostics)

        diagnostics.result()

  private def detectEntityFieldCollisions(
      rules: List[convention_rule_full],
      ir: service_ir_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit =
    val entityNames = ir.c.map(_.name).toSet
    val grouped = rules
      .collect:
        case r @ ConventionRuleFull(t, "test_strategy", Some(f), StringLitF(v, _), _)
            if entityNames.contains(t) =>
          (f, t, v, r)
      .groupBy((field, _, _, _) => field)
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
            rule.span,
            rule.a,
            rule.b
          )

  private def validateValue(
      rule: convention_rule_full,
      ir: service_ir_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.b match
    case "http_method"         => validateHttpMethod(rule, diagnostics)
    case "http_status_success" => validateHttpStatus(rule, diagnostics)
    case "http_path"           => validateHttpPath(rule, diagnostics)
    case "http_header"         => validateHttpHeader(rule, diagnostics)
    case "db_table"            => validateDbTable(rule, diagnostics)
    case "db_timestamps"       => validateDbTimestamps(rule, diagnostics)
    case "plural"              => validatePlural(rule, diagnostics)
    case "strategy"            => validateStrategy(rule, diagnostics)
    case "test_strategy"       => validateTestStrategy(rule, ir, diagnostics)
    case _                     => ()

  private def err(
      rule: convention_rule_full,
      msg: String,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit =
    diagnostics += ConventionDiagnostic(
      DiagnosticLevel.Error,
      msg,
      rule.span,
      rule.a,
      rule.b
    )

  private def warn(
      rule: convention_rule_full,
      msg: String,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit =
    diagnostics += ConventionDiagnostic(
      DiagnosticLevel.Warning,
      msg,
      rule.span,
      rule.a,
      rule.b
    )

  private def validateHttpMethod(
      rule: convention_rule_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case StringLitF(v, _) if HttpMethod.parse(v).isEmpty =>
      err(
        rule,
        s"invalid value for ${rule.a}.http_method — expected one of GET, POST, PUT, PATCH, DELETE, got \"$v\"",
        diagnostics
      )
    case StringLitF(_, _) => ()
    case _ =>
      err(rule, s"invalid value for ${rule.a}.http_method — expected a string", diagnostics)

  private def validateHttpStatus(
      rule: convention_rule_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case IntLitF(v, _) if v < 100 || v > 599 =>
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

  private def validateHttpPath(
      rule: convention_rule_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case StringLitF(v, _) if !v.startsWith("/") =>
      err(
        rule,
        s"invalid value for ${rule.a}.http_path — path must start with '/'",
        diagnostics
      )
    case StringLitF(_, _) => ()
    case _ =>
      err(rule, s"invalid value for ${rule.a}.http_path — expected a string", diagnostics)

  private def validateHttpHeader(
      rule: convention_rule_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit =
    if rule.c.isEmpty then
      err(
        rule,
        s"""${rule
            .a}.http_header requires a header name qualifier (e.g., http_header "Location")""",
        diagnostics
      )
    else
      rule.value match
        case StringLitF(_, _) | FieldAccessF(_, _, _) | IdentifierF(_, _) => ()
        case _ =>
          warn(
            rule,
            s"""${rule.a}.http_header "${rule.c
                .get}" value is a complex expression (not validated at compile time)""",
            diagnostics
          )

  private def validateDbTable(
      rule: convention_rule_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case StringLitF(v, _) if v.isEmpty =>
      err(rule, s"invalid value for ${rule.a}.db_table — cannot be empty", diagnostics)
    case StringLitF(_, _) => ()
    case _ =>
      err(rule, s"invalid value for ${rule.a}.db_table — expected a string", diagnostics)

  private def validateDbTimestamps(
      rule: convention_rule_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case BoolLitF(_, _) => ()
    case _ =>
      err(
        rule,
        s"invalid value for ${rule.a}.db_timestamps — expected true or false",
        diagnostics
      )

  private def validatePlural(
      rule: convention_rule_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case StringLitF(v, _) if v.isEmpty =>
      err(rule, s"invalid value for ${rule.a}.plural — cannot be empty", diagnostics)
    case StringLitF(_, _) => ()
    case _ =>
      err(rule, s"invalid value for ${rule.a}.plural — expected a string", diagnostics)

  private def validateStrategy(
      rule: convention_rule_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
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

  private def validateTestStrategy(
      rule: convention_rule_full,
      ir: service_ir_full,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
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
        val opMatch     = ir.g.find(_.name == rule.a)
        val entityMatch = ir.c.find(_.name == rule.a)
        val knownField =
          opMatch.exists(_.b.exists(_.name == field)) ||
            entityMatch.exists(_.fields.exists(_.name == field))
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

    rule.value match
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
