"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { PLAYGROUND_EXAMPLES } from "@/lib/playground-examples.generated";

type Target = "check" | "summary" | "ir" | "dafny";

const TARGETS: { value: Target; label: string; description: string }[] = [
  { value: "check", label: "Check", description: "Parse + lint" },
  { value: "summary", label: "Summary", description: "Operations + classifications" },
  { value: "ir", label: "IR", description: "Internal representation (case-class text)" },
  { value: "dafny", label: "Dafny", description: "Generated verification kernel" },
];

const DEFAULT_SPEC =
  PLAYGROUND_EXAMPLES[0]?.spec ?? "service Empty { state { count: Int } }\n";

type ApiResponse =
  | { ok: true; stdout: string; stderr: string }
  | { ok: false; stdout: string; stderr: string; error: string };

type OutputTab = "stdout" | "stderr";

type RunState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "ok"; stdout: string; stderr: string; elapsedMs: number }
  | { kind: "error"; message: string; stderr?: string; elapsedMs: number };

export function Playground() {
  const [spec, setSpec] = useState<string>(DEFAULT_SPEC);
  const [target, setTarget] = useState<Target>("ir");
  const [exampleName, setExampleName] = useState<string>(PLAYGROUND_EXAMPLES[0]?.name ?? "");
  const [state, setState] = useState<RunState>({ kind: "idle" });
  const [tab, setTab] = useState<OutputTab>("stdout");
  const abortRef = useRef<AbortController | null>(null);

  const submit = useCallback(async () => {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setState({ kind: "loading" });
    setTab("stdout");
    const t0 = performance.now();
    try {
      const res = await fetch("/api/compile", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ spec, target }),
        signal: ctrl.signal,
      });
      const data = (await res.json()) as ApiResponse;
      const elapsedMs = Math.round(performance.now() - t0);
      if (data.ok) {
        setState({ kind: "ok", stdout: data.stdout, stderr: data.stderr, elapsedMs });
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
  }, [spec, target]);

  useEffect(() => () => abortRef.current?.abort(), []);

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
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <SpecEditor spec={spec} onChange={setSpec} onSubmit={submit} />
        <OutputPane state={state} tab={tab} onTab={setTab} />
      </div>
      <StatusLine state={state} />
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
    <svg
      className="h-3 w-3 animate-spin"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
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
  return (
    <div className="flex flex-col overflow-hidden rounded-md border border-fd-border bg-fd-card">
      <div className="border-b border-fd-border bg-fd-secondary/30 px-3 py-1.5 text-xs font-medium text-fd-muted-foreground">
        spec
      </div>
      <textarea
        spellCheck={false}
        autoCorrect="off"
        autoCapitalize="off"
        value={props.spec}
        onChange={(e) => props.onChange(e.target.value)}
        onKeyDown={(e) => {
          if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
            e.preventDefault();
            props.onSubmit();
          }
        }}
        className="block h-[460px] w-full resize-none bg-fd-background p-3 font-mono text-[13px] leading-relaxed text-fd-foreground focus:outline-none"
      />
    </div>
  );
}

function OutputPane(props: {
  state: RunState;
  tab: OutputTab;
  onTab: (t: OutputTab) => void;
}) {
  const stderrLen = useMemo(() => stderrText(props.state).length, [props.state]);
  return (
    <div className="flex flex-col overflow-hidden rounded-md border border-fd-border bg-fd-card">
      <div role="tablist" className="flex items-center gap-1 border-b border-fd-border bg-fd-secondary/30 px-2 py-1.5">
        <TabButton active={props.tab === "stdout"} onClick={() => props.onTab("stdout")}>
          stdout
        </TabButton>
        <TabButton
          active={props.tab === "stderr"}
          onClick={() => props.onTab("stderr")}
          badge={stderrLen > 0 ? "•" : undefined}
        >
          stderr
        </TabButton>
      </div>
      <pre className="m-0 h-[460px] overflow-auto whitespace-pre-wrap break-words bg-fd-background p-3 font-mono text-[12px] leading-relaxed text-fd-foreground">
        {props.tab === "stdout" ? stdoutText(props.state) : stderrText(props.state)}
      </pre>
    </div>
  );
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

function StatusLine({ state }: { state: RunState }) {
  if (state.kind === "idle") {
    return (
      <p className="text-xs text-fd-muted-foreground">
        Press <kbd className="rounded border border-fd-border px-1 text-[10px]">⌘/Ctrl + ↵</kbd> in the
        editor or click <strong>Run</strong>. Limits: 50 KB spec, 8 s exec, 256 KB output.
      </p>
    );
  }
  if (state.kind === "loading") {
    return <p className="text-xs text-fd-muted-foreground">Running…</p>;
  }
  if (state.kind === "ok") {
    return (
      <p className="text-xs text-fd-muted-foreground">
        <span className="text-green-600 dark:text-green-400">✓ ok</span> in {state.elapsedMs} ms
      </p>
    );
  }
  return (
    <p className="text-xs">
      <span className="text-red-600 dark:text-red-400">✗ error</span>{" "}
      <span className="text-fd-muted-foreground">in {state.elapsedMs} ms — {state.message}</span>
    </p>
  );
}

function stdoutText(s: RunState): string {
  switch (s.kind) {
    case "idle":
      return "// Click Run to compile the spec.";
    case "loading":
      return "// Running…";
    case "ok":
      return s.stdout || "// (no stdout — the subcommand produced no output)";
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
