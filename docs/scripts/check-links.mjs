import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..", "..");
const DOCS_CONTENT = path.join(REPO_ROOT, "docs", "content", "docs");

function walk(dir, exts) {
  if (!fs.existsSync(dir)) return [];
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full, exts));
    else if (exts.some((x) => entry.name.endsWith(x))) out.push(full);
  }
  return out;
}

const sources = [path.join(REPO_ROOT, "README.md"), ...walk(DOCS_CONTENT, [".md", ".mdx"])].filter(
  (f) => fs.existsSync(f),
);

const LINK_RE = /\[[^\]]*\]\(([^)]+)\)/g;
const HEADING_RE = /^(#{1,6})\s+(.+?)\s*$/;
const ASSET_RE = /\.(png|jpe?g|svg|gif|webp|ico|pdf)$/i;

const isExternal = (t) => /^[a-z][a-z0-9+.-]*:/i.test(t) || t.startsWith("//");
const exists = (p) => fs.existsSync(p);
const isFile = (p) => fs.existsSync(p) && fs.statSync(p).isFile();

const stripFences = (text) => text.replace(/```[\s\S]*?```/g, "");
// Strip fenced and inline code so links inside examples are not checked.
const stripCode = (text) => stripFences(text).replace(/`[^`\n]*`/g, "");

// Slug of a heading's rendered text, matching github-slugger (docs are ASCII):
// drop inline markdown, lowercase, keep [a-z0-9 _-], spaces to hyphens.
function slugify(heading) {
  return heading
    .replace(/`([^`]*)`/g, "$1")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/\*([^*]+)\*/g, "$1")
    .replace(/__([^_]+)__/g, "$1")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9 _-]/g, "")
    .replace(/ /g, "-");
}

const anchorCache = new Map();
function anchorsOf(file) {
  const cached = anchorCache.get(file);
  if (cached) return cached;
  const ids = new Set();
  const counts = new Map();
  for (const line of stripFences(fs.readFileSync(file, "utf8")).split("\n")) {
    const m = HEADING_RE.exec(line);
    if (!m) continue;
    const base = slugify(m[2]);
    if (base === "") continue;
    const seen = counts.get(base) ?? 0;
    counts.set(base, seen + 1);
    ids.add(seen === 0 ? base : `${base}-${seen}`);
  }
  anchorCache.set(file, ids);
  return ids;
}

// A docs route like `/research/x/theorem` maps to a content file under
// docs/content/docs, trying page, index, and both markdown extensions.
function routeToFile(route) {
  const rel = route.replace(/^\//, "").replace(/^docs\//, "");
  const candidates =
    rel === ""
      ? ["index.mdx", "index.md"]
      : [`${rel}.mdx`, `${rel}.md`, path.join(rel, "index.mdx"), path.join(rel, "index.md")];
  for (const candidate of candidates) {
    const full = path.join(DOCS_CONTENT, candidate);
    if (isFile(full)) return full;
  }
  return null;
}

const issues = [];
for (const src of sources) {
  const text = stripCode(fs.readFileSync(src, "utf8"));
  const fromRel = path.relative(REPO_ROOT, src);
  let m;
  LINK_RE.lastIndex = 0;
  while ((m = LINK_RE.exec(text)) !== null) {
    const target = m[1].trim().split(/\s+/)[0];
    if (!target || isExternal(target)) continue;
    const hash = target.indexOf("#");
    const frag = hash >= 0 ? decodeURIComponent(target.slice(hash + 1)) : "";
    const clean = (hash >= 0 ? target.slice(0, hash) : target).split("?")[0];

    if (clean === "") {
      if (frag && !anchorsOf(src).has(frag)) {
        issues.push(`${fromRel} -> #${frag} (no heading on this page)`);
      }
      continue;
    }
    if (ASSET_RE.test(clean)) continue;

    let targetFile;
    if (clean.startsWith("/")) {
      targetFile = routeToFile(clean);
    } else {
      const resolved = path.resolve(path.dirname(src), clean);
      targetFile = isFile(resolved) ? resolved : exists(resolved) ? "" : null;
    }
    if (targetFile === null) {
      issues.push(`${fromRel} -> ${clean}`);
      continue;
    }
    if (frag && targetFile && !anchorsOf(targetFile).has(frag)) {
      issues.push(`${fromRel} -> ${clean}#${frag} (no heading on target page)`);
    }
  }
}

if (issues.length) {
  console.error(`Found ${issues.length} broken internal link(s):`);
  for (const i of issues) console.error(`  ${i}`);
  process.exit(1);
}
console.log(`Link check passed (${sources.length} files scanned).`);
