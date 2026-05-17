package specrest.testgen

import specrest.codegen.SensitiveFields
import specrest.convention.Naming
import specrest.ir.generated.SpecRestGenerated.*

final case class StrategyImport(module: String, symbol: String)

final case class StrategySpec(
    typeName: String,
    functionName: String,
    body: String,
    skipped: List[String],
    imports: List[StrategyImport] = Nil
)

enum StrategyExpr derives CanEqual:
  case Code(text: String)
  case Skip(reason: String)

enum StrategyCtx derives CanEqual:
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

  def from(ir: ServiceIRFull): TestStrategyOverrides =
    val rules = ir.n.toList.flatMap { case ConventionsDeclFull(rs, _) => rs }.collect:
      case ConventionRuleFull(target, "test_strategy", Some(field), StringLitF(v, _), _)
          if v == "live" || v == "redacted" =>
        (target, field, v)
    val opNames     = ir.g.collect { case o: OperationDeclFull => o.a }.toSet
    val entityNames = ir.c.collect { case e: EntityDeclFull => e.a }.toSet
    val perOp = rules.collect:
      case (t, f, v) if opNames.contains(t) => (t, f) -> v
    val perField = rules.collect:
      case (t, f, v) if entityNames.contains(t) => f -> v
    TestStrategyOverrides(perOp.toMap, perField.toMap)

final private[testgen] case class StringConstraint(
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

final private[testgen] case class IntConstraint(
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

  // Generator-construction backend. Hardcoded to Python today; the per-language
  // selector is threaded through the emitters in a later slice (see TestBackend).
  private val B: StrategyBackend = PythonHypothesisStrategy

  def forIR(ir: ServiceIRFull): List[StrategySpec] =
    val overrides     = strategyOverrides(ir)
    val aliasSpecs    = ir.e.collect { case a: TypeAliasDeclFull => specForAlias(a, ir, overrides) }
    val enumSpecs     = ir.d.collect { case e: EnumDeclFull => specForEnum(e, overrides) }
    val transEntities = transitionEntityNames(ir)
    val entitySpecs = ir.c
      .collect { case e: EntityDeclFull if transEntities.contains(e.a) => e }
      .map(e => specForEntity(e, ir))
    aliasSpecs ++ enumSpecs ++ entitySpecs

  def transitionEntityNames(ir: ServiceIRFull): Set[String] =
    ir.h.collect { case t: TransitionDeclFull => t.b }.toSet

  private def strategyOverrides(ir: ServiceIRFull): Map[String, StrategyImport] =
    ir.n.toList.flatMap { case ConventionsDeclFull(rs, _) => rs }.flatMap:
      case ConventionRuleFull(target, "strategy", _, StringLitF(v, _), _) =>
        v.split(':') match
          case Array(m, s) if m.nonEmpty && s.nonEmpty =>
            Some(target -> StrategyImport(m, s))
          case _ => None
      case _ => None
    .toMap

  def expressionFor(t: type_expr_full, ir: ServiceIRFull): StrategyExpr =
    expressionFor(t, ir, StrategyCtx.Anonymous, TestStrategyOverrides.from(ir))

  def expressionFor(
      t: type_expr_full,
      ir: ServiceIRFull,
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
          case Some("redacted") => StrategyExpr.Code(B.redactedPlaceholder)
          case _ if isSensitive => StrategyExpr.Code(B.redactWrap(t))
          case _                => StrategyExpr.Code(t)

  private[testgen] val RedactedPlaceholder: String = "***REDACTED***"

  private def bareExpression(t: type_expr_full, ir: ServiceIRFull): StrategyExpr = t match
    case NamedTypeF("String", _)   => StrategyExpr.Code(B.string)
    case NamedTypeF("Int", _)      => StrategyExpr.Code(B.int)
    case NamedTypeF("Float", _)    => StrategyExpr.Code(B.float)
    case NamedTypeF("Bool", _)     => StrategyExpr.Code(B.bool)
    case NamedTypeF("DateTime", _) => StrategyExpr.Code(B.datetime)
    case NamedTypeF("Duration", _) => StrategyExpr.Code(B.duration)
    case NamedTypeF("Id", _)       => StrategyExpr.Code(B.id)
    case NamedTypeF(name, _) =>
      if ir.e.exists { case _a: TypeAliasDeclFull => _a.a == name } || ir.d.exists {
          case _e: EnumDeclFull => _e.a == name
        }
      then
        StrategyExpr.Code(B.call(strategyFunctionName(name)))
      else StrategyExpr.Skip(s"unknown named type '$name'")
    case OptionTypeF(inner, _) =>
      bareExpression(inner, ir) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(B.option(t))
        case s @ StrategyExpr.Skip(_) => s
    case SetTypeF(inner, _) =>
      bareExpression(inner, ir) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(B.set(t))
        case s @ StrategyExpr.Skip(_) => s
    case SeqTypeF(inner, _) =>
      bareExpression(inner, ir) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(B.seq(t))
        case s @ StrategyExpr.Skip(_) => s
    case MapTypeF(_, _, _)         => StrategyExpr.Skip("MapType strategy")
    case RelationTypeF(_, _, _, _) => StrategyExpr.Skip("RelationType strategy")

  private[testgen] def strategyFunctionName(typeName: String): String =
    B.functionName(typeName)

  private def specForAlias(
      alias: TypeAliasDeclFull,
      ir: ServiceIRFull,
      overrides: Map[String, StrategyImport]
  ): StrategySpec =
    overrides.get(alias.a) match
      case Some(imp) =>
        StrategySpec(
          typeName = alias.a,
          functionName = strategyFunctionName(alias.a),
          body = s"${imp.symbol}()",
          skipped = Nil,
          imports = List(imp)
        )
      case None =>
        val (body, skipped) = renderAlias(alias, ir)
        StrategySpec(
          typeName = alias.a,
          functionName = strategyFunctionName(alias.a),
          body = body,
          skipped = skipped
        )

  private def specForEntity(entity: EntityDeclFull, ir: ServiceIRFull): StrategySpec =
    val overrides = TestStrategyOverrides.from(ir)
    val pairs = entity.c.collect { case f: FieldDeclFull =>
      val ctx     = StrategyCtx.EntityField(entity.a, f.a)
      val rawExpr = jsonStrategyForField(f, ir)
      val expr    = applyRedaction(rawExpr, ctx, overrides)
      (f.a, expr)
    }
    val skipped = pairs.collect:
      case (n, StrategyExpr.Skip(r)) => s"entity '${entity.a}' field '$n': $r"
    val codeEntries = pairs.collect:
      case (n, StrategyExpr.Code(t)) => (n, t)
    val body = B.fixedDict(codeEntries)
    StrategySpec(
      typeName = entity.a,
      functionName = strategyFunctionName(entity.a),
      body = body,
      skipped = skipped
    )

  private def jsonStrategyForField(f: FieldDeclFull, ir: ServiceIRFull): StrategyExpr =
    jsonStrategyForType(f.b, f.c, ir)

  private def jsonStrategyForType(
      t: type_expr_full,
      constraint: Option[expr_full],
      ir: ServiceIRFull
  ): StrategyExpr = t match
    case NamedTypeF("String", _) =>
      val (cs, _) = collectStringConstraint(constraint, ir)
      StrategyExpr.Code(B.constrainedString(cs))
    case NamedTypeF("Int", _) =>
      val (cs, _) = collectIntConstraint(constraint)
      StrategyExpr.Code(B.constrainedInt(cs))
    case NamedTypeF("Float", _)    => StrategyExpr.Code(B.float)
    case NamedTypeF("Bool", _)     => StrategyExpr.Code(B.bool)
    case NamedTypeF("DateTime", _) => StrategyExpr.Code(B.jsonDatetime)
    case NamedTypeF("Duration", _) => StrategyExpr.Code(B.jsonDuration)
    case NamedTypeF("Id", _)       => StrategyExpr.Code(B.id)
    case NamedTypeF(name, _) =>
      if ir.d.exists { case _e: EnumDeclFull => _e.a == name } then
        StrategyExpr.Code(B.call(strategyFunctionName(name)))
      else
        ir.e.collectFirst { case _a: TypeAliasDeclFull if _a.a == name => _a } match
          case Some(alias) =>
            val combined = (constraint, alias.c) match
              case (Some(c), Some(a)) => Some(BinaryOpF(BAnd(), c, a, None))
              case (c, a)             => c.orElse(a)
            jsonStrategyForType(alias.b, combined, ir)
          case None =>
            if ir.c.exists { case _e: EntityDeclFull => _e.a == name } then
              StrategyExpr.Skip(s"nested entity reference '$name' not seedable")
            else StrategyExpr.Skip(s"unknown named type '$name'")
    case OptionTypeF(inner, _) =>
      jsonStrategyForType(inner, None, ir) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(B.option(t))
        case StrategyExpr.Skip(_) => StrategyExpr.Code(B.noneValue)
    case SetTypeF(inner, _) =>
      jsonStrategyForType(inner, None, ir) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(B.jsonSetUnique(t))
        case s                    => s
    case SeqTypeF(inner, _) =>
      jsonStrategyForType(inner, None, ir) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(B.seq(t))
        case s                    => s
    case MapTypeF(_, _, _)         => StrategyExpr.Skip("MapType field not seedable")
    case RelationTypeF(_, _, _, _) => StrategyExpr.Skip("RelationType field not seedable")

  private def specForEnum(
      decl: EnumDeclFull,
      overrides: Map[String, StrategyImport]
  ): StrategySpec =
    overrides.get(decl.a) match
      case Some(imp) =>
        StrategySpec(
          typeName = decl.a,
          functionName = strategyFunctionName(decl.a),
          body = s"${imp.symbol}()",
          skipped = Nil,
          imports = List(imp)
        )
      case None =>
        StrategySpec(
          typeName = decl.a,
          functionName = strategyFunctionName(decl.a),
          body = B.enumSampled(decl.b),
          skipped = Nil
        )

  private def renderAlias(
      alias: TypeAliasDeclFull,
      ir: ServiceIRFull
  ): (String, List[String]) =
    alias.b match
      case NamedTypeF("String", _) =>
        val (cs, skipped) = collectStringConstraint(alias.c, ir)
        (B.constrainedString(cs), skipped)
      case NamedTypeF("Int", _) =>
        val (cs, skipped) = collectIntConstraint(alias.c)
        (B.constrainedInt(cs), skipped)
      case other =>
        expressionFor(other, ir) match
          case StrategyExpr.Code(t) => (t, Nil)
          case StrategyExpr.Skip(r) => (B.nothing, List(r))

  private def collectStringConstraint(
      c: Option[expr_full],
      ir: ServiceIRFull
  ): (StringConstraint, List[String]) =
    c match
      case None    => (StringConstraint(), Nil)
      case Some(e) => walkStringConstraint(e, ir)

  private def walkStringConstraint(
      e: expr_full,
      ir: ServiceIRFull
  ): (StringConstraint, List[String]) = e match
    case BinaryOpF(BAnd(), l, r, _) =>
      val (lc, lsk) = walkStringConstraint(l, ir)
      val (rc, rsk) = walkStringConstraint(r, ir)
      (lc.merge(rc), lsk ++ rsk)

    case LenCmp(op, n) =>
      op match
        case BGe() => (StringConstraint(minSize = Some(n.toInt)), Nil)
        case BGt() => (StringConstraint(minSize = Some((n + 1).toInt)), Nil)
        case BLe() => (StringConstraint(maxSize = Some(n.toInt)), Nil)
        case BLt() => (StringConstraint(maxSize = Some((n - 1).toInt)), Nil)
        case BEq() => (StringConstraint(minSize = Some(n.toInt), maxSize = Some(n.toInt)), Nil)
        case _     => (StringConstraint(), List(s"unsupported len comparison $op"))

    case MatchesF(IdentifierF("value", _), pattern, _) =>
      (StringConstraint(regexes = List(pattern)), Nil)

    case CallF(IdentifierF(name, _), List(IdentifierF("value", _)), _) =>
      inlineMatchesPredicate(name, ir) match
        case Some(pattern) =>
          (StringConstraint(regexes = List(pattern)), Nil)
        case None =>
          ir.m.collectFirst { case _p: PredicateDeclFull if _p.a == name => _p } match
            case None =>
              (StringConstraint(), List(s"unknown predicate '$name' in string constraint"))
            case Some(pr) if pr.b.size != 1 =>
              (
                StringConstraint(),
                List(
                  s"predicate '$name' has arity ${pr.b.size}; string-constraint filters require arity 1"
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

  private def inlineMatchesPredicate(name: String, ir: ServiceIRFull): Option[String] =
    ir.m
      .collectFirst { case _p: PredicateDeclFull if _p.a == name => _p }
      .filter(_.b.size == 1)
      .flatMap: pr =>
        val paramName = pr.b.head match { case ParamDeclFull(n, _, _) => n }
        pr.c match
          case MatchesF(IdentifierF(p, _), pattern, _) if p == paramName =>
            Some(pattern)
          case _ => None

  private def collectIntConstraint(c: Option[expr_full]): (IntConstraint, List[String]) =
    c match
      case None    => (IntConstraint(), Nil)
      case Some(e) => walkIntConstraint(e)

  private def walkIntConstraint(e: expr_full): (IntConstraint, List[String]) = e match
    case BinaryOpF(BAnd(), l, r, _) =>
      val (lc, lsk) = walkIntConstraint(l)
      val (rc, rsk) = walkIntConstraint(r)
      (lc.merge(rc), lsk ++ rsk)

    case BinaryOpF(op, IdentifierF("value", _), IntLitF(int_of_integer(n), _), _) =>
      op match
        case _: BGe => (IntConstraint(minValue = Some(n.toLong)), Nil)
        case _: BGt => (IntConstraint(minValue = Some((n + 1).toLong)), Nil)
        case _: BLe => (IntConstraint(maxValue = Some(n.toLong)), Nil)
        case _: BLt => (IntConstraint(maxValue = Some((n - 1).toLong)), Nil)
        case _: BEq => (IntConstraint(minValue = Some(n.toLong), maxValue = Some(n.toLong)), Nil)
        case _      => (IntConstraint(), List(s"unsupported int comparison $op"))

    case other =>
      (IntConstraint(), List(s"unhandled int constraint: ${shortShape(other)}"))

  private object LenCmp:
    def unapply(e: expr_full): Option[(bin_op_full, Long)] = e match
      case BinaryOpF(
            op,
            CallF(IdentifierF("len", _), List(IdentifierF("value", _)), _),
            IntLitF(int_of_integer(n), _),
            _
          ) =>
        Some((op, n.toLong))
      case _ => None

  private def shortShape(e: expr_full): String = e match
    case BinaryOpF(op, _, _, _) => s"BinaryOp($op)"
    case UnaryOpF(op, _, _)     => s"UnaryOp($op)"
    case CallF(_, _, _)         => "Call"
    case MatchesF(_, _, _)      => "Matches"
    case IdentifierF(n, _)      => s"Identifier($n)"
    case _                      => e.getClass.getSimpleName
