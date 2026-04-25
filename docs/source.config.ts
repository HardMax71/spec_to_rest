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
import remarkTreeBlock from "./lib/remark-tree-block";
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

const specGrammar: LanguageRegistration = {
  name: "spec",
  scopeName: "source.spec",
  aliases: ["specrest"],
  patterns: [
    { name: "comment.line.double-slash.spec", match: "//.*$" },
    { name: "comment.block.spec", begin: "/\\*", end: "\\*/" },
    {
      name: "string.quoted.double.spec",
      begin: "\"",
      end: "\"",
      patterns: [{ match: "\\\\.", name: "constant.character.escape.spec" }],
    },
    {
      name: "string.regexp.spec",
      begin: "/(?=[^/\\s])",
      end: "/[a-z]*",
      patterns: [{ match: "\\\\.", name: "constant.character.escape.spec" }],
    },
    { name: "constant.numeric.spec", match: "\\b-?[0-9]+(\\.[0-9]+)?\\b" },
    { name: "constant.language.spec", match: "\\b(true|false|none)\\b" },
    {
      name: "keyword.control.spec",
      match:
        "\\b(service|entity|enum|type|operation|invariant|state|input|output|transition|temporal|when|where|extends|via|conventions|fact|field|function|predicate|requires|ensures|implies|iff|if|then|else|let|with|the|pre|matches|always|eventually|fairness|before|after|until|since|import|return)\\b",
    },
    {
      name: "keyword.operator.word.spec",
      match: "\\b(and|or|not|in|subset|union|intersect|minus|all|some|no|exists|one|lone)\\b",
    },
    {
      name: "support.type.spec",
      match: "\\b(Int|Bool|String|Float|DateTime|Date|Email|URL|Money|Set|Map|Seq|Option|UUID|Decimal)\\b",
    },
    {
      name: "entity.name.type.spec",
      match: "\\b[A-Z][A-Za-z0-9_]*\\b",
    },
    {
      name: "keyword.operator.symbolic.spec",
      match: "==|!=|<=|>=|=>|<=>|->|::|\\+\\+|--|&|\\^|#|<|>|=|\\+|\\*|/|\\?|'",
    },
    { name: "punctuation.terminator.spec", match: ";|," },
    { name: "punctuation.section.spec", match: "[(){}\\[\\]]" },
    { name: "variable.other.spec", match: "\\b[a-z_][A-Za-z0-9_]*\\b" },
  ],
  repository: {},
};

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
