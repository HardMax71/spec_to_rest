import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileP = promisify(execFile);
const BINARY = process.env.SPEC_TO_REST_BIN ?? "/usr/local/bin/spec-to-rest";

export interface Targets {
  frameworks: string[];
  dbs: string[];
  languages: string[];
}

const FALLBACK: Targets = {
  frameworks: ["chi", "express", "fastapi"],
  dbs: ["mysql", "postgres", "sqlite"],
  languages: ["go", "python", "ts"],
};

let cached: Targets | null = null;
let inflight: Promise<Targets> | null = null;

export async function loadTargets(): Promise<Targets> {
  if (cached) return cached;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const { stdout } = await execFileP(BINARY, ["compile", "--help"], {
        timeout: 5_000,
        env: { ...process.env, NO_COLOR: "1" },
      });
      const parsed = parseHelp(stdout);
      cached = parsed.frameworks.length && parsed.dbs.length && parsed.languages.length
        ? parsed
        : FALLBACK;
      return cached;
    } catch {
      cached = FALLBACK;
      return cached;
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

export function parseHelp(text: string): Targets {
  return {
    frameworks: pickList(text, /framework:\s*([a-z][a-z0-9,\s]*)/i),
    dbs: pickList(text, /database:\s*([a-z][a-z0-9,\s]*)/i),
    languages: pickList(text, /language:\s*([a-z][a-z0-9,\s]*)/i),
  };
}

function pickList(text: string, re: RegExp): string[] {
  const m = text.match(re);
  if (!m) return [];
  return m[1]
    .split(",")
    .map((s) => s.trim())
    .filter((s) => /^[a-z][a-z0-9-]*$/.test(s));
}
