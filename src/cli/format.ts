import type { ServiceIR } from "#ir/index.js";
import { serializeIR } from "#ir/index.js";
import { classifyOperations } from "#convention/classify.js";
import { deriveEndpoints, getConvention } from "#convention/path.js";
import { buildProfiledService } from "#profile/annotate.js";

export type Format = "summary" | "json" | "ir" | "endpoints" | "profile";

export function formatIR(ir: ServiceIR, format: Format, target?: string): string {
  switch (format) {
    case "json":
    case "ir":
      return serializeIR(ir);
    case "summary":
      return formatSummary(ir);
    case "endpoints":
      return formatEndpoints(ir);
    case "profile":
      return formatProfile(ir, target ?? "python-fastapi-postgres");
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

export function formatProfile(ir: ServiceIR, profileName: string): string {
  const profiled = buildProfiledService(ir, profileName);
  const p = profiled.profile;
  const lines: string[] = [
    `Service: ${ir.name}`,
    `Target:  ${p.name} (${p.displayName})`,
    "",
    "Stack:",
    `  Language:        ${p.language}`,
    `  Framework:       ${p.framework}`,
    `  ORM:             ${p.orm}${p.async ? " (async)" : ""}`,
    `  Migrations:      ${p.migrationTool}`,
    `  Validation:      ${p.validation}`,
    `  Package Manager: ${p.packageManager}`,
    `  DB Driver:       ${p.dbDriver}`,
  ];

  if (profiled.entities.length > 0) {
    lines.push("", "Entities:");
    for (const entity of profiled.entities) {
      lines.push(`  ${entity.entityName}`);
      lines.push(`    Model:   ${p.modelDir}/${entity.modelFileName}  -> class ${entity.modelClassName}(Base)`);
      lines.push(
        `    Schemas: ${p.schemaDir}/${entity.schemaFileName} -> ${entity.createSchemaName}, ${entity.readSchemaName}, ${entity.updateSchemaName}`,
      );
      lines.push(`    Router:  ${p.routerDir}/${entity.routerFileName}`);

      if (entity.fields.length > 0) {
        lines.push("    Fields:");
        const maxName = Math.max(0, ...entity.fields.map((f) => f.fieldName.length));
        const maxPy = Math.max(0, ...entity.fields.map((f) => f.pythonType.length));
        const maxSa = Math.max(0, ...entity.fields.map((f) => f.sqlalchemyType.length));
        const maxCol = Math.max(0, ...entity.fields.map((f) => f.sqlalchemyColumnType.length));

        for (const f of entity.fields) {
          const name = f.fieldName.padEnd(maxName);
          const py = f.pythonType.padEnd(maxPy);
          const sa = f.sqlalchemyType.padEnd(maxSa);
          const col = f.sqlalchemyColumnType.padEnd(maxCol);
          const null_ = f.nullable ? "NULL" : "NOT NULL";
          lines.push(`      ${name}  ${py}  ${sa}  ${col}  ${null_}`);
        }
      }
    }
  }

  if (profiled.operations.length > 0) {
    lines.push("", "Endpoints:");
    const maxName = Math.max(0, ...profiled.operations.map((o) => o.operationName.length));
    const maxMethod = Math.max(0, ...profiled.operations.map((o) => o.endpoint.method.length));
    const maxPath = Math.max(0, ...profiled.operations.map((o) => o.endpoint.path.length));

    for (const op of profiled.operations) {
      const name = op.operationName.padEnd(maxName);
      const method = op.endpoint.method.padEnd(maxMethod);
      const path = op.endpoint.path.padEnd(maxPath);
      const asyncPrefix = p.async ? "async def" : "def";
      lines.push(`  ${name}  ${method}  ${path}  ${op.endpoint.successStatus}  -> ${asyncPrefix} ${op.handlerName}(...)`);
    }
  }

  const deps = p.dependencies.map((d) => `${d.name}${d.version}`).join(", ");
  lines.push("", `Dependencies (pyproject.toml via ${p.packageManager}):`);
  lines.push(`  ${deps}`);

  return lines.join("\n");
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
