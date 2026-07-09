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
const ASSET_RE = /\.(png|jpe?g|svg|gif|webp|ico|pdf)$/i;

const isExternal = (t) => /^[a-z][a-z0-9+.-]*:/i.test(t) || t.startsWith("//");
const exists = (p) => fs.existsSync(p);
const isFile = (p) => fs.existsSync(p) && fs.statSync(p).isFile();

// Strip fenced and inline code so links inside examples are not checked.
const stripCode = (text) => text.replace(/```[\s\S]*?```/g, "").replace(/`[^`\n]*`/g, "");

// A docs route like `/research/x/theorem` maps to a content file under
// docs/content/docs, trying page, index, and both markdown extensions.
function resolvesAsRoute(route) {
  const rel = route.replace(/^\//, "").replace(/^docs\//, "");
  if (rel === "") return isFile(path.join(DOCS_CONTENT, "index.mdx"));
  return [`${rel}.mdx`, `${rel}.md`, path.join(rel, "index.mdx"), path.join(rel, "index.md")].some(
    (candidate) => isFile(path.join(DOCS_CONTENT, candidate)),
  );
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
    const clean = target.split("#")[0].split("?")[0];
    if (clean === "" || ASSET_RE.test(clean)) continue;
    const ok = clean.startsWith("/")
      ? resolvesAsRoute(clean)
      : exists(path.resolve(path.dirname(src), clean));
    if (!ok) issues.push(`${fromRel} -> ${target}`);
  }
}

if (issues.length) {
  console.error(`Found ${issues.length} broken internal link(s):`);
  for (const i of issues) console.error(`  ${i}`);
  process.exit(1);
}
console.log(`Link check passed (${sources.length} files scanned).`);
