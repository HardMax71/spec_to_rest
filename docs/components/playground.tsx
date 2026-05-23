"use client";

import Editor, { type OnMount } from "@monaco-editor/react";
import { Tabs, TabsContent } from "fumadocs-ui/components/tabs";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { PLAYGROUND_EXAMPLES } from "@/lib/playground-examples.generated";

type Target = "check" | "summary" | "ir" | "dafny";

const TARGETS: { value: Target; label: string; lang: string; description: string }[] = [
  { value: "check", label: "Check", lang: "plaintext", description: "Parse + lint" },
  { value: "summary", label: "Summary", lang: "plaintext", description: "Operations + classifications" },
  { value: "ir", label: "IR", lang: "scala", description: "Internal representation (Scala case classes)" },
  { value: "dafny", label: "Dafny", lang: "scala", description: "Generated Dafny verification kernel" },
];

const DEFAULT_SPEC =
  PLAYGROUND_EXAMPLES[0]?.spec ?? "service Empty { state { count: Int } }\n";

type ApiResponse =
  | { ok: true; stdout: string; stderr: string }
  | { ok: false; stdout: string; stderr: string; error: string };

type RunState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "ok"; stdout: string; stderr: string }
  | { kind: "error"; message: string; stderr?: string };

export function Playground() {
  const [spec, setSpec] = useState<string>(DEFAULT_SPEC);
  const [target, setTarget] = useState<Target>("ir");
  const [state, setState] = useState<RunState>({ kind: "idle" });
  const abortRef = useRef<AbortController | null>(null);

  const targetMeta = useMemo(() => TARGETS.find((t) => t.value === target)!, [target]);

  const submit = useCallback(async () => {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setState({ kind: "loading" });
    try {
      const res = await fetch("/api/compile", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ spec, target }),
        signal: ctrl.signal,
      });
      const data = (await res.json()) as ApiResponse;
      if (data.ok) {
        setState({ kind: "ok", stdout: data.stdout, stderr: data.stderr });
      } else {
        setState({
          kind: "error",
          message: data.error || `Backend returned ${res.status}`,
          stderr: data.stderr,
        });
      }
    } catch (err) {
      if ((err as Error).name === "AbortError") return;
      setState({
        kind: "error",
        message: err instanceof Error ? err.message : "network error",
      });
    }
  }, [spec, target]);

  const onMount: OnMount = useCallback(
    (editor, monaco) => {
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
        void submit();
      });
    },
    [submit],
  );

  useEffect(() => () => abortRef.current?.abort(), []);

  return (
    <div className="not-prose flex flex-col gap-3">
      <Toolbar
        target={target}
        onTarget={setTarget}
        onExample={(s) => setSpec(s)}
        onSubmit={submit}
        running={state.kind === "loading"}
      />
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        <EditorPane spec={spec} onChange={setSpec} onMount={onMount} />
        <OutputPane state={state} targetLang={targetMeta.lang} />
      </div>
    </div>
  );
}

function Toolbar(props: {
  target: Target;
  onTarget: (t: Target) => void;
  onExample: (spec: string) => void;
  onSubmit: () => void;
  running: boolean;
}) {
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-md border border-fd-border bg-fd-card p-3">
      <label className="flex items-center gap-2 text-sm">
        <span className="text-fd-muted-foreground">Example</span>
        <select
          className="rounded border border-fd-border bg-fd-background px-2 py-1 text-sm"
          defaultValue={PLAYGROUND_EXAMPLES[0]?.name}
          onChange={(e) => {
            const ex = PLAYGROUND_EXAMPLES.find((x) => x.name === e.target.value);
            if (ex) props.onExample(ex.spec);
          }}
        >
          {PLAYGROUND_EXAMPLES.map((ex) => (
            <option key={ex.name} value={ex.name}>
              {ex.name}
            </option>
          ))}
        </select>
      </label>
      <label className="flex items-center gap-2 text-sm">
        <span className="text-fd-muted-foreground">Target</span>
        <select
          className="rounded border border-fd-border bg-fd-background px-2 py-1 text-sm"
          value={props.target}
          onChange={(e) => props.onTarget(e.target.value as Target)}
        >
          {TARGETS.map((t) => (
            <option key={t.value} value={t.value} title={t.description}>
              {t.label}
            </option>
          ))}
        </select>
      </label>
      <button
        type="button"
        onClick={props.onSubmit}
        disabled={props.running}
        className="ml-auto rounded bg-fd-primary px-3 py-1.5 text-sm font-medium text-fd-primary-foreground disabled:opacity-50"
      >
        {props.running ? "Running…" : "Run (⌘/Ctrl+Enter)"}
      </button>
    </div>
  );
}

function EditorPane(props: {
  spec: string;
  onChange: (s: string) => void;
  onMount: OnMount;
}) {
  return (
    <div className="overflow-hidden rounded-md border border-fd-border">
      <Editor
        height="500px"
        defaultLanguage="plaintext"
        theme="vs-dark"
        value={props.spec}
        onChange={(v) => props.onChange(v ?? "")}
        onMount={props.onMount}
        options={{
          minimap: { enabled: false },
          fontSize: 13,
          scrollBeyondLastLine: false,
          tabSize: 2,
          wordWrap: "on",
        }}
      />
    </div>
  );
}

function OutputPane(props: { state: RunState; targetLang: string }) {
  return (
    <div className="overflow-hidden rounded-md border border-fd-border">
      <Tabs items={["stdout", "stderr"]} defaultIndex={0}>
        <TabsContent value="stdout">
          <ReadOnlyView
            text={renderStdout(props.state)}
            language={props.state.kind === "ok" ? props.targetLang : "plaintext"}
          />
        </TabsContent>
        <TabsContent value="stderr">
          <ReadOnlyView text={renderStderr(props.state)} language="plaintext" />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function ReadOnlyView(props: { text: string; language: string }) {
  return (
    <Editor
      height="500px"
      theme="vs-dark"
      language={props.language}
      value={props.text}
      options={{
        readOnly: true,
        minimap: { enabled: false },
        fontSize: 12,
        scrollBeyondLastLine: false,
        wordWrap: "on",
      }}
    />
  );
}

function renderStdout(s: RunState): string {
  switch (s.kind) {
    case "idle":
      return "// Click Run to compile the spec.\n";
    case "loading":
      return "// Running…\n";
    case "ok":
      return s.stdout || "// (no stdout — the subcommand produced no output)\n";
    case "error":
      return `// ERROR: ${s.message}\n`;
  }
}

function renderStderr(s: RunState): string {
  switch (s.kind) {
    case "idle":
    case "loading":
      return "";
    case "ok":
      return s.stderr || "(empty)";
    case "error":
      return s.stderr || s.message;
  }
}
