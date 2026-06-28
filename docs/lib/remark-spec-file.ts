import type { Plugin } from "unified";
import type { Code, Root } from "mdast";
import { visit } from "unist-util-visit";
import { readFileSync } from "node:fs";
import path from "node:path";

const REPO_ROOT = path.resolve(process.cwd(), "..");

const remarkSpecFile: Plugin<[], Root> = () => (tree, file) => {
  visit(tree, "code", (node: Code) => {
    const match = node.meta?.match(/\bfile="([^"]+)"/);
    if (!match) return;
    const abs = path.join(REPO_ROOT, match[1]);
    try {
      node.value = readFileSync(abs, "utf8").replace(/\s+$/, "");
    } catch {
      file.fail(`remark-spec-file: cannot read transcluded file "${match[1]}"`, node);
    }
    const rest = node.meta!.replace(/\bfile="[^"]+"/, "").trim();
    node.meta = rest.length > 0 ? rest : null;
  });
};

export default remarkSpecFile;
