import { spawn } from "node:child_process";
import { mkdtemp, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, relative } from "node:path";
import { NextResponse } from "next/server";
import { loadTargets } from "@/lib/targets";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";
export const maxDuration = 660;

const MAX_SPEC_BYTES = 50 * 1024;
const MAX_OUTPUT_BYTES = 256 * 1024;
const MAX_COMPILE_FILES = 200;
const MAX_COMPILE_TOTAL_BYTES = 2 * 1024 * 1024;
const BINARY_PATH = process.env.SPEC_TO_REST_BIN ?? "/usr/local/bin/spec-to-rest";

type FastTarget = "check" | "summary" | "ir" | "dafny";
type SlowTarget = "verify" | "compile" | "synth";
type Target = FastTarget | SlowTarget;

interface CompileOpts {
  framework: string;
  db: string;
}

interface SynthOpts {
  operation: string;
  model: string;
  apiKey: string;
}

type CompileRequest = {
  spec?: unknown;
  target?: unknown;
  compile?: unknown;
  synth?: unknown;
};

interface FileEntry {
  path: string;
  content: string;
  truncated: boolean;
}

interface CompileSuccess {
  ok: true;
  target: Target;
  stdout: string;
  stderr: string;
  files?: FileEntry[];
  totalFiles?: number;
  totalBytes?: number;
}

interface CompileFailure {
  ok: false;
  target?: Target;
  stdout: string;
  stderr: string;
  error: string;
}

type CompileResponse = CompileSuccess | CompileFailure;

const TIMEOUTS_MS: Record<Target, number> = {
  check: 8_000,
  summary: 8_000,
  ir: 8_000,
  dafny: 8_000,
  verify: 60_000,
  compile: 30_000,
  synth: 600_000,
};

const TARGETS: ReadonlySet<Target> = new Set<Target>([
  "check",
  "summary",
  "ir",
  "dafny",
  "verify",
  "compile",
  "synth",
]);

function isTarget(t: unknown): t is Target {
  return typeof t === "string" && TARGETS.has(t as Target);
}


export async function POST(req: Request) {
  let body: CompileRequest;
  try {
    body = (await req.json()) as CompileRequest;
  } catch (e) {
    return jerr(400, `invalid JSON body: ${(e as Error).message}`);
  }
  const spec = typeof body.spec === "string" ? body.spec : "";
  if (!spec) return jerr(400, "empty spec");
  if (Buffer.byteLength(spec, "utf8") > MAX_SPEC_BYTES) {
    return jerr(413, `spec exceeds ${MAX_SPEC_BYTES} bytes`);
  }
  if (!isTarget(body.target)) {
    return jerr(
      400,
      `unsupported target ${JSON.stringify(body.target)}; allowed: ${[...TARGETS].join(", ")}`,
    );
  }
  const target = body.target;

  const tmp = await mkdtemp(join(tmpdir(), "spec-"));
  try {
    const specPath = join(tmp, "playground.spec");
    await writeFile(specPath, spec, "utf8");

    switch (target) {
      case "check":
      case "summary":
      case "ir":
      case "dafny":
        return await runInspect(target, specPath);
      case "verify":
        return await runVerify(specPath);
      case "compile":
        return await runCompile(specPath, tmp, body.compile);
      case "synth":
        return await runSynth(specPath, body.synth, req.headers);
    }
  } catch (e) {
    return jerr(500, `internal: ${(e as Error).message}`);
  } finally {
    await rm(tmp, { recursive: true, force: true });
  }
}

async function runInspect(target: FastTarget, specPath: string) {
  const args =
    target === "check"
      ? ["check", "--quiet"]
      : ["inspect", "--format", target, "--quiet"];
  const r = await runBinary(args, specPath, TIMEOUTS_MS[target]);
  return NextResponse.json<CompileResponse>(
    r.code === 0
      ? { ok: true, target, stdout: r.stdout, stderr: r.stderr }
      : {
          ok: false,
          target,
          stdout: r.stdout,
          stderr: r.stderr,
          error: r.error ?? `spec-to-rest exited with code ${r.code}`,
        },
  );
}

async function runVerify(specPath: string) {
  const r = await runBinary(["verify", "--quiet"], specPath, TIMEOUTS_MS.verify);
  return NextResponse.json<CompileResponse>(
    r.code === 0
      ? { ok: true, target: "verify", stdout: r.stdout, stderr: r.stderr }
      : {
          ok: false,
          target: "verify",
          stdout: r.stdout,
          stderr: r.stderr,
          error: r.error ?? `spec-to-rest exited with code ${r.code}`,
        },
  );
}

async function runCompile(specPath: string, tmp: string, raw: unknown) {
  const opts = await parseCompileOpts(raw);
  if (typeof opts === "string") return jerr(400, opts);
  const outDir = join(tmp, "out");
  const r = await runBinary(
    [
      "compile",
      "--framework",
      opts.framework,
      "--db",
      opts.db,
      "--out",
      outDir,
      "--quiet",
    ],
    specPath,
    TIMEOUTS_MS.compile,
  );
  if (r.code !== 0) {
    return NextResponse.json<CompileResponse>({
      ok: false,
      target: "compile",
      stdout: r.stdout,
      stderr: r.stderr,
      error: r.error ?? `spec-to-rest exited with code ${r.code}`,
    });
  }
  const tree = await collectFiles(outDir);
  if (typeof tree === "string") {
    return NextResponse.json<CompileResponse>({
      ok: false,
      target: "compile",
      stdout: r.stdout,
      stderr: r.stderr,
      error: tree,
    });
  }
  return NextResponse.json<CompileResponse>({
    ok: true,
    target: "compile",
    stdout: r.stdout,
    stderr: r.stderr,
    files: tree.files,
    totalFiles: tree.totalFiles,
    totalBytes: tree.totalBytes,
  });
}

async function runSynth(specPath: string, raw: unknown, headers: Headers) {
  const opts = parseSynthOpts(raw);
  if (typeof opts === "string") return jerr(400, opts);

  const headerKey =
    headers.get("x-llm-api-key") ?? headers.get("X-LLM-API-Key") ?? "";
  const apiKey = opts.apiKey || headerKey;
  if (!apiKey) {
    return jerr(
      400,
      "synth requires an LLM API key — pass it in the `synth.apiKey` field of the request body or in the `X-LLM-API-Key` header. The key is forwarded to the model provider for this single request and never persisted server-side.",
    );
  }
  const providerEnv = pickProviderEnv(opts.model, apiKey);
  const args = [
    "synth",
    "verify",
    "--operation",
    opts.operation,
    "--model",
    opts.model,
    "--cost-cap-usd",
    "0.50",
    "--max-iter",
    "4",
    "--no-cache",
    "--quiet",
  ];
  const r = await runBinary(args, specPath, TIMEOUTS_MS.synth, providerEnv);
  return NextResponse.json<CompileResponse>(
    r.code === 0
      ? { ok: true, target: "synth", stdout: r.stdout, stderr: r.stderr }
      : {
          ok: false,
          target: "synth",
          stdout: r.stdout,
          stderr: r.stderr,
          error: r.error ?? `spec-to-rest exited with code ${r.code}`,
        },
  );
}

async function parseCompileOpts(raw: unknown): Promise<CompileOpts | string> {
  const { frameworks, dbs } = await loadTargets();
  if (raw === undefined || raw === null)
    return { framework: frameworks[0] ?? "fastapi", db: dbs[0] ?? "sqlite" };
  if (typeof raw !== "object")
    return "compile options must be an object: { framework, db }";
  const r = raw as Record<string, unknown>;
  if (typeof r.framework !== "string" || !frameworks.includes(r.framework))
    return `compile.framework must be one of: ${frameworks.join(", ")}`;
  if (typeof r.db !== "string" || !dbs.includes(r.db))
    return `compile.db must be one of: ${dbs.join(", ")}`;
  return { framework: r.framework, db: r.db };
}

function parseSynthOpts(raw: unknown): SynthOpts | string {
  if (raw === undefined || raw === null)
    return "synth requires { operation, model, apiKey } (apiKey may also come from X-LLM-API-Key header)";
  if (typeof raw !== "object") return "synth options must be an object";
  const r = raw as Record<string, unknown>;
  if (typeof r.operation !== "string" || !r.operation)
    return "synth.operation (string) is required";
  const model = typeof r.model === "string" && r.model ? r.model : "gpt-5-mini";
  const apiKey = typeof r.apiKey === "string" ? r.apiKey : "";
  return { operation: r.operation, model, apiKey };
}

function pickProviderEnv(model: string, key: string): Record<string, string> {
  if (model.toLowerCase().startsWith("gpt")) return { OPENAI_API_KEY: key };
  if (model.toLowerCase().startsWith("claude")) return { ANTHROPIC_API_KEY: key };
  // Unknown model prefix — set both, let the provider pick what it needs.
  return { OPENAI_API_KEY: key, ANTHROPIC_API_KEY: key };
}

async function collectFiles(
  root: string,
): Promise<{ files: FileEntry[]; totalFiles: number; totalBytes: number } | string> {
  const out: FileEntry[] = [];
  let totalBytes = 0;
  let totalFiles = 0;
  async function walk(dir: string): Promise<string | undefined> {
    let entries;
    try {
      entries = await readdir(dir, { withFileTypes: true });
    } catch (e) {
      return `failed to read ${dir}: ${(e as Error).message}`;
    }
    entries.sort((a, b) => a.name.localeCompare(b.name));
    for (const ent of entries) {
      const full = join(dir, ent.name);
      if (ent.isDirectory()) {
        const err = await walk(full);
        if (err) return err;
        continue;
      }
      if (!ent.isFile()) continue;
      totalFiles += 1;
      if (out.length >= MAX_COMPILE_FILES) continue;
      const st = await stat(full);
      const rel = relative(root, full);
      const sliceBytes = Math.min(
        st.size,
        Math.max(0, MAX_COMPILE_TOTAL_BYTES - totalBytes),
      );
      if (sliceBytes <= 0) {
        out.push({ path: rel, content: "", truncated: true });
        continue;
      }
      const buf = await readFile(full);
      const slice = buf.subarray(0, sliceBytes);
      const truncated = sliceBytes < st.size;
      const looksBinary = slice.includes(0);
      out.push({
        path: rel,
        content: looksBinary
          ? `(binary file — ${st.size} bytes; download via CLI)`
          : slice.toString("utf8"),
        truncated,
      });
      totalBytes += slice.length;
    }
    return undefined;
  }
  const err = await walk(root);
  if (err) return err;
  return { files: out, totalFiles, totalBytes };
}

function jerr(status: number, msg: string) {
  return NextResponse.json<CompileResponse>(
    { ok: false, stdout: "", stderr: "", error: msg },
    { status },
  );
}

type RunResult = { code: number; stdout: string; stderr: string; error?: string };

class CappedSink {
  private chunks: Buffer[] = [];
  private size = 0;
  overflowed = false;
  constructor(private readonly limit: number) {}
  push(buf: Buffer): boolean {
    if (this.overflowed) return false;
    const remaining = this.limit - this.size;
    if (buf.length <= remaining) {
      this.chunks.push(buf);
      this.size += buf.length;
      return false;
    }
    if (remaining > 0) {
      this.chunks.push(buf.subarray(0, remaining));
      this.size = this.limit;
    }
    this.overflowed = true;
    return true;
  }
  toString(): string {
    return Buffer.concat(this.chunks).toString("utf8");
  }
}

async function runBinary(
  cliArgs: readonly string[],
  specPath: string,
  timeoutMs: number,
  extraEnv: Record<string, string> = {},
): Promise<RunResult> {
  return new Promise((resolve) => {
    const child = spawn(BINARY_PATH, [...cliArgs, specPath], {
      env: {
        ...process.env,
        HOME: "/tmp",
        PATH: "/usr/local/bin:/usr/bin:/bin",
        NO_COLOR: "1",
        ...extraEnv,
      },
    });
    const out = new CappedSink(MAX_OUTPUT_BYTES);
    const err = new CappedSink(MAX_OUTPUT_BYTES);
    let killed = false;
    let killReason = "";
    const killChild = (reason: string) => {
      if (killed) return;
      killed = true;
      killReason = reason;
      child.kill("SIGKILL");
    };
    child.stdout.on("data", (d: Buffer) => {
      if (out.push(d)) killChild(`stdout exceeded ${MAX_OUTPUT_BYTES} bytes`);
    });
    child.stderr.on("data", (d: Buffer) => {
      if (err.push(d)) killChild(`stderr exceeded ${MAX_OUTPUT_BYTES} bytes`);
    });
    const timer = setTimeout(
      () => killChild(`execution timed out after ${timeoutMs}ms`),
      timeoutMs,
    );
    child.on("error", (e) => {
      clearTimeout(timer);
      resolve({
        code: -1,
        stdout: out.toString(),
        stderr: err.toString(),
        error: e.message,
      });
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      const stdout = out.toString();
      const stderr = err.toString();
      resolve(
        killed
          ? { code: -1, stdout, stderr, error: killReason }
          : { code: code ?? -1, stdout, stderr },
      );
    });
  });
}
