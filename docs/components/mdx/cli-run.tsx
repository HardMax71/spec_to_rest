import fs from "node:fs";
import path from "node:path";
import { createHighlighter } from "shiki";
import { specGrammar } from "@/lib/grammars";

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

let highlighterPromise: ReturnType<typeof createHighlighter> | null = null;
function getHighlighter() {
  if (!highlighterPromise) {
    highlighterPromise = createHighlighter({
      themes: ["github-light", "github-dark"],
      langs: [specGrammar, "text"],
    });
  }
  return highlighterPromise;
}

async function highlight(code: string, lang: "spec" | "text"): Promise<string> {
  const hl = await getHighlighter();
  return hl.codeToHtml(code, {
    lang,
    themes: { light: "github-light", dark: "github-dark" },
    defaultColor: false,
  });
}

interface PanelProps {
  inputHtml: string;
  outputHtml: string;
  command: string;
  flags: string;
  specName: string | null;
  exitCode: number;
}

type Severity = "ok" | "warn" | "error";

function severityFor(exitCode: number): Severity {
  if (exitCode === 0) return "ok";
  if (exitCode === 2 || exitCode === 4) return "warn";
  return "error";
}

function CliRunPanel({
  inputHtml,
  outputHtml,
  command,
  flags,
  specName,
  exitCode,
}: PanelProps) {
  const argument = specName ? `fixtures/spec/${specName}.spec` : "spec.spec";
  const flagPart = flags ? ` ${flags}` : "";
  const cmdLine = `$ spec-to-rest ${command} ${argument}${flagPart}`;
  const severity = severityFor(exitCode);
  const exitLabel = severity === "ok" ? "exit 0" : `exit ${exitCode}`;
  const exitTone =
    severity === "ok"
      ? "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
      : severity === "warn"
        ? "bg-amber-500/10 text-amber-700 dark:text-amber-300"
        : "bg-rose-500/10 text-rose-700 dark:text-rose-300";
  const outputTone =
    severity === "ok"
      ? ""
      : severity === "warn"
        ? "[&_pre]:bg-amber-500/5!"
        : "[&_pre]:bg-rose-500/5!";
  return (
    <div className="not-prose my-6 rounded-lg border bg-fd-card overflow-hidden text-sm [&_pre]:my-0! [&_pre]:rounded-none! [&_pre]:border-0! [&_pre]:bg-transparent! [&_pre]:whitespace-pre!">
      <div className="border-b bg-fd-muted/40 px-4 py-2 text-xs uppercase tracking-wide text-fd-muted-foreground">
        {specName ? `fixtures/spec/${specName}.spec` : "inline spec"}
      </div>
      <div
        className="cli-run-pane overflow-x-auto py-3"
        dangerouslySetInnerHTML={{ __html: inputHtml }}
      />
      <div className="flex items-center justify-between gap-3 border-y bg-fd-background px-4 py-2 font-mono text-xs">
        <span className="text-fd-muted-foreground">{cmdLine}</span>
        <span className={`rounded px-2 py-0.5 font-semibold ${exitTone}`}>{exitLabel}</span>
      </div>
      <div
        className={`cli-run-pane overflow-x-auto py-3 ${outputTone}`}
        dangerouslySetInnerHTML={{ __html: outputHtml }}
      />
    </div>
  );
}

function combine(stdout: string, stderr: string): string {
  const out = stdout.trim();
  const err = stderr.trim();
  if (!out && !err) return "(no output)";
  if (!out) return stderr.trimEnd();
  if (!err) return stdout.trimEnd();
  return `${stdout.trimEnd()}\n${stderr.trimEnd()}`;
}

export interface CliRunProps {
  spec: string;
  command: string;
  flags?: string;
  expectExit?: string | number;
}

export async function CliRun({ spec, command, flags = "", expectExit }: CliRunProps) {
  const idx = getIndex();
  const key = `external|${spec}|${command}|${flags}`;
  const id = idx[key];
  if (!id) {
    throw new Error(
      `no golden indexed for ${key}; run \`node docs/scripts/run-cli-snippets.mjs --regen\``,
    );
  }
  const golden = loadGolden(id);
  if (expectExit !== undefined) {
    const want = Number(expectExit);
    if (!Number.isFinite(want)) {
      throw new Error(`CliRun: expectExit must be numeric, got ${String(expectExit)}`);
    }
    if (golden.exitCode !== want) {
      throw new Error(
        `CliRun ${key}: golden exit ${golden.exitCode} differs from expectExit=${want}; ` +
          `regen the golden or correct expectExit on the MDX tag`,
      );
    }
  }
  const input = loadFixture(spec);
  const [inputHtml, outputHtml] = await Promise.all([
    highlight(input.trimEnd(), "spec"),
    highlight(combine(golden.stdout, golden.stderr), "text"),
  ]);
  return (
    <CliRunPanel
      inputHtml={inputHtml}
      outputHtml={outputHtml}
      command={command}
      flags={flags}
      specName={spec}
      exitCode={golden.exitCode}
    />
  );
}

export interface CliRunInlineProps {
  id: string;
  command: string;
  flags?: string;
  spec: string;
}

export async function CliRunInline({ id, command, flags = "", spec }: CliRunInlineProps) {
  const golden = loadGolden(id);
  const [inputHtml, outputHtml] = await Promise.all([
    highlight(spec.trimEnd(), "spec"),
    highlight(combine(golden.stdout, golden.stderr), "text"),
  ]);
  return (
    <CliRunPanel
      inputHtml={inputHtml}
      outputHtml={outputHtml}
      command={command}
      flags={flags}
      specName={null}
      exitCode={golden.exitCode}
    />
  );
}
