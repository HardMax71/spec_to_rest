import type { TypeExpr } from "#ir/types.js";

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

// ── Endpoint Derivation ──────────────────────────────────────

export interface EndpointSpec {
  readonly operationName: string;
  readonly method: HttpMethod;
  readonly path: string;
  readonly pathParams: readonly ParamSpec[];
  readonly queryParams: readonly ParamSpec[];
  readonly bodyParams: readonly ParamSpec[];
  readonly successStatus: number;
}

export interface ParamSpec {
  readonly name: string;
  readonly typeExpr: TypeExpr;
  readonly required: boolean;
}

// ── Database Schema ──────────────────────────────────────────

export interface DatabaseSchema {
  readonly tables: readonly TableSpec[];
}

export interface TableSpec {
  readonly name: string;
  readonly entityName: string;
  readonly columns: readonly ColumnSpec[];
  readonly primaryKey: string;
  readonly foreignKeys: readonly ForeignKeySpec[];
  readonly checks: readonly string[];
  readonly indexes: readonly IndexSpec[];
}

export interface ColumnSpec {
  readonly name: string;
  readonly sqlType: string;
  readonly nullable: boolean;
  readonly defaultValue: string | null;
}

export interface ForeignKeySpec {
  readonly column: string;
  readonly refTable: string;
  readonly refColumn: string;
  readonly onDelete: string;
}

export interface IndexSpec {
  readonly name: string;
  readonly columns: readonly string[];
  readonly unique: boolean;
}
