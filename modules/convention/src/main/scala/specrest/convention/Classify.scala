package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

object Classify:

  def classifyOperations(ir: ServiceIRFull): List[OperationClassification] =
    ir.g.collect { case op: OperationDeclFull => classifyOperation(op, ir) }

  def classifyOperation(op: OperationDeclFull, ir: ServiceIRFull): OperationClassification =
    val stateFieldNames = ir.f match
      case Some(StateDeclFull(fs, _)) => fs.collect { case StateFieldDeclFull(n, _, _) => n }.toSet
      case None                       => Set.empty[String]
    val signals      = analyze(op, ir, stateFieldNames)
    val entityMap    = ir.c.collect { case e: EntityDeclFull => e.a -> e }.toMap
    val targetEntity = resolveTargetEntity(op, ir, entityMap)
    val strategy     = classifyStrategy(op, stateFieldNames)

    if signals.isTransition then
      result(op, OperationKind.Transition, HttpMethod.POST, "M10", targetEntity, strategy, signals)
    else if signals.deletesKey then
      result(op, OperationKind.Delete, HttpMethod.DELETE, "M5", targetEntity, strategy, signals)
    else if signals.mutatedRelations.nonEmpty && signals.createsNewKey then
      result(op, OperationKind.Create, HttpMethod.POST, "M1", targetEntity, strategy, signals)
    else if signals.mutatedRelations.isEmpty then
      if signals.filterParamCount > 3 then
        result(
          op,
          OperationKind.FilteredRead,
          HttpMethod.GET,
          "M7",
          targetEntity,
          strategy,
          signals
        )
      else result(op, OperationKind.Read, HttpMethod.GET, "M2", targetEntity, strategy, signals)
    else if signals.hasCollectionInput && signals.mutatedRelations.nonEmpty then
      result(
        op,
        OperationKind.BatchMutation,
        HttpMethod.POST,
        "M9",
        targetEntity,
        strategy,
        signals
      )
    else if signals.mutatedRelations.nonEmpty && !signals.createsNewKey && !signals.deletesKey then
      classifyPutPatch(op, signals, targetEntity, strategy, entityMap)
    else
      result(op, OperationKind.SideEffect, HttpMethod.POST, "M8", targetEntity, strategy, signals)

  private def analyze(
      op: OperationDeclFull,
      ir: ServiceIRFull,
      stateFieldNames: Set[String]
  ): AnalysisSignals =
    val primedIds = ExprAnalysis.collectPrimedIdentifiers(op.e)
    val preserved = ExprAnalysis.collectPreservedRelations(op.e, stateFieldNames)

    val primedStateFields = primedIds.toList.filter(stateFieldNames.contains)
    val mutated           = primedStateFields.filterNot(preserved.contains)

    val createInfo   = ExprAnalysis.detectCreatePattern(op.e, stateFieldNames)
    val deleteInfo   = ExprAnalysis.detectDeletePattern(op.e, stateFieldNames)
    val existingKeys = ExprAnalysis.detectKeyExistsInRequires(op.d, stateFieldNames)
    val createsNewKey =
      createInfo.exists(ci => !existingKeys.contains(ci.field))

    val withInfo = ExprAnalysis.collectWithFields(op.e)

    val isTransition = ir.h.exists {
      case TransitionDeclFull(_, _, _, rules, _) =>
        rules.exists { case TransitionRuleFull(_, _, via, _, _) => via == op.a }
    }

    val params = op.b.collect { case p: ParamDeclFull => p }
    AnalysisSignals(
      mutatedRelations = mutated,
      preservedRelations = preserved.toList,
      createsNewKey = createsNewKey,
      deletesKey = deleteInfo.isDefined,
      targetEntityFieldCount = None,
      withFieldCount = withInfo.map(_.fieldNames.length),
      filterParamCount = ExprAnalysis.countFilterParams(params),
      isTransition = isTransition,
      hasCollectionInput = ExprAnalysis.hasCollectionInput(params)
    )

  private def resolveTargetEntity(
      op: OperationDeclFull,
      ir: ServiceIRFull,
      entityMap: Map[String, EntityDeclFull]
  ): Option[String] =
    ir.f match
      case None => None
      case Some(StateDeclFull(fs, _)) =>
        val primedIds = ExprAnalysis.collectPrimedIdentifiers(op.e)
        val fromState = fs.iterator
          .collect { case StateFieldDeclFull(n, t, _) if primedIds.contains(n) => t }
          .flatMap(entityNameFromType)
          .find(entityMap.contains)
        fromState.orElse:
          op.c.iterator
            .collect { case ParamDeclFull(_, t, _) => t }
            .flatMap(entityNameFromType)
            .find(entityMap.contains)

  private def entityNameFromType(typeExpr: type_expr_full): Option[String] = typeExpr match
    case RelationTypeF(_, _, to, _) => typeNameString(to)
    case NamedTypeF(n, _)           => Some(n)
    case SetTypeF(inner, _)         => entityNameFromType(inner)
    case SeqTypeF(inner, _)         => entityNameFromType(inner)
    case OptionTypeF(inner, _)      => entityNameFromType(inner)
    case MapTypeF(_, v, _)          => entityNameFromType(v)

  private def typeNameString(typeExpr: type_expr_full): Option[String] = type_name(typeExpr)

  private def classifyPutPatch(
      op: OperationDeclFull,
      signals: AnalysisSignals,
      targetEntity: Option[String],
      strategy: SynthesisStrategy,
      entityMap: Map[String, EntityDeclFull]
  ): OperationClassification =
    signals.withFieldCount match
      case None =>
        result(
          op,
          OperationKind.PartialUpdate,
          HttpMethod.PATCH,
          "M4",
          targetEntity,
          strategy,
          signals
        )
      case Some(wfc) =>
        targetEntity.flatMap(entityMap.get) match
          case Some(EntityDeclFull(_, _, fs, _, _)) =>
            val total   = fs.length
            val updated = signals.copy(targetEntityFieldCount = Some(total))
            if wfc >= total then
              result(
                op,
                OperationKind.Replace,
                HttpMethod.PUT,
                "M3",
                targetEntity,
                strategy,
                updated
              )
            else
              result(
                op,
                OperationKind.PartialUpdate,
                HttpMethod.PATCH,
                "M4",
                targetEntity,
                strategy,
                updated
              )
          case None =>
            result(
              op,
              OperationKind.PartialUpdate,
              HttpMethod.PATCH,
              "M4",
              targetEntity,
              strategy,
              signals
            )

  private def result(
      op: OperationDeclFull,
      kind: OperationKind,
      method: HttpMethod,
      matchedRule: String,
      targetEntity: Option[String],
      strategy: SynthesisStrategy,
      signals: AnalysisSignals
  ): OperationClassification =
    OperationClassification(op.a, kind, method, matchedRule, targetEntity, strategy, signals)

  def classifyStrategy(
      op: OperationDeclFull,
      stateFieldNames: Set[String]
  ): SynthesisStrategy =
    val outputNames = op.c.collect { case ParamDeclFull(n, _, _) => n }.toSet
    val clauses     = ExprAnalysis.flattenEnsures(op.e)
    if clauses.nonEmpty &&
      clauses.forall(c => isDirectEmitShape(c, stateFieldNames, outputNames))
    then SynthesisStrategy.DirectEmit
    else SynthesisStrategy.LlmSynthesis

  private def isDirectEmitShape(
      clause: expr_full,
      stateFieldNames: Set[String],
      outputNames: Set[String]
  ): Boolean = clause match
    case BinaryOpF(BEq(), PrimeF(IdentifierF(l, _), _), IdentifierF(r, _), _)
        if l == r && stateFieldNames.contains(l) =>
      true
    case BinaryOpF(BNotIn(), _, PrimeF(IdentifierF(n, _), _), _)
        if stateFieldNames.contains(n) =>
      true
    case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(IdentifierF(n, _), _), _), rhs, _)
        if stateFieldNames.contains(n) =>
      isCardinalityRhs(rhs, n)
    case BinaryOpF(
          BEq(),
          PrimeF(IdentifierF(l, _), _),
          BinaryOpF(BAdd(), PreF(IdentifierF(r, _), _), MapLiteralF(entries, _), _),
          _
        ) if l == r && stateFieldNames.contains(l) =>
      entries.forall { case MapEntryFull(k, v, _) =>
        isLeafValue(k) && isLeafValue(v)
      }
    case BinaryOpF(BEq(), IndexF(PrimeF(IdentifierF(n, _), _), idx, _), rhs, _)
        if stateFieldNames.contains(n) =>
      isLeafValue(idx) && isLeafValue(rhs)
    case BinaryOpF(
          BEq(),
          FieldAccessF(IndexF(PrimeF(IdentifierF(n, _), _), idx, _), _, _),
          rhs,
          _
        ) if stateFieldNames.contains(n) =>
      isLeafValue(idx) && isLeafValue(rhs)
    case BinaryOpF(BEq(), IdentifierF(name, _), rhs, _) if outputNames.contains(name) =>
      isPureRead(rhs)
    case _ => false

  private def isCardinalityRhs(rhs: expr_full, n: String): Boolean = rhs match
    case UnaryOpF(UCardinality(), PreF(IdentifierF(m, _), _), _) => m == n
    case UnaryOpF(UCardinality(), IdentifierF(m, _), _)          => m == n
    case BinaryOpF(BAdd(), inner, IntLitF(_, _), _)              => isCardinalityRhs(inner, n)
    case BinaryOpF(BSub(), inner, IntLitF(_, _), _)              => isCardinalityRhs(inner, n)
    case _                                                       => false

  private def isLeafValue(expr: expr_full): Boolean = expr match
    case _: IntLitF | _: FloatLitF | _: StringLitF | _: BoolLitF | _: NoneLitF => true
    case IdentifierF(_, _)                                                     => true
    case EnumAccessF(_, _, _)                                                  => true
    case _                                                                     => false

  private def isPureRead(expr: expr_full): Boolean = expr match
    case IdentifierF(_, _)                                                     => true
    case _: IntLitF | _: FloatLitF | _: StringLitF | _: BoolLitF | _: NoneLitF => true
    case EnumAccessF(_, _, _)                                                  => true
    case PreF(inner, _)                                                        => isPureRead(inner)
    case IndexF(base, idx, _)                                                  => isPureRead(base) && isPureRead(idx)
    case FieldAccessF(base, _, _)                                              => isPureRead(base)
    case _                                                                     => false
