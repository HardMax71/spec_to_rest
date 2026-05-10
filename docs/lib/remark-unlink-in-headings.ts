import type { Plugin } from "unified";
import type { Heading, Root } from "mdast";
import { visit } from "unist-util-visit";

interface ParentLike {
  children?: unknown[];
}

function unwrapLinks(node: ParentLike): void {
  const children = node.children;
  if (!Array.isArray(children)) return;
  const next: unknown[] = [];
  for (const child of children) {
    if (child && typeof child === "object" && (child as { type?: string }).type === "link") {
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
