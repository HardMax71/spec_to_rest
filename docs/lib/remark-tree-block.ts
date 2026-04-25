import type { Plugin } from "unified";
import type { Root, Code, Parent } from "mdast";

interface TreeNode {
  name: string;
  path: string;
  note?: string;
  isFolder: boolean;
  children: TreeNode[];
  jsx: JsxElement;
}

interface JsxAttribute {
  type: "mdxJsxAttribute";
  name: string;
  value: string | null;
}

interface JsxElement {
  type: "mdxJsxFlowElement";
  name: string;
  attributes: JsxAttribute[];
  children: unknown[];
}

const PIPE_INDENT = "│   ";
const SPACE_INDENT = "    ";
const BRANCH = /^(?:├──|└──)\s+/;

function depthOf(line: string): number {
  let depth = 0;
  let i = 0;
  while (i < line.length) {
    const slice = line.slice(i, i + 4);
    if (slice === PIPE_INDENT || slice === SPACE_INDENT) {
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

function attr(name: string, value: string | undefined): JsxAttribute | null {
  if (value === undefined || value === null) return null;
  return { type: "mdxJsxAttribute", name, value };
}

function jsxNode(name: string, attrs: (JsxAttribute | null)[], children: unknown[] = []): JsxElement {
  const out: JsxAttribute[] = [];
  for (const a of attrs) if (a) out.push(a);
  return { type: "mdxJsxFlowElement", name, attributes: out, children };
}

function buildJsxIterative(root: TreeNode): void {
  const order: TreeNode[] = [];
  const stack: TreeNode[] = [root];
  while (stack.length) {
    const n = stack.pop() as TreeNode;
    order.push(n);
    for (const c of n.children) stack.push(c);
  }
  for (let i = order.length - 1; i >= 0; i--) {
    const n = order[i];
    if (n.isFolder) {
      n.jsx = jsxNode(
        "FileTreeFolder",
        [attr("name", n.name), attr("note", n.note)],
        n.children.map((c) => c.jsx),
      );
    } else {
      n.jsx = jsxNode("FileTreeRow", [attr("name", n.name), attr("note", n.note)]);
    }
  }
}

function parseTree(value: string): { root: TreeNode | null; flat: TreeNode[] } {
  const lines = value.split("\n");
  const flat: TreeNode[] = [];
  const stack: { node: TreeNode; depth: number }[] = [];
  let root: TreeNode | null = null;

  for (const line of lines) {
    if (!line.trim()) continue;
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

  if (root) buildJsxIterative(root);
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
    body = value.slice(sepIdx).replace(/^---\s*\r?\n?/, "");
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

function upgradeRowToDetails(node: TreeNode, description?: string, source?: string, lang?: string) {
  const innerChildren: unknown[] = [];
  if (source) {
    innerChildren.push({
      type: "code",
      lang: lang ?? "text",
      meta: null,
      value: source,
    });
  }
  const attrs: (JsxAttribute | null)[] = [
    attr("name", node.name),
    attr("note", node.note),
  ];
  if (description) attrs.push(attr("description", description));
  const detailsJsx = jsxNode("FileTreeDetails", attrs, innerChildren);
  node.jsx.name = detailsJsx.name;
  node.jsx.attributes = detailsJsx.attributes;
  node.jsx.children = detailsJsx.children;
}

function isParent(n: unknown): n is Parent {
  return !!n && typeof n === "object" && Array.isArray((n as Parent).children);
}

function processOneContainer(parent: Parent): Parent[] {
  const newContainers: Parent[] = [];
  const children = parent.children as unknown[];
  let i = 0;
  while (i < children.length) {
    const node = children[i];
    const isCode = (node as Code)?.type === "code";
    const isTree = isCode && (node as Code).lang === "tree";

    if (!isTree) {
      if (isParent(node)) newContainers.push(node);
      i++;
      continue;
    }

    const codeNode = node as Code;
    const { root, flat } = parseTree(codeNode.value);

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
      if (meta.description || meta.source) {
        upgradeRowToDetails(target, meta.description, meta.source, meta.lang);
      }
    }

    const wrapper = jsxNode("FileTree", [], [root.jsx]);
    children.splice(i, detailEnd - i, wrapper as never);
    i++;
  }
  return newContainers;
}

function sweepOrphanDetails(root: Root): void {
  const stack: Parent[] = [root as Parent];
  while (stack.length) {
    const parent = stack.pop() as Parent;
    const children = parent.children as unknown[];
    for (let i = children.length - 1; i >= 0; i--) {
      const node = children[i];
      const isCode = (node as Code)?.type === "code";
      if (isCode && (node as Code).lang === "tree-detail") {
        children.splice(i, 1);
        continue;
      }
      if (isParent(node)) stack.push(node);
    }
  }
}

const remarkTreeBlock: Plugin<[], Root> = () => {
  return (tree, file) => {
    const raw = String((file as { value?: unknown }).value ?? "");
    if (!raw.includes("```tree")) return;

    const stack: Parent[] = [tree as Parent];
    while (stack.length) {
      const parent = stack.pop() as Parent;
      const more = processOneContainer(parent);
      for (const m of more) stack.push(m);
    }

    sweepOrphanDetails(tree);
  };
};

export default remarkTreeBlock;
