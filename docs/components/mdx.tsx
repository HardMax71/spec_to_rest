import defaultMdxComponents from "fumadocs-ui/mdx";
import type { MDXComponents } from "mdx/types";
import { Mermaid } from "@/components/mdx/mermaid";
import {
  FileTree,
  FileTreeFolder,
  FileTreeRow,
  FileTreeDetails,
} from "@/components/mdx/file-tree";

export function getMDXComponents(components?: MDXComponents): MDXComponents {
  return {
    ...defaultMdxComponents,
    Mermaid,
    FileTree,
    FileTreeFolder,
    FileTreeRow,
    FileTreeDetails,
    ...components,
  };
}
