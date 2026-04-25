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
import type { LanguageRegistration } from "shiki";
import remarkRepoLinks from "./lib/remark-repo-links";
import { createCustomRehypeCode } from "./lib/rehype-code-factory";

const antlr4Grammar: LanguageRegistration = {
  name: "antlr4",
  scopeName: "source.antlr",
  aliases: ["antlr", "g4"],
  patterns: [
    { name: "comment.block.antlr", begin: "/\\*", end: "\\*/" },
    { name: "comment.line.antlr", match: "//.*$" },
    {
      name: "string.quoted.single.antlr",
      begin: "'",
      end: "'",
      patterns: [{ match: "\\\\.", name: "constant.character.escape.antlr" }],
    },
    {
      name: "keyword.other.antlr",
      match:
        "\\b(grammar|lexer|parser|fragment|returns|locals|options|tokens|channels|import|mode)\\b",
    },
    { name: "entity.name.function.lexer.antlr", match: "\\b[A-Z][A-Za-z_0-9]*\\b" },
    { name: "entity.name.function.parser.antlr", match: "\\b[a-z][A-Za-z_0-9]*\\b" },
    { name: "entity.name.tag.antlr", match: "#\\s*[A-Za-z_][A-Za-z_0-9]*" },
    { name: "entity.other.block.antlr", begin: "\\{", end: "\\}", patterns: [{ include: "$self" }] },
    { name: "keyword.operator.antlr", match: "->|=>|::|[|;:?+*~]" },
  ],
  repository: {},
};

const customRehypeCode = createCustomRehypeCode([antlr4Grammar]);

export const docs = defineDocs({
  dir: "content/docs",
});

export default defineConfig({
  mdxOptions: {
    format: "md",
    remarkPlugins: [
      remarkMath,
      remarkMdxMermaid,
      remarkRepoLinks,
      [
        remarkGithub,
        {
          repository: "HardMax71/spec_to_rest",
          mentionStrong: false,
        },
      ],
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
          content: { type: "text", value: " ↗" },
          test: (element: { properties?: { href?: unknown } }) => {
            const href = element.properties?.href;
            return typeof href === "string" && /^https?:\/\//.test(href);
          },
        },
      ],
    ],
  },
});
