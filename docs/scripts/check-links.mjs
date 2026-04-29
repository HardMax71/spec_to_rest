import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.resolve(__dirname, "..", "out");
const BASE_PATH = "/spec_to_rest";
const ORIGIN = "http://docs.local";

if (!fs.existsSync(OUT_DIR)) {
  console.error(`✘ ${OUT_DIR} does not exist; run \`next build\` first`);
  process.exit(1);
}

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else if (entry.isFile() && entry.name.endsWith(".html")) out.push(full);
  }
  return out;
}

const HREF_RE = /<a\b[^>]*?\shref=("([^"]*)"|'([^']*)')/gi;
const ID_RE = /\sid=("([^"]+)"|'([^']+)')/gi;

function extractHrefs(html) {
  const hrefs = [];
  let m;
  HREF_RE.lastIndex = 0;
  while ((m = HREF_RE.exec(html)) !== null) hrefs.push(m[2] ?? m[3]);
  return hrefs;
}

function extractIds(html) {
  const ids = new Set();
  let m;
  ID_RE.lastIndex = 0;
  while ((m = ID_RE.exec(html)) !== null) ids.add(m[2] ?? m[3]);
  return ids;
}

function pageUrlForHtml(htmlAbs) {
  const rel = path.relative(OUT_DIR, htmlAbs).split(path.sep).join("/");
  if (rel === "index.html") return `${BASE_PATH}/`;
  if (rel.endsWith("/index.html")) return `${BASE_PATH}/${rel.slice(0, -"index.html".length)}`;
  return `${BASE_PATH}/${rel}`;
}

function isExternal(href) {
  return /^[a-z][a-z0-9+.-]*:/i.test(href) || href.startsWith("//");
}

function shouldCheck(href) {
  if (!href) return false;
  if (isExternal(href)) return false;
  if (href.startsWith("#")) return false;
  return true;
}

function resolveAbs(href, fromHtml) {
  const pageUrl = pageUrlForHtml(fromHtml);
  return new URL(href, `${ORIGIN}${pageUrl}`);
}

const ASSET_PREFIXES = ["/_next/", "/api/"];
function isAssetPath(pathname) {
  for (const p of ASSET_PREFIXES) if (pathname.startsWith(BASE_PATH + p)) return true;
  return false;
}

function resolveTargetFile(pathname) {
  if (!pathname.startsWith(BASE_PATH)) return { error: "outside-basepath" };
  let rel = pathname.slice(BASE_PATH.length);
  if (rel === "" || rel === "/") return { file: path.join(OUT_DIR, "index.html") };
  if (rel.endsWith("/")) {
    const f = path.join(OUT_DIR, rel, "index.html");
    return fs.existsSync(f) ? { file: f } : { error: "missing" };
  }
  const candidates = [
    path.join(OUT_DIR, rel),
    path.join(OUT_DIR, rel + ".html"),
    path.join(OUT_DIR, rel, "index.html"),
  ];
  for (const c of candidates) {
    if (fs.existsSync(c) && fs.statSync(c).isFile()) return { file: c };
  }
  return { error: "missing" };
}

const idCache = new Map();
function getIds(file) {
  if (idCache.has(file)) return idCache.get(file);
  const html = fs.readFileSync(file, "utf8");
  const ids = extractIds(html);
  idCache.set(file, ids);
  return ids;
}

const issues = [];
const htmlFiles = walk(OUT_DIR);

for (const htmlFile of htmlFiles) {
  const html = fs.readFileSync(htmlFile, "utf8");
  const hrefs = extractHrefs(html);
  const fromRel = path.relative(OUT_DIR, htmlFile);
  for (const href of hrefs) {
    if (!shouldCheck(href)) continue;
    let abs;
    try {
      abs = resolveAbs(href, htmlFile);
    } catch (e) {
      issues.push({ from: fromRel, href, msg: `unparseable URL (${e.message})` });
      continue;
    }
    if (abs.origin !== ORIGIN) continue;
    if (isAssetPath(abs.pathname)) continue;
    const resolved = resolveTargetFile(abs.pathname);
    if (resolved.error) {
      issues.push({ from: fromRel, href, msg: `404 (${abs.pathname})` });
      continue;
    }
    if (abs.hash) {
      const anchor = decodeURIComponent(abs.hash.slice(1));
      const ids = getIds(resolved.file);
      if (!ids.has(anchor)) {
        issues.push({
          from: fromRel,
          href,
          msg: `missing anchor #${anchor} in ${path.relative(OUT_DIR, resolved.file)}`,
        });
      }
    }
  }
}

if (issues.length) {
  console.error(`✘ Found ${issues.length} broken internal link(s):`);
  const seen = new Set();
  for (const i of issues) {
    const key = `${i.from} -> ${i.href}`;
    if (seen.has(key)) continue;
    seen.add(key);
    console.error(`  ${i.from}`);
    console.error(`    -> ${i.href}`);
    console.error(`    ${i.msg}`);
  }
  process.exit(1);
}

console.log(`✓ Link check passed (${htmlFiles.length} html files scanned)`);
