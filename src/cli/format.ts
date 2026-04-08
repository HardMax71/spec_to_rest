import type { ServiceIR } from "#ir/index.js";
import { serializeIR } from "#ir/index.js";
import { classifyOperations } from "#convention/classify.js";
import { deriveEndpoints, getConvention } from "#convention/path.js";

export type Format = "summary" | "json" | "ir" | "endpoints";

export function formatIR(ir: ServiceIR, format: Format): string {
  switch (format) {
    case "json":
    case "ir":
      return serializeIR(ir);
    case "summary":
      return formatSummary(ir);
    case "endpoints":
      return formatEndpoints(ir);
    default: {
      const _exhaustive: never = format;
      throw new Error(`Unsupported format: ${String(_exhaustive)}`);
    }
  }
}

export function formatSummary(ir: ServiceIR): string {
  const lines: string[] = [`Service: ${ir.name}`];

  const push = (label: string, count: number, names?: readonly string[]) => {
    const pad = label.padEnd(13);
    const detail = names?.length ? `  ${names.join(", ")}` : "";
    lines.push(`  ${pad}${count}${detail}`);
  };

  push(
    "Types:",
    ir.typeAliases.length,
    ir.typeAliases.map((t) => t.name),
  );
  push(
    "Entities:",
    ir.entities.length,
    ir.entities.map(
      (e) =>
        `${e.name} (${e.fields.length} fields, ${e.invariants.length} invariants)`,
    ),
  );
  push(
    "Enums:",
    ir.enums.length,
    ir.enums.map((e) => e.name),
  );
  push("State:", ir.state?.fields.length ?? 0, ir.state ? ["fields"] : []);
  push(
    "Operations:",
    ir.operations.length,
    ir.operations.map(
      (o) => `${o.name} (${o.inputs.length}\u2192${o.outputs.length})`,
    ),
  );
  push("Transitions:", ir.transitions.length, ir.transitions.map((t) => t.name));
  push(
    "Invariants:",
    ir.invariants.length,
    ir.invariants.filter((i) => i.name).map((i) => i.name!),
  );
  push("Facts:", ir.facts.length, ir.facts.filter((f) => f.name).map((f) => f.name!));
  push(
    "Functions:",
    ir.functions.length,
    ir.functions.map((f) => f.name),
  );
  push(
    "Predicates:",
    ir.predicates.length,
    ir.predicates.map((p) => p.name),
  );
  push(
    "Conventions:",
    ir.conventions?.rules.length ?? 0,
  );
  if (ir.conventions) {
    for (const rule of ir.conventions.rules) {
      const qual = rule.qualifier ? ` "${rule.qualifier}"` : "";
      const val = formatConventionValue(rule.value);
      lines.push(`    ${rule.target}.${rule.property}${qual} = ${val}`);
    }
  }

  return lines.join("\n");
}

function formatConventionValue(expr: { kind: string; value?: unknown }): string {
  switch (expr.kind) {
    case "StringLit":
      return `"${expr.value}"`;
    case "IntLit":
    case "FloatLit":
    case "BoolLit":
      return String(expr.value);
    default:
      return "<expr>";
  }
}

export function formatEndpoints(ir: ServiceIR): string {
  const classifications = classifyOperations(ir);
  const endpoints = deriveEndpoints(classifications, ir);
  const lines: string[] = [`Service: ${ir.name}`, "", "Endpoints:"];

  const maxName = Math.max(0, ...endpoints.map((e) => e.operationName.length));
  const maxMethod = Math.max(0, ...endpoints.map((e) => e.method.length));
  const maxPath = Math.max(0, ...endpoints.map((e) => e.path.length));

  for (const ep of endpoints) {
    const methodSrc = getConvention(ir.conventions, ep.operationName, "http_method") ? "override" : "auto";
    const pathSrc = getConvention(ir.conventions, ep.operationName, "http_path") ? "override" : "auto";
    const statusSrc = getConvention(ir.conventions, ep.operationName, "http_status_success") ? "override" : "auto";

    const name = ep.operationName.padEnd(maxName);
    const method = ep.method.padEnd(maxMethod);
    const path = ep.path.padEnd(maxPath);

    lines.push(
      `  ${name}  ${method}  ${path}  ${ep.successStatus}  [method: ${methodSrc}, path: ${pathSrc}, status: ${statusSrc}]`,
    );
  }

  return lines.join("\n");
}
