import type { Plugin } from "unified";
import type { Root, Parents } from "mdast";
import { visitParents } from "unist-util-visit-parents";
import fs from "node:fs";
import path from "node:path";

const REPO_ROOT = path.resolve(process.cwd(), "..");
const BLOB_BASE = "https://github.com/HardMax71/spec_to_rest/blob/main/";
const TREE_BASE = "https://github.com/HardMax71/spec_to_rest/tree/main/";

const PATH_PREFIXES = ["modules/", "fixtures/", "docs/", "project/"];

const statCache = new Map<string, { exists: boolean; isDirectory: boolean }>();

function statOnce(absolute: string) {
  const cached = statCache.get(absolute);
  if (cached) return cached;
  let entry: { exists: boolean; isDirectory: boolean };
  try {
    const s = fs.statSync(absolute);
    entry = { exists: true, isDirectory: s.isDirectory() };
  } catch {
    entry = { exists: false, isDirectory: false };
  }
  statCache.set(absolute, entry);
  return entry;
}
const ROOT_FILES = new Set([
  "build.sbt",
  "CLAUDE.md",
  "README.md",
  ".scalafmt.conf",
  ".scalafix.conf",
]);

function shortenLabel(p: string): string {
  // modules/<m>/src/main/scala/specrest/[<m>/]<rest>  →  <m>/<rest>
  const scalaMain = p.match(
    /^modules\/([^/]+)\/src\/main\/scala\/specrest\/(?:\1\/)?(.+)$/,
  );
  if (scalaMain) return `${scalaMain[1]}/${scalaMain[2]}`;
  // modules/<m>/src/test/scala/specrest/[<m>/]<rest>  →  <m>/test/<rest>
  const scalaTest = p.match(
    /^modules\/([^/]+)\/src\/test\/scala\/specrest\/(?:\1\/)?(.+)$/,
  );
  if (scalaTest) return `${scalaTest[1]}/test/${scalaTest[2]}`;
  // modules/<m>/src/main/resources/<rest>             →  <m>/resources/<rest>
  const resources = p.match(
    /^modules\/([^/]+)\/src\/main\/resources\/(.+)$/,
  );
  if (resources) return `${resources[1]}/resources/${resources[2]}`;
  // modules/<m>/src/main/<other>/<rest>               →  <m>/<rest>  (e.g. antlr4)
  const otherMain = p.match(/^modules\/([^/]+)\/src\/main\/[^/]+\/(.+)$/);
  if (otherMain) return `${otherMain[1]}/${otherMain[2]}`;
  return p;
}

function looksLikeRepoPath(value: string): boolean {
  if (/\s/.test(value)) return false;
  if (value.startsWith("./") || value.startsWith("../")) return false;
  if (value.includes("*") || value.includes("?")) return false;
  return PATH_PREFIXES.some((p) => value.startsWith(p)) || ROOT_FILES.has(value);
}

const remarkRepoLinks: Plugin<[], Root> = () => {
  return (tree, file) => {
    visitParents(tree, "inlineCode", (node, ancestors: Parents[]) => {
      if (ancestors.some((a) => a.type === "link" || a.type === "linkReference")) {
        return;
      }
      const parent = ancestors[ancestors.length - 1];
      if (!parent || !("children" in parent)) return;
      const index = parent.children.indexOf(node as never);
      if (index < 0) return;

      const value = node.value.trim();
      if (!looksLikeRepoPath(value)) return;

      const cleanPath = value.replace(/[#?].*$/, "");
      const abs = path.join(REPO_ROOT, cleanPath);
      const stat = statOnce(abs);
      if (!stat.exists) {
        const message = `remark-repo-links: path not found in repo: ${cleanPath}`;
        if (process.env.DOCS_STRICT_LINKS === "1") {
          file.fail(message, node);
        } else {
          file.message(message, node);
        }
        return;
      }
      const base = stat.isDirectory ? TREE_BASE : BLOB_BASE;
      const url = base + cleanPath;

      const label = shortenLabel(cleanPath);
      parent.children[index] = {
        type: "link",
        url,
        title: cleanPath,
        children: [{ type: "inlineCode", value: label }],
      } as never;
    });
  };
};

export default remarkRepoLinks;
