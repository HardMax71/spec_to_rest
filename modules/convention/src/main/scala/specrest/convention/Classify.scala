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

    if signals.isTransition then
      result(op, OperationKind.Transition, HttpMethod.POST, "M10", targetEntity, signals)
    else if signals.deletesKey then
      result(op, OperationKind.Delete, HttpMethod.DELETE, "M5", targetEntity, signals)
    else if signals.mutatedRelations.nonEmpty && signals.createsNewKey then
      result(op, OperationKind.Create, HttpMethod.POST, "M1", targetEntity, signals)
    else if signals.mutatedRelations.isEmpty then
      if signals.filterParamCount > 3 then
        result(op, OperationKind.FilteredRead, HttpMethod.GET, "M7", targetEntity, signals)
      else result(op, OperationKind.Read, HttpMethod.GET, "M2", targetEntity, signals)
    else if signals.hasCollectionInput && signals.mutatedRelations.nonEmpty then
      result(op, OperationKind.BatchMutation, HttpMethod.POST, "M9", targetEntity, signals)
    else if signals.mutatedRelations.nonEmpty && !signals.createsNewKey && !signals.deletesKey then
      classifyPutPatch(op, signals, targetEntity, entityMap)
    else result(op, OperationKind.SideEffect, HttpMethod.POST, "M8", targetEntity, signals)

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

  private def typeNameString(typeExpr: type_expr_full): Option[String] = typeExpr match
    case NamedTypeF(n, _) => Some(n)
    case _                => None

  private def classifyPutPatch(
      op: OperationDeclFull,
      signals: AnalysisSignals,
      targetEntity: Option[String],
      entityMap: Map[String, EntityDeclFull]
  ): OperationClassification =
    signals.withFieldCount match
      case None =>
        result(op, OperationKind.PartialUpdate, HttpMethod.PATCH, "M4", targetEntity, signals)
      case Some(wfc) =>
        targetEntity.flatMap(entityMap.get) match
          case Some(EntityDeclFull(_, _, fs, _, _)) =>
            val total   = fs.length
            val updated = signals.copy(targetEntityFieldCount = Some(total))
            if wfc >= total then
              result(op, OperationKind.Replace, HttpMethod.PUT, "M3", targetEntity, updated)
            else
              result(op, OperationKind.PartialUpdate, HttpMethod.PATCH, "M4", targetEntity, updated)
          case None =>
            result(op, OperationKind.PartialUpdate, HttpMethod.PATCH, "M4", targetEntity, signals)

  private def result(
      op: OperationDeclFull,
      kind: OperationKind,
      method: HttpMethod,
      matchedRule: String,
      targetEntity: Option[String],
      signals: AnalysisSignals
  ): OperationClassification =
    OperationClassification(op.a, kind, method, matchedRule, targetEntity, signals)
