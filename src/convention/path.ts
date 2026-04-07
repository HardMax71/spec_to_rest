import type { ServiceIR, OperationDecl, ConventionsDecl, Expr } from "#ir/types.js";
import type { OperationClassification, EndpointSpec, ParamSpec, HttpMethod } from "#convention/types.js";
import { toPathSegment, toKebabCase } from "#convention/naming.js";

export function deriveEndpoints(
  classifications: readonly OperationClassification[],
  ir: ServiceIR,
): readonly EndpointSpec[] {
  return classifications.map((c) => {
    const op = ir.operations.find((o) => o.name === c.operationName)!;
    return deriveEndpoint(c, op, ir);
  });
}

function deriveEndpoint(
  classification: OperationClassification,
  op: OperationDecl,
  ir: ServiceIR,
): EndpointSpec {
  const method = resolveMethod(classification, ir.conventions);
  const path = resolvePath(classification, op, ir);
  const successStatus = resolveStatus(classification, ir.conventions, method);

  const pathParamNames = extractPathParamNames(path);
  const pathParams: ParamSpec[] = [];
  const otherParams: ParamSpec[] = [];

  for (const input of op.inputs) {
    if (pathParamNames.has(input.name)) {
      pathParams.push({
        name: input.name,
        typeExpr: input.typeExpr,
        required: true,
      });
    } else {
      otherParams.push({
        name: input.name,
        typeExpr: input.typeExpr,
        required: input.typeExpr.kind !== "OptionType",
      });
    }
  }

  const isGet = method === "GET";
  return {
    operationName: classification.operationName,
    method,
    path,
    pathParams,
    queryParams: isGet ? otherParams : [],
    bodyParams: isGet ? [] : otherParams,
    successStatus,
  };
}

const VALID_METHODS = new Set<HttpMethod>(["GET", "POST", "PUT", "PATCH", "DELETE"]);

function resolveMethod(
  classification: OperationClassification,
  conventions: ConventionsDecl | null,
): HttpMethod {
  const override = getConvention(conventions, classification.operationName, "http_method");
  if (override && VALID_METHODS.has(override as HttpMethod)) {
    return override as HttpMethod;
  }
  return classification.method;
}

function resolvePath(
  classification: OperationClassification,
  op: OperationDecl,
  ir: ServiceIR,
): string {
  const override = getConvention(ir.conventions, op.name, "http_path");
  if (override) return override;

  return autoDerivePath(classification, op, ir);
}

function autoDerivePath(
  classification: OperationClassification,
  op: OperationDecl,
  ir: ServiceIR,
): string {
  const entity = classification.targetEntity;
  const segment = entity ? toPathSegment(entity) : toKebabCase(op.name);

  switch (classification.kind) {
    case "create":
      return `/${segment}`;

    case "read":
    case "filtered_read": {
      const idParam = findIdParam(op, ir);
      if (idParam) return `/${segment}/{${idParam}}`;
      return `/${segment}`;
    }

    case "replace":
    case "partial_update": {
      const idParam = findIdParam(op, ir);
      if (idParam) return `/${segment}/{${idParam}}`;
      return `/${segment}`;
    }

    case "delete": {
      const idParam = findIdParam(op, ir);
      if (idParam) return `/${segment}/{${idParam}}`;
      return `/${segment}`;
    }

    case "transition": {
      const idParam = findIdParam(op, ir);
      const action = extractActionVerb(op.name, entity);
      if (idParam) return `/${segment}/{${idParam}}/${action}`;
      return `/${segment}/${action}`;
    }

    case "batch_mutation":
      return `/${segment}/batch`;

    case "side_effect":
      return `/${toKebabCase(op.name)}`;

    case "create_child":
      return `/${segment}`;
  }
}

function findIdParam(op: OperationDecl, ir: ServiceIR): string | null {
  if (!ir.state) return null;

  const keyTypeNames = new Set<string>();
  for (const field of ir.state.fields) {
    if (field.typeExpr.kind === "RelationType") {
      const keyName = typeExprName(field.typeExpr.fromType);
      if (keyName) keyTypeNames.add(keyName);
    }
  }

  for (const input of op.inputs) {
    const inputTypeName = typeExprName(input.typeExpr);
    if (inputTypeName && keyTypeNames.has(inputTypeName)) {
      return input.name;
    }
    if (input.typeExpr.kind === "NamedType" && input.typeExpr.name === "Int") {
      if (input.name === "id" || input.name.endsWith("_id")) {
        return input.name;
      }
    }
  }

  return null;
}

function typeExprName(typeExpr: { kind: string; name?: string }): string | null {
  if (typeExpr.kind === "NamedType" && "name" in typeExpr) {
    return typeExpr.name as string;
  }
  return null;
}

function extractActionVerb(opName: string, entityName: string | null): string {
  if (!entityName) return toKebabCase(opName);

  if (opName.endsWith(entityName)) {
    const verb = opName.slice(0, -entityName.length);
    if (verb.length > 0) return toKebabCase(verb);
  }
  if (opName.startsWith(entityName)) {
    const verb = opName.slice(entityName.length);
    if (verb.length > 0) return toKebabCase(verb);
  }

  return toKebabCase(opName);
}

function extractPathParamNames(path: string): Set<string> {
  const result = new Set<string>();
  const regex = /\{(\w+)\}/g;
  let match;
  while ((match = regex.exec(path)) !== null) {
    result.add(match[1]);
  }
  return result;
}

function resolveStatus(
  classification: OperationClassification,
  conventions: ConventionsDecl | null,
  effectiveMethod: HttpMethod,
): number {
  const override = getConvention(conventions, classification.operationName, "http_status_success");
  if (override) return Number(override);

  if (effectiveMethod === "DELETE") return 204;

  switch (classification.kind) {
    case "create":
    case "create_child":
      return 201;
    case "delete":
      return 204;
    default:
      return 200;
  }
}

export function getConvention(
  conventions: ConventionsDecl | null,
  target: string,
  property: string,
): string | null {
  if (!conventions) return null;

  for (const rule of conventions.rules) {
    if (rule.target === target && rule.property === property) {
      return exprToString(rule.value);
    }
  }
  return null;
}

function exprToString(expr: Expr): string | null {
  switch (expr.kind) {
    case "StringLit":
      return expr.value;
    case "IntLit":
      return String(expr.value);
    case "FloatLit":
      return String(expr.value);
    case "BoolLit":
      return String(expr.value);
    default:
      return null;
  }
}
