export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export type OperationKind =
  | "create"
  | "read"
  | "replace"
  | "partial_update"
  | "delete"
  | "create_child"
  | "filtered_read"
  | "side_effect"
  | "batch_mutation"
  | "transition";

export interface AnalysisSignals {
  readonly mutatedRelations: readonly string[];
  readonly preservedRelations: readonly string[];
  readonly createsNewKey: boolean;
  readonly deletesKey: boolean;
  readonly targetEntityFieldCount: number | null;
  readonly withFieldCount: number | null;
  readonly filterParamCount: number;
  readonly isTransition: boolean;
  readonly hasCollectionInput: boolean;
}

export interface OperationClassification {
  readonly operationName: string;
  readonly kind: OperationKind;
  readonly method: HttpMethod;
  readonly matchedRule: string;
  readonly targetEntity: string | null;
  readonly signals: AnalysisSignals;
}
