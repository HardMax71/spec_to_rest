import type { Plugin } from "unified";
import type { Root } from "mdast";
import { visit } from "unist-util-visit";
import fs from "node:fs";
import path from "node:path";

const REPO_ROOT = path.resolve(process.cwd(), "..");
const BLOB_BASE = "https://github.com/HardMax71/spec_to_rest/blob/main/";
const TREE_BASE = "https://github.com/HardMax71/spec_to_rest/tree/main/";

const PATH_PREFIXES = ["modules/", "fixtures/", "docs/", "project/"];
const ROOT_FILES = new Set([
  "build.sbt",
  "CLAUDE.md",
  "README.md",
  ".scalafmt.conf",
  ".scalafix.conf",
]);

function looksLikeRepoPath(value: string): boolean {
  if (/\s/.test(value)) return false;
  if (value.startsWith("./") || value.startsWith("../")) return false;
  if (value.includes("*") || value.includes("?")) return false;
  return PATH_PREFIXES.some((p) => value.startsWith(p)) || ROOT_FILES.has(value);
}

const remarkRepoLinks: Plugin<[], Root> = () => {
  return (tree, file) => {
    visit(tree, "inlineCode", (node, index, parent) => {
      if (!parent || index === undefined) return;
      const value = node.value.trim();
      if (!looksLikeRepoPath(value)) return;

      const cleanPath = value.replace(/[#?].*$/, "");
      const abs = path.join(REPO_ROOT, cleanPath);
      if (!fs.existsSync(abs)) {
        const message = `remark-repo-links: path not found in repo: ${cleanPath}`;
        if (process.env.DOCS_STRICT_LINKS === "1") {
          file.fail(message, node);
        } else {
          file.message(message, node);
        }
        return;
      }
      const stat = fs.statSync(abs);
      const base = stat.isDirectory() ? TREE_BASE : BLOB_BASE;
      const url = base + cleanPath;

      parent.children[index] = {
        type: "link",
        url,
        children: [{ type: "inlineCode", value }],
      } as never;
    });
  };
};

export default remarkRepoLinks;
