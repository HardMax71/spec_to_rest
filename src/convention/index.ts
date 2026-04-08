export type {
  HttpMethod,
  OperationKind,
  OperationClassification,
  AnalysisSignals,
  EndpointSpec,
  ParamSpec,
  DatabaseSchema,
  TableSpec,
  ColumnSpec,
  ForeignKeySpec,
  IndexSpec,
  ConventionDiagnostic,
} from "#convention/types.js";

export { classifyOperation, classifyOperations } from "#convention/classify.js";
export { deriveEndpoints, getConvention, VALID_METHODS } from "#convention/path.js";
export { deriveSchema } from "#convention/schema.js";
export { validateConventions } from "#convention/validate.js";
export {
  pluralize,
  splitCamelCase,
  toKebabCase,
  toSnakeCase,
  toPathSegment,
  toTableName,
  toColumnName,
} from "#convention/naming.js";
