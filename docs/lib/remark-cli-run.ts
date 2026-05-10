import type { Plugin } from "unified";
import type { Code, Parents, Root } from "mdast";
import { visitParents } from "unist-util-visit-parents";
import { createHash } from "node:crypto";

interface JsxAttribute {
  type: "mdxJsxAttribute";
  name: string;
  value: string;
}

interface JsxFlowElement {
  type: "mdxJsxFlowElement";
  name: string;
  attributes: JsxAttribute[];
  children: unknown[];
}

function parseDirective(meta: string | null | undefined): {
  command: string;
  flags: string;
} | null {
  if (!meta) return null;
  const runM = meta.match(/\brun=([\w-]+)/);
  if (!runM) return null;
  const flagsM = meta.match(/\bflags="([^"]*)"/);
  return {
    command: runM[1],
    flags: flagsM ? flagsM[1] : "",
  };
}

function snippetId(value: string, command: string, flags: string): string {
  return createHash("sha256")
    .update(`inline|${value}|${command}|${flags}`)
    .digest("hex")
    .slice(0, 16);
}

const remarkCliRun: Plugin<[], Root> = () => (tree) => {
  visitParents(tree, "code", (node: Code, ancestors: Parents[]) => {
    if (node.lang !== "spec") return;
    const directive = parseDirective(node.meta);
    if (!directive) return;

    const id = snippetId(node.value, directive.command, directive.flags);
    const replacement: JsxFlowElement = {
      type: "mdxJsxFlowElement",
      name: "CliRunInline",
      attributes: [
        { type: "mdxJsxAttribute", name: "id", value: id },
        { type: "mdxJsxAttribute", name: "command", value: directive.command },
        { type: "mdxJsxAttribute", name: "flags", value: directive.flags },
        { type: "mdxJsxAttribute", name: "spec", value: node.value },
      ],
      children: [],
    };
    const parent = ancestors[ancestors.length - 1] as Parents | undefined;
    if (!parent || !("children" in parent) || !Array.isArray(parent.children)) {
      return;
    }
    const children = parent.children as unknown[];
    const idx = children.indexOf(node);
    if (idx >= 0) {
      children.splice(idx, 1, replacement);
    }
  });
};

export default remarkCliRun;
