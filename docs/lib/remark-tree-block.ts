import type { Plugin } from "unified";
import type { Root, Code, Parent } from "mdast";
import { visit } from "unist-util-visit";

interface TreeNode {
  name: string;
  path: string;
  note?: string;
  isFolder: boolean;
  children: TreeNode[];
  jsx: ReturnType<typeof jsxNode>;
}

const PIPE_INDENT = "│   ";
const SPACE_INDENT = "    ";
const BRANCH = /^(?:├──|└──)\s+/;

function depthOf(line: string): number {
  let depth = 0;
  let i = 0;
  while (i < line.length) {
    if (line.slice(i, i + 4) === PIPE_INDENT) {
      depth++;
      i += 4;
    } else if (line.slice(i, i + 4) === SPACE_INDENT) {
      depth++;
      i += 4;
    } else {
      break;
    }
  }
  return depth;
}

function parseLine(raw: string): { depth: number; name: string; note?: string; isFolder: boolean } | null {
  const trimmedRight = raw.replace(/\s+$/, "");
  if (!trimmedRight) return null;

  const depth = depthOf(trimmedRight);
  const after = trimmedRight.slice(depth * 4);
  const cleaned = after.replace(BRANCH, "");
  if (!cleaned) return null;

  let name = cleaned;
  let note: string | undefined;
  const hashIdx = cleaned.search(/\s+#\s+/);
  if (hashIdx > 0) {
    name = cleaned.slice(0, hashIdx).trim();
    note = cleaned.slice(hashIdx).replace(/^\s+#\s+/, "").trim();
  }
  const isRoot = depth === 0 && !BRANCH.test(after);
  const isFolder = name.endsWith("/");

  return {
    depth: isRoot ? 0 : depth + 1,
    name,
    note,
    isFolder,
  };
}

function attr(name: string, value: unknown) {
  if (value === undefined || value === null || value === false) return null;
  if (value === true) {
    return { type: "mdxJsxAttribute", name, value: null };
  }
  return { type: "mdxJsxAttribute", name, value: String(value) };
}

function jsxNode(name: string, attrs: ReturnType<typeof attr>[], children: unknown[] = []) {
  return {
    type: "mdxJsxFlowElement",
    name,
    attributes: attrs.filter(Boolean),
    children,
  };
}

function buildJsx(node: TreeNode): ReturnType<typeof jsxNode> {
  if (node.isFolder) {
    const folderJsx = jsxNode(
      "FileTreeFolder",
      [attr("name", node.name), attr("note", node.note)],
      node.children.map((c) => {
        buildJsx(c);
        return c.jsx;
      }),
    );
    node.jsx = folderJsx;
    return folderJsx;
  }
  const rowJsx = jsxNode("FileTreeRow", [attr("name", node.name), attr("note", node.note)]);
  node.jsx = rowJsx;
  return rowJsx;
}

function parseTree(value: string): { root: TreeNode | null; flat: TreeNode[] } {
  const lines = value.split("\n").filter((l) => l.trim().length > 0);
  const flat: TreeNode[] = [];
  if (!lines.length) return { root: null, flat };

  const stack: { node: TreeNode; depth: number }[] = [];
  let root: TreeNode | null = null;

  for (const line of lines) {
    const parsed = parseLine(line);
    if (!parsed) continue;

    const node: TreeNode = {
      name: parsed.name,
      path: parsed.name,
      note: parsed.note,
      isFolder: parsed.isFolder,
      children: [],
      jsx: { type: "mdxJsxFlowElement", name: "", attributes: [], children: [] },
    };
    flat.push(node);

    if (parsed.depth === 0 && !root) {
      root = node;
      stack.push({ node, depth: 0 });
      continue;
    }

    while (stack.length && stack[stack.length - 1].depth >= parsed.depth) {
      stack.pop();
    }
    const parent = stack.length ? stack[stack.length - 1].node : root;
    if (parent) {
      const parentBase = parent.path.replace(/\/+$/, "");
      node.path = parentBase ? `${parentBase}/${node.name}` : node.name;
      parent.children.push(node);
    }
    if (node.isFolder) stack.push({ node, depth: parsed.depth });
  }

  if (root) buildJsx(root);
  return { root, flat };
}

interface DetailMeta {
  file?: string;
  lang?: string;
  description?: string;
  source?: string;
}

function parseDetail(value: string): DetailMeta {
  const meta: DetailMeta = {};
  const sepIdx = value.search(/^---\s*$/m);
  let header: string;
  let body: string;
  if (sepIdx >= 0) {
    header = value.slice(0, sepIdx);
    const after = value.slice(sepIdx).replace(/^---\s*\r?\n?/, "");
    body = after;
  } else {
    header = value;
    body = "";
  }
  for (const raw of header.split("\n")) {
    const line = raw.trim();
    if (!line) continue;
    const m = line.match(/^(file|lang|description)\s*:\s*(.+)$/);
    if (!m) continue;
    const k = m[1] as keyof DetailMeta;
    const v = m[2].trim().replace(/^"(.*)"$/, "$1");
    meta[k] = v;
  }
  if (body) meta.source = body.replace(/^\r?\n+/, "").replace(/\s+$/, "");
  return meta;
}

function processContainer(parent: Parent) {
  const children = parent.children as unknown[];
  for (let i = 0; i < children.length; i++) {
    const node = children[i] as Code;
    if (node?.type !== "code" || node.lang !== "tree") continue;

    const { root, flat } = parseTree(node.value);

    let detailEnd = i + 1;
    while (detailEnd < children.length) {
      const c = children[detailEnd] as Code;
      if (c?.type === "code" && c.lang === "tree-detail") {
        detailEnd++;
        continue;
      }
      break;
    }

    if (!root) {
      children.splice(i, detailEnd - i);
      i -= 1;
      continue;
    }

    const byPath = new Map<string, TreeNode>();
    const byName = new Map<string, TreeNode>();
    for (const n of flat) {
      byPath.set(n.path, n);
      if (!byName.has(n.name)) byName.set(n.name, n);
    }

    for (let j = i + 1; j < detailEnd; j++) {
      const det = children[j] as Code;
      const meta = parseDetail(det.value);
      const target =
        (meta.file && (byPath.get(meta.file) || byName.get(meta.file))) ||
        undefined;
      if (!target) continue;
      if (meta.description) {
        target.jsx.attributes.push(attr("description", meta.description) as never);
      }
      if (meta.source) {
        target.jsx.attributes.push(attr("source", meta.source) as never);
        target.jsx.attributes.push(attr("sourceLang", meta.lang ?? "text") as never);
      }
    }

    const wrapper = jsxNode("FileTree", [], [root.jsx]);
    children.splice(i, detailEnd - i, wrapper as never);
  }
}

const remarkTreeBlock: Plugin<[], Root> = () => {
  return (tree) => {
    visit(tree, (node) => {
      if (node && (node as Parent).children !== undefined) {
        processContainer(node as Parent);
      }
    });

    visit(tree, "code", (node, index, parent) => {
      if (node.lang !== "tree-detail") return;
      if (!parent || index === undefined) return;
      (parent.children as unknown[]).splice(index, 1);
    });
  };
};

export default remarkTreeBlock;
