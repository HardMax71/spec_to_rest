import type { Z3Script, Z3Expr, Z3Sort, Z3FunctionDecl } from "#verify/script.js";

export function renderSmtLib(script: Z3Script, timeoutMs?: number): string {
  const lines: string[] = [];
  lines.push("(set-logic ALL)");
  lines.push("(set-option :produce-models true)");
  if (timeoutMs !== undefined && timeoutMs > 0) {
    lines.push(`(set-option :timeout ${timeoutMs})`);
  }

  if (script.sorts.length > 0) lines.push(";; sorts");
  for (const s of script.sorts) {
    if (s.kind === "Uninterp") lines.push(`(declare-sort ${s.name} 0)`);
  }

  if (script.funcs.length > 0) lines.push(";; funcs");
  for (const f of script.funcs) lines.push(renderFuncDecl(f));

  if (script.assertions.length > 0) lines.push(";; assertions");
  for (const a of script.assertions) lines.push(`(assert ${renderExpr(a)})`);

  lines.push("(check-sat)");
  return lines.join("\n") + "\n";
}

function renderSort(s: Z3Sort): string {
  if (s.kind === "Int") return "Int";
  if (s.kind === "Bool") return "Bool";
  return s.name;
}

function renderFuncDecl(f: Z3FunctionDecl): string {
  const args = f.argSorts.map(renderSort).join(" ");
  return `(declare-fun ${f.name} (${args}) ${renderSort(f.resultSort)})`;
}

export function renderExpr(e: Z3Expr): string {
  switch (e.kind) {
    case "Var":
      return e.name;
    case "App":
      if (e.args.length === 0) return e.func;
      return `(${e.func} ${e.args.map(renderExpr).join(" ")})`;
    case "IntLit":
      return e.value < 0 ? `(- ${-e.value})` : `${e.value}`;
    case "BoolLit":
      return e.value ? "true" : "false";
    case "And":
      if (e.args.length === 0) return "true";
      if (e.args.length === 1) return renderExpr(e.args[0]);
      return `(and ${e.args.map(renderExpr).join(" ")})`;
    case "Or":
      if (e.args.length === 0) return "false";
      if (e.args.length === 1) return renderExpr(e.args[0]);
      return `(or ${e.args.map(renderExpr).join(" ")})`;
    case "Not":
      return `(not ${renderExpr(e.arg)})`;
    case "Implies":
      return `(=> ${renderExpr(e.lhs)} ${renderExpr(e.rhs)})`;
    case "Cmp": {
      const op = e.op === "!=" ? "distinct" : e.op;
      return `(${op} ${renderExpr(e.lhs)} ${renderExpr(e.rhs)})`;
    }
    case "Arith":
      return `(${e.op} ${e.args.map(renderExpr).join(" ")})`;
    case "Quantifier": {
      const q = e.q === "ForAll" ? "forall" : "exists";
      const binders = e.bindings.map((b) => `(${b.name} ${renderSort(b.sort)})`).join(" ");
      return `(${q} (${binders}) ${renderExpr(e.body)})`;
    }
  }
}
