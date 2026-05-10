import type { LanguageRegistration } from "shiki";

export const antlr4Grammar: LanguageRegistration = {
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

export const specGrammar: LanguageRegistration = {
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
