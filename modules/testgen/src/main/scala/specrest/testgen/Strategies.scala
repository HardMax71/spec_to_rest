package specrest.testgen

import specrest.codegen.SensitiveFields
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.ir.idx

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
    val rules = irConventionRules(ir).collect:
      case ConventionRuleFull(target, "test_strategy", Some(field), CvOk(PvBool(live)), _) =>
        (target, field, if live then "live" else "redacted")
    val opNames     = svcOperations(ir).map(operName).toSet
    val entityNames = ir.idx.entityNames
    val perOp = rules.collect:
      case (t, f, v) if opNames.contains(t) => (t, f) -> v
    val perField = rules.collect:
      case (t, f, v) if entityNames.contains(t) => f -> v
    TestStrategyOverrides(perOp.toMap, perField.toMap)

object Strategies:

  // Generator-construction backend, selected per target. Defaults to Python so
  // every existing call site (TestEmit/Behavioral/Stateful) stays byte-identical;
  // TsBehavioral/TsStateful pass TsFastCheckStrategy.
  def forIR(
      ir: ServiceIRFull,
      b: StrategyBackend = PythonHypothesisStrategy
  ): List[StrategySpec] =
    val overrides     = strategyOverrides(ir)
    val ix            = ir.idx
    val aliasSpecs    = ix.aliases.map(a => specForAlias(a, ir, overrides, b))
    val enumSpecs     = ix.enums.map(e => specForEnum(e, overrides, b))
    val transEntities = transitionEntityNames(ir)
    val entitySpecs =
      ix.entities.filter(e => transEntities.contains(entName(e))).map(e => specForEntity(e, ir, b))
    aliasSpecs ++ enumSpecs ++ entitySpecs

  def transitionEntityNames(ir: ServiceIRFull): Set[String] =
    svcTransitions(ir).map(trnEntity).toSet

  private def strategyOverrides(ir: ServiceIRFull): Map[String, StrategyImport] =
    irConventionRules(ir).flatMap:
      case ConventionRuleFull(target, "strategy", _, CvOk(PvStrPair(m, s)), _) =>
        Some(target -> StrategyImport(m, s))
      case _ => None
    .toMap

  def expressionFor(t: type_expr, ir: ServiceIRFull): StrategyExpr =
    expressionFor(
      t,
      ir,
      StrategyCtx.Anonymous,
      TestStrategyOverrides.from(ir),
      PythonHypothesisStrategy
    )

  def expressionFor(
      t: type_expr,
      ir: ServiceIRFull,
      ctx: StrategyCtx,
      overrides: TestStrategyOverrides,
      b: StrategyBackend = PythonHypothesisStrategy
  ): StrategyExpr =
    val raw = bareExpression(t, ir, b)
    applyRedaction(raw, ctx, overrides, b)

  // A plain-String operation input still carries requires-level bounds
  // (`len(password) >= 8`); generating outside them makes a stateful rule
  // fire calls the guard rightly rejects. The atoms whose only free variable
  // is the input fold into the same constrained-string build the aliases use.
  // Only atoms the walker fully recognizes fold into generation; the rest
  // neither narrow the strategy nor count as satisfied by construction (the
  // stateful strictness check asks the same question through this method).
  def inputAtomEncodable(atom: expr, paramName: String, ir: ServiceIRFull): Boolean =
    free_vars(atom).distinct == List(paramName) && {
      val substd     = subst(paramName, IdentifierF("value", None), atom)
      val (_, skips) = collectStringConstraint(Some(substd), ir)
      skips.isEmpty
    }

  def expressionForInput(
      t: type_expr,
      ir: ServiceIRFull,
      ctx: StrategyCtx,
      overrides: TestStrategyOverrides,
      paramName: String,
      requiresAtoms: List[expr],
      b: StrategyBackend = PythonHypothesisStrategy
  ): StrategyExpr =
    val encoded = requiresAtoms
      .filter(a => inputAtomEncodable(a, paramName, ir))
      .map(a => subst(paramName, IdentifierF("value", None), a))
    val raw = (t, encoded) match
      case (NamedTypeF("String", _), atoms @ (_ :: _)) =>
        val merged  = atoms.reduceLeft((l, r) => BinaryOpF(BAnd(), l, r, None))
        val (cs, _) = collectStringConstraint(Some(merged), ir)
        StrategyExpr.Code(b.constrainedString(cs))
      case _ => bareExpression(t, ir, b)
    applyRedaction(raw, ctx, overrides, b)

  private def applyRedaction(
      raw: StrategyExpr,
      ctx: StrategyCtx,
      overrides: TestStrategyOverrides,
      b: StrategyBackend
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
          case Some("redacted") => StrategyExpr.Code(b.redactedPlaceholder)
          case _ if isSensitive => StrategyExpr.Code(b.redactWrap(t))
          case _                => StrategyExpr.Code(t)

  private[testgen] val RedactedPlaceholder: String = "***REDACTED***"

  // Anchors a user pattern for whole-string matching in engines whose match
  // primitive is search-like (JS RegExp.test, Go regexp). Python needs none of
  // this: it matches via re.fullmatch.
  private[testgen] def fullMatchPattern(pattern: String): String = s"^(?:$pattern)$$"

  private def bareExpression(
      t: type_expr,
      ir: ServiceIRFull,
      b: StrategyBackend
  ): StrategyExpr = t match
    case NamedTypeF("String", _)   => StrategyExpr.Code(b.string)
    case NamedTypeF("Int", _)      => StrategyExpr.Code(b.int)
    case NamedTypeF("Float", _)    => StrategyExpr.Code(b.float)
    case NamedTypeF("Bool", _)     => StrategyExpr.Code(b.bool)
    case NamedTypeF("DateTime", _) => StrategyExpr.Code(b.datetime)
    case NamedTypeF("Duration", _) => StrategyExpr.Code(b.duration)
    case NamedTypeF("Id", _)       => StrategyExpr.Code(b.id)
    case NamedTypeF(name, _) =>
      if svcTypeAliases(ir).exists(a => talName(a) == name) ||
        svcEnums(ir).exists(e => enmName(e) == name)
      then
        StrategyExpr.Code(b.call(strategyFunctionName(name, b)))
      else StrategyExpr.Skip(s"unknown named type '$name'")
    case OptionTypeF(inner, _) =>
      bareExpression(inner, ir, b) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(b.option(t))
        case s @ StrategyExpr.Skip(_) => s
    case SetTypeF(inner, _) =>
      bareExpression(inner, ir, b) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(b.set(t))
        case s @ StrategyExpr.Skip(_) => s
    case SeqTypeF(inner, _) =>
      bareExpression(inner, ir, b) match
        case StrategyExpr.Code(t)     => StrategyExpr.Code(b.seq(t))
        case s @ StrategyExpr.Skip(_) => s
    case MapTypeF(_, _, _)         => StrategyExpr.Skip("MapType strategy")
    case RelationTypeF(_, _, _, _) => StrategyExpr.Skip("RelationType strategy")

  private[testgen] def strategyFunctionName(
      typeName: String,
      b: StrategyBackend = PythonHypothesisStrategy
  ): String =
    b.functionName(typeName)

  private def specForAlias(
      alias: type_alias_decl,
      ir: ServiceIRFull,
      overrides: Map[String, StrategyImport],
      b: StrategyBackend
  ): StrategySpec =
    overrides.get(talName(alias)) match
      case Some(imp) =>
        StrategySpec(
          typeName = talName(alias),
          functionName = strategyFunctionName(talName(alias), b),
          body = s"${imp.symbol}()",
          skipped = Nil,
          imports = List(imp)
        )
      case None =>
        val (body, skipped) = renderAlias(alias, ir, b)
        StrategySpec(
          typeName = talName(alias),
          functionName = strategyFunctionName(talName(alias), b),
          body = body,
          skipped = skipped
        )

  private def specForEntity(
      entity: entity_decl,
      ir: ServiceIRFull,
      b: StrategyBackend
  ): StrategySpec =
    val overrides = TestStrategyOverrides.from(ir)
    val pairs = entFields(entity).map { f =>
      val ctx     = StrategyCtx.EntityField(entName(entity), fldName(f))
      val rawExpr = jsonStrategyForField(f, ir, b)
      val expr    = applyRedaction(rawExpr, ctx, overrides, b)
      (fldName(f), expr)
    }
    val skipped = pairs.collect:
      case (n, StrategyExpr.Skip(r)) => s"entity '${entName(entity)}' field '$n': $r"
    val codeEntries = pairs.collect:
      case (n, StrategyExpr.Code(t)) => (n, t)
    val body = b.fixedDict(codeEntries)
    StrategySpec(
      typeName = entName(entity),
      functionName = strategyFunctionName(entName(entity), b),
      body = body,
      skipped = skipped
    )

  private def jsonStrategyForField(
      f: field_decl,
      ir: ServiceIRFull,
      b: StrategyBackend
  ): StrategyExpr =
    jsonStrategyForType(fldType(f), fldDefault(f), ir, b)

  private def jsonStrategyForType(
      t: type_expr,
      constraint: Option[expr],
      ir: ServiceIRFull,
      b: StrategyBackend
  ): StrategyExpr = t match
    case NamedTypeF("String", _) =>
      val (cs, _) = collectStringConstraint(constraint, ir)
      StrategyExpr.Code(b.constrainedString(cs))
    case NamedTypeF("Int", _) =>
      val (cs, _) = collectIntConstraint(constraint)
      StrategyExpr.Code(b.constrainedInt(cs))
    case NamedTypeF("Float", _)    => StrategyExpr.Code(b.float)
    case NamedTypeF("Bool", _)     => StrategyExpr.Code(b.bool)
    case NamedTypeF("DateTime", _) => StrategyExpr.Code(b.jsonDatetime)
    case NamedTypeF("Duration", _) => StrategyExpr.Code(b.jsonDuration)
    case NamedTypeF("Id", _)       => StrategyExpr.Code(b.id)
    case NamedTypeF(name, _) =>
      if svcEnums(ir).exists(e => enmName(e) == name) then
        StrategyExpr.Code(b.call(strategyFunctionName(name, b)))
      else
        svcTypeAliases(ir).find(a => talName(a) == name) match
          case Some(alias) =>
            val combined = (constraint, talConstraint(alias)) match
              case (Some(c), Some(a)) => Some(BinaryOpF(BAnd(), c, a, None))
              case (c, a)             => c.orElse(a)
            jsonStrategyForType(talType(alias), combined, ir, b)
          case None =>
            if svcEntities(ir).exists(e => entName(e) == name) then
              StrategyExpr.Skip(s"nested entity reference '$name' not seedable")
            else StrategyExpr.Skip(s"unknown named type '$name'")
    case OptionTypeF(inner, _) =>
      jsonStrategyForType(inner, None, ir, b) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(b.option(t))
        case StrategyExpr.Skip(_) => StrategyExpr.Code(b.noneValue)
    case SetTypeF(inner, _) =>
      jsonStrategyForType(inner, None, ir, b) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(b.jsonSetUnique(t))
        case s                    => s
    case SeqTypeF(inner, _) =>
      jsonStrategyForType(inner, None, ir, b) match
        case StrategyExpr.Code(t) => StrategyExpr.Code(b.seq(t))
        case s                    => s
    case MapTypeF(_, _, _)         => StrategyExpr.Skip("MapType field not seedable")
    case RelationTypeF(_, _, _, _) => StrategyExpr.Skip("RelationType field not seedable")

  private def specForEnum(
      decl: enum_decl,
      overrides: Map[String, StrategyImport],
      b: StrategyBackend
  ): StrategySpec =
    overrides.get(enmName(decl)) match
      case Some(imp) =>
        StrategySpec(
          typeName = enmName(decl),
          functionName = strategyFunctionName(enmName(decl), b),
          body = s"${imp.symbol}()",
          skipped = Nil,
          imports = List(imp)
        )
      case None =>
        StrategySpec(
          typeName = enmName(decl),
          functionName = strategyFunctionName(enmName(decl), b),
          body = b.enumSampled(enmVariants(decl)),
          skipped = Nil
        )

  private def renderAlias(
      alias: type_alias_decl,
      ir: ServiceIRFull,
      b: StrategyBackend
  ): (String, List[String]) =
    talType(alias) match
      case NamedTypeF("String", _) =>
        val (cs, skipped) = collectStringConstraint(talConstraint(alias), ir)
        (b.constrainedString(cs), skipped)
      case NamedTypeF("Int", _) =>
        val (cs, skipped) = collectIntConstraint(talConstraint(alias))
        (b.constrainedInt(cs), skipped)
      case other =>
        expressionFor(other, ir, StrategyCtx.Anonymous, TestStrategyOverrides.from(ir), b) match
          case StrategyExpr.Code(t) => (t, Nil)
          case StrategyExpr.Skip(r) => (b.nothing, List(r))

  private def collectIntConstraint(c: Option[expr]): (int_constraint, List[String]) =
    c match
      case None    => (emptyIntConstraint, Nil)
      case Some(e) => walkIntConstraint(e)

  // The extracted walkStringConstraint emits RaPredCall(name) as a skip-reason
  // entry (the lifted layer is IR-agnostic). Resolve those predicate-name skips
  // here against the service IR: inline arity-1 matches predicates as regexes,
  // surface other arity-1 predicates as filter helpers, and drop them from skips.
  private def collectStringConstraint(
      c: Option[expr],
      ir: ServiceIRFull
  ): (string_constraint, List[String]) =
    c match
      case None => (emptyStringConstraint, Nil)
      case Some(e) =>
        val (raw, skips) = walkStringConstraint(e)
        resolvePredicateSkips(raw, skips, ir)

  private def resolvePredicateSkips(
      raw: string_constraint,
      skips: List[String],
      ir: ServiceIRFull
  ): (string_constraint, List[String]) =
    val predicateNames          = svcPredicates(ir).map(prdName).toSet
    val (predSkips, otherSkips) = skips.partition(predicateNames.contains)
    predSkips.foldLeft((raw, otherSkips)):
      case ((accConstraint, accSkips), name) =>
        val (extra, newSkips) = resolveOnePredicate(name, ir)
        (mergeStringConstraint(accConstraint, extra), accSkips ++ newSkips)

  private def resolveOnePredicate(
      name: String,
      ir: ServiceIRFull
  ): (string_constraint, List[String]) =
    inlineMatchesPredicate(name, ir) match
      case Some(pattern) =>
        (StringConstraint(None, None, List(pattern), Nil, Nil), Nil)
      case None =>
        svcPredicates(ir).find(p => prdName(p) == name) match
          case None =>
            (emptyStringConstraint, List(s"unknown predicate '$name' in string constraint"))
          case Some(pr) if prdParams(pr).size != 1 =>
            (
              emptyStringConstraint,
              List(
                s"predicate '$name' has arity ${prdParams(pr).size}; string-constraint filters require arity 1"
              )
            )
          case Some(_) =>
            val snake = Naming.toSnakeCase(name)
            if PythonReservedNames.contains(snake) then
              (
                emptyStringConstraint,
                List(
                  s"predicate '$name' (snake-cased to '$snake') is a Python-reserved name; cannot emit strategy filter"
                )
              )
            else (StringConstraint(None, None, Nil, List(snake), Nil), Nil)

  private def inlineMatchesPredicate(name: String, ir: ServiceIRFull): Option[String] =
    svcPredicates(ir)
      .find(p => prdName(p) == name)
      .filter(pr => prdParams(pr).size == 1)
      .flatMap: pr =>
        val paramName = prmName(prdParams(pr).head)
        matchesIdentityShape(prdBody(pr), paramName)
