import railroad from "railroad-diagrams";
const {
  Diagram,
  Sequence,
  Choice,
  Optional,
  ZeroOrMore,
  OneOrMore,
  Terminal,
  NonTerminal,
} = railroad;
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.resolve(here, "..", "public", "grammar");
fs.mkdirSync(outDir, { recursive: true });

const diagrams = {
  "expr-precedence": Diagram(
    Choice(
      0,
      NonTerminal("postfix (', ., [], ())"),
      NonTerminal("with-update (with { ... })"),
      NonTerminal("unary prefix (#, -, ^)"),
      NonTerminal("multiplicative (*, /)"),
      NonTerminal("additive (+, -, ++, --, &)"),
      NonTerminal("relational (<, <=, >, >=, in, not in, subset, matches)"),
      NonTerminal("equality (=, !=)"),
      NonTerminal("logical not"),
      NonTerminal("logical and"),
      NonTerminal("logical or"),
      NonTerminal("implication (=>, <=>)"),
    ),
  ),
  "operation-decl": Diagram(
    Terminal("operation"),
    NonTerminal("Name"),
    Terminal("("),
    Optional(
      Sequence(
        NonTerminal("Param"),
        ZeroOrMore(Sequence(Terminal(","), NonTerminal("Param"))),
      ),
    ),
    Terminal(")"),
    Optional(Sequence(Terminal("->"), NonTerminal("ReturnType"))),
    Terminal("{"),
    ZeroOrMore(
      Choice(
        0,
        Sequence(Terminal("requires"), NonTerminal("Expr")),
        Sequence(Terminal("ensures"), NonTerminal("Expr")),
        Sequence(Terminal("modifies"), NonTerminal("FieldRef")),
      ),
    ),
    Terminal("}"),
  ),
  "service-toplevel": Diagram(
    Terminal("service"),
    NonTerminal("Name"),
    Terminal("{"),
    OneOrMore(
      Choice(
        0,
        NonTerminal("entity"),
        NonTerminal("enum"),
        NonTerminal("operation"),
        NonTerminal("invariant"),
        NonTerminal("state"),
      ),
    ),
    Terminal("}"),
  ),
};

const STYLE = `
<defs><style type="text/css">
svg.railroad-diagram { background-color: transparent; }
svg.railroad-diagram path { stroke-width: 2; stroke: currentColor; fill: none; }
svg.railroad-diagram text { font: 12px monospace; text-anchor: middle; fill: currentColor; }
svg.railroad-diagram text.diagram-text { font-size: 11px; }
svg.railroad-diagram rect { stroke-width: 2; stroke: currentColor; fill: none; }
svg.railroad-diagram rect.group-box { stroke: currentColor; stroke-dasharray: 10 5; fill: none; }
</style></defs>
`.trim();

for (const [name, diagram] of Object.entries(diagrams)) {
  const raw = diagram.toString();
  const svg = raw.replace(/<svg([^>]*)>/, `<svg$1>\n${STYLE}`);
  const out = path.join(outDir, `${name}.svg`);
  fs.writeFileSync(out, svg, "utf8");
  console.log(`wrote ${path.relative(process.cwd(), out)}`);
}
