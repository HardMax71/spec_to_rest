import { spawn } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const MAX_SPEC_BYTES = 50 * 1024;
const MAX_OUTPUT_BYTES = 256 * 1024;
const EXEC_TIMEOUT_MS = 8_000;
const BINARY_PATH = process.env.SPEC_TO_REST_BIN ?? "/usr/local/bin/spec-to-rest";

type TargetKey = "check" | "summary" | "ir" | "dafny";

const TARGETS: Record<TargetKey, readonly string[]> = {
  check: ["check", "--quiet"],
  summary: ["inspect", "--format", "summary", "--quiet"],
  ir: ["inspect", "--format", "ir", "--quiet"],
  dafny: ["inspect", "--format", "dafny", "--quiet"],
};

// Own-property lookup: `target in TARGETS` would also resolve prototype keys
// like "toString" / "constructor", which the typecheck after wouldn't catch.
function targetArgs(t: string): readonly string[] | undefined {
  return Object.prototype.hasOwnProperty.call(TARGETS, t)
    ? TARGETS[t as TargetKey]
    : undefined;
}

type CompileRequest = { spec?: unknown; target?: unknown };
type CompileResponse = {
  ok: boolean;
  stdout: string;
  stderr: string;
  error?: string;
};

export async function POST(req: Request) {
  let body: CompileRequest;
  try {
    body = (await req.json()) as CompileRequest;
  } catch (e) {
    return jerr(400, `invalid JSON body: ${(e as Error).message}`);
  }
  const spec = typeof body.spec === "string" ? body.spec : "";
  const target = typeof body.target === "string" ? body.target : "";

  if (!spec) return jerr(400, "empty spec");
  if (Buffer.byteLength(spec, "utf8") > MAX_SPEC_BYTES) {
    return jerr(413, `spec exceeds ${MAX_SPEC_BYTES} bytes`);
  }
  const args = targetArgs(target);
  if (!args) {
    return jerr(
      400,
      `unsupported target "${target}"; allowed: ${Object.keys(TARGETS).join(", ")}`,
    );
  }

  const tmp = await mkdtemp(join(tmpdir(), "spec-"));
  try {
    const specPath = join(tmp, "playground.spec");
    await writeFile(specPath, spec, "utf8");
    const result = await run(BINARY_PATH, [...args, specPath]);
    const resp: CompileResponse = {
      ok: result.code === 0,
      stdout: result.stdout,
      stderr: result.stderr,
    };
    if (result.code !== 0) {
      resp.error = result.error ?? `spec-to-rest exited with code ${result.code}`;
    }
    return NextResponse.json(resp);
  } finally {
    await rm(tmp, { recursive: true, force: true });
  }
}

function jerr(status: number, msg: string) {
  return NextResponse.json<CompileResponse>(
    { ok: false, stdout: "", stderr: "", error: msg },
    { status },
  );
}

type RunResult = { code: number; stdout: string; stderr: string; error?: string };

// Buffer-backed accumulator that hard-caps total bytes, kills the child when
// the cap is hit, and reports it back via the result error. Without this, a
// pathological spec that emits a few MB of Dafny per pass could exhaust the
// container's heap before the 8s timeout fires.
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

function run(cmd: string, args: readonly string[]): Promise<RunResult> {
  return new Promise((resolve) => {
    const child = spawn(cmd, args, {
      env: {
        ...process.env,
        HOME: "/tmp",
        PATH: "/usr/local/bin:/usr/bin:/bin",
        NO_COLOR: "1",
      },
    });
    const out = new CappedSink(MAX_OUTPUT_BYTES);
    const err = new CappedSink(MAX_OUTPUT_BYTES);
    let killed = false;
    let killedReason = "";
    const killChild = (reason: string) => {
      if (killed) return;
      killed = true;
      killedReason = reason;
      child.kill("SIGKILL");
    };
    child.stdout.on("data", (d: Buffer) => {
      if (out.push(d)) killChild(`stdout exceeded ${MAX_OUTPUT_BYTES} bytes`);
    });
    child.stderr.on("data", (d: Buffer) => {
      if (err.push(d)) killChild(`stderr exceeded ${MAX_OUTPUT_BYTES} bytes`);
    });
    const timer = setTimeout(
      () => killChild(`execution timed out after ${EXEC_TIMEOUT_MS}ms`),
      EXEC_TIMEOUT_MS,
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
      if (killed) {
        resolve({ code: -1, stdout, stderr, error: killedReason });
      } else {
        resolve({ code: code ?? -1, stdout, stderr });
      }
    });
  });
}
