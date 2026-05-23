"use client";

import Editor, { type OnMount } from "@monaco-editor/react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "fumadocs-ui/components/tabs";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

const API_URL = process.env.NEXT_PUBLIC_PLAYGROUND_API ?? "";

type Target = "check" | "summary" | "ir" | "dafny";

const TARGETS: { value: Target; label: string; lang: string; description: string }[] = [
  { value: "check", label: "Check", lang: "plaintext", description: "Parse + lint" },
  { value: "summary", label: "Summary", lang: "plaintext", description: "Operations + classifications" },
  { value: "ir", label: "IR", lang: "scala", description: "Internal representation (Scala case classes)" },
  { value: "dafny", label: "Dafny", lang: "scala", description: "Generated Dafny verification kernel" },
];

const EXAMPLES: { name: string; spec: string }[] = [
  {
    name: "safe_counter",
    spec: `service SafeCounter {

  state {
    count: Int where value >= 0
  }

  operation Increment {
    requires: count < 100
    ensures:  count' = pre(count) + 1
  }

  operation Decrement {
    requires: count > 0
    ensures:  count' = pre(count) - 1
  }

  invariant countBounded: count >= 0 and count <= 100
}
`,
  },
  {
    name: "url_shortener",
    spec: `service UrlShortener {

  type Code = String where len(value) >= 4 and len(value) <= 12

  entity UrlMapping {
    code: Code
    url: String
    click_count: Int where value >= 0
  }

  state {
    store: Code -> lone String
    metadata: Code -> lone UrlMapping
  }

  operation Shorten {
    input:  code: Code, url: String
    requires:
      code not in store
      len(url) >= 1
    ensures:
      store' = pre(store) + {code -> url}
      metadata' = pre(metadata) + {code -> UrlMapping { code = code, url = url, click_count = 0 }}
  }

  operation Resolve {
    input:  code: Code
    output: url: String
    requires: code in store
    ensures:  url = pre(store)[code]
  }

  invariant codeIndexConsistent:
    all c in store | c in metadata
}
`,
  },
  {
    name: "todo_list",
    spec: `service TodoList {

  entity Todo {
    id: Int where value > 0
    title: String where len(value) >= 1
    done: Bool
  }

  state {
    todos: Int -> lone Todo
    next_id: Int where value > 0
  }

  operation Add {
    input:  title: String
    output: todo: Todo
    requires: len(title) >= 1
    ensures:
      todo.id = pre(next_id)
      todo.title = title
      todo.done = false
      todos' = pre(todos) + {todo.id -> todo}
      next_id' = pre(next_id) + 1
  }

  operation Complete {
    input: id: Int
    requires: id in todos
    ensures:
      todos'[id].done = true
      todos'[id].id = pre(todos)[id].id
      todos'[id].title = pre(todos)[id].title
  }
}
`,
  },
];

type ApiResponse =
  | { ok: true; stdout: string; stderr: string }
  | { ok: false; stdout: string; stderr: string; error: string };

type RunState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "ok"; stdout: string; stderr: string }
  | { kind: "error"; message: string; stderr?: string };

export function Playground() {
  const [spec, setSpec] = useState<string>(EXAMPLES[0].spec);
  const [target, setTarget] = useState<Target>("ir");
  const [state, setState] = useState<RunState>({ kind: "idle" });
  const abortRef = useRef<AbortController | null>(null);

  const targetMeta = useMemo(() => TARGETS.find((t) => t.value === target)!, [target]);

  const submit = useCallback(async () => {
    if (!API_URL) {
      setState({
        kind: "error",
        message:
          "Playground backend not configured. Set NEXT_PUBLIC_PLAYGROUND_API at build time. See playground/README.md.",
      });
      return;
    }
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setState({ kind: "loading" });
    try {
      const res = await fetch(`${API_URL.replace(/\/$/, "")}/api/compile`, {
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
      {!API_URL && (
        <p className="text-xs text-fd-muted-foreground">
          <strong>Backend not configured.</strong> Set <code>NEXT_PUBLIC_PLAYGROUND_API</code> in the docs
          build env. See <a href="https://github.com/HardMax71/spec_to_rest/tree/main/playground"><code>playground/README.md</code></a>.
        </p>
      )}
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
          defaultValue={EXAMPLES[0].name}
          onChange={(e) => {
            const ex = EXAMPLES.find((x) => x.name === e.target.value);
            if (ex) props.onExample(ex.spec);
          }}
        >
          {EXAMPLES.map((ex) => (
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
  const items = ["stdout", "stderr"];
  return (
    <div className="overflow-hidden rounded-md border border-fd-border">
      <Tabs items={items} defaultIndex={0}>
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
