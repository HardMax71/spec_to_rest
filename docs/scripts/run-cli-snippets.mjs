import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DOCS_ROOT = path.resolve(HERE, "..");
const REPO_ROOT = path.resolve(DOCS_ROOT, "..");
const DOCS_CONTENT = path.join(DOCS_ROOT, "content/docs");
const FIXTURES_DIR = path.join(REPO_ROOT, "fixtures/spec");
const CACHE_DIR = path.join(DOCS_ROOT, "lib/cli-runs");
const INDEX_FILE = path.join(CACHE_DIR, "index.json");

const args = new Set(process.argv.slice(2));
const MODE = args.has("--regen")
  ? "regen"
  : args.has("--check")
    ? "check"
    : "lookup";
const VERBOSE = args.has("--verbose") || args.has("-v");

function log(msg) {
  process.stdout.write(`${msg}\n`);
}
function vlog(msg) {
  if (VERBOSE) log(msg);
}
function fail(msg) {
  process.stderr.write(`run-cli-snippets: ${msg}\n`);
  process.exit(1);
}

function sha(s) {
  return createHash("sha256").update(s).digest("hex").slice(0, 16);
}

function* walkMdx(dir) {
  for (const ent of readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) yield* walkMdx(p);
    else if (ent.isFile() && /\.(md|mdx)$/.test(ent.name)) yield p;
  }
}

function parseInlineMeta(meta) {
  const runM = meta.match(/\brun=([\w-]+)/);
  if (!runM) return null;
  const flagsM = meta.match(/\bflags="([^"]*)"/);
  const expectM = meta.match(/\bexpectExit=(\d+)/);
  return {
    command: runM[1],
    flags: flagsM ? flagsM[1] : "",
    expectExit: expectM ? Number(expectM[1]) : null,
  };
}

function parseJsxAttrs(attrString) {
  const out = {};
  const re = /([a-zA-Z][a-zA-Z0-9]*)="([^"]*)"/g;
  let m;
  while ((m = re.exec(attrString)) !== null) out[m[1]] = m[2];
  return out;
}

function extractSnippets(file, source) {
  const snippets = [];
  const externalRe = /<CliRun\s+([^/>]+?)\s*\/>/g;
  let m;
  while ((m = externalRe.exec(source)) !== null) {
    const props = parseJsxAttrs(m[1]);
    if (!props.spec || !props.command) {
      fail(`${file}: <CliRun /> missing required prop (spec, command)`);
    }
    const specPath = path.join(FIXTURES_DIR, `${props.spec}.spec`);
    if (!existsSync(specPath)) {
      fail(`${file}: <CliRun spec="${props.spec}" /> -> fixtures/spec/${props.spec}.spec not found`);
    }
    const specText = readFileSync(specPath, "utf8");
    snippets.push({
      kind: "external",
      file,
      spec: props.spec,
      specText,
      command: props.command,
      flags: props.flags ?? "",
      expectExit: props.expectExit ? Number(props.expectExit) : null,
      id: sha(`external|${props.spec}|${props.command}|${props.flags ?? ""}`),
    });
  }

  const inlineRe = /^```spec([^\n`]*)\n([\s\S]*?)\n^```/gm;
  while ((m = inlineRe.exec(source)) !== null) {
    const meta = m[1].trim();
    const body = m[2];
    const directive = parseInlineMeta(meta);
    if (!directive) continue;
    snippets.push({
      kind: "inline",
      file,
      spec: null,
      specText: body,
      command: directive.command,
      flags: directive.flags,
      expectExit: directive.expectExit,
      id: sha(`inline|${body}|${directive.command}|${directive.flags}`),
    });
  }

  return snippets;
}

function locateCli() {
  if (process.env.SPEC_TO_REST_BIN) {
    if (!existsSync(process.env.SPEC_TO_REST_BIN)) {
      fail(`SPEC_TO_REST_BIN points at non-existent path: ${process.env.SPEC_TO_REST_BIN}`);
    }
    return { kind: "native", path: process.env.SPEC_TO_REST_BIN };
  }
  const native = path.join(REPO_ROOT, "modules/cli/target/native-image/spec-to-rest");
  if (existsSync(native)) return { kind: "native", path: native };

  const cpExport = path.join(
    REPO_ROOT,
    "modules/cli/target/streams/runtime/fullClasspathAsJars/_global/streams/export",
  );
  if (existsSync(cpExport)) {
    return { kind: "java", classpath: readFileSync(cpExport, "utf8").trim() };
  }
  return null;
}

function runCli(cli, snippet, specFile) {
  const argv = [snippet.command, specFile];
  if (snippet.flags.trim()) argv.push(...snippet.flags.trim().split(/\s+/));
  let proc;
  if (cli.kind === "native") {
    proc = spawnSync(cli.path, argv, { encoding: "utf8" });
  } else {
    proc = spawnSync(
      "java",
      ["-cp", cli.classpath, "specrest.cli.Main", ...argv],
      { encoding: "utf8" },
    );
  }
  if (proc.error) fail(`spawn failed: ${proc.error.message}`);
  return {
    stdout: proc.stdout ?? "",
    stderr: proc.stderr ?? "",
    exitCode: proc.status ?? 1,
  };
}

function normalize(text, snippet, specFile) {
  let out = text;
  out = out.replaceAll(specFile, "<spec>");
  out = out.replace(/\(\d+(?:\.\d+)?\s*(?:ns|µs|us|ms|s)\)/g, "(<elapsed>)");
  out = out.replace(/\b\d+(?:\.\d+)?\s*ms\b/g, "<elapsed>ms");
  out = out.replace(/\bin\s+\d+(?:\.\d+)?\s*(?:ms|s)\b/g, "in <elapsed>");
  return out.trimEnd() + "\n";
}

function loadGolden(id) {
  const p = path.join(CACHE_DIR, `${id}.json`);
  if (!existsSync(p)) return null;
  try {
    return JSON.parse(readFileSync(p, "utf8"));
  } catch (e) {
    fail(`malformed golden ${p}: ${e.message}`);
  }
  return null;
}

function executeSnippet(cli, snippet) {
  const tmp = path.join(os.tmpdir(), `specrest-snippet-${snippet.id}.spec`);
  writeFileSync(tmp, snippet.specText);
  try {
    const raw = runCli(cli, snippet, tmp);
    return {
      command: snippet.command,
      flags: snippet.flags,
      exitCode: raw.exitCode,
      stdout: normalize(raw.stdout, snippet, tmp),
      stderr: normalize(raw.stderr, snippet, tmp),
    };
  } finally {
    rmSync(tmp, { force: true });
  }
}

function payloadFor(snippet, exec) {
  return {
    kind: snippet.kind,
    spec: snippet.spec,
    command: exec.command,
    flags: exec.flags,
    exitCode: exec.exitCode,
    stdout: exec.stdout,
    stderr: exec.stderr,
    specText: snippet.kind === "inline" ? snippet.specText : null,
  };
}

function compareGolden(a, b) {
  return (
    a.exitCode === b.exitCode &&
    a.stdout === b.stdout &&
    a.stderr === b.stderr
  );
}

function buildIndex(snippets) {
  const idx = {};
  for (const s of snippets) {
    if (s.kind === "external") {
      idx[`external|${s.spec}|${s.command}|${s.flags}`] = s.id;
    }
  }
  return idx;
}

function main() {
  if (!existsSync(DOCS_CONTENT)) fail(`docs content dir missing: ${DOCS_CONTENT}`);
  const snippets = [];
  for (const file of walkMdx(DOCS_CONTENT)) {
    const src = readFileSync(file, "utf8");
    snippets.push(...extractSnippets(file, src));
  }
  log(`found ${snippets.length} snippet(s)`);

  if (MODE === "lookup") {
    let missing = 0;
    for (const s of snippets) {
      if (!loadGolden(s.id)) {
        process.stderr.write(
          `missing golden for ${s.kind} snippet (${s.command}) in ${path.relative(REPO_ROOT, s.file)}\n  -> id=${s.id}\n`,
        );
        missing++;
      }
    }
    if (missing > 0) {
      fail(`${missing} snippet(s) lack golden output. Run \`node docs/scripts/run-cli-snippets.mjs --regen\``);
    }
    mkdirSync(CACHE_DIR, { recursive: true });
    writeFileSync(INDEX_FILE, JSON.stringify(buildIndex(snippets), null, 2) + "\n");
    log("OK: all snippets have goldens; index refreshed.");
    return;
  }

  const cli = locateCli();
  if (!cli) {
    fail(
      "cannot locate CLI binary. Set SPEC_TO_REST_BIN, or build with `sbt cli/nativeImage`, or compile classpath with `sbt cli/compile`.",
    );
  }
  vlog(`using CLI: ${cli.kind}`);

  let drift = 0;
  let exitMismatch = 0;
  for (const s of snippets) {
    vlog(`running ${s.command} (${s.id}) for ${path.relative(REPO_ROOT, s.file)}`);
    const exec = executeSnippet(cli, s);

    if (s.expectExit !== null && exec.exitCode !== s.expectExit) {
      process.stderr.write(
        `expectExit mismatch for ${s.id} in ${path.relative(REPO_ROOT, s.file)}: expected ${s.expectExit}, got ${exec.exitCode}\n`,
      );
      exitMismatch++;
      continue;
    }

    const payload = payloadFor(s, exec);
    if (MODE === "regen") {
      mkdirSync(CACHE_DIR, { recursive: true });
      writeFileSync(
        path.join(CACHE_DIR, `${s.id}.json`),
        JSON.stringify(payload, null, 2) + "\n",
      );
      log(`  wrote ${s.id}.json (exit=${exec.exitCode})`);
    } else {
      const golden = loadGolden(s.id);
      if (!golden) {
        process.stderr.write(
          `missing golden for ${s.id} (${path.relative(REPO_ROOT, s.file)})\n`,
        );
        drift++;
        continue;
      }
      if (!compareGolden(golden, payload)) {
        process.stderr.write(
          `\n--- drift in ${s.id} (${path.relative(REPO_ROOT, s.file)}) ---\n`,
        );
        process.stderr.write(`expected exit=${golden.exitCode}, got ${payload.exitCode}\n`);
        if (golden.stdout !== payload.stdout) {
          process.stderr.write(`stdout diff:\n=== golden ===\n${golden.stdout}\n=== fresh ===\n${payload.stdout}\n`);
        }
        if (golden.stderr !== payload.stderr) {
          process.stderr.write(`stderr diff:\n=== golden ===\n${golden.stderr}\n=== fresh ===\n${payload.stderr}\n`);
        }
        drift++;
      }
    }
  }

  mkdirSync(CACHE_DIR, { recursive: true });
  writeFileSync(INDEX_FILE, JSON.stringify(buildIndex(snippets), null, 2) + "\n");

  if (exitMismatch > 0) {
    fail(`${exitMismatch} snippet(s) had unexpected exit codes`);
  }
  if (MODE === "check" && drift > 0) {
    fail(`${drift} snippet(s) drifted; run \`node docs/scripts/run-cli-snippets.mjs --regen\` and commit the diff`);
  }
  log(MODE === "regen" ? "OK: regen complete" : "OK: no drift");
}

main();
