import { spawn } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const MAX_SPEC_BYTES = 50 * 1024;
const EXEC_TIMEOUT_MS = 8_000;
const BINARY_PATH = process.env.SPEC_TO_REST_BIN ?? "/usr/local/bin/spec-to-rest";

type TargetKey = "check" | "summary" | "ir" | "dafny";

const TARGETS: Record<TargetKey, readonly string[]> = {
  check: ["check", "--quiet"],
  summary: ["inspect", "--format", "summary", "--quiet"],
  ir: ["inspect", "--format", "ir", "--quiet"],
  dafny: ["inspect", "--format", "dafny", "--quiet"],
};

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
  if (!(target in TARGETS)) {
    return jerr(
      400,
      `unsupported target "${target}"; allowed: ${Object.keys(TARGETS).join(", ")}`,
    );
  }
  const args = TARGETS[target as TargetKey];

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
    let stdout = "";
    let stderr = "";
    let killed = false;
    child.stdout.on("data", (d: Buffer) => {
      stdout += d.toString();
    });
    child.stderr.on("data", (d: Buffer) => {
      stderr += d.toString();
    });
    const timer = setTimeout(() => {
      killed = true;
      child.kill("SIGKILL");
    }, EXEC_TIMEOUT_MS);
    child.on("error", (err) => {
      clearTimeout(timer);
      resolve({ code: -1, stdout, stderr, error: err.message });
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      if (killed) {
        resolve({
          code: -1,
          stdout,
          stderr,
          error: `execution timed out after ${EXEC_TIMEOUT_MS}ms`,
        });
      } else {
        resolve({ code: code ?? -1, stdout, stderr });
      }
    });
  });
}
