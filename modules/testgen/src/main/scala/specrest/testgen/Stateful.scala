package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

import specrest.convention.EndpointSpec
import specrest.convention.Naming
import specrest.convention.OperationKind
import specrest.ir.PrettyPrint
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

final case class StatefulOutput(
    file: String,
    skips: List[TestSkip]
)

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Return",
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.IsInstanceOf"
  )
)
object Stateful:

  final private case class BundleSpec(
      entityName: String,
      statusValue: Option[String],
      bundleName: String,
      pyVarName: String,
      pkFieldName: String,
      pkTypeExpr: type_expr_full
  )

  final private case class EntityBundles(
      entityName: String,
      pkFieldName: String,
      pkTypeExpr: type_expr_full,
      transition: Option[TransitionDeclFull],
      enumValues: List[String],
      bundles: List[BundleSpec],
      initialStatusByCreateOp: Map[String, String]
  )

  private enum InputBinding:
    case BundleDraw(bundle: BundleSpec, strictByConstruction: Boolean)
    case BundleConsume(bundle: BundleSpec, strictByConstruction: Boolean)
    case BundleUnion(bundles: List[BundleSpec], strictByConstruction: Boolean)
    case Generated(strategy: String)
    case Skip(reason: String)

  private enum RuleRole derives CanEqual:
    case CreateTarget(bundle: BundleSpec, pkProjection: String)
    case Plain

  private val TQ = "\"\"\""

  def emitFor(profiled: ProfiledService): StatefulOutput =
    val ir            = profiled.ir
    val entityBundles = inferEntityBundles(profiled)
    val bundles       = entityBundles.flatMap(_.bundles)
    val machName      = s"${ir.a}StateMachine"
    val testName      = s"TestStateful${ir.a}"

    val opsConcrete = ir.g.collect { case op: OperationDeclFull => op }
    val rulesAndSkips = profiled.operations.flatMap: pop =>
      opsConcrete.find(_.a == pop.operationName) match
        case Some(opDecl) => emitRules(pop, opDecl, ir, entityBundles)
        case None         => Nil
    val ruleBlocks = rulesAndSkips.flatMap(_._1.toOption.toList.flatten)
    val ruleSkips  = rulesAndSkips.flatMap(_._2)

    val invariantsAndSkips =
      ir.i.collect { case inv: InvariantDeclFull => inv }.zipWithIndex.map: (inv, idx) =>
        emitInvariant(inv, idx, ir)
    val invariantBlocks = invariantsAndSkips.flatMap(_._1.toList)
    val invariantSkips  = invariantsAndSkips.flatMap(_._2.toList)

    val py = renderFile(
      ir = ir,
      machineName = machName,
      testName = testName,
      bundles = bundles,
      ruleBlocks = ruleBlocks,
      invariantBlocks = invariantBlocks
    )

    StatefulOutput(file = py, skips = ruleSkips ++ invariantSkips)

  private def inferEntityBundles(profiled: ProfiledService): List[EntityBundles] =
    val ir = profiled.ir
    val createOps = profiled.operations.filter: pop =>
      pop.kind == OperationKind.Create || pop.kind == OperationKind.CreateChild
    val byEntity         = createOps.flatMap(pop => pop.targetEntity.map(_ -> pop)).groupBy(_._1)
    val entitiesConcrete = ir.c.collect { case e: EntityDeclFull => e }
    byEntity.keys.toList.sorted.flatMap: entityName =>
      val createOpsForEntity = byEntity(entityName).map(_._2)
      val createOpNames      = createOpsForEntity.map(_.operationName).toSet
      entitiesConcrete.find(_.a == entityName).flatMap: entity =>
        primaryKey(entity).map: pk =>
          val perStatus = perStatusBundlesFor(entity, ir, createOpNames)
          perStatus match
            case Some((td, enumValues, initialByOp)) =>
              val perStatusBundles = enumValues.map: status =>
                BundleSpec(
                  entityName = entity.a,
                  statusValue = Some(status),
                  bundleName = s"${Naming.toSnakeCase(entity.a)}_${status.toLowerCase}_ids",
                  pyVarName = s"${Naming.toSnakeCase(entity.a)}_${status.toLowerCase}_ids",
                  pkFieldName = pk.a,
                  pkTypeExpr = pk.b
                )
              EntityBundles(
                entityName = entity.a,
                pkFieldName = pk.a,
                pkTypeExpr = pk.b,
                transition = Some(td),
                enumValues = enumValues,
                bundles = perStatusBundles,
                initialStatusByCreateOp = initialByOp
              )
            case None =>
              val legacy = BundleSpec(
                entityName = entity.a,
                statusValue = None,
                bundleName = s"${Naming.toSnakeCase(entity.a)}_ids",
                pyVarName = s"${Naming.toSnakeCase(entity.a)}_ids",
                pkFieldName = pk.a,
                pkTypeExpr = pk.b
              )
              EntityBundles(
                entityName = entity.a,
                pkFieldName = pk.a,
                pkTypeExpr = pk.b,
                transition = None,
                enumValues = Nil,
                bundles = List(legacy),
                initialStatusByCreateOp = Map.empty
              )

  private def perStatusBundlesFor(
      entity: EntityDeclFull,
      ir: ServiceIRFull,
      createOpNames: Set[String]
  ): Option[(TransitionDeclFull, List[String], Map[String, String])] =
    val transitionsConcrete = ir.h.collect { case t: TransitionDeclFull => t }
    val td = transitionsConcrete.find(_.b == entity.a) match
      case Some(t) => t
      case None    => return None
    val fieldsConcrete = entity.c.collect { case f: FieldDeclFull => f }
    val field = fieldsConcrete.find(_.a == td.c) match
      case Some(f) => f
      case None    => return None
    val enumValues = enumValuesForField(field, ir) match
      case Some(vs) if vs.nonEmpty => vs
      case _                       => return None

    val opsConcrete = ir.g.collect { case op: OperationDeclFull => op }
    val createDecls = opsConcrete.filter(op => createOpNames.contains(op.a))
    if createDecls.isEmpty then return None
    val initialByOp = createDecls.flatMap: op =>
      val outParams = op.c.collect { case p: ParamDeclFull => p }
      outParams.find(p => isEntityType(p.b, entity.a)).flatMap: p =>
        op.e.iterator
          .collectFirst:
            case BinaryOpF(
                  BEq(),
                  FieldAccessF(IdentifierF(b, _), f, _),
                  rhs,
                  _
                )
                if b == p.a && f == td.c =>
              enumLiteralName(rhs, enumValues)
          .flatten
          .map(op.a -> _)
    if initialByOp.size != createDecls.size then None
    else Some((td, enumValues, initialByOp.toMap))

  private def enumLiteralName(rhs: expr_full, enumValues: List[String]): Option[String] =
    rhs match
      case EnumAccessF(_, member, _) if enumValues.contains(member) => Some(member)
      case IdentifierF(name, _) if enumValues.contains(name)        => Some(name)
      case _                                                        => None

  private def primaryKey(entity: EntityDeclFull): Option[FieldDeclFull] =
    val fields = entity.c.collect { case f: FieldDeclFull => f }
    fields.find(_.a == "id").orElse(fields.headOption)

  private def emitRules(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      ir: ServiceIRFull,
      entityBundles: List[EntityBundles]
  ): List[(Either[Unit, List[String]], List[TestSkip])] =
    if pop.kind == OperationKind.Transition then
      pop.targetEntity.flatMap(en => entityBundles.find(_.entityName == en)) match
        case Some(eb) if eb.bundles.exists(_.statusValue.isDefined) =>
          emitTransitionRules(pop, opDecl, ir, eb, entityBundles)
        case _ =>
          List(emitRule(pop, opDecl, ir, entityBundles))
    else
      List(emitRule(pop, opDecl, ir, entityBundles))

  private def emitTransitionRules(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      ir: ServiceIRFull,
      eb: EntityBundles,
      entityBundles: List[EntityBundles]
  ): List[(Either[Unit, List[String]], List[TestSkip])] =
    val td            = eb.transition.get
    val rulesConcrete = td.d.collect { case r: TransitionRuleFull => r }
    val matchingRules = rulesConcrete.filter(_.c == pop.operationName)
    if matchingRules.isEmpty then List(emitRule(pop, opDecl, ir, entityBundles))
    else
      val pathParamNames = pop.endpoint.pathParams.map(_.name)
      if pathParamNames.size != 1 || pop.endpoint.bodyParams.nonEmpty || pop.endpoint.queryParams.nonEmpty
      then List(emitRule(pop, opDecl, ir, entityBundles))
      else
        val pathParam = pathParamNames.head
        matchingRules.toList.map: tr =>
          buildTransitionMoveRule(pop, opDecl, eb, tr, pathParam)

  private def buildTransitionMoveRule(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      eb: EntityBundles,
      tr: TransitionRuleFull,
      pathParam: String
  ): (Either[Unit, List[String]], List[TestSkip]) =
    val fromBundle = eb.bundles.find(_.statusValue.contains(tr.a))
    val toBundle   = eb.bundles.find(_.statusValue.contains(tr.b))
    (fromBundle, toBundle) match
      case (Some(fb), Some(tb)) =>
        val funcName =
          s"${Naming.toSnakeCase(opDecl.a)}_from_${tr.a.toLowerCase}_to_${tr.b.toLowerCase}"
        val body = buildTransitionMoveBlock(
          pop = pop,
          opDecl = opDecl,
          from = tr.a,
          to = tr.b,
          fromBundle = fb,
          toBundle = tb,
          pathParam = pathParam,
          guarded = tr.d.isDefined,
          funcName = funcName
        )
        (Right(List(body)), Nil)
      case _ =>
        val skip = TestSkip(
          opDecl.a,
          s"stateful_transition[${tr.a}_to_${tr.b}]",
          s"unknown enum value for transition '${tr.a} -> ${tr.b}'"
        )
        (Right(Nil), List(skip))

  private def buildTransitionMoveBlock(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      from: String,
      to: String,
      fromBundle: BundleSpec,
      toBundle: BundleSpec,
      pathParam: String,
      guarded: Boolean,
      funcName: String
  ): String =
    val ruleArgs  = s"a = ${toBundle.pyVarName}, $pathParam=consumes(${fromBundle.pyVarName})"
    val sigParams = s"self, $pathParam"
    val sb        = new StringBuilder
    sb.append(s"    @rule($ruleArgs)\n")
    sb.append(s"    def $funcName($sigParams):\n")
    sb.append(
      s"        $TQ${escapeDocstring(operationSummary(opDecl))} (transition $from -> $to)$TQ\n"
    )
    sb.append(s"        response = ${requestCallExpr(pop)}\n")
    val successCode = pop.endpoint.successStatus
    if guarded then
      sb.append(s"        if response.status_code == $successCode:\n")
      sb.append(s"            return $pathParam\n")
      sb.append("        elif 400 <= response.status_code < 500:\n")
      sb.append("            return multiple()\n")
      sb.append(
        "        else:\n            assert False, f\"unexpected status {response.status_code}: {response.text}\"\n"
      )
    else
      sb.append(s"        assert response.status_code == $successCode, response.text\n")
      sb.append(s"        return $pathParam\n")
    sb.toString

  private def emitRule(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      ir: ServiceIRFull,
      entityBundles: List[EntityBundles]
  ): (Either[Unit, List[String]], List[TestSkip]) =
    val (role, roleSkips) = inferCreateRole(pop, opDecl, entityBundles)
    val pathParamNames    = pop.endpoint.pathParams.map(_.name).toSet
    val bodyParamNames    = pop.endpoint.bodyParams.map(_.name).toSet
    val queryParamNames   = pop.endpoint.queryParams.map(_.name).toSet
    val allParams         = pathParamNames ++ bodyParamNames ++ queryParamNames

    val statusRestriction = recognizeStatusRestriction(opDecl, ir)

    val bindings = opDecl.b.collect:
      case ParamDeclFull(n, t, _) if allParams.contains(n) =>
        n -> bindForInput(
          n,
          t,
          pop,
          ir,
          entityBundles,
          statusRestriction
        )
    val skipped = bindings.collect:
      case (n, InputBinding.Skip(r)) =>
        TestSkip(opDecl.a, "stateful_rule", s"input '$n': $r")
    if skipped.nonEmpty then (Right(Nil), skipped ++ roleSkips)
    else
      val stateFields = stateFieldNames(ir)
      val ruleBody = buildRuleBlock(
        pop = pop,
        opDecl = opDecl,
        bindings = bindings,
        role = role,
        stateFields = stateFields
      )
      (Right(List(ruleBody)), roleSkips)

  private def stateFieldNames(ir: ServiceIRFull): Set[String] =
    ir.f.toList.flatMap:
      case StateDeclFull(fs, _) => fs.collect { case f: StateFieldDeclFull => f.a }
    .toSet

  private def inferCreateRole(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      entityBundles: List[EntityBundles]
  ): (RuleRole, List[TestSkip]) =
    if pop.kind != OperationKind.Create && pop.kind != OperationKind.CreateChild then
      (RuleRole.Plain, Nil)
    else
      pop.targetEntity.flatMap(en => entityBundles.find(_.entityName == en)) match
        case None => (RuleRole.Plain, Nil)
        case Some(eb) =>
          val targetBundle =
            if eb.bundles.exists(_.statusValue.isDefined) then
              eb.initialStatusByCreateOp.get(opDecl.a).flatMap: status =>
                eb.bundles.find(_.statusValue.contains(status))
            else eb.bundles.headOption
          targetBundle match
            case None => (RuleRole.Plain, Nil)
            case Some(bundle) =>
              projectionForCreateOutput(opDecl, bundle) match
                case Some(proj) => (RuleRole.CreateTarget(bundle, proj), Nil)
                case None =>
                  val skip = TestSkip(
                    opDecl.a,
                    "stateful_create_target",
                    s"Create operation has no output of entity type '${bundle.entityName}' " +
                      s"or PK type '${typeName(bundle.pkTypeExpr).getOrElse("?")}'; " +
                      "emitting parameter-less rule without a = bundle"
                  )
                  (RuleRole.Plain, List(skip))

  private def projectionForCreateOutput(
      opDecl: OperationDeclFull,
      bundle: BundleSpec
  ): Option[String] =
    val outputs = opDecl.c.collect { case p: ParamDeclFull => p }
    outputs match
      case List(ParamDeclFull(_, t, _)) if isEntityType(t, bundle.entityName) =>
        Some(s"response_data[${ExprToPython.pyString(bundle.pkFieldName)}]")
      case _ =>
        outputs
          .find(o => sameNamedType(o.b, bundle.pkTypeExpr))
          .map(o => s"response_data[${ExprToPython.pyString(o.a)}]")
          .orElse(
            outputs
              .find(_.a == bundle.pkFieldName)
              .map(o => s"response_data[${ExprToPython.pyString(o.a)}]")
          )

  private def isEntityType(t: type_expr_full, name: String): Boolean = t match
    case NamedTypeF(n, _) => n == name
    case _                => false

  private def sameNamedType(a: type_expr_full, b: type_expr_full): Boolean = (a, b) match
    case (NamedTypeF(x, _), NamedTypeF(y, _)) => x == y
    case _                                    => false

  private def typeName(t: type_expr_full): Option[String] = t match
    case NamedTypeF(n, _) => Some(n)
    case _                => None

  private def enumValuesForField(
      field: FieldDeclFull,
      ir: ServiceIRFull
  ): Option[List[String]] =
    enumValuesForType(field.b, ir, Set.empty)

  private def enumValuesForType(
      t: type_expr_full,
      ir: ServiceIRFull,
      seen: Set[String]
  ): Option[List[String]] =
    t match
      case NamedTypeF(name, _) =>
        val enums   = ir.d.collect { case e: EnumDeclFull => e }
        val aliases = ir.e.collect { case a: TypeAliasDeclFull => a }
        enums.find(_.a == name).map(_.b).orElse:
          if seen.contains(name) then None
          else
            aliases
              .find(_.a == name)
              .flatMap(alias => enumValuesForType(alias.b, ir, seen + name))
      case _ => None

  final private case class StatusRestriction(
      stateFieldName: String,
      inputName: String,
      perFieldRestrictions: Map[String, Set[String]]
  )

  private def recognizeStatusRestriction(
      opDecl: OperationDeclFull,
      ir: ServiceIRFull
  ): Option[StatusRestriction] =
    val stateFields = stateFieldNames(ir)
    val inputs      = opDecl.b.collect { case p: ParamDeclFull => p.a }.toSet
    val conjuncts   = flattenAnd(opDecl.d)
    val keyExists = conjuncts.collectFirst:
      case BinaryOpF(BIn(), IdentifierF(in, _), IdentifierF(state, _), _)
          if inputs.contains(in) && stateFields.contains(state) =>
        (in, state)
    keyExists.flatMap: (inputName, stateName) =>
      var perField     = Map.empty[String, Set[String]]
      var unrecognized = false
      conjuncts.foreach: c =>
        if isKeyExistsConj(c, inputName, stateName) || isTrivialTrue(c) then ()
        else
          fieldRestrictionConjunct(c, inputName, stateName) match
            case Some((fieldName, allowed)) =>
              val merged = perField.get(fieldName) match
                case Some(existing) => existing.intersect(allowed)
                case None           => allowed
              perField = perField.updated(fieldName, merged)
            case None => unrecognized = true
      if unrecognized then None
      else Some(StatusRestriction(stateName, inputName, perField))

  private def isKeyExistsConj(c: expr_full, inputName: String, stateName: String): Boolean =
    c match
      case BinaryOpF(
            BIn(),
            IdentifierF(in, _),
            IdentifierF(state, _),
            _
          ) =>
        in == inputName && state == stateName
      case _ => false

  private def fieldRestrictionConjunct(
      c: expr_full,
      inputName: String,
      stateName: String
  ): Option[(String, Set[String])] =
    c match
      case BinaryOpF(BEq(), lhs, rhs, _) =>
        fieldNameIfStateIndex(lhs, inputName, stateName).flatMap: fname =>
          enumLitFromExpr(rhs).map(lit => (fname, Set(lit)))
      case BinaryOpF(BIn(), lhs, SetLiteralF(elems, _), _) =>
        fieldNameIfStateIndex(lhs, inputName, stateName).flatMap: fname =>
          val maybeSet = elems.map(enumLitFromExpr)
          if maybeSet.forall(_.isDefined) then Some((fname, maybeSet.flatten.toSet))
          else None
      case _ => None

  private def fieldNameIfStateIndex(
      e: expr_full,
      inputName: String,
      stateName: String
  ): Option[String] =
    e match
      case FieldAccessF(
            IndexF(IdentifierF(s, _), IdentifierF(i, _), _),
            fname,
            _
          ) if s == stateName && i == inputName =>
        Some(fname)
      case _ => None

  private def enumLitFromExpr(e: expr_full): Option[String] = e match
    case EnumAccessF(_, member, _) => Some(member)
    case IdentifierF(name, _)      => Some(name)
    case _                         => None

  private def flattenAnd(exprs: List[expr_full]): List[expr_full] =
    exprs.flatMap(flattenAndOne)

  private def flattenAndOne(e: expr_full): List[expr_full] = e match
    case BinaryOpF(BAnd(), l, r, _) => flattenAndOne(l) ++ flattenAndOne(r)
    case other                      => List(other)

  private def bindForInput(
      paramName: String,
      paramType: type_expr_full,
      pop: ProfiledOperation,
      ir: ServiceIRFull,
      entityBundles: List[EntityBundles],
      statusRestriction: Option[StatusRestriction]
  ): InputBinding =
    val targetEbBundles = pop.targetEntity.flatMap: en =>
      entityBundles
        .find(eb => eb.entityName == en && sameNamedType(paramType, eb.pkTypeExpr))
    val pkFieldNameMatch = entityBundles.find: eb =>
      sameNamedType(paramType, eb.pkTypeExpr) &&
        (paramName == eb.pkFieldName || paramName == s"${Naming.toSnakeCase(eb.entityName)}_id")
    val typeMatches = entityBundles.filter(eb => sameNamedType(paramType, eb.pkTypeExpr))
    val uniqueTypeMatch = typeMatches match
      case head :: Nil => Some(head)
      case _           => None

    val matchingEb = targetEbBundles.orElse(pkFieldNameMatch).orElse(uniqueTypeMatch)

    matchingEb match
      case Some(eb) =>
        val perStatus       = eb.bundles.exists(_.statusValue.isDefined)
        val applicableSr    = statusRestriction.filter(_.inputName == paramName)
        val transitionField = eb.transition.map(_.c)
        val statusFilter: Option[Set[String]] = (applicableSr, transitionField) match
          case (Some(sr), Some(tf)) => sr.perFieldRestrictions.get(tf)
          case _                    => None
        val activeBundles =
          if !perStatus then eb.bundles
          else
            statusFilter match
              case Some(allowed) => eb.bundles.filter(_.statusValue.exists(allowed.contains))
              case None          => eb.bundles
        val isDelete = pop.kind == OperationKind.Delete
        val strictByConstruction = applicableSr.exists: sr =>
          transitionField match
            case Some(tf) => sr.perFieldRestrictions.keys.forall(_ == tf)
            case None     => sr.perFieldRestrictions.isEmpty
        activeBundles match
          case Nil =>
            InputBinding.Skip(s"no bundle matches restriction for entity '${eb.entityName}'")
          case head :: Nil =>
            // Only consume when success is guaranteed by construction; otherwise a 4xx
            // would leave the row in the SUT while the bundle has dropped its id.
            if isDelete && strictByConstruction then
              InputBinding.BundleConsume(head, strictByConstruction = true)
            else InputBinding.BundleDraw(head, strictByConstruction)
          case multi =>
            // Multi-bundle non-consuming union; for Delete this leaks ids past the
            // SUT's view → subsequent draws may hit deleted ids and 4xx, so we mark
            // it not strict-by-construction even when the recognizer fully understood.
            val unionStrict = strictByConstruction && !isDelete
            InputBinding.BundleUnion(multi, unionStrict)
      case None =>
        val ctx       = StrategyCtx.OperationInput(pop.operationName, paramName)
        val overrides = TestStrategyOverrides.from(ir)
        Strategies.expressionFor(paramType, ir, ctx, overrides) match
          case StrategyExpr.Code(text) => InputBinding.Generated(text)
          case StrategyExpr.Skip(r)    => InputBinding.Skip(r)

  private def buildRuleBlock(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      bindings: List[(String, InputBinding)],
      role: RuleRole,
      stateFields: Set[String]
  ): String =
    val sb        = new StringBuilder
    val ruleArgs  = ruleDecoratorArgs(bindings, role)
    val funcName  = Naming.toSnakeCase(opDecl.a)
    val sigParams = ("self" :: bindings.map(_._1)).mkString(", ")

    sb.append(s"    @rule($ruleArgs)\n")
    sb.append(s"    def $funcName($sigParams):\n")
    sb.append(s"        $TQ${escapeDocstring(operationSummary(opDecl))}$TQ\n")
    sb.append(s"        response = ${requestCallExpr(pop)}\n")

    val classicBundleInputNames = bindings.collect:
      case (n, InputBinding.BundleDraw(_, _) | InputBinding.BundleConsume(_, _)) => n
    val classicSatisfied =
      opDecl.d.forall: r =>
        requiresIsSatisfiedByBundles(r, classicBundleInputNames.toSet, stateFields)
    val anyBundleBinding = bindings.exists:
      case (
            _,
            _: (InputBinding.BundleDraw | InputBinding.BundleConsume | InputBinding.BundleUnion)
          ) =>
        true
      case _ => false
    val allBundleBindingsStrict = bindings.forall:
      case (_, InputBinding.BundleDraw(_, s))    => s
      case (_, InputBinding.BundleConsume(_, s)) => s
      case (_, InputBinding.BundleUnion(_, s))   => s
      case _                                     => true
    val recognizedStrict = anyBundleBinding && allBundleBindingsStrict
    val expectsStrictSuccess =
      role match
        case RuleRole.CreateTarget(_, _) => true
        case RuleRole.Plain              => classicSatisfied || recognizedStrict

    val successCode = pop.endpoint.successStatus
    if expectsStrictSuccess then
      sb.append(s"        assert response.status_code == $successCode, response.text\n")
      role match
        case RuleRole.CreateTarget(_, proj) =>
          sb.append("        response_data = response.json() if response.content else {}\n")
          sb.append(s"        return $proj\n")
        case RuleRole.Plain =>
          ()
    else
      sb.append(
        s"        if response.status_code == $successCode:\n            pass\n"
      )
      sb.append(
        "        elif 400 <= response.status_code < 500:\n            pass\n"
      )
      sb.append(
        "        else:\n            assert False, f\"unexpected status {response.status_code}: {response.text}\"\n"
      )
    sb.toString

  private def ruleDecoratorArgs(
      bindings: List[(String, InputBinding)],
      role: RuleRole
  ): String =
    val targetArg = role match
      case RuleRole.CreateTarget(b, _) => List(s"a = ${b.pyVarName}")
      case RuleRole.Plain              => Nil
    val paramArgs = bindings.map: (name, b) =>
      val rhs = b match
        case InputBinding.BundleDraw(bundle, _)    => bundle.pyVarName
        case InputBinding.BundleConsume(bundle, _) => s"consumes(${bundle.pyVarName})"
        case InputBinding.BundleUnion(bs, _) =>
          val joined = bs.map(_.pyVarName).mkString(", ")
          s"st.one_of($joined)"
        case InputBinding.Generated(text) => text
        case InputBinding.Skip(_)         => "st.nothing()"
      s"$name=$rhs"
    (targetArg ++ paramArgs).mkString(", ")

  private def emitInvariant(
      inv: InvariantDeclFull,
      idx: Int,
      ir: ServiceIRFull
  ): (Option[String], Option[TestSkip]) =
    val stateFieldsAll = ir.f.toList.flatMap:
      case StateDeclFull(fs, _) => fs.collect { case f: StateFieldDeclFull => f }
    val ctx = TestCtx(
      inputs = Set.empty,
      outputs = Set.empty,
      stateFields = stateFieldsAll.map(_.a).toSet,
      mapStateFields = stateFieldsAll.collect {
        case StateFieldDeclFull(n, _: MapTypeF, _) => n
      }.toSet,
      enumValues = ir.d.collect { case e: EnumDeclFull => e.a -> e.b.toSet }.toMap,
      userFunctions = ir.l.collect { case f: FunctionDeclFull => f.a -> f }.toMap,
      userPredicates = ir.m.collect { case p: PredicateDeclFull => p.a -> p }.toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState
    )
    val name       = inv.a.getOrElse(s"anon_$idx")
    val methodName = Naming.toSnakeCase(name)
    ExprToPython.translate(inv.b, ctx) match
      case ExprPy.Skip(reason, _) =>
        val skip = TestSkip("<invariants>", s"stateful_invariant[$name]", reason)
        (None, Some(skip))
      case ExprPy.Py(text) =>
        val sb = new StringBuilder
        sb.append("    @invariant()\n")
        sb.append(s"    def invariant_$methodName(self):\n")
        sb.append(
          s"        ${TQ}invariant $name: ${escapeDocstring(prettyOneLine(inv.b))}$TQ\n"
        )
        sb.append("        post_state = client.get(\"/__test_admin__/state\").json()\n")
        sb.append(
          s"        assert $text, ${ExprToPython.pyString(s"invariant violated: $name")}\n"
        )
        (Some(sb.toString), None)

  private def renderFile(
      ir: ServiceIRFull,
      machineName: String,
      testName: String,
      bundles: List[BundleSpec],
      ruleBlocks: List[String],
      invariantBlocks: List[String]
  ): String =
    val needsConsumes = ruleBlocks.exists(_.contains("consumes("))
    val needsMultiple = ruleBlocks.exists(_.contains("multiple()"))
    val statefulImports = List(
      "RuleBasedStateMachine",
      "Bundle",
      "rule",
      "initialize",
      "invariant"
    ) ++ (if needsConsumes then List("consumes") else Nil) ++
      (if needsMultiple then List("multiple") else Nil)
    val statefulImportLine =
      statefulImports.map("    " + _ + ",").mkString("\n")

    val strategySpecs = Strategies.forIR(ir)
    val strategyImport =
      if strategySpecs.isEmpty then ""
      else
        val names = strategySpecs.map(_.functionName).sorted.mkString(",\n    ")
        s"""|from tests.strategies import (
            |    $names,
            |)
            |
            |""".stripMargin

    val bundleDecls =
      if bundles.isEmpty then ""
      else
        bundles
          .map(b => s"    ${b.pyVarName} = Bundle(${ExprToPython.pyString(b.bundleName)})")
          .mkString("\n") + "\n\n"

    val initializeBlock =
      """    @initialize()
        |    def _reset(self):
        |        client.post("/__test_admin__/reset")
        |
        |""".stripMargin

    val ruleSection =
      if ruleBlocks.isEmpty then "    # No rules generated; see _testgen_skips.json.\n    pass\n"
      else ruleBlocks.mkString("\n")

    val invariantSection =
      if invariantBlocks.isEmpty then ""
      else "\n" + invariantBlocks.mkString("\n")

    s"""|${TQ}Auto-generated stateful tests for ${ir.a}.
        |
        |Builds a Hypothesis RuleBasedStateMachine: each spec operation becomes a
        |@rule that performs the real HTTP call; entity ids returned from Create
        |operations flow through Bundles into Read/Update/Delete rules; global
        |invariants are checked after every step against /__test_admin__/state.
        |
        |See tests/_testgen_skips.json for clauses skipped during translation.
        |${TQ}
        |import datetime
        |import re
        |
        |from hypothesis import HealthCheck, settings
        |from hypothesis import strategies as st
        |from hypothesis.stateful import (
        |$statefulImportLine
        |)
        |
        |from tests.conftest import client
        |from tests.m import is_valid_email, is_valid_uri
        |from tests.redaction import redact
        |
        |${strategyImport}class $machineName(RuleBasedStateMachine):
        |
        |${bundleDecls}${initializeBlock}${ruleSection}${invariantSection}
        |
        |$machineName.TestCase.settings = settings(
        |    max_examples=25,
        |    stateful_step_count=20,
        |    deadline=None,
        |    suppress_health_check=[HealthCheck.too_slow, HealthCheck.function_scoped_fixture],
        |)
        |$testName = $machineName.TestCase
        |""".stripMargin

  private def requestCallExpr(pop: ProfiledOperation): String =
    val ep              = pop.endpoint
    val method          = ep.method.toString.toLowerCase
    val bodyParamNames  = ep.bodyParams.map(_.name)
    val queryParamNames = ep.queryParams.map(_.name)
    val pathExpr        = pythonPathLiteral(ep)
    val bodyExpr =
      if bodyParamNames.isEmpty then ""
      else
        val pairs =
          bodyParamNames.map(n => s"${ExprToPython.pyString(n)}: $n").mkString(", ")
        s", json={$pairs}"
    val queryExpr =
      if queryParamNames.isEmpty then ""
      else
        val pairs =
          queryParamNames.map(n => s"${ExprToPython.pyString(n)}: $n").mkString(", ")
        s", params={$pairs}"
    s"client.$method($pathExpr$bodyExpr$queryExpr)"

  private def pythonPathLiteral(ep: EndpointSpec): String =
    if ep.pathParams.isEmpty then ExprToPython.pyString(ep.path)
    else "f" + ExprToPython.pyString(ep.path)

  private def operationSummary(op: OperationDeclFull): String =
    val req = op.d
      .filterNot(isTrivialTrue)
      .map(prettyOneLine)
      .mkString("; ")
    val ens = op.e.map(prettyOneLine).mkString("; ")
    val parts = List(
      Option.when(req.nonEmpty)(s"requires: $req"),
      Option.when(ens.nonEmpty)(s"ensures: $ens")
    ).flatten
    if parts.isEmpty then op.a else s"${op.a}: ${parts.mkString(" | ")}"

  private def isTrivialTrue(e: expr_full): Boolean = e match
    case BoolLitF(true, _) => true
    case _                 => false

  private def requiresIsSatisfiedByBundles(
      e: expr_full,
      bundleInputs: Set[String],
      stateFields: Set[String]
  ): Boolean =
    e match
      case BoolLitF(true, _) => true
      case BinaryOpF(BIn(), IdentifierF(in, _), IdentifierF(state, _), _)
          if bundleInputs.contains(in) && stateFields.contains(state) =>
        true
      case BinaryOpF(BAnd(), l, r, _) =>
        requiresIsSatisfiedByBundles(l, bundleInputs, stateFields) &&
        requiresIsSatisfiedByBundles(r, bundleInputs, stateFields)
      case _ => false

  private def prettyOneLine(e: expr_full): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private def escapeDocstring(s: String): String =
    s.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")
