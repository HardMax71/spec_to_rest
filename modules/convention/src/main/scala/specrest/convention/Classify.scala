package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*
import specrest.ir.idx

object Classify:

  def classifyOperations(ir: ServiceIRFull): List[operation_classification] =
    svcOperations(ir).map(op => classifyOperation(op, ir))

  def classifyOperation(op: operation_decl_full, ir: ServiceIRFull): operation_classification =
    val stateFieldNames = svcState(ir) match
      case Some(sd) => stdFields(sd).map(stfName).toSet
      case None     => Set.empty[String]
    val signals                       = analyze(op, ir, stateFieldNames)
    val entityMap                     = ir.idx.entityByName
    val targetEntity                  = resolveTargetEntity(op, ir, entityMap)
    val outputNames                   = operOutputs(op).map(prmName)
    val strategy                      = classifyStrategy(operEnsures(op), stateFieldNames.toList, outputNames)
    val entityFieldCount: Option[nat] =
      targetEntity.flatMap(entityMap.get).map(e => Nata(BigInt(entFields(e).length)))
    buildOperationClassification(
      operName(op),
      targetEntity,
      strategy,
      decideKindAndMethod(signals, entityFieldCount)
    )

  private def analyze(
      op: operation_decl_full,
      ir: ServiceIRFull,
      stateFieldNames: Set[String]
  ): analysis_signals =
    val stateFieldList = stateFieldNames.toList
    val primedIds      = collectPrimedIdentifiers(operEnsures(op)).toSet
    val preserved      = collectPreservedRelations(operEnsures(op), stateFieldList).toSet

    val primedStateFields = primedIds.toList.filter(stateFieldNames.contains)
    val mutated           = primedStateFields.filterNot(preserved.contains)

    val createInfo    = detectCreatePattern(operEnsures(op), stateFieldList)
    val deleteInfo    = detectDeletePattern(operEnsures(op), stateFieldList)
    val existingKeys  = detectKeyExistsInRequires(operRequires(op), stateFieldList).toSet
    val createsNewKey =
      createInfo.exists(name => !existingKeys.contains(name))

    val withInfo = collectWithFields(operEnsures(op))

    val isTransition = svcTransitions(ir).exists { td =>
      trnRules(td).exists(tr => trlVia(tr) == operName(op))
    }

    val params      = operInputs(op)
    val filterCount = params.count { p =>
      prmType(p) match
        case _: OptionTypeF => true
        case _              => false
    }
    AnalysisSignals(
      mutated,
      preserved.toList,
      createsNewKey,
      deleteInfo.isDefined,
      None,
      withInfo.map(wi => Nata(BigInt(withInfoFieldNames(wi).length))),
      Nata(BigInt(filterCount)),
      isTransition,
      params.exists(p => isInputCollectionType(prmType(p)))
    )

  private def resolveTargetEntity(
      op: operation_decl_full,
      ir: ServiceIRFull,
      entityMap: Map[String, entity_decl_full]
  ): Option[String] =
    svcState(ir) match
      case None     => None
      case Some(sd) =>
        val primedIds = collectPrimedIdentifiers(operEnsures(op)).toSet
        val fromState = stdFields(sd).iterator
          .filter(sf => primedIds.contains(stfName(sf)))
          .map(stfType)
          .flatMap(entityNameFromType)
          .find(entityMap.contains)
        fromState.orElse:
          operOutputs(op).iterator
            .map(prmType)
            .flatMap(entityNameFromType)
            .find(entityMap.contains)
