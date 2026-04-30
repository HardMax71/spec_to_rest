package specrest.testgen

import specrest.codegen.SensitiveFields
import specrest.convention.Naming
import specrest.ir.BinOp
import specrest.ir.ConventionRule
import specrest.ir.EntityDecl
import specrest.ir.EnumDecl
import specrest.ir.Expr
import specrest.ir.FieldDecl
import specrest.ir.ServiceIR
import specrest.ir.TypeAliasDecl
import specrest.ir.TypeExpr

final case class StrategyImport(module: String, symbol: String)

final case class StrategySpec(
    typeName: String,
    functionName: String,
    body: String,
    skipped: List[String],
    imports: List[StrategyImport] = Nil
)

enum StrategyExpr:
  case Code(text: String)
  case Skip(reason: String)

enum StrategyCtx:
  case Anonymous
  case OperationInput(opName: String, fieldName: String)
  case EntityField(entityName: String, fieldName: String)

final case class TestStrategyOverrides(
    perOperation: Map[(String, String), String],
    perEntityField: Map[String, String]
):
  def resolve(ctx: StrategyCtx): Option[String] = ctx match
    case StrategyCtx.Anonymous => None
    case StrategyCtx.OperationInput(op, f) =>
      perOperation.get((op, f)).orElse(perEntityField.get(f))
    case StrategyCtx.EntityField(_, f) =>
      perEntityField.get(f)

object TestStrategyOverrides:
  val Empty: TestStrategyOverrides = TestStrategyOverrides(Map.empty, Map.empty)

  def from(ir: ServiceIR): TestStrategyOverrides =
    val rules = ir.conventions.toList.flatMap(_.rules).collect:
      case ConventionRule(target, "test_strategy", Some(field), Expr.StringLit(v, _), _)
          if v == "live" || v == "redacted" =>
        (target, field, v)
    val opNames     = ir.operations.map(_.name).toSet
    val entityNames = ir.entities.map(_.name).toSet
    val perOp = rules.collect:
      case (t, f, v) if opNames.contains(t) => (t, f) -> v
    val perField = rules.collect:
      case (t, f, v) if entityNames.contains(t) => f -> v
    TestStrategyOverrides(perOp.toMap, perField.toMap)

final private case class StringConstraint(
    minSize: Option[Int] = None,
    maxSize: Option[Int] = None,
    regexes: List[String] = Nil,
    predicateHelpers: List[String] = Nil,
    extraFilters: List[String] = Nil
):
  def merge(other: StringConstraint): StringConstraint =
    StringConstraint(
      minSize = (minSize, other.minSize) match
        case (Some(a), Some(b)) => Some(a max b)
        case (a, b)             => a.orElse(b),
      maxSize = (maxSize, other.maxSize) match
        case (Some(a), Some(b)) => Some(a min b)
        case (a, b)             => a.orElse(b),
      regexes = (regexes ++ other.regexes).distinct,
      predicateHelpers = (predicateHelpers ++ other.predicateHelpers).distinct,
      extraFilters = extraFilters ++ other.extraFilters
    )

final private case class IntConstraint(
    minValue: Option[Long] = None,
    maxValue: Option[Long] = None,
    extraFilters: List[String] = Nil
):
  def merge(other: IntConstraint): IntConstraint =
    IntConstraint(
      minValue = (minValue, other.minValue) match
        case (Some(a), Some(b)) => Some(a max b)
        case (a, b)             => a.orElse(b),
      maxValue = (maxValue, other.maxValue) match
        case (Some(a), Some(b)) => Some(a min b)
        case (a, b)             => a.orElse(b),
      extraFilters = extraFilters ++ other.extraFilters
    )

object Strategies:

  def forIR(ir: ServiceIR): List[StrategySpec] =
    val overrides     = strategyOverrides(ir)
    val aliasSpecs    = ir.typeAliases.map(specForAlias(_, ir, overrides))
    val enumSpecs     = ir.enums.map(specForEnum(_, overrides))
    val transEntities = transitionEntityNames(ir)
    val entitySpecs = ir.entities
      .filter(e => transEntities.contains(e.name))
      .map(e => specForEntity(e, ir))
    aliasSpecs ++ enumSpecs ++ entitySpecs

  def transitionEntityNames(ir: ServiceIR): Set[String] =
    ir.transitions.map(_.entityName).toSet

  private def strategyOverrides(ir: ServiceIR): Map[String, StrategyImport] =
    ir.conventions.toList.flatMap(_.rules).flatMap:
      case ConventionRule(target, "strategy", _, Expr.StringLit(v, _), _) =>
        v.split(':') match
          case Array(m, s) if m.nonEmpty && s.nonEmpty =>
            Some(target -> StrategyImport(m, s))
          case _ => None
      case _ => None
    .toMap

  def expressionFor(t: TypeExpr, ir: ServiceIR): StrategyExpr =
    expressionFor(t, ir, StrategyCtx.Anonymous, TestStrategyOverrides.from(ir))

  def expressionFor(
      t: TypeExpr,
      ir: ServiceIR,
      ctx: StrategyCtx,
      overrides: TestStrategyOverrides
  ): StrategyExpr =
    val raw = bareExpression(t, ir)
    applyRedaction(raw, ctx, overrides)

  private def applyRedaction(
      raw: StrategyExpr,
      ctx: StrategyCtx,
      overrides: TestStrategyOverrides
  ): StrategyExpr =
    raw match
      case skip: StrategyExpr.Skip => skip
      case StrategyExpr.Code(t) =>
        val fieldName = ctx match
          case StrategyCtx.Anonymous            => None
          case StrategyCtx.OperationInput(_, f) => Some(f)
          case StrategyCtx.EntityField(_, f)    => Some(f)
        val isSensitive = fieldName.exists(SensitiveFields.isSensitive)
        overrides.resolve(ctx) match
          case Some("live")     => StrategyExpr.Code(t)
          case Some("redacted") => StrategyExpr.Code(s"""st.just("${RedactedPlaceholder}")""")
          case _ if isSensitive => StrategyExpr.Code(s"redact($t)")
          case _                => StrategyExpr.Code(t)

  private[testgen] val RedactedPlaceholder: String = "***REDACTED***"

  private def bareExpression(t: TypeExpr, ir: ServiceIR): StrategyExpr = t match
    case TypeExpr.NamedType("String", _) => StrategyExpr.Code("st.text()")
    case TypeExpr.NamedType("Int", _)    => StrategyExpr.Code("st.integers()")
    case TypeExpr.NamedType("Float", _) =>
      StrategyExpr.Code("st.floats(allow_nan=False, allow_infinity=False)")
    case TypeExpr.NamedType("Bool", _)     => StrategyExpr.Code("st.booleans()")
    case TypeExpr.NamedType("DateTime", _) => StrategyExpr.Code("st.datetimes()")
    case TypeExpr.NamedType("Duration", _) => StrategyExpr.Code("st.timedeltas()")
    case TypeExpr.NamedType("Id", _)       => StrategyExpr.Code("st.uuids().map(str)")
    case TypeExpr.NamedType(name, _) =>
      if ir.typeAliases.exists(_.name == name) || ir.enums.exists(_.name == name) then
        StrategyExpr.Code(s"${strategyFunctionName(name)}()")
      else StrategyExpr.Skip(s"unknown named type '$name'")
    case TypeExpr.OptionType(inner, _) =>
      bareExpression(inner, ir) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(s"st.one_of(st.none(), $t)")
        case s @ StrategyExpr.Skip(_) => s
    case TypeExpr.SetType(inner, _) =>
      bareExpression(inner, ir) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(s"st.sets($t, max_size=5)")
        case s @ StrategyExpr.Skip(_) => s
    case TypeExpr.SeqType(inner, _) =>
      bareExpression(inner, ir) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(s"st.lists($t, max_size=5)")
        case s @ StrategyExpr.Skip(_) => s
    case TypeExpr.MapType(_, _, _)         => StrategyExpr.Skip("MapType strategy")
    case TypeExpr.RelationType(_, _, _, _) => StrategyExpr.Skip("RelationType strategy")

  private[testgen] def strategyFunctionName(typeName: String): String =
    s"strategy_${Naming.toSnakeCase(typeName)}"

  private def specForAlias(
      alias: TypeAliasDecl,
      ir: ServiceIR,
      overrides: Map[String, StrategyImport]
  ): StrategySpec =
    overrides.get(alias.name) match
      case Some(imp) =>
        StrategySpec(
          typeName = alias.name,
          functionName = strategyFunctionName(alias.name),
          body = s"${imp.symbol}()",
          skipped = Nil,
          imports = List(imp)
        )
      case None =>
        val (body, skipped) = renderAlias(alias, ir)
        StrategySpec(
          typeName = alias.name,
          functionName = strategyFunctionName(alias.name),
          body = body,
          skipped = skipped
        )

  private def specForEntity(entity: EntityDecl, ir: ServiceIR): StrategySpec =
    val overrides = TestStrategyOverrides.from(ir)
    val pairs = entity.fields.map: f =>
      val ctx     = StrategyCtx.EntityField(entity.name, f.name)
      val rawExpr = jsonStrategyForField(f, ir)
      val expr    = applyRedaction(rawExpr, ctx, overrides)
      (f.name, expr)
    val skipped = pairs.collect:
      case (n, StrategyExpr.Skip(r)) => s"entity '${entity.name}' field '$n': $r"
    val codeEntries = pairs.collect:
      case (n, StrategyExpr.Code(t)) => s"        ${ExprToPython.pyString(n)}: $t"
    val body =
      if codeEntries.isEmpty then "st.fixed_dictionaries({})"
      else s"st.fixed_dictionaries({\n${codeEntries.mkString(",\n")},\n    })"
    StrategySpec(
      typeName = entity.name,
      functionName = strategyFunctionName(entity.name),
      body = body,
      skipped = skipped
    )

  private def jsonStrategyForField(f: FieldDecl, ir: ServiceIR): StrategyExpr =
    jsonStrategyForType(f.typeExpr, f.constraint, ir)

  private def jsonStrategyForType(
      t: TypeExpr,
      constraint: Option[Expr],
      ir: ServiceIR
  ): StrategyExpr = t match
    case TypeExpr.NamedType("String", _) =>
      val (cs, _) = collectStringConstraint(constraint, ir)
      StrategyExpr.Code(renderStringStrategy(cs))
    case TypeExpr.NamedType("Int", _) =>
      val (cs, _) = collectIntConstraint(constraint)
      StrategyExpr.Code(renderIntStrategy(cs))
    case TypeExpr.NamedType("Float", _) =>
      StrategyExpr.Code("st.floats(allow_nan=False, allow_infinity=False)")
    case TypeExpr.NamedType("Bool", _) => StrategyExpr.Code("st.booleans()")
    case TypeExpr.NamedType("DateTime", _) =>
      StrategyExpr.Code("st.datetimes().map(lambda d: d.isoformat())")
    case TypeExpr.NamedType("Duration", _) =>
      StrategyExpr.Code("st.timedeltas().map(lambda d: d.total_seconds())")
    case TypeExpr.NamedType("Id", _) => StrategyExpr.Code("st.uuids().map(str)")
    case TypeExpr.NamedType(name, _) =>
      if ir.enums.exists(_.name == name) then
        StrategyExpr.Code(s"${strategyFunctionName(name)}()")
      else
        ir.typeAliases.find(_.name == name) match
          case Some(alias) =>
            val combined = (constraint, alias.constraint) match
              case (Some(c), Some(a)) => Some(Expr.BinaryOp(BinOp.And, c, a))
              case (c, a)             => c.orElse(a)
            jsonStrategyForType(alias.typeExpr, combined, ir)
          case None =>
            if ir.entities.exists(_.name == name) then
              StrategyExpr.Skip(s"nested entity reference '$name' not seedable")
            else StrategyExpr.Skip(s"unknown named type '$name'")
    case TypeExpr.OptionType(inner, _) =>
      jsonStrategyForType(inner, None, ir) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(s"st.one_of(st.none(), $t)")
        case StrategyExpr.Skip(_) => StrategyExpr.Code("st.none()")
    case TypeExpr.SetType(inner, _) =>
      jsonStrategyForType(inner, None, ir) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(s"st.lists($t, unique=True, max_size=5)")
        case s                    => s
    case TypeExpr.SeqType(inner, _) =>
      jsonStrategyForType(inner, None, ir) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(s"st.lists($t, max_size=5)")
        case s                    => s
    case TypeExpr.MapType(_, _, _)         => StrategyExpr.Skip("MapType field not seedable")
    case TypeExpr.RelationType(_, _, _, _) => StrategyExpr.Skip("RelationType field not seedable")

  private def specForEnum(
      decl: EnumDecl,
      overrides: Map[String, StrategyImport]
  ): StrategySpec =
    overrides.get(decl.name) match
      case Some(imp) =>
        StrategySpec(
          typeName = decl.name,
          functionName = strategyFunctionName(decl.name),
          body = s"${imp.symbol}()",
          skipped = Nil,
          imports = List(imp)
        )
      case None =>
        val literals = decl.values.map(v => ExprToPython.pyString(v)).mkString(", ")
        StrategySpec(
          typeName = decl.name,
          functionName = strategyFunctionName(decl.name),
          body = s"st.sampled_from([$literals])",
          skipped = Nil
        )

  private def renderAlias(alias: TypeAliasDecl, ir: ServiceIR): (String, List[String]) =
    alias.typeExpr match
      case TypeExpr.NamedType("String", _) =>
        val (cs, skipped) = collectStringConstraint(alias.constraint, ir)
        (renderStringStrategy(cs), skipped)
      case TypeExpr.NamedType("Int", _) =>
        val (cs, skipped) = collectIntConstraint(alias.constraint)
        (renderIntStrategy(cs), skipped)
      case other =>
        expressionFor(other, ir) match
          case StrategyExpr.Code(t) => (t, Nil)
          case StrategyExpr.Skip(r) => (s"st.nothing()", List(r))

  private def collectStringConstraint(
      c: Option[Expr],
      ir: ServiceIR
  ): (StringConstraint, List[String]) =
    c match
      case None    => (StringConstraint(), Nil)
      case Some(e) => walkStringConstraint(e, ir)

  private def walkStringConstraint(
      e: Expr,
      ir: ServiceIR
  ): (StringConstraint, List[String]) = e match
    case Expr.BinaryOp(BinOp.And, l, r, _) =>
      val (lc, lsk) = walkStringConstraint(l, ir)
      val (rc, rsk) = walkStringConstraint(r, ir)
      (lc.merge(rc), lsk ++ rsk)

    case LenCmp(op, n) =>
      op match
        case BinOp.Ge => (StringConstraint(minSize = Some(n.toInt)), Nil)
        case BinOp.Gt => (StringConstraint(minSize = Some((n + 1).toInt)), Nil)
        case BinOp.Le => (StringConstraint(maxSize = Some(n.toInt)), Nil)
        case BinOp.Lt => (StringConstraint(maxSize = Some((n - 1).toInt)), Nil)
        case BinOp.Eq => (StringConstraint(minSize = Some(n.toInt), maxSize = Some(n.toInt)), Nil)
        case _        => (StringConstraint(), List(s"unsupported len comparison $op"))

    case Expr.Matches(Expr.Identifier("value", _), pattern, _) =>
      (StringConstraint(regexes = List(pattern)), Nil)

    case Expr.Call(Expr.Identifier(name, _), List(Expr.Identifier("value", _)), _) =>
      inlineMatchesPredicate(name, ir) match
        case Some(pattern) =>
          (StringConstraint(regexes = List(pattern)), Nil)
        case None =>
          ir.predicates.find(_.name == name) match
            case None =>
              (StringConstraint(), List(s"unknown predicate '$name' in string constraint"))
            case Some(pr) if pr.params.size != 1 =>
              (
                StringConstraint(),
                List(
                  s"predicate '$name' has arity ${pr.params.size}; string-constraint filters require arity 1"
                )
              )
            case Some(_) =>
              val snake = Naming.toSnakeCase(name)
              if PythonReservedNames.contains(snake) then
                (
                  StringConstraint(),
                  List(
                    s"predicate '$name' (snake-cased to '$snake') is a Python-reserved name; cannot emit strategy filter"
                  )
                )
              else (StringConstraint(predicateHelpers = List(snake)), Nil)

    case other =>
      (StringConstraint(), List(s"unhandled string constraint: ${shortShape(other)}"))

  private def inlineMatchesPredicate(name: String, ir: ServiceIR): Option[String] =
    ir.predicates
      .find(_.name == name)
      .filter(_.params.size == 1)
      .flatMap: pr =>
        val paramName = pr.params.head.name
        pr.body match
          case Expr.Matches(Expr.Identifier(p, _), pattern, _) if p == paramName =>
            Some(pattern)
          case _ => None

  private def collectIntConstraint(c: Option[Expr]): (IntConstraint, List[String]) =
    c match
      case None    => (IntConstraint(), Nil)
      case Some(e) => walkIntConstraint(e)

  private def walkIntConstraint(e: Expr): (IntConstraint, List[String]) = e match
    case Expr.BinaryOp(BinOp.And, l, r, _) =>
      val (lc, lsk) = walkIntConstraint(l)
      val (rc, rsk) = walkIntConstraint(r)
      (lc.merge(rc), lsk ++ rsk)

    case Expr.BinaryOp(op, Expr.Identifier("value", _), Expr.IntLit(n, _), _) =>
      op match
        case BinOp.Ge => (IntConstraint(minValue = Some(n)), Nil)
        case BinOp.Gt => (IntConstraint(minValue = Some(n + 1)), Nil)
        case BinOp.Le => (IntConstraint(maxValue = Some(n)), Nil)
        case BinOp.Lt => (IntConstraint(maxValue = Some(n - 1)), Nil)
        case BinOp.Eq => (IntConstraint(minValue = Some(n), maxValue = Some(n)), Nil)
        case _        => (IntConstraint(), List(s"unsupported int comparison $op"))

    case other =>
      (IntConstraint(), List(s"unhandled int constraint: ${shortShape(other)}"))

  private def renderStringStrategy(c: StringConstraint): String =
    val (primaryRegex, extraRegexes) = c.regexes match
      case head :: tail => (Some(head), tail)
      case Nil          => (None, Nil)
    val base = primaryRegex match
      case Some(p) => s"st.from_regex(${ExprToPython.pyString(p)}, fullmatch=True)"
      case None =>
        val args = List(
          c.minSize.map(n => s"min_size=$n"),
          c.maxSize.map(n => s"max_size=$n")
        ).flatten.mkString(", ")
        if args.isEmpty then "st.text()" else s"st.text($args)"
    val withLenFilter = (primaryRegex, c.minSize, c.maxSize) match
      case (Some(_), Some(lo), Some(hi)) => s"$base.filter(lambda v: $lo <= len(v) <= $hi)"
      case (Some(_), Some(lo), None)     => s"$base.filter(lambda v: len(v) >= $lo)"
      case (Some(_), None, Some(hi))     => s"$base.filter(lambda v: len(v) <= $hi)"
      case _                             => base
    val withExtraRegex = extraRegexes.foldLeft(withLenFilter): (acc, r) =>
      s"$acc.filter(lambda v: __import__('re').fullmatch(${ExprToPython.pyString(r)}, v) is not None)"
    c.predicateHelpers.foldLeft(withExtraRegex): (acc, h) =>
      s"$acc.filter(lambda v: $h(v))"

  private def renderIntStrategy(c: IntConstraint): String =
    val args = List(
      c.minValue.map(n => s"min_value=$n"),
      c.maxValue.map(n => s"max_value=$n")
    ).flatten.mkString(", ")
    if args.isEmpty then "st.integers()" else s"st.integers($args)"

  private object LenCmp:
    def unapply(e: Expr): Option[(BinOp, Long)] = e match
      case Expr.BinaryOp(
            op,
            Expr.Call(Expr.Identifier("len", _), List(Expr.Identifier("value", _)), _),
            Expr.IntLit(n, _),
            _
          ) =>
        Some((op, n))
      case _ => None

  private def shortShape(e: Expr): String = e match
    case Expr.BinaryOp(op, _, _, _) => s"BinaryOp($op)"
    case Expr.UnaryOp(op, _, _)     => s"UnaryOp($op)"
    case Expr.Call(_, _, _)         => "Call"
    case Expr.Matches(_, _, _)      => "Matches"
    case Expr.Identifier(n, _)      => s"Identifier($n)"
    case _                          => e.getClass.getSimpleName
