package specrest.convention

import specrest.ir.*

object Classify:

  def classifyOperations(ir: ServiceIR): List[OperationClassification] =
    ir.operations.map(op => classifyOperation(op, ir))

  def classifyOperation(op: OperationDecl, ir: ServiceIR): OperationClassification =
    val stateFieldNames = ir.state.map(_.fields.map(_.name).toSet).getOrElse(Set.empty)
    val signals         = analyze(op, ir, stateFieldNames)
    val entityMap       = ir.entities.map(e => e.name -> e).toMap
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
      op: OperationDecl,
      ir: ServiceIR,
      stateFieldNames: Set[String],
  ): AnalysisSignals =
    val primedIds = ExprAnalysis.collectPrimedIdentifiers(op.ensures)
    val preserved = ExprAnalysis.collectPreservedRelations(op.ensures, stateFieldNames)

    val primedStateFields = primedIds.toList.filter(stateFieldNames.contains)
    val mutated           = primedStateFields.filterNot(preserved.contains)

    val createInfo   = ExprAnalysis.detectCreatePattern(op.ensures, stateFieldNames)
    val deleteInfo   = ExprAnalysis.detectDeletePattern(op.ensures, stateFieldNames)
    val existingKeys = ExprAnalysis.detectKeyExistsInRequires(op.requires, stateFieldNames)
    val createsNewKey =
      createInfo.exists(ci => !existingKeys.contains(ci.field))

    val withInfo = ExprAnalysis.collectWithFields(op.ensures)

    val isTransition = ir.transitions.exists(t => t.rules.exists(_.via == op.name))

    AnalysisSignals(
      mutatedRelations       = mutated,
      preservedRelations     = preserved.toList,
      createsNewKey          = createsNewKey,
      deletesKey             = deleteInfo.isDefined,
      targetEntityFieldCount = None,
      withFieldCount         = withInfo.map(_.fieldNames.length),
      filterParamCount       = ExprAnalysis.countFilterParams(op.inputs),
      isTransition           = isTransition,
      hasCollectionInput     = ExprAnalysis.hasCollectionInput(op.inputs),
    )

  private def resolveTargetEntity(
      op: OperationDecl,
      ir: ServiceIR,
      entityMap: Map[String, EntityDecl],
  ): Option[String] =
    ir.state match
      case None => None
      case Some(state) =>
        val primedIds = ExprAnalysis.collectPrimedIdentifiers(op.ensures)
        val fromState = state.fields.iterator
          .filter(f => primedIds.contains(f.name))
          .flatMap(f => entityNameFromType(f.typeExpr))
          .find(entityMap.contains)
        fromState.orElse:
          op.outputs.iterator
            .flatMap(p => entityNameFromType(p.typeExpr))
            .find(entityMap.contains)

  private def entityNameFromType(typeExpr: TypeExpr): Option[String] = typeExpr match
    case TypeExpr.RelationType(_, _, to, _) => typeNameString(to)
    case TypeExpr.NamedType(n, _)            => Some(n)
    case TypeExpr.SetType(inner, _)          => entityNameFromType(inner)
    case TypeExpr.SeqType(inner, _)          => entityNameFromType(inner)
    case TypeExpr.OptionType(inner, _)       => entityNameFromType(inner)
    case TypeExpr.MapType(_, v, _)           => entityNameFromType(v)

  private def typeNameString(typeExpr: TypeExpr): Option[String] = typeExpr match
    case TypeExpr.NamedType(n, _) => Some(n)
    case _                        => None

  private def classifyPutPatch(
      op: OperationDecl,
      signals: AnalysisSignals,
      targetEntity: Option[String],
      entityMap: Map[String, EntityDecl],
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
      op: OperationDecl,
      kind: OperationKind,
      method: HttpMethod,
      matchedRule: String,
      targetEntity: Option[String],
      signals: AnalysisSignals,
  ): OperationClassification =
    OperationClassification(op.name, kind, method, matchedRule, targetEntity, signals)
