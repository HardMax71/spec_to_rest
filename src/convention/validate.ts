import type { ServiceIR, ConventionsDecl, ConventionRule } from "#ir/types.js";
import type { ConventionDiagnostic } from "#convention/types.js";
import { VALID_METHODS } from "#convention/path.js";

const OPERATION_PROPERTIES = new Set([
  "http_method",
  "http_path",
  "http_status_success",
  "http_header",
]);

const ENTITY_PROPERTIES = new Set([
  "db_table",
  "db_timestamps",
  "plural",
]);

export function validateConventions(
  conventions: ConventionsDecl | null,
  ir: ServiceIR,
): readonly ConventionDiagnostic[] {
  if (!conventions) return [];

  const opNames = new Set(ir.operations.map((o) => o.name));
  const entityNames = new Set(ir.entities.map((e) => e.name));
  const diagnostics: ConventionDiagnostic[] = [];
  const seen = new Map<string, ConventionRule>();

  for (const rule of conventions.rules) {
    const key = rule.qualifier
      ? `${rule.target}.${rule.property}:${rule.qualifier}`
      : `${rule.target}.${rule.property}`;

    const existing = seen.get(key);
    if (existing) {
      const loc = existing.span
        ? ` (first defined at ${existing.span.startLine}:${existing.span.startCol})`
        : "";
      diagnostics.push({
        level: "error",
        message: `duplicate override for ${key}${loc}`,
        span: rule.span,
        target: rule.target,
        property: rule.property,
      });
    } else {
      seen.set(key, rule);
    }

    const targetKind = opNames.has(rule.target)
      ? "operation"
      : entityNames.has(rule.target)
        ? "entity"
        : null;

    if (!targetKind) {
      diagnostics.push({
        level: "error",
        message: `no operation or entity named '${rule.target}'`,
        span: rule.span,
        target: rule.target,
        property: rule.property,
      });
      continue;
    }

    if (targetKind === "operation" && ENTITY_PROPERTIES.has(rule.property)) {
      diagnostics.push({
        level: "error",
        message: `property '${rule.property}' is not valid for operation '${rule.target}'; it applies to entities`,
        span: rule.span,
        target: rule.target,
        property: rule.property,
      });
      continue;
    }

    if (targetKind === "entity" && OPERATION_PROPERTIES.has(rule.property)) {
      diagnostics.push({
        level: "error",
        message: `property '${rule.property}' is not valid for entity '${rule.target}'; it applies to operations`,
        span: rule.span,
        target: rule.target,
        property: rule.property,
      });
      continue;
    }

    if (!OPERATION_PROPERTIES.has(rule.property) && !ENTITY_PROPERTIES.has(rule.property)) {
      diagnostics.push({
        level: "error",
        message: `unknown convention property '${rule.property}'`,
        span: rule.span,
        target: rule.target,
        property: rule.property,
      });
      continue;
    }

    validateValue(rule, diagnostics);
  }

  return diagnostics;
}

function validateValue(rule: ConventionRule, diagnostics: ConventionDiagnostic[]): void {
  switch (rule.property) {
    case "http_method":
      validateHttpMethod(rule, diagnostics);
      break;
    case "http_status_success":
      validateHttpStatus(rule, diagnostics);
      break;
    case "http_path":
      validateHttpPath(rule, diagnostics);
      break;
    case "http_header":
      validateHttpHeader(rule, diagnostics);
      break;
    case "db_table":
      validateDbTable(rule, diagnostics);
      break;
    case "db_timestamps":
      validateDbTimestamps(rule, diagnostics);
      break;
    case "plural":
      validatePlural(rule, diagnostics);
      break;
  }
}

function validateHttpMethod(rule: ConventionRule, diagnostics: ConventionDiagnostic[]): void {
  if (rule.value.kind !== "StringLit") {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.http_method — expected a string`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
    return;
  }
  if (!VALID_METHODS.has(rule.value.value as never)) {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.http_method — expected one of GET, POST, PUT, PATCH, DELETE, got "${rule.value.value}"`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
  }
}

function validateHttpStatus(rule: ConventionRule, diagnostics: ConventionDiagnostic[]): void {
  if (rule.value.kind !== "IntLit") {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.http_status_success — expected an integer`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
    return;
  }
  if (rule.value.value < 100 || rule.value.value > 599) {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.http_status_success — expected integer between 100 and 599, got ${rule.value.value}`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
  }
}

function validateHttpPath(rule: ConventionRule, diagnostics: ConventionDiagnostic[]): void {
  if (rule.value.kind !== "StringLit") {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.http_path — expected a string`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
    return;
  }
  if (!rule.value.value.startsWith("/") && !rule.value.value.startsWith("{")) {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.http_path — path must start with '/' or '{'`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
  }
}

function validateHttpHeader(rule: ConventionRule, diagnostics: ConventionDiagnostic[]): void {
  if (!rule.qualifier) {
    diagnostics.push({
      level: "error",
      message: `${rule.target}.http_header requires a header name qualifier (e.g., http_header "Location")`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
    return;
  }
  if (rule.value.kind !== "StringLit" && rule.value.kind !== "FieldAccess" && rule.value.kind !== "Identifier") {
    diagnostics.push({
      level: "warning",
      message: `${rule.target}.http_header "${rule.qualifier}" value is a complex expression (not validated at compile time)`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
  }
}

function validateDbTable(rule: ConventionRule, diagnostics: ConventionDiagnostic[]): void {
  if (rule.value.kind !== "StringLit") {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.db_table — expected a string`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
    return;
  }
  if (rule.value.value.length === 0) {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.db_table — cannot be empty`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
  }
}

function validateDbTimestamps(rule: ConventionRule, diagnostics: ConventionDiagnostic[]): void {
  if (rule.value.kind !== "BoolLit") {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.db_timestamps — expected true or false`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
  }
}

function validatePlural(rule: ConventionRule, diagnostics: ConventionDiagnostic[]): void {
  if (rule.value.kind !== "StringLit") {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.plural — expected a string`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
    return;
  }
  if (rule.value.value.length === 0) {
    diagnostics.push({
      level: "error",
      message: `invalid value for ${rule.target}.plural — cannot be empty`,
      span: rule.span,
      target: rule.target,
      property: rule.property,
    });
  }
}
