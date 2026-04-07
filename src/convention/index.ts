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
} from "#convention/types.js";

export { classifyOperation, classifyOperations } from "#convention/classify.js";
export { deriveEndpoints, getConvention } from "#convention/path.js";
export { deriveSchema } from "#convention/schema.js";
export {
  pluralize,
  splitCamelCase,
  toKebabCase,
  toSnakeCase,
  toPathSegment,
  toTableName,
  toColumnName,
} from "#convention/naming.js";
