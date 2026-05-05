package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

import specrest.ir.*

object Classify:

  def classifyOperations(ir: ServiceIRFull): List[OperationClassification] =
    ir.g.map(op => classifyOperation(op, ir))

  def classifyOperation(op: OperationDeclFull, ir: ServiceIRFull): OperationClassification =
    val stateFieldNames = ir.state.map(_.fields.map(_.name).toSet).getOrElse(Set.empty)
    val signals         = analyze(op, ir, stateFieldNames)
    val entityMap       = ir.c.map(e => e.name -> e).toMap
    val targetEntity    = resolveTargetEntity(op, ir, entityMap)

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

    val isTransition = ir.h.exists(t => t.rules.exists(_.c == op.name))

    AnalysisSignals(
      mutatedRelations = mutated,
      preservedRelations = preserved.toList,
      createsNewKey = createsNewKey,
      deletesKey = deleteInfo.isDefined,
      targetEntityFieldCount = None,
      withFieldCount = withInfo.map(_.fieldNames.length),
      filterParamCount = ExprAnalysis.countFilterParams(op.b),
      isTransition = isTransition,
      hasCollectionInput = ExprAnalysis.hasCollectionInput(op.b)
    )

  private def resolveTargetEntity(
      op: OperationDeclFull,
      ir: ServiceIRFull,
      entityMap: Map[String, EntityDeclFull]
  ): Option[String] =
    ir.state match
      case None => None
      case Some(state) =>
        val primedIds = ExprAnalysis.collectPrimedIdentifiers(op.e)
        val fromState = state.fields.iterator
          .filter(f => primedIds.contains(f.name))
          .flatMap(f => entityNameFromType(f.typeExpr))
          .find(entityMap.contains)
        fromState.orElse:
          op.c.iterator
            .flatMap(p => entityNameFromType(p.typeExpr))
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
          case Some(entity) =>
            val total   = entity.fields.length
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
    OperationClassification(op.name, kind, method, matchedRule, targetEntity, signals)
