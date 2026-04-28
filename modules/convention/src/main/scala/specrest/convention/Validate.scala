package specrest.convention

import specrest.ir.*

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

  def validateConventions(
      conventions: Option[ConventionsDecl],
      ir: ServiceIR
  ): List[ConventionDiagnostic] =
    conventions match
      case None => Nil
      case Some(c) =>
        val opNames     = ir.operations.map(_.name).toSet
        val entityNames = ir.entities.map(_.name).toSet
        val aliasNames  = ir.typeAliases.map(_.name).toSet
        val enumNames   = ir.enums.map(_.name).toSet
        val diagnostics = List.newBuilder[ConventionDiagnostic]
        val seen        = scala.collection.mutable.Map.empty[String, ConventionRule]

        for rule <- c.rules do
          val key = rule.qualifier match
            case Some(q) => s"${rule.target}.${rule.property}:$q"
            case None    => s"${rule.target}.${rule.property}"

          seen.get(key) match
            case Some(existing) =>
              val loc = existing.span
                .map(s => s" (first defined at ${s.startLine}:${s.startCol})")
                .getOrElse("")
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"duplicate override for $key$loc",
                rule.span,
                rule.target,
                rule.property
              )
            case None => seen(key) = rule

          val targetKind =
            if opNames.contains(rule.target) then Some("operation")
            else if entityNames.contains(rule.target) then Some("entity")
            else if aliasNames.contains(rule.target) then Some("alias")
            else if enumNames.contains(rule.target) then Some("enum")
            else None

          targetKind match
            case None =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"no operation, entity, type alias, or enum named '${rule.target}'",
                rule.span,
                rule.target,
                rule.property
              )
            case Some("operation") if EntityProperties.contains(rule.property) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.property}' is not valid for operation '${rule.target}'; it applies to entities",
                rule.span,
                rule.target,
                rule.property
              )
            case Some("operation") if AliasOrEnumProperties.contains(rule.property) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.property}' is not valid for operation '${rule.target}'; it applies to type aliases and enums",
                rule.span,
                rule.target,
                rule.property
              )
            case Some("entity") if OperationProperties.contains(rule.property) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.property}' is not valid for entity '${rule.target}'; it applies to operations",
                rule.span,
                rule.target,
                rule.property
              )
            case Some("entity") if AliasOrEnumProperties.contains(rule.property) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.property}' is not valid for entity '${rule.target}'; it applies to type aliases and enums",
                rule.span,
                rule.target,
                rule.property
              )
            case Some("alias" | "enum") if FieldQualifiedProperties.contains(rule.property) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.property}' is not valid for type alias / enum '${rule.target}'; it applies to operations and entities",
                rule.span,
                rule.target,
                rule.property
              )
            case Some("alias" | "enum") if !AliasOrEnumProperties.contains(rule.property) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"property '${rule.property}' is not valid for type alias / enum '${rule.target}'; only 'strategy' applies",
                rule.span,
                rule.target,
                rule.property
              )
            case Some(_)
                if !OperationProperties.contains(rule.property) &&
                  !EntityProperties.contains(rule.property) &&
                  !AliasOrEnumProperties.contains(rule.property) &&
                  !FieldQualifiedProperties.contains(rule.property) =>
              diagnostics += ConventionDiagnostic(
                DiagnosticLevel.Error,
                s"unknown convention property '${rule.property}'",
                rule.span,
                rule.target,
                rule.property
              )
            case Some(_) =>
              validateValue(rule, ir, diagnostics)

        diagnostics.result()

  private def validateValue(
      rule: ConventionRule,
      ir: ServiceIR,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.property match
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
      rule: ConventionRule,
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
      rule.target,
      rule.property
    )

  private def warn(
      rule: ConventionRule,
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
      rule.target,
      rule.property
    )

  private def validateHttpMethod(
      rule: ConventionRule,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case Expr.StringLit(v, _) if HttpMethod.parse(v).isEmpty =>
      err(
        rule,
        s"invalid value for ${rule.target}.http_method — expected one of GET, POST, PUT, PATCH, DELETE, got \"$v\"",
        diagnostics
      )
    case Expr.StringLit(_, _) => ()
    case _ =>
      err(rule, s"invalid value for ${rule.target}.http_method — expected a string", diagnostics)

  private def validateHttpStatus(
      rule: ConventionRule,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case Expr.IntLit(v, _) if v < 100 || v > 599 =>
      err(
        rule,
        s"invalid value for ${rule.target}.http_status_success — expected integer between 100 and 599, got $v",
        diagnostics
      )
    case Expr.IntLit(_, _) => ()
    case _ =>
      err(
        rule,
        s"invalid value for ${rule.target}.http_status_success — expected an integer",
        diagnostics
      )

  private def validateHttpPath(
      rule: ConventionRule,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case Expr.StringLit(v, _) if !v.startsWith("/") =>
      err(
        rule,
        s"invalid value for ${rule.target}.http_path — path must start with '/'",
        diagnostics
      )
    case Expr.StringLit(_, _) => ()
    case _ =>
      err(rule, s"invalid value for ${rule.target}.http_path — expected a string", diagnostics)

  private def validateHttpHeader(
      rule: ConventionRule,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit =
    if rule.qualifier.isEmpty then
      err(
        rule,
        s"""${rule
            .target}.http_header requires a header name qualifier (e.g., http_header "Location")""",
        diagnostics
      )
    else
      rule.value match
        case Expr.StringLit(_, _) | Expr.FieldAccess(_, _, _) | Expr.Identifier(_, _) => ()
        case _ =>
          warn(
            rule,
            s"""${rule.target}.http_header "${rule.qualifier
                .get}" value is a complex expression (not validated at compile time)""",
            diagnostics
          )

  private def validateDbTable(
      rule: ConventionRule,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case Expr.StringLit(v, _) if v.isEmpty =>
      err(rule, s"invalid value for ${rule.target}.db_table — cannot be empty", diagnostics)
    case Expr.StringLit(_, _) => ()
    case _ =>
      err(rule, s"invalid value for ${rule.target}.db_table — expected a string", diagnostics)

  private def validateDbTimestamps(
      rule: ConventionRule,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case Expr.BoolLit(_, _) => ()
    case _ =>
      err(
        rule,
        s"invalid value for ${rule.target}.db_timestamps — expected true or false",
        diagnostics
      )

  private def validatePlural(
      rule: ConventionRule,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case Expr.StringLit(v, _) if v.isEmpty =>
      err(rule, s"invalid value for ${rule.target}.plural — cannot be empty", diagnostics)
    case Expr.StringLit(_, _) => ()
    case _ =>
      err(rule, s"invalid value for ${rule.target}.plural — expected a string", diagnostics)

  private def validateStrategy(
      rule: ConventionRule,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit = rule.value match
    case Expr.StringLit(v, _) =>
      v.split(':') match
        case Array(m, s) if m.nonEmpty && s.nonEmpty => ()
        case _ =>
          err(
            rule,
            s"""invalid value for ${rule.target}.strategy — expected "module:symbol" (e.g., "tests.strategies_user:valid_url"), got "$v"""",
            diagnostics
          )
    case _ =>
      err(rule, s"invalid value for ${rule.target}.strategy — expected a string", diagnostics)

  private def validateTestStrategy(
      rule: ConventionRule,
      ir: ServiceIR,
      diagnostics: scala.collection.mutable.Builder[
        ConventionDiagnostic,
        List[ConventionDiagnostic]
      ]
  ): Unit =
    rule.qualifier match
      case None =>
        err(
          rule,
          s"""${rule.target}.test_strategy requires a field qualifier (e.g., ${rule
              .target}.test_strategy "password" = "redacted" or ${rule
              .target}.password.test_strategy = "redacted")""",
          diagnostics
        )
      case Some(field) =>
        val opMatch     = ir.operations.find(_.name == rule.target)
        val entityMatch = ir.entities.find(_.name == rule.target)
        val knownField =
          opMatch.exists(_.inputs.exists(_.name == field)) ||
            entityMatch.exists(_.fields.exists(_.name == field))
        if !knownField then
          val targetKind =
            if opMatch.isDefined then "operation"
            else if entityMatch.isDefined then "entity"
            else "target"
          err(
            rule,
            s"""${rule.target}.test_strategy "$field" — no field named '$field' on $targetKind '${rule
                .target}'""",
            diagnostics
          )

    rule.value match
      case Expr.StringLit("live", _) | Expr.StringLit("redacted", _) => ()
      case Expr.StringLit(v, _) =>
        err(
          rule,
          s"""invalid value for ${rule
              .target}.test_strategy — expected "live" or "redacted", got "$v"""",
          diagnostics
        )
      case _ =>
        err(
          rule,
          s"invalid value for ${rule.target}.test_strategy — expected a string (\"live\" or \"redacted\")",
          diagnostics
        )
