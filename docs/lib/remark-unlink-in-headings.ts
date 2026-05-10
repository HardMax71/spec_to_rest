import type { Plugin } from "unified";
import type { Heading, Root } from "mdast";
import { visit } from "unist-util-visit";

interface ParentLike {
  children?: unknown[];
}

const LINK_NODE_TYPES = new Set(["link", "linkReference"]);

function unwrapLinks(node: ParentLike): void {
  const children = node.children;
  if (!Array.isArray(children)) return;
  const next: unknown[] = [];
  for (const child of children) {
    const childType =
      child && typeof child === "object" ? (child as { type?: string }).type : undefined;
    if (childType && LINK_NODE_TYPES.has(childType)) {
      const linkChildren = (child as { children?: unknown[] }).children;
      if (Array.isArray(linkChildren)) {
        for (const lc of linkChildren) {
          unwrapLinks(lc as ParentLike);
          next.push(lc);
        }
      }
      continue;
    }
    unwrapLinks(child as ParentLike);
    next.push(child);
  }
  node.children = next;
}

const remarkUnlinkInHeadings: Plugin<[], Root> = () => (tree) => {
  visit(tree, "heading", (heading: Heading) => {
    unwrapLinks(heading as unknown as ParentLike);
  });
};

export default remarkUnlinkInHeadings;
