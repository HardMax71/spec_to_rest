package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

object Classify:

  def classifyOperations(ir: ServiceIRFull): List[operation_classification] =
    ir.g.collect { case op: OperationDeclFull => classifyOperation(op, ir) }

  def classifyOperation(op: OperationDeclFull, ir: ServiceIRFull): operation_classification =
    val stateFieldNames = ir.f match
      case Some(StateDeclFull(fs, _)) => fs.collect { case StateFieldDeclFull(n, _, _) => n }.toSet
      case None                       => Set.empty[String]
    val signals      = analyze(op, ir, stateFieldNames)
    val entityMap    = ir.c.collect { case e: EntityDeclFull => e.a -> e }.toMap
    val targetEntity = resolveTargetEntity(op, ir, entityMap)
    val outputNames  = op.c.collect { case ParamDeclFull(n, _, _) => n }
    val strategy     = classifyStrategy(op.e, stateFieldNames.toList, outputNames)
    val entityFieldCount: Option[nat] =
      targetEntity.flatMap(entityMap.get).map { case EntityDeclFull(_, _, fs, _, _) =>
        Nata(BigInt(fs.length))
      }
    buildOperationClassification(
      op.a,
      targetEntity,
      strategy,
      decideKindAndMethod(signals, entityFieldCount)
    )

  private def analyze(
      op: OperationDeclFull,
      ir: ServiceIRFull,
      stateFieldNames: Set[String]
  ): analysis_signals =
    val stateFieldList = stateFieldNames.toList
    val primedIds      = collectPrimedIdentifiers(op.e).toSet
    val preserved      = collectPreservedRelations(op.e, stateFieldList).toSet

    val primedStateFields = primedIds.toList.filter(stateFieldNames.contains)
    val mutated           = primedStateFields.filterNot(preserved.contains)

    val createInfo   = detectCreatePattern(op.e, stateFieldList)
    val deleteInfo   = detectDeletePattern(op.e, stateFieldList)
    val existingKeys = detectKeyExistsInRequires(op.d, stateFieldList).toSet
    val createsNewKey =
      createInfo.exists(name => !existingKeys.contains(name))

    val withInfo = collectWithFields(op.e)

    val isTransition = ir.h.exists {
      case TransitionDeclFull(_, _, _, rules, _) =>
        rules.exists { case TransitionRuleFull(_, _, via, _, _) => via == op.a }
    }

    val params = op.b.collect { case p: ParamDeclFull => p }
    val filterCount = params.count {
      case ParamDeclFull(_, _: OptionTypeF, _) => true
      case _                                   => false
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
      params.exists { case ParamDeclFull(_, t, _) => isInputCollectionType(t) }
    )

  private def resolveTargetEntity(
      op: OperationDeclFull,
      ir: ServiceIRFull,
      entityMap: Map[String, EntityDeclFull]
  ): Option[String] =
    ir.f match
      case None => None
      case Some(StateDeclFull(fs, _)) =>
        val primedIds = collectPrimedIdentifiers(op.e).toSet
        val fromState = fs.iterator
          .collect { case StateFieldDeclFull(n, t, _) if primedIds.contains(n) => t }
          .flatMap(entityNameFromType)
          .find(entityMap.contains)
        fromState.orElse:
          op.c.iterator
            .collect { case ParamDeclFull(_, t, _) => t }
            .flatMap(entityNameFromType)
            .find(entityMap.contains)
