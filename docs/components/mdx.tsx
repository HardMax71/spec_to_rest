import defaultMdxComponents from "fumadocs-ui/mdx";
import type { MDXComponents } from "mdx/types";
import { Mermaid } from "@/components/mdx/mermaid";
import {
  FileTree,
  FileTreeFolder,
  FileTreeRow,
} from "@/components/mdx/file-tree";
import { APIPage } from "@/lib/openapi";

export function getMDXComponents(components?: MDXComponents): MDXComponents {
  return {
    ...defaultMdxComponents,
    Mermaid,
    APIPage,
    FileTree,
    FileTreeFolder,
    FileTreeRow,
    ...components,
  };
}
