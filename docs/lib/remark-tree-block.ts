import type { Plugin } from "unified";
import type { Root, Code } from "mdast";
import { visit } from "unist-util-visit";

interface TreeNode {
  name: string;
  note?: string;
  isFolder: boolean;
  children: TreeNode[];
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

function parseTree(value: string): TreeNode | null {
  const lines = value.split("\n").filter((l) => l.trim().length > 0);
  if (!lines.length) return null;

  const stack: { node: TreeNode; depth: number }[] = [];
  let root: TreeNode | null = null;

  for (const line of lines) {
    const parsed = parseLine(line);
    if (!parsed) continue;

    const node: TreeNode = {
      name: parsed.name,
      note: parsed.note,
      isFolder: parsed.isFolder,
      children: [],
    };

    if (parsed.depth === 0 && !root) {
      root = node;
      stack.push({ node, depth: 0 });
      continue;
    }

    while (stack.length && stack[stack.length - 1].depth >= parsed.depth) {
      stack.pop();
    }
    const parent = stack.length ? stack[stack.length - 1].node : root;
    if (parent) parent.children.push(node);
    if (node.isFolder) stack.push({ node, depth: parsed.depth });
  }

  return root;
}

function attr(name: string, value: string) {
  return { type: "mdxJsxAttribute", name, value };
}

function jsxNode(name: string, attrs: ReturnType<typeof attr>[], children: unknown[] = []) {
  return {
    type: "mdxJsxFlowElement",
    name,
    attributes: attrs,
    children,
  };
}

function toJsx(node: TreeNode): unknown {
  const attrs = [attr("name", node.name)];
  if (node.note) attrs.push(attr("note", node.note));

  if (node.isFolder) {
    return jsxNode(
      "FileTreeFolder",
      attrs,
      node.children.map(toJsx),
    );
  }
  return jsxNode("FileTreeRow", attrs, []);
}

const remarkTreeBlock: Plugin<[], Root> = () => {
  return (tree) => {
    visit(tree, "code", (node: Code, index, parent) => {
      if (node.lang !== "tree" || !parent || index === undefined) return;
      const root = parseTree(node.value);
      if (!root) return;

      const wrapper = jsxNode("FileTree", [], [toJsx(root)]);
      parent.children[index] = wrapper as never;
    });
  };
};

export default remarkTreeBlock;
