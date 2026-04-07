import type { ServiceIR, OperationDecl, EntityDecl, TypeExpr } from "#ir/types.js";
import type { OperationClassification, AnalysisSignals, HttpMethod, OperationKind } from "#convention/types.js";
import {
  collectPrimedIdentifiers,
  collectPreservedRelations,
  detectCreatePattern,
  detectDeletePattern,
  collectWithFields,
  countFilterParams,
  hasCollectionInput,
  detectKeyExistsInRequires,
} from "#convention/expr-analysis.js";

export function classifyOperations(ir: ServiceIR): readonly OperationClassification[] {
  return ir.operations.map((op) => classifyOperation(op, ir));
}

export function classifyOperation(op: OperationDecl, ir: ServiceIR): OperationClassification {
  const stateFieldNames = new Set(ir.state?.fields.map((f) => f.name) ?? []);
  const signals = analyze(op, ir, stateFieldNames);
  const entityMap = new Map(ir.entities.map((e) => [e.name, e]));

  const targetEntity = resolveTargetEntity(op, ir, stateFieldNames, entityMap);

  // Priority 5: M10
  if (signals.isTransition) {
    return result(op, "transition", "POST", "M10", targetEntity, signals);
  }

  // Priority 10: M5, M1, M2, M3, M4
  if (signals.deletesKey) {
    return result(op, "delete", "DELETE", "M5", targetEntity, signals);
  }

  if (signals.mutatedRelations.length > 0 && signals.createsNewKey) {
    return result(op, "create", "POST", "M1", targetEntity, signals);
  }

  if (signals.mutatedRelations.length === 0) {
    if (signals.filterParamCount > 3) {
      return result(op, "filtered_read", "GET", "M7", targetEntity, signals);
    }
    return result(op, "read", "GET", "M2", targetEntity, signals);
  }

  // PUT vs PATCH: mutates existing entity, no create, no delete
  if (signals.mutatedRelations.length > 0 && !signals.createsNewKey && !signals.deletesKey) {
    const putOrPatch = classifyPutPatch(signals, targetEntity, entityMap);
    if (putOrPatch) return putOrPatch(op, targetEntity, signals);
  }

  // Priority 15: M9
  if (signals.hasCollectionInput && signals.mutatedRelations.length > 0) {
    return result(op, "batch_mutation", "POST", "M9", targetEntity, signals);
  }

  // Priority 30: M8 fallback
  return result(op, "side_effect", "POST", "M8", targetEntity, signals);
}

function analyze(
  op: OperationDecl,
  ir: ServiceIR,
  stateFieldNames: Set<string>,
): AnalysisSignals {
  const primedIds = collectPrimedIdentifiers(op.ensures);
  const preserved = collectPreservedRelations(op.ensures, stateFieldNames);

  const primedStateFields = [...primedIds].filter((id) => stateFieldNames.has(id));
  const mutated = primedStateFields.filter((f) => !preserved.has(f));

  const createInfo = detectCreatePattern(op.ensures, stateFieldNames);
  const deleteInfo = detectDeletePattern(op.ensures, stateFieldNames);
  const existingKeys = detectKeyExistsInRequires(op.requires, stateFieldNames);

  // If the "create" pattern targets a relation where the key already exists
  // (confirmed by requires), it's an update, not a create
  const createsNewKey = createInfo !== null && !existingKeys.has(createInfo.field);

  const withInfo = collectWithFields(op.ensures);

  const isTransition = ir.transitions.some((t) =>
    t.rules.some((r) => r.via === op.name),
  );

  return {
    mutatedRelations: mutated,
    preservedRelations: [...preserved],
    createsNewKey,
    deletesKey: deleteInfo !== null,
    targetEntityFieldCount: null,
    withFieldCount: withInfo?.fieldNames.length ?? null,
    filterParamCount: countFilterParams(op.inputs),
    isTransition,
    hasCollectionInput: hasCollectionInput(op.inputs),
  };
}

function resolveTargetEntity(
  op: OperationDecl,
  ir: ServiceIR,
  stateFieldNames: Set<string>,
  entityMap: Map<string, EntityDecl>,
): string | null {
  if (!ir.state) return null;

  const primedIds = collectPrimedIdentifiers(op.ensures);

  for (const field of ir.state.fields) {
    if (!primedIds.has(field.name)) continue;
    const entityName = entityNameFromType(field.typeExpr);
    if (entityName && entityMap.has(entityName)) return entityName;
  }

  // Fallback: check output types
  for (const out of op.outputs) {
    const name = entityNameFromType(out.typeExpr);
    if (name && entityMap.has(name)) return name;
  }

  return null;
}

function entityNameFromType(typeExpr: TypeExpr): string | null {
  switch (typeExpr.kind) {
    case "RelationType":
      return typeNameString(typeExpr.toType);
    case "NamedType":
      return typeExpr.name;
    case "SetType":
      return entityNameFromType(typeExpr.elementType);
    case "SeqType":
      return entityNameFromType(typeExpr.elementType);
    case "OptionType":
      return entityNameFromType(typeExpr.innerType);
    case "MapType":
      return entityNameFromType(typeExpr.valueType);
  }
}

function typeNameString(typeExpr: TypeExpr): string | null {
  return typeExpr.kind === "NamedType" ? typeExpr.name : null;
}

function classifyPutPatch(
  signals: AnalysisSignals,
  targetEntity: string | null,
  entityMap: Map<string, EntityDecl>,
): ((op: OperationDecl, target: string | null, signals: AnalysisSignals) => OperationClassification) | null {
  if (signals.withFieldCount === null) {
    // No `with` expression — treat as partial update (conditional field assignments like implies)
    return (op, target, sigs) => result(op, "partial_update", "PATCH", "M4", target, sigs);
  }

  if (targetEntity && entityMap.has(targetEntity)) {
    const entity = entityMap.get(targetEntity)!;
    const totalFields = entity.fields.length;

    if (signals.withFieldCount >= totalFields) {
      return (op, target, sigs) =>
        result(op, "replace", "PUT", "M3", target, {
          ...sigs,
          targetEntityFieldCount: totalFields,
        });
    }

    return (op, target, sigs) =>
      result(op, "partial_update", "PATCH", "M4", target, {
        ...sigs,
        targetEntityFieldCount: totalFields,
      });
  }

  return (op, target, sigs) => result(op, "partial_update", "PATCH", "M4", target, sigs);
}

function result(
  op: OperationDecl,
  kind: OperationKind,
  method: HttpMethod,
  matchedRule: string,
  targetEntity: string | null,
  signals: AnalysisSignals,
): OperationClassification {
  return { operationName: op.name, kind, method, matchedRule, targetEntity, signals };
}
