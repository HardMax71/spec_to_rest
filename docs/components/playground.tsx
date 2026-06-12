"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Highlighter } from "shiki";
import { PLAYGROUND_EXAMPLES } from "@/lib/playground-examples.generated";
import { makeZip } from "@/lib/zip";

type Target =
  | "check"
  | "summary"
  | "ir"
  | "dafny"
  | "verify"
  | "compile"
  | "synth";

const TARGETS: { value: Target; label: string; description: string }[] = [
  { value: "check", label: "Check", description: "Parse + lint" },
  { value: "summary", label: "Summary", description: "Operations + classifications" },
  { value: "ir", label: "IR", description: "Internal representation" },
  { value: "dafny", label: "Dafny", description: "Verification kernel" },
  { value: "verify", label: "Verify", description: "Alloy / Z3 model check" },
  { value: "compile", label: "Compile", description: "Emit a service project" },
  { value: "synth", label: "Synth", description: "LLM CEGIS (BYO API key)" },
];

// Pretty display labels are still hardcoded - there's no "human name for a
// framework ID" anywhere in the Scala source. If a fourth framework arrives,
// add a row here OR fall back to the bare ID via FRAMEWORK_LABEL_FALLBACK.
const FRAMEWORK_DISPLAY: Record<string, string> = {
  chi: "chi (Go)",
  express: "express (TypeScript)",
  fastapi: "fastapi (Python)",
};

interface Targets {
  frameworks: string[];
  dbs: string[];
  languages: string[];
}

const TARGETS_FALLBACK: Targets = {
  frameworks: ["chi", "express", "fastapi"],
  dbs: ["mysql", "postgres", "sqlite"],
  languages: ["go", "python", "ts"],
};

const MODELS = [
  { value: "gpt-5-mini", label: "gpt-5-mini (OpenAI)" },
  { value: "gpt-5", label: "gpt-5 (OpenAI)" },
  { value: "gpt-4.1", label: "gpt-4.1 (OpenAI)" },
  { value: "claude-sonnet-4-6", label: "claude-sonnet-4-6 (Anthropic)" },
  { value: "claude-haiku-4-5", label: "claude-haiku-4-5 (Anthropic)" },
];

const DEFAULT_SPEC =
  PLAYGROUND_EXAMPLES[0]?.spec ?? "service Empty { state { count: Int } }\n";

interface FileEntry {
  path: string;
  content: string;
  truncated: boolean;
}

type ApiResponse =
  | {
      ok: true;
      target: Target;
      stdout: string;
      stderr: string;
      files?: FileEntry[];
      totalFiles?: number;
      totalBytes?: number;
    }
  | { ok: false; target?: Target; stdout: string; stderr: string; error: string };

type OutputTab = "stdout" | "stderr" | "files";

type RunState =
  | { kind: "idle" }
  | { kind: "loading" }
  | {
      kind: "ok";
      target: Target;
      stdout: string;
      stderr: string;
      files?: FileEntry[];
      totalFiles?: number;
      totalBytes?: number;
      elapsedMs: number;
    }
  | {
      kind: "error";
      message: string;
      stderr?: string;
      elapsedMs: number;
    };

interface CompileOpts {
  framework: string;
  db: string;
}

interface SynthOpts {
  operation: string;
  model: string;
  apiKey: string;
}

export function Playground() {
  const [spec, setSpec] = useState<string>(DEFAULT_SPEC);
  const [target, setTarget] = useState<Target>("ir");
  const [exampleName, setExampleName] = useState<string>(PLAYGROUND_EXAMPLES[0]?.name ?? "");
  const [state, setState] = useState<RunState>({ kind: "idle" });
  const [tab, setTab] = useState<OutputTab>("stdout");
  const [compileOpts, setCompileOpts] = useState<CompileOpts>({
    framework: "fastapi",
    db: "sqlite",
  });
  const [synthOpts, setSynthOpts] = useState<SynthOpts>({
    operation: "",
    model: "gpt-5-mini",
    apiKey: "",
  });
  const [selectedFile, setSelectedFile] = useState<string>("");
  const [targets, setTargets] = useState<Targets>(TARGETS_FALLBACK);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch("/api/meta")
      .then((r) => r.json() as Promise<Targets>)
      .then((t) => {
        if (cancelled) return;
        setTargets(t);
        setCompileOpts((co) => ({
          framework: t.frameworks.includes(co.framework)
            ? co.framework
            : (t.frameworks[0] ?? co.framework),
          db: t.dbs.includes(co.db) ? co.db : (t.dbs[0] ?? co.db),
        }));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  const submit = useCallback(async () => {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setState({ kind: "loading" });
    setTab("stdout");
    setSelectedFile("");
    const t0 = performance.now();
    const reqBody: Record<string, unknown> = { spec, target };
    if (target === "compile") reqBody.compile = compileOpts;
    if (target === "synth") {
      reqBody.synth = { operation: synthOpts.operation, model: synthOpts.model };
    }
    const headers: Record<string, string> = { "content-type": "application/json" };
    if (target === "synth" && synthOpts.apiKey) {
      headers["x-llm-api-key"] = synthOpts.apiKey;
    }
    try {
      const res = await fetch("/api/compile", {
        method: "POST",
        headers,
        body: JSON.stringify(reqBody),
        signal: ctrl.signal,
      });
      const data = (await res.json()) as ApiResponse;
      const elapsedMs = Math.round(performance.now() - t0);
      if (data.ok) {
        setState({
          kind: "ok",
          target: data.target,
          stdout: data.stdout,
          stderr: data.stderr,
          files: data.files,
          totalFiles: data.totalFiles,
          totalBytes: data.totalBytes,
          elapsedMs,
        });
        if (data.files && data.files.length > 0) {
          setSelectedFile(data.files[0].path);
          setTab("files");
        }
      } else {
        setState({
          kind: "error",
          message: data.error || `Backend returned ${res.status}`,
          stderr: data.stderr,
          elapsedMs,
        });
        if (data.stderr) setTab("stderr");
      }
    } catch (err) {
      if ((err as Error).name === "AbortError") return;
      setState({
        kind: "error",
        message: err instanceof Error ? err.message : "network error",
        elapsedMs: Math.round(performance.now() - t0),
      });
    }
  }, [spec, target, compileOpts, synthOpts]);

  useEffect(() => () => abortRef.current?.abort(), []);

  const showCompileOpts = target === "compile";
  const showSynthOpts = target === "synth";
  const hasFiles = state.kind === "ok" && state.files && state.files.length > 0;

  return (
    <div className="not-prose flex flex-col gap-3 my-4">
      <Toolbar
        target={target}
        onTarget={setTarget}
        exampleName={exampleName}
        onExample={(name, src) => {
          setExampleName(name);
          setSpec(src);
        }}
        onSubmit={submit}
        running={state.kind === "loading"}
      />
      {showCompileOpts && (
        <CompileOptsRow opts={compileOpts} onChange={setCompileOpts} targets={targets} />
      )}
      {showSynthOpts && <SynthOptsRow opts={synthOpts} onChange={setSynthOpts} />}
      <Split
        left={<SpecEditor spec={spec} onChange={setSpec} onSubmit={submit} />}
        right={
          <OutputPane
            state={state}
            tab={tab}
            onTab={setTab}
            hasFiles={!!hasFiles}
            selectedFile={selectedFile}
            onSelectFile={setSelectedFile}
          />
        }
      />
      <StatusLine state={state} target={target} />
    </div>
  );
}

let specHighlighter: Promise<Highlighter> | null = null;

function getSpecHighlighter(): Promise<Highlighter> {
  if (!specHighlighter) {
    specHighlighter = (async () => {
      const { createHighlighter, createJavaScriptRegexEngine } = await import("shiki");
      const { specGrammar } = await import("@/lib/grammars");
      return createHighlighter({
        engine: createJavaScriptRegexEngine(),
        langs: [specGrammar],
        themes: ["github-light", "github-dark"],
      });
    })();
  }
  return specHighlighter;
}

async function highlightSpecHtml(code: string): Promise<string> {
  const hl = await getSpecHighlighter();
  return hl.codeToHtml(code, {
    lang: "spec",
    themes: { light: "github-light", dark: "github-dark" },
    defaultColor: false,
  });
}

function Split(props: { left: React.ReactNode; right: React.ReactNode }) {
  const [pct, setPct] = useState(50);
  const ref = useRef<HTMLDivElement>(null);
  const dragging = useRef(false);
  useEffect(() => {
    const move = (e: PointerEvent) => {
      if (!dragging.current || !ref.current) return;
      const rect = ref.current.getBoundingClientRect();
      const p = ((e.clientX - rect.left) / rect.width) * 100;
      setPct(Math.min(75, Math.max(25, p)));
    };
    const up = () => {
      dragging.current = false;
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    return () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
  }, []);
  return (
    <div
      ref={ref}
      style={{ "--lp": `${pct}%` } as React.CSSProperties}
      className="flex flex-col gap-3 md:h-[480px] md:flex-row md:gap-0"
    >
      <div className="h-[360px] min-h-0 min-w-0 md:h-full md:shrink-0 md:grow-0 md:basis-[var(--lp)]">
        {props.left}
      </div>
      <div
        role="separator"
        aria-orientation="vertical"
        onPointerDown={(e) => {
          e.preventDefault();
          dragging.current = true;
          document.body.style.cursor = "col-resize";
          document.body.style.userSelect = "none";
        }}
        className="group hidden shrink-0 cursor-col-resize touch-none items-center justify-center px-1.5 md:flex"
      >
        <div className="h-12 w-1 rounded-full bg-fd-border transition group-hover:bg-fd-primary" />
      </div>
      <div className="h-[360px] min-h-0 min-w-0 md:h-full md:flex-1">{props.right}</div>
    </div>
  );
}

function Toolbar(props: {
  target: Target;
  onTarget: (t: Target) => void;
  exampleName: string;
  onExample: (name: string, spec: string) => void;
  onSubmit: () => void;
  running: boolean;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-md border border-fd-border bg-fd-card p-2 text-sm">
      <Select
        label="Example"
        value={props.exampleName}
        onChange={(v) => {
          const ex = PLAYGROUND_EXAMPLES.find((x) => x.name === v);
          if (ex) props.onExample(ex.name, ex.spec);
        }}
        options={PLAYGROUND_EXAMPLES.map((ex) => ({ value: ex.name, label: ex.name }))}
      />
      <Select
        label="Target"
        value={props.target}
        onChange={(v) => props.onTarget(v as Target)}
        options={TARGETS.map((t) => ({ value: t.value, label: t.label, title: t.description }))}
      />
      <button
        type="button"
        onClick={props.onSubmit}
        disabled={props.running}
        className="ml-auto inline-flex items-center gap-2 rounded-md bg-fd-primary px-3 py-1.5 text-sm font-medium text-fd-primary-foreground transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {props.running ? (
          <>
            <Spinner />
            Running…
          </>
        ) : (
          <>
            Run
            <kbd className="rounded border border-fd-primary-foreground/30 px-1 text-[10px] opacity-70">
              ⌘↵
            </kbd>
          </>
        )}
      </button>
    </div>
  );
}

function CompileOptsRow(props: {
  opts: CompileOpts;
  onChange: (o: CompileOpts) => void;
  targets: Targets;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-md border border-fd-border bg-fd-card/60 p-2 text-sm">
      <span className="text-fd-muted-foreground">compile:</span>
      <Select
        label="Framework"
        value={props.opts.framework}
        onChange={(v) => props.onChange({ ...props.opts, framework: v })}
        options={props.targets.frameworks.map((f) => ({
          value: f,
          label: FRAMEWORK_DISPLAY[f] ?? f,
        }))}
      />
      <Select
        label="DB"
        value={props.opts.db}
        onChange={(v) => props.onChange({ ...props.opts, db: v })}
        options={props.targets.dbs.map((d) => ({ value: d, label: d }))}
      />
    </div>
  );
}

function SynthOptsRow(props: {
  opts: SynthOpts;
  onChange: (o: SynthOpts) => void;
}) {
  return (
    <div className="flex flex-col gap-2 rounded-md border border-fd-border bg-fd-card/60 p-2 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-fd-muted-foreground">synth:</span>
        <label className="inline-flex items-center gap-2">
          <span className="text-fd-muted-foreground">Operation</span>
          <input
            type="text"
            placeholder="Increment"
            value={props.opts.operation}
            onChange={(e) => props.onChange({ ...props.opts, operation: e.target.value })}
            className="w-40 rounded-md border border-fd-border bg-fd-background px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-fd-ring"
          />
        </label>
        <Select
          label="Model"
          value={props.opts.model}
          onChange={(v) => props.onChange({ ...props.opts, model: v })}
          options={MODELS}
        />
      </div>
      <label className="flex items-center gap-2">
        <span className="text-fd-muted-foreground">API key</span>
        <input
          type="password"
          autoComplete="off"
          placeholder="sk-... (forwarded to the model provider, never stored)"
          value={props.opts.apiKey}
          onChange={(e) => props.onChange({ ...props.opts, apiKey: e.target.value })}
          className="flex-1 min-w-0 rounded-md border border-fd-border bg-fd-background px-2 py-1 font-mono text-xs focus:outline-none focus:ring-2 focus:ring-fd-ring"
        />
      </label>
      <p className="text-xs text-fd-muted-foreground">
        Key is sent in the <code>X-LLM-API-Key</code> header for this single request, used to call
        the model provider, then dropped. No server-side persistence or logging. Cost cap: $0.50
        per run, max 4 CEGIS iterations.
      </p>
    </div>
  );
}

function Select(props: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string; title?: string }[];
}) {
  return (
    <label className="inline-flex items-center gap-2">
      <span className="text-fd-muted-foreground">{props.label}</span>
      <select
        className="rounded-md border border-fd-border bg-fd-background px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-fd-ring"
        value={props.value}
        onChange={(e) => props.onChange(e.target.value)}
      >
        {props.options.map((o) => (
          <option key={o.value} value={o.value} title={o.title}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

function Spinner() {
  return (
    <svg className="h-3 w-3 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" opacity="0.25" />
      <path
        d="M22 12a10 10 0 0 0-10-10"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  );
}

function SpecEditor(props: {
  spec: string;
  onChange: (s: string) => void;
  onSubmit: () => void;
}) {
  const taRef = useRef<HTMLTextAreaElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const [html, setHtml] = useState("");
  useEffect(() => {
    let cancelled = false;
    highlightSpecHtml(props.spec)
      .then((h) => {
        if (!cancelled) setHtml(h);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [props.spec]);
  const syncScroll = () => {
    if (taRef.current && overlayRef.current) {
      overlayRef.current.scrollTop = taRef.current.scrollTop;
      overlayRef.current.scrollLeft = taRef.current.scrollLeft;
    }
  };
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-md border border-fd-border bg-fd-card">
      <div className="flex h-9 items-center border-b border-fd-border bg-fd-secondary/30 px-3 text-xs font-medium text-fd-muted-foreground">
        spec
      </div>
      <div className="relative flex-1 overflow-hidden bg-fd-background">
        {/* not-fumadocs-codeblock: fumadocs' shiki.css pads every .line span,
            which would shift the highlighted overlay right of the transparent
            textarea's real glyphs and misalign selection. */}
        <div
          ref={overlayRef}
          aria-hidden="true"
          className="not-fumadocs-codeblock pointer-events-none absolute inset-0 overflow-auto p-3 font-mono text-[13px] leading-relaxed text-fd-foreground [&_code]:font-mono [&_pre]:m-0 [&_pre]:!bg-transparent [&_pre]:p-0"
        >
          {html ? (
            <div dangerouslySetInnerHTML={{ __html: html }} />
          ) : (
            <pre className="m-0 whitespace-pre p-0">{props.spec}</pre>
          )}
        </div>
        <textarea
          ref={taRef}
          spellCheck={false}
          autoCorrect="off"
          autoCapitalize="none"
          value={props.spec}
          onChange={(e) => props.onChange(e.target.value)}
          onScroll={syncScroll}
          onKeyDown={(e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
              e.preventDefault();
              props.onSubmit();
            }
          }}
          className="absolute inset-0 m-0 block resize-none overflow-auto whitespace-pre bg-transparent p-3 font-mono text-[13px] leading-relaxed text-transparent caret-black focus:outline-none dark:caret-white"
        />
      </div>
    </div>
  );
}

function OutputPane(props: {
  state: RunState;
  tab: OutputTab;
  onTab: (t: OutputTab) => void;
  hasFiles: boolean;
  selectedFile: string;
  onSelectFile: (p: string) => void;
}) {
  const files = props.state.kind === "ok" ? props.state.files ?? [] : [];
  const fileEntry = files.find((f) => f.path === props.selectedFile);
  const showFilesTab = props.hasFiles;
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-md border border-fd-border bg-fd-card">
      <div
        role="tablist"
        className="flex h-9 items-center gap-1 border-b border-fd-border bg-fd-secondary/30 px-2"
      >
        {showFilesTab && (
          <TabButton active={props.tab === "files"} onClick={() => props.onTab("files")}>
            files
            <span className="ml-1 text-fd-muted-foreground">({files.length})</span>
          </TabButton>
        )}
        <TabButton active={props.tab === "stdout"} onClick={() => props.onTab("stdout")}>
          stdout
        </TabButton>
        <TabButton
          active={props.tab === "stderr"}
          onClick={() => props.onTab("stderr")}
          badge={stderrText(props.state).length > 0 ? "•" : undefined}
        >
          stderr
        </TabButton>
      </div>
      {props.tab === "files" && showFilesTab ? (
        <div className="flex min-h-0 flex-1 flex-col">
          <div className="flex items-center gap-2 border-b border-fd-border bg-fd-secondary/20 px-2 py-1.5 text-xs">
            <span className="text-fd-muted-foreground">file</span>
            <select
              value={props.selectedFile}
              onChange={(e) => props.onSelectFile(e.target.value)}
              className="min-w-0 flex-1 rounded border border-fd-border bg-fd-background px-2 py-1 font-mono text-xs"
            >
              {files.map((f) => (
                <option key={f.path} value={f.path}>
                  {f.path}
                  {f.truncated ? " (truncated)" : ""}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => downloadZip(files, props.state)}
              className="shrink-0 rounded border border-fd-border bg-fd-background px-2 py-1 font-medium hover:bg-fd-accent"
              title="Download all generated files as a .zip (large files are truncated as shown)"
            >
              Download .zip
            </button>
          </div>
          <pre className="m-0 min-h-0 flex-1 overflow-auto whitespace-pre bg-fd-background p-3 font-mono text-[12px] leading-relaxed text-fd-foreground">
            {fileEntry?.content ?? "// no file selected"}
          </pre>
        </div>
      ) : (
        <pre className="m-0 min-h-0 flex-1 overflow-auto whitespace-pre-wrap break-words bg-fd-background p-3 font-mono text-[12px] leading-relaxed text-fd-foreground">
          {props.tab === "stdout" ? stdoutText(props.state) : stderrText(props.state)}
        </pre>
      )}
    </div>
  );
}

function downloadZip(files: FileEntry[], state: RunState): void {
  if (files.length === 0) return;
  const target = state.kind === "ok" ? state.target : "output";
  const blob = makeZip(files.map((f) => ({ path: f.path, content: f.content })));
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `spec-to-rest-${target}.zip`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function TabButton(props: {
  active: boolean;
  onClick: () => void;
  badge?: string;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={props.active}
      onClick={props.onClick}
      className={[
        "rounded px-2 py-1 text-xs font-medium transition",
        props.active
          ? "bg-fd-background text-fd-foreground"
          : "text-fd-muted-foreground hover:bg-fd-background/60",
      ].join(" ")}
    >
      {props.children}
      {props.badge && <span className="ml-1 text-fd-primary">{props.badge}</span>}
    </button>
  );
}

function StatusLine({ state, target }: { state: RunState; target: Target }) {
  if (state.kind === "idle") {
    return (
      <p className="text-xs text-fd-muted-foreground">
        Press <Kbd>⌘/Ctrl + ↵</Kbd> in the editor or click <strong>Run</strong>. Limits: 50 KB spec,
        256 KB output, wall-clock {wallClockHint(target)}.
      </p>
    );
  }
  if (state.kind === "loading") {
    return (
      <p className="text-xs text-fd-muted-foreground">
        Running {target}… {target === "synth" || target === "verify" ? "(can take several minutes)" : ""}
      </p>
    );
  }
  if (state.kind === "ok") {
    const extra =
      state.target === "compile" && state.totalFiles !== undefined
        ? ` · ${state.totalFiles} files / ${Math.round((state.totalBytes ?? 0) / 1024)} KB`
        : "";
    return (
      <>
        <p className="text-xs text-fd-muted-foreground">
          <span className="text-green-600 dark:text-green-400">✓ ok</span> in {state.elapsedMs} ms
          {extra}
        </p>
        {state.target === "ir" && (
          <p className="text-xs text-fd-muted-foreground">
            The IR dump is the raw verifier input. <code>SpanT(line, col, line, col)</code> nodes
            are source positions (1-based lines, 0-based columns), and the{" "}
            <code>isValidURI</code> / <code>isValidEmail</code> predicates are built-ins merged
            from the parser preamble - their spans point into the preamble, not your spec.
          </p>
        )}
      </>
    );
  }
  return (
    <p className="text-xs">
      <span className="text-red-600 dark:text-red-400">✗ error</span>{" "}
      <span className="text-fd-muted-foreground">
        in {state.elapsedMs} ms - {state.message}
      </span>
    </p>
  );
}

function wallClockHint(target: Target): string {
  switch (target) {
    case "verify":
      return "60 s";
    case "compile":
      return "30 s";
    case "synth":
      return "10 min";
    default:
      return "8 s";
  }
}

function Kbd({ children }: { children: React.ReactNode }) {
  return (
    <kbd className="rounded border border-fd-border px-1 text-[10px]">{children}</kbd>
  );
}

function stdoutText(s: RunState): string {
  switch (s.kind) {
    case "idle":
      return "// Click Run to compile the spec.";
    case "loading":
      return "// Running…";
    case "ok":
      return s.stdout || "// (no stdout - the subcommand produced no output)";
    case "error":
      return `// ERROR: ${s.message}`;
  }
}

function stderrText(s: RunState): string {
  switch (s.kind) {
    case "idle":
    case "loading":
      return "";
    case "ok":
      return s.stderr || "";
    case "error":
      return s.stderr || s.message;
  }
}
