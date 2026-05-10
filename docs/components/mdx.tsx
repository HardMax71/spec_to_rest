import defaultMdxComponents from "fumadocs-ui/mdx";
import type { MDXComponents } from "mdx/types";
import { CliRun, CliRunInline } from "@/components/mdx/cli-run";
import { Collapsible } from "@/components/mdx/collapsible";
import { Mermaid } from "@/components/mdx/mermaid";
import { RailroadDiagram } from "@/components/mdx/railroad-diagram";
import {
  FileTree,
  FileTreeFolder,
  FileTreeRow,
  FileTreeDetails,
} from "@/components/mdx/file-tree";

export function getMDXComponents(components?: MDXComponents): MDXComponents {
  return {
    ...defaultMdxComponents,
    CliRun,
    CliRunInline,
    Collapsible,
    Mermaid,
    RailroadDiagram,
    FileTree,
    FileTreeFolder,
    FileTreeRow,
    FileTreeDetails,
    ...components,
  };
}
