import fs from "node:fs";
import path from "node:path";

interface Golden {
  exitCode: number;
  stdout: string;
  stderr: string;
  command: string;
  flags: string;
}

const DOCS_ROOT = process.cwd();
const REPO_ROOT = path.resolve(DOCS_ROOT, "..");
const CACHE_DIR = path.join(DOCS_ROOT, "lib", "cli-runs");
const FIXTURES_DIR = path.join(REPO_ROOT, "fixtures", "spec");
const INDEX_PATH = path.join(CACHE_DIR, "index.json");

let cachedIndex: Record<string, string> | null = null;
function getIndex(): Record<string, string> {
  if (cachedIndex) return cachedIndex;
  if (!fs.existsSync(INDEX_PATH)) {
    cachedIndex = {};
    return cachedIndex;
  }
  cachedIndex = JSON.parse(fs.readFileSync(INDEX_PATH, "utf8"));
  return cachedIndex!;
}

function loadGolden(id: string): Golden {
  const p = path.join(CACHE_DIR, `${id}.json`);
  if (!fs.existsSync(p)) {
    throw new Error(
      `cli-run golden missing: ${id}.json — run \`node docs/scripts/run-cli-snippets.mjs --regen\``,
    );
  }
  return JSON.parse(fs.readFileSync(p, "utf8")) as Golden;
}

function loadFixture(spec: string): string {
  const p = path.join(FIXTURES_DIR, `${spec}.spec`);
  if (!fs.existsSync(p)) {
    throw new Error(`fixture not found: fixtures/spec/${spec}.spec`);
  }
  return fs.readFileSync(p, "utf8");
}

interface PanelProps {
  input: string;
  command: string;
  flags: string;
  specName: string | null;
  output: Golden;
}

function CliRunPanel({ input, command, flags, specName, output }: PanelProps) {
  const argument = specName ? `fixtures/spec/${specName}.spec` : "spec.spec";
  const flagPart = flags ? ` ${flags}` : "";
  const cmdLine = `$ spec-to-rest ${command}${flagPart} ${argument}`;
  const ok = output.exitCode === 0;
  const exitLabel = ok ? "exit 0" : `exit ${output.exitCode}`;
  const exitTone = ok
    ? "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
    : "bg-rose-500/10 text-rose-700 dark:text-rose-300";
  return (
    <div className="cli-run my-6 rounded-lg border border-fd-border bg-fd-card overflow-hidden">
      <div className="border-b border-fd-border bg-fd-muted/40 px-4 py-2 text-xs uppercase tracking-wide text-fd-muted-foreground">
        {specName ? `fixtures/spec/${specName}.spec` : "inline spec"}
      </div>
      <pre className="overflow-x-auto px-4 py-3 text-sm leading-relaxed">
        <code className="language-spec">{input}</code>
      </pre>
      <div className="flex items-center justify-between gap-3 border-y border-fd-border bg-fd-background px-4 py-2 font-mono text-xs">
        <span className="text-fd-muted-foreground">{cmdLine}</span>
        <span className={`rounded px-2 py-0.5 font-semibold ${exitTone}`}>{exitLabel}</span>
      </div>
      {output.stdout.trim() && (
        <pre className="overflow-x-auto bg-fd-background px-4 py-3 text-sm leading-relaxed">
          <code>{output.stdout}</code>
        </pre>
      )}
      {output.stderr.trim() && (
        <pre
          className={`overflow-x-auto px-4 py-3 text-sm leading-relaxed ${
            output.stdout.trim() ? "border-t border-fd-border" : ""
          } ${
            ok
              ? "bg-fd-background text-fd-foreground"
              : "bg-rose-500/5 text-rose-700 dark:text-rose-300"
          }`}
        >
          <code>{output.stderr}</code>
        </pre>
      )}
    </div>
  );
}

export interface CliRunProps {
  spec: string;
  command: string;
  flags?: string;
  expectExit?: string | number;
}

export function CliRun({ spec, command, flags = "" }: CliRunProps) {
  const idx = getIndex();
  const key = `external|${spec}|${command}|${flags}`;
  const id = idx[key];
  if (!id) {
    throw new Error(
      `no golden indexed for ${key}; run \`node docs/scripts/run-cli-snippets.mjs --regen\``,
    );
  }
  const golden = loadGolden(id);
  const input = loadFixture(spec);
  return (
    <CliRunPanel
      input={input}
      command={command}
      flags={flags}
      specName={spec}
      output={golden}
    />
  );
}

export interface CliRunInlineProps {
  id: string;
  command: string;
  flags?: string;
  spec: string;
}

export function CliRunInline({ id, command, flags = "", spec }: CliRunInlineProps) {
  const golden = loadGolden(id);
  return (
    <CliRunPanel
      input={spec}
      command={command}
      flags={flags}
      specName={null}
      output={golden}
    />
  );
}
