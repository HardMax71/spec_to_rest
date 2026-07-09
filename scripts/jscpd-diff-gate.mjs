import { execSync } from "node:child_process";
import { readFileSync, existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const base = process.env.DIFF_BASE ?? "origin/main";
const threshold = Number.parseFloat(process.env.DIFF_THRESHOLD ?? "1");
const minTokens = process.env.DIFF_MIN_TOKENS ?? "100";
const jscpdVersion = process.env.JSCPD_VERSION ?? "5.0.12";

const sh = (cmd) => execSync(cmd, { encoding: "utf8" });
const isScalaMain = (f) =>
  /^modules\/[^/]+\/src\/main\/scala\/.*\.scala$/.test(f) && !f.includes("/generated/");

let changed;
try {
  changed = sh(`git diff --name-only ${base}...HEAD`)
    .split("\n")
    .map((s) => s.trim())
    .filter(isScalaMain)
    .filter(existsSync);
} catch (e) {
  console.error(`could not diff against ${base}: ${e.message}`);
  process.exit(1);
}

if (changed.length === 0) {
  console.log(`no changed Scala main sources vs ${base}; changed-code duplication gate passes`);
  process.exit(0);
}

// Scan the whole tree, not only the changed files: a clone between a file this PR
// touched and one it did not is exactly the "copied existing code" case we want to
// catch, and it is invisible if jscpd only sees the changed files.
const out = mkdtempSync(join(tmpdir(), "jscpd-diff-"));
sh(
  `npx --yes jscpd@${jscpdVersion} . --pattern "modules/**/src/main/scala/**/*.scala" ` +
    `--ignore "**/generated/**" --min-tokens ${minTokens} --min-lines 5 ` +
    `--reporters json --output ${out} --silent`,
);
const report = JSON.parse(readFileSync(join(out, "jscpd-report.json"), "utf8"));

const changedSet = new Set(changed);
const dupLines = new Map();
const mark = (inst) => {
  if (!changedSet.has(inst.name)) return;
  const set = dupLines.get(inst.name) ?? new Set();
  for (let l = inst.start; l <= inst.end; l++) set.add(l);
  dupLines.set(inst.name, set);
};
for (const d of report.duplicates ?? []) {
  mark(d.firstFile);
  mark(d.secondFile);
}

let changedTotal = 0;
let changedDup = 0;
for (const f of changed) {
  const content = readFileSync(f, "utf8");
  changedTotal += content === "" ? 0 : content.split("\n").length - (content.endsWith("\n") ? 1 : 0);
  changedDup += dupLines.get(f)?.size ?? 0;
}
const pct = changedTotal === 0 ? 0 : (changedDup / changedTotal) * 100;

console.log(
  `changed Scala main: ${changed.length} files, ${changedDup}/${changedTotal} lines duplicated = ${pct.toFixed(2)}%`,
);
for (const [f, s] of dupLines) console.log(`  ${f}: ${s.size} duplicated lines`);

if (pct > threshold) {
  console.error(`FAIL: changed-code duplication ${pct.toFixed(2)}% exceeds ${threshold}%`);
  process.exit(1);
}
console.log(`OK: changed-code duplication ${pct.toFixed(2)}% within ${threshold}%`);
