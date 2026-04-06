import { defineConfig, defineDocs } from "fumadocs-mdx/config";
import { remarkMdxMermaid } from "fumadocs-core/mdx-plugins";
import type { LanguageRegistration } from "shiki";

// Minimal TextMate grammar for ANTLR4 .g4 syntax highlighting.
// Covers keywords, lexer/parser rule names, strings, comments, and actions.
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

export const docs = defineDocs({
  dir: "content/docs",
});

export default defineConfig({
  mdxOptions: {
    format: "md",
    remarkPlugins: [remarkMdxMermaid],
    rehypeCodeOptions: {
      themes: { light: "github-light", dark: "github-dark" },
      defaultColor: false,
      langs: [antlr4Grammar],
    },
  },
});
