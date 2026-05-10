import { defineConfig, defineDocs } from "fumadocs-mdx/config";
import { remarkMdxMermaid } from "fumadocs-core/mdx-plugins";
import remarkGithub from "remark-github";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeExternalLinks from "rehype-external-links";
import {
  transformerNotationDiff,
  transformerNotationFocus,
  transformerNotationHighlight,
} from "@shikijs/transformers";
import { antlr4Grammar, specGrammar } from "./lib/grammars";
import remarkCliRun from "./lib/remark-cli-run";
import remarkRepoLinks from "./lib/remark-repo-links";
import remarkTreeBlock from "./lib/remark-tree-block";
import remarkUnlinkInHeadings from "./lib/remark-unlink-in-headings";
import { createCustomRehypeCode } from "./lib/rehype-code-factory";

const customRehypeCode = createCustomRehypeCode([antlr4Grammar, specGrammar]);

export const docs = defineDocs({
  dir: "content/docs",
});

export default defineConfig({
  mdxOptions: {
    format: "md",
    remarkPlugins: [
      remarkMath,
      remarkMdxMermaid,
      remarkTreeBlock,
      remarkCliRun,
      remarkRepoLinks,
      [
        remarkGithub,
        {
          repository: "HardMax71/spec_to_rest",
          mentionStrong: false,
        },
      ],
      remarkUnlinkInHeadings,
    ],
    rehypeCodeOptions: false,
    rehypePlugins: (v) => [
      rehypeKatex,
      ...v,
      [
        customRehypeCode,
        {
          themes: { light: "github-light", dark: "github-dark" },
          defaultColor: false,
          defaultLanguage: "spec",
          transformers: [
            transformerNotationDiff(),
            transformerNotationHighlight(),
            transformerNotationFocus(),
          ],
        },
      ],
      [
        rehypeExternalLinks,
        {
          target: "_blank",
          rel: ["noopener", "noreferrer"],
          test: (element: { properties?: { href?: unknown } }) => {
            const href = element.properties?.href;
            return typeof href === "string" && /^https?:\/\//.test(href);
          },
        },
      ],
    ],
  },
});
