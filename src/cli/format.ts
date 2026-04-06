import type { ServiceIR } from "../ir/index.js";
import { serializeIR } from "../ir/index.js";

export type Format = "summary" | "json" | "ir";

export function formatIR(ir: ServiceIR, format: Format): string {
  switch (format) {
    case "json":
    case "ir":
      return serializeIR(ir);
    case "summary":
      return formatSummary(ir);
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
    ir.conventions ? ["rules"] : [],
  );

  return lines.join("\n");
}
