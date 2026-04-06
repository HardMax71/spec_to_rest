---
title: "Implementation Architecture"
description: "Compiler toolchain, parser technology, IR design, and build plan"
---

> Design document for the compiler toolchain, parser technology, IR design, project structure,
> dependency choices, and build plan. This covers HOW we build the compiler itself -- not what it
> compiles to.

---

## Table of Contents

1. [Language Choice for the Compiler Itself](#1-language-choice-for-the-compiler-itself)
2. [Parser Technology](#2-parser-technology)
3. [Intermediate Representation (IR) Design](#3-intermediate-representation-ir-design)
4. [Solver Integration Architecture](#4-solver-integration-architecture)
5. [Project Structure](#5-project-structure)
6. [CLI Design](#6-cli-design)
7. [Testing Strategy for the Compiler Itself](#7-testing-strategy-for-the-compiler-itself)
8. [Dependency Management and Distribution](#8-dependency-management-and-distribution)
9. [Build Plan with Milestones](#9-build-plan-with-milestones)
10. [Risk Mitigation](#10-risk-mitigation)

---

## 1. Language Choice for the Compiler Itself

The compiler implementation language determines development velocity, integration story with formal
tools, and distribution ergonomics. We evaluate five candidates against nine criteria.

### 1.1 Python

**Parser code example (hand-written recursive descent):**

```python
from dataclasses import dataclass
from typing import List, Optional

@dataclass
class OperationDecl:
    name: str
    inputs: List["Parameter"]
    outputs: List["Parameter"]
    requires: List["Expr"]
    ensures: List["Expr"]

class Parser:
    def __init__(self, tokens: List[Token]):
        self.tokens = tokens
        self.pos = 0

    def parse_operation(self) -> OperationDecl:
        self.expect(TokenKind.KEYWORD, "operation")
        name = self.expect(TokenKind.IDENT).value
        self.expect(TokenKind.LBRACE)
        inputs = self.parse_param_block("input")
        outputs = self.parse_param_block("output")
        requires = self.parse_constraint_block("requires")
        ensures = self.parse_constraint_block("ensures")
        self.expect(TokenKind.RBRACE)
        return OperationDecl(name, inputs, outputs, requires, ensures)

    def parse_constraint_block(self, keyword: str) -> List[Expr]:
        if not self.check(TokenKind.KEYWORD, keyword):
            return []
        self.advance()
        self.expect(TokenKind.COLON)
        constraints = []
        while not self.check(TokenKind.KEYWORD) and not self.check(TokenKind.RBRACE):
            constraints.append(self.parse_expr())
            self.skip_newlines()
        return constraints
```

**Template/emitter code example (Jinja2):**

```python
from jinja2 import Environment, FileSystemLoader

class PythonFastAPIEmitter:
    def __init__(self, ir: ServiceIR):
        self.ir = ir
        self.env = Environment(loader=FileSystemLoader("templates/python"))

    def emit_route(self, op: OperationIR) -> str:
        template = self.env.get_template("route.py.j2")
        return template.render(
            method=op.http_method.lower(),
            path=op.http_path,
            func_name=snake_case(op.name),
            input_model=op.input_model_name,
            output_model=op.output_model_name,
            requires_checks=op.requires,
            status_code=op.http_status_success,
        )
```

**Z3 integration:** Native. The `z3-solver` PyPI package ships prebuilt binaries for all platforms.
The Python API is the primary Z3 interface -- it is the best-documented and most feature-complete
binding. Example:

```python
from z3 import Solver, Int, And, ForAll, Implies, sat

s = Solver()
code_len = Int("code_len")
s.add(And(code_len >= 6, code_len <= 10))
result = s.check()  # sat
```

**LLM API integration:** Best-in-class. The `anthropic` Python SDK is Anthropic's primary SDK.
OpenAI, Cohere, and every other LLM provider ship Python SDKs first. Streaming, tool use, batch APIs
are all production-grade.

**Distribution/packaging:** Weakest dimension. Python packaging is notoriously fragile. Options:
`pip install spec-to-rest` requires users have a working Python environment. `pipx` improves
isolation. PyInstaller or Nuitka can produce single binaries but add 50-100 MB and have
platform-specific quirks. Docker is the reliable fallback.

**Assessment:** Best for prototyping and integration with Z3/LLM ecosystems. The packaging story is
the main liability, mitigated by Docker distribution. Type annotations with mypy provide adequate
safety for a project of this scale.

---

### 1.2 Rust

**Parser code example (using pest PEG parser):**

```rust
use pest::Parser;
use pest_derive::Parser;

#[derive(Parser)]
#[grammar = "dsl.pest"]
struct DslParser;

fn parse_operation(pair: pest::iterators::Pair<Rule>) -> OperationDecl {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str().to_string();
    let mut inputs = Vec::new();
    let mut outputs = Vec::new();
    let mut requires = Vec::new();
    let mut ensures = Vec::new();

    for item in inner {
        match item.as_rule() {
            Rule::input_block => inputs = parse_params(item),
            Rule::output_block => outputs = parse_params(item),
            Rule::requires_block => requires = parse_constraints(item),
            Rule::ensures_block => ensures = parse_constraints(item),
            _ => unreachable!(),
        }
    }
    OperationDecl { name, inputs, outputs, requires, ensures }
}
```

**Template/emitter code example (askama):**

```rust
use askama::Template;

#[derive(Template)]
#[template(path = "route.py.j2")]
struct RouteTemplate<'a> {
    method: &'a str,
    path: &'a str,
    func_name: &'a str,
    input_model: &'a str,
    output_model: &'a str,
    status_code: u16,
}

fn emit_route(op: &OperationIR) -> String {
    let tmpl = RouteTemplate {
        method: &op.http_method.to_lowercase(),
        path: &op.http_path,
        func_name: &snake_case(&op.name),
        input_model: &op.input_model_name,
        output_model: &op.output_model_name,
        status_code: op.http_status_success,
    };
    tmpl.render().unwrap()
}
```

**Z3 integration:** Available via `z3` crate (Rust bindings to the C API). Functional but less
ergonomic than Python. The bindings are maintained but lag behind the Python API in coverage.
Building from source requires LLVM/Clang toolchain.

**LLM API integration:** The `anthropic` Rust crate exists but is community-maintained, not
official. HTTP-level integration via `reqwest` is straightforward but requires implementing
streaming, retry logic, and error handling manually.

**Distribution/packaging:** Excellent. `cargo install spec-to-rest` produces a single static binary.
Cross-compilation via `cross` gives Linux/macOS/Windows from any host. Binary size ~10-20 MB. No
runtime dependencies.

**Assessment:** Best for production distribution and performance. Slower development velocity (2-3x
vs Python for compiler code). The Z3 and LLM integration stories are adequate but not first-class.
Good choice if the project outlives prototyping and performance matters (large specs with many
operations).

---

### 1.3 TypeScript/Node

**Parser code example (hand-written with tokenizer):**

```typescript
interface OperationDecl {
  name: string;
  inputs: Parameter[];
  outputs: Parameter[];
  requires: Expr[];
  ensures: Expr[];
}

class Parser {
  private tokens: Token[];
  private pos: number = 0;

  parseOperation(): OperationDecl {
    this.expect(TokenKind.Keyword, "operation");
    const name = this.expect(TokenKind.Ident).value;
    this.expect(TokenKind.LBrace);
    const inputs = this.parseParamBlock("input");
    const outputs = this.parseParamBlock("output");
    const requires = this.parseConstraintBlock("requires");
    const ensures = this.parseConstraintBlock("ensures");
    this.expect(TokenKind.RBrace);
    return { name, inputs, outputs, requires, ensures };
  }

  private parseConstraintBlock(keyword: string): Expr[] {
    if (!this.check(TokenKind.Keyword, keyword)) return [];
    this.advance();
    this.expect(TokenKind.Colon);
    const constraints: Expr[] = [];
    while (!this.check(TokenKind.Keyword) && !this.check(TokenKind.RBrace)) {
      constraints.push(this.parseExpr());
      this.skipNewlines();
    }
    return constraints;
  }
}
```

**Template/emitter code example (EJS):**

```typescript
import * as ejs from "ejs";
import * as fs from "fs";

class PythonFastAPIEmitter {
  private templates: Map<string, string>;

  constructor(templateDir: string) {
    this.templates = new Map();
    this.templates.set("route", fs.readFileSync(`${templateDir}/route.py.ejs`, "utf-8"));
  }

  emitRoute(op: OperationIR): string {
    return ejs.render(this.templates.get("route")!, {
      method: op.httpMethod.toLowerCase(),
      path: op.httpPath,
      funcName: snakeCase(op.name),
      inputModel: op.inputModelName,
      outputModel: op.outputModelName,
      statusCode: op.httpStatusSuccess,
    });
  }
}
```

**Z3 integration:** The `z3-solver` npm package provides WASM-compiled Z3. It works but is ~3x
slower than native Z3 and has a 35 MB WASM payload. Alternatively, shell out to the Z3 binary.
Neither option is as clean as the Python bindings.

**LLM API integration:** Good. The `@anthropic-ai/sdk` is an official Anthropic package. OpenAI's
Node SDK is equally mature. Streaming with async iterators works well.

**Distribution/packaging:** Decent. `npm install -g spec-to-rest` works for Node users. `pkg` or
`esbuild` can produce single-binary executables (~50-80 MB). Bun offers faster startup and
single-binary distribution.

**Assessment:** Good middle ground. TypeScript provides static typing with fast iteration. The Z3
story is the weakest link. Tree-sitter has native JS bindings. Best choice if the team is JS-native
and prioritizes development speed over solver integration quality.

---

### 1.4 Go

**Parser code example (hand-written):**

```go
type OperationDecl struct {
    Name     string
    Inputs   []Parameter
    Outputs  []Parameter
    Requires []Expr
    Ensures  []Expr
}

func (p *Parser) parseOperation() (*OperationDecl, error) {
    if err := p.expect(TokenKeyword, "operation"); err != nil {
        return nil, err
    }
    name, err := p.expectIdent()
    if err != nil {
        return nil, err
    }
    if err := p.expect(TokenLBrace, ""); err != nil {
        return nil, err
    }
    inputs, err := p.parseParamBlock("input")
    if err != nil {
        return nil, err
    }
    outputs, err := p.parseParamBlock("output")
    if err != nil {
        return nil, err
    }
    requires, err := p.parseConstraintBlock("requires")
    if err != nil {
        return nil, err
    }
    ensures, err := p.parseConstraintBlock("ensures")
    if err != nil {
        return nil, err
    }
    if err := p.expect(TokenRBrace, ""); err != nil {
        return nil, err
    }
    return &OperationDecl{
        Name: name, Inputs: inputs, Outputs: outputs,
        Requires: requires, Ensures: ensures,
    }, nil
}
```

**Template/emitter code example:**

```go
import "text/template"

const routeTemplate = `
@app.{{.Method}}("{{.Path}}", status_code={{.StatusCode}})
async def {{.FuncName}}({{if .InputModel}}body: {{.InputModel}}{{end}}) -> {{.OutputModel}}:
    {{range .RequiresChecks}}
    if not ({{.}}):
        raise HTTPException(status_code=422, detail="Precondition failed: {{.}}")
    {{end}}
    # TODO: implementation
    pass
`

func emitRoute(op *OperationIR) (string, error) {
    tmpl, err := template.New("route").Parse(routeTemplate)
    if err != nil {
        return "", err
    }
    var buf bytes.Buffer
    err = tmpl.Execute(&buf, op)
    return buf.String(), err
}
```

**Z3 integration:** No maintained Go bindings. Must shell out to the `z3` binary or use CGo with the
C API (fragile, breaks cross-compilation). This is a significant gap.

**LLM API integration:** No official Anthropic Go SDK. Community packages exist but are not
feature-complete. HTTP-level integration is straightforward with `net/http`.

**Distribution/packaging:** Excellent. `go install` produces a single static binary.
Cross-compilation is trivial (`GOOS=linux GOARCH=amd64 go build`). Binary size ~15 MB.

**Assessment:** Excellent distribution but poor integration with formal tools and LLM SDKs. The
verbose error handling makes compiler code significantly more boilerplate. Go's lack of sum types
makes IR representation awkward (must use interfaces with type switches). Not recommended for this
project.

---

### 1.5 Kotlin/JVM

**Parser code example (ANTLR4 + Kotlin):**

```kotlin
class SpecIRBuilder : SpecBaseVisitor<Any>() {
    override fun visitOperationDecl(ctx: SpecParser.OperationDeclContext): OperationDecl {
        val name = ctx.IDENT().text
        val inputs = ctx.paramBlock("input")?.let { visitParamBlock(it) } ?: emptyList()
        val outputs = ctx.paramBlock("output")?.let { visitParamBlock(it) } ?: emptyList()
        val requires = ctx.constraintBlock("requires")?.let { visitConstraints(it) } ?: emptyList()
        val ensures = ctx.constraintBlock("ensures")?.let { visitConstraints(it) } ?: emptyList()
        return OperationDecl(name, inputs, outputs, requires, ensures)
    }

    override fun visitConstraintBlock(ctx: SpecParser.ConstraintBlockContext): List<Expr> {
        return ctx.expr().map { visitExpr(it) as Expr }
    }
}
```

**Template/emitter code example (Kotlin + StringTemplate):**

```kotlin
class PythonFastAPIEmitter(private val ir: ServiceIR) {
    fun emitRoute(op: OperationIR): String = buildString {
        appendLine("@app.${op.httpMethod.lowercase()}(\"${op.httpPath}\", status_code=${op.httpStatusSuccess})")
        append("async def ${op.name.toSnakeCase()}(")
        if (op.inputModelName != null) append("body: ${op.inputModelName}")
        appendLine(") -> ${op.outputModelName}:")
        for (req in op.requires) {
            appendLine("    if not (${emitExpr(req)}):")
            appendLine("        raise HTTPException(status_code=422, detail=\"Precondition failed\")")
        }
        appendLine("    # implementation")
    }
}
```

**Z3 integration:** Excellent. Z3 ships official Java bindings (`com.microsoft.z3`). Kotlin
interoperates perfectly with Java libraries. The Z3 Java API mirrors the Python API closely.

**LLM API integration:** No official Anthropic Kotlin/Java SDK. However, the HTTP API is trivial to
call via `ktor-client` or `OkHttp`. Alternatively, use `langchain4j` which wraps Anthropic and
OpenAI with a unified interface.

**Distribution/packaging:** Moderate. GraalVM native-image can produce a single binary (~30-50 MB)
with fast startup. Without GraalVM, requires a JVM (~200 MB). Fat JARs via `shadowJar` are the
standard approach but require Java on the user's machine.

**Z3 + Alloy + Dafny synergy:** This is Kotlin's killer advantage. Alloy Analyzer is a Java library
(`edu.mit.csail.sdg:org.alloytools.alloy`). Kodkod (Alloy's SAT backend) is Java. Dafny has a JVM
compilation target and its API is accessible from the JVM. All three formal tools are natively
available without subprocess calls.

**Assessment:** Strongest integration with formal methods tools (Alloy, Z3, Dafny all JVM-native).
ANTLR4 is the gold standard parser generator and it targets Kotlin/Java natively. Kotlin's sealed
classes provide excellent sum type support for IR representation. The main cost is JVM distribution
weight, mitigated by GraalVM native-image. Recommended if the team is JVM-proficient.

---

### 1.6 Comparative Matrix

| Criterion               | Python         | Rust           | TypeScript     | Go             | Kotlin         |
| ----------------------- | -------------- | -------------- | -------------- | -------------- | -------------- |
| Dev velocity            | 5              | 2              | 4              | 3              | 4              |
| Type safety             | 3 (mypy)       | 5              | 4              | 4              | 5              |
| Parser ecosystem        | 4              | 4              | 3              | 2              | 5 (ANTLR)      |
| Z3 integration          | 5              | 3              | 2              | 1              | 4              |
| Alloy integration       | 2 (subprocess) | 1 (subprocess) | 1 (subprocess) | 1 (subprocess) | 5 (native)     |
| Dafny integration       | 3 (subprocess) | 2 (subprocess) | 2 (subprocess) | 2 (subprocess) | 4 (JVM target) |
| LLM SDK quality         | 5              | 2              | 4              | 2              | 3              |
| Distribution            | 2              | 5              | 3              | 5              | 3 (GraalVM)    |
| IR modeling (sum types) | 3              | 5 (enums)      | 3              | 2              | 5 (sealed)     |
| **Weighted total**      | **32**         | **29**         | **26**         | **22**         | **38**         |

Weights: Parser, Z3, Alloy, Dafny integration weighted 1.5x (core compiler concerns).

### 1.7 Recommendation

**Decision: TypeScript** for the compiler implementation (all phases).

Rationale:

- ANTLR4 is available via the `antlr-ng` TypeScript target, providing a mature parser generator with
  the TypeScript ecosystem's development velocity.
- The `@anthropic-ai/sdk` is an official, production-grade Anthropic package for LLM integration.
  OpenAI's Node SDK is equally mature.
- TypeScript's discriminated union types and interfaces provide good IR modeling.
- Distribution via `npm install -g spec-to-rest` or single-binary via `esbuild`/`bun` is adequate
  for the target audience (developers).
- The `z3-solver` npm package provides WASM-compiled Z3. For heavier workloads, the compiler can
  shell out to the native Z3 binary.
- A single language across the compiler, CLI, and potential IDE extension (LSP server) reduces
  context switching and maximizes code reuse.

Note: The generated code targets (Python/FastAPI, Go/chi, TypeScript/Express) remain unchanged --
those are output targets, not the compiler's own implementation language.

The remainder of this document presents design examples in Python and TypeScript for illustrative
purposes. The production compiler will be TypeScript throughout.

---

## 2. Parser Technology

### 2.1 Hand-Written Recursive Descent

**When is this the right choice?**

When the grammar is small, error messages must be exceptional, and the team wants full control over
recovery strategies. Most production compilers (Go, Rust, Swift, V8's JS parser) use hand-written
recursive descent parsers. The DSL grammar for spec-to-rest is small enough (~30 productions) that a
hand-written parser is viable.

**Estimated code size:** 800-1200 lines for the full grammar (lexer + parser).

**Error recovery strategies:**

- Synchronization: on error, skip tokens until a synchronization point (next `}`, next keyword like
  `operation`, `entity`, `state`)
- Insert missing token: if `{` expected but `IDENT` found, insert `{` and report
- Panic-mode with context: maintain a stack of "currently parsing X" for messages like "expected `:`
  after parameter name in operation `Shorten`"

**Parser sketch for operation declaration:**

```python
class Parser:
    def parse_operation(self) -> OperationDecl:
        """
        operation_decl := 'operation' IDENT '{' param_blocks constraint_blocks '}'
        param_blocks   := (input_block | output_block)*
        input_block    := 'input' ':' param_list
        output_block   := 'output' ':' param_list
        param_list     := param (',' param)*
        param          := IDENT ':' type_expr
        constraint_blocks := (requires_block | ensures_block)*
        requires_block := 'requires' ':' expr_list
        ensures_block  := 'ensures' ':' expr_list
        expr_list      := expr (NEWLINE expr)*
        """
        start = self.current_pos()
        self.expect(TokenKind.KEYWORD, "operation")
        name_tok = self.expect(TokenKind.IDENT)
        self.expect(TokenKind.LBRACE)

        inputs: List[Parameter] = []
        outputs: List[Parameter] = []
        requires: List[Expr] = []
        ensures: List[Expr] = []

        while not self.check(TokenKind.RBRACE) and not self.is_at_end():
            if self.check_keyword("input"):
                inputs = self.parse_input_block()
            elif self.check_keyword("output"):
                outputs = self.parse_output_block()
            elif self.check_keyword("requires"):
                requires = self.parse_requires_block()
            elif self.check_keyword("ensures"):
                ensures = self.parse_ensures_block()
            else:
                self.error_and_sync(
                    f"unexpected {self.current().kind} in operation body; "
                    f"expected 'input', 'output', 'requires', or 'ensures'",
                    sync_to={TokenKind.KEYWORD, TokenKind.RBRACE}
                )

        self.expect(TokenKind.RBRACE)
        return OperationDecl(
            name=name_tok.value,
            inputs=inputs,
            outputs=outputs,
            requires=requires,
            ensures=ensures,
            span=Span(start, self.current_pos()),
        )

    def parse_expr(self, min_prec: int = 0) -> Expr:
        """Pratt parser for expressions with operator precedence."""
        left = self.parse_prefix()

        while not self.is_at_end():
            op = self.current()
            prec = self.precedence(op)
            if prec < min_prec:
                break

            if op.kind == TokenKind.KEYWORD and op.value in ("in", "not"):
                left = self.parse_membership(left)
            elif op.kind == TokenKind.KEYWORD and op.value in ("and", "or"):
                left = self.parse_logical(left, prec)
            elif op.kind in (TokenKind.EQ, TokenKind.NEQ, TokenKind.LT,
                             TokenKind.GT, TokenKind.LTE, TokenKind.GTE):
                left = self.parse_comparison(left, prec)
            elif op.kind == TokenKind.DOT:
                left = self.parse_field_access(left)
            elif op.kind == TokenKind.LBRACKET:
                left = self.parse_index(left)
            elif op.kind in (TokenKind.PLUS, TokenKind.MINUS):
                left = self.parse_arithmetic(left, prec)
            else:
                break

        return left

    def parse_prefix(self) -> Expr:
        tok = self.current()

        if tok.kind == TokenKind.HASH:
            self.advance()
            operand = self.parse_prefix()
            return Cardinality(operand, span=Span(tok.pos, operand.span.end))

        if tok.kind == TokenKind.KEYWORD and tok.value == "pre":
            self.advance()
            self.expect(TokenKind.LPAREN)
            expr = self.parse_expr()
            self.expect(TokenKind.RPAREN)
            return PreState(expr, span=Span(tok.pos, self.prev_pos()))

        if tok.kind == TokenKind.KEYWORD and tok.value == "all":
            return self.parse_quantifier(QuantifierKind.FORALL)

        if tok.kind == TokenKind.KEYWORD and tok.value == "some":
            return self.parse_quantifier(QuantifierKind.EXISTS)

        if tok.kind == TokenKind.IDENT:
            self.advance()
            name = tok.value
            if name.endswith("'"):
                return PostState(Variable(name[:-1]), span=tok.span)
            return Variable(name, span=tok.span)

        if tok.kind == TokenKind.STRING:
            self.advance()
            return Literal(tok.value, LiteralKind.STRING, span=tok.span)

        if tok.kind == TokenKind.NUMBER:
            self.advance()
            return Literal(int(tok.value), LiteralKind.INT, span=tok.span)

        if tok.kind == TokenKind.LPAREN:
            self.advance()
            expr = self.parse_expr()
            self.expect(TokenKind.RPAREN)
            return expr

        self.error(f"expected expression, got {tok.kind}")
```

---

### 2.2 ANTLR4

**Complete grammar for a significant subset of the DSL:**

```text
grammar Spec;

// ===== Parser Rules =====

specFile
    : serviceDecl EOF
    ;

serviceDecl
    : 'service' IDENT '{' serviceBody* '}'
    ;

serviceBody
    : entityDecl
    | stateDecl
    | operationDecl
    | invariantDecl
    | conventionsDecl
    ;

entityDecl
    : 'entity' IDENT '{' entityField* entityInvariant* '}'
    ;

entityField
    : IDENT ':' typeExpr
    ;

entityInvariant
    : 'invariant' ':' expr
    ;

stateDecl
    : 'state' '{' stateRelation* '}'
    ;

stateRelation
    : IDENT ':' typeExpr '->' multiplicity typeExpr     // e.g., store: ShortCode -> lone LongURL
    ;

multiplicity
    : 'one' | 'lone' | 'some' | 'set'
    ;

operationDecl
    : 'operation' IDENT '{' operationBody* '}'
    ;

operationBody
    : inputBlock
    | outputBlock
    | requiresBlock
    | ensuresBlock
    ;

inputBlock
    : 'input' ':' paramList
    ;

outputBlock
    : 'output' ':' paramList
    ;

paramList
    : param (',' param)*
    ;

param
    : IDENT ':' typeExpr
    ;

requiresBlock
    : 'requires' ':' exprList
    ;

ensuresBlock
    : 'ensures' ':' exprList
    ;

exprList
    : expr (NEWLINE expr)*
    ;

invariantDecl
    : 'invariant' ':' expr
    ;

conventionsDecl
    : 'conventions' '{' conventionEntry* '}'
    ;

conventionEntry
    : qualifiedIdent '=' expr                           // Resolve.http_method = GET
    | qualifiedIdent STRING '=' expr                    // Resolve.http_header "Location" = ...
    ;

qualifiedIdent
    : IDENT ('.' IDENT)*
    ;

// ----- Type Expressions -----

typeExpr
    : IDENT                              // simple type: String, Int, ShortCode
    | typeExpr '?'                       // optional: String?
    | 'Set' '<' typeExpr '>'             // set type
    | 'Map' '<' typeExpr ',' typeExpr '>'// map type
    | 'List' '<' typeExpr '>'            // list type
    ;

// ----- Expressions -----

expr
    : expr 'and' expr                    // logical and
    | expr 'or' expr                     // logical or
    | 'not' expr                         // logical not
    | expr ('=' | '!=' | '<' | '>' | '<=' | '>=') expr  // comparison
    | expr ('+' | '-') expr              // arithmetic
    | expr ('*' | '/') expr              // arithmetic
    | expr 'in' expr                     // membership
    | expr 'not' 'in' expr              // non-membership
    | '#' expr                           // cardinality
    | 'pre' '(' expr ')'                // pre-state
    | expr PRIME                          // post-state (store')
    | expr '[' expr ']'                  // indexing
    | expr '.' IDENT                     // field access
    | IDENT '(' exprArgList? ')'         // function call
    | quantifierExpr                     // forall / exists
    | IDENT                              // variable
    | STRING                             // string literal
    | NUMBER                             // number literal
    | BOOL                               // boolean literal
    | '(' expr ')'                       // grouping
    ;

quantifierExpr
    : ('all' | 'some' | 'no') IDENT 'in' expr '|' expr
    ;

exprArgList
    : expr (',' expr)*
    ;

// ===== Lexer Rules =====

PRIME     : '\'' ;
IDENT     : [a-zA-Z_][a-zA-Z0-9_]* ;
STRING    : '"' (~["\\\n] | '\\' .)* '"' ;
NUMBER    : [0-9]+ ('.' [0-9]+)? ;
BOOL      : 'true' | 'false' ;
NEWLINE   : '\r'? '\n' ;
WS        : [ \t]+ -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
```

**Error reporting quality:** ANTLR4 generates decent default error messages. Custom error strategies
can be implemented via `ANTLRErrorStrategy`. The `DefaultErrorStrategy` provides
synchronization-based recovery. For production quality, implement a custom `BailErrorStrategy` that
collects multiple errors before aborting.

**IDE integration:** ANTLR4 grammars can be used to generate TextMate grammars (via `antlr4-tm`) for
VS Code syntax highlighting, and Language Server Protocol (LSP) implementations can use the ANTLR
parser for completions and diagnostics.

---

### 2.3 tree-sitter

**Complete tree-sitter grammar for a significant subset:**

```javascript
// grammar.js
module.exports = grammar({
  name: "spec",

  extras: ($) => [/\s/, $.comment],

  rules: {
    source_file: ($) => $.service_decl,

    service_decl: ($) =>
      seq(
        "service",
        $.identifier,
        "{",
        repeat(
          choice(
            $.entity_decl,
            $.state_decl,
            $.operation_decl,
            $.invariant_decl,
            $.conventions_decl,
          ),
        ),
        "}",
      ),

    entity_decl: ($) =>
      seq("entity", $.identifier, "{", repeat(choice($.entity_field, $.entity_invariant)), "}"),

    entity_field: ($) => seq($.identifier, ":", $.type_expr),

    entity_invariant: ($) => seq("invariant", ":", $._expr),

    state_decl: ($) => seq("state", "{", repeat($.state_relation), "}"),

    state_relation: ($) => seq($.identifier, ":", $.type_expr, "->", $.multiplicity, $.type_expr),

    multiplicity: ($) => choice("one", "lone", "some", "set"),

    operation_decl: ($) =>
      seq(
        "operation",
        $.identifier,
        "{",
        repeat(choice($.input_block, $.output_block, $.requires_block, $.ensures_block)),
        "}",
      ),

    input_block: ($) => seq("input", ":", $.param_list),
    output_block: ($) => seq("output", ":", $.param_list),
    param_list: ($) => seq($.param, repeat(seq(",", $.param))),
    param: ($) => seq($.identifier, ":", $.type_expr),

    requires_block: ($) => seq("requires", ":", $.expr_list),
    ensures_block: ($) => seq("ensures", ":", $.expr_list),
    expr_list: ($) => seq($._expr, repeat($._expr)),

    invariant_decl: ($) => seq("invariant", ":", $._expr),

    conventions_decl: ($) => seq("conventions", "{", repeat($.convention_entry), "}"),

    convention_entry: ($) => seq($.qualified_ident, "=", $._expr),

    qualified_ident: ($) => seq($.identifier, repeat(seq(".", $.identifier))),

    type_expr: ($) =>
      choice(
        $.identifier,
        seq($.type_expr, "?"),
        seq("Set", "<", $.type_expr, ">"),
        seq("Map", "<", $.type_expr, ",", $.type_expr, ">"),
        seq("List", "<", $.type_expr, ">"),
      ),

    _expr: ($) =>
      choice(
        $.binary_expr,
        $.unary_expr,
        $.membership_expr,
        $.cardinality_expr,
        $.pre_state_expr,
        $.post_state_expr,
        $.index_expr,
        $.field_access_expr,
        $.call_expr,
        $.quantifier_expr,
        $.identifier,
        $.string,
        $.number,
        $.boolean,
        $.paren_expr,
      ),

    binary_expr: ($) =>
      choice(
        prec.left(1, seq($._expr, choice("and", "or"), $._expr)),
        prec.left(2, seq($._expr, choice("=", "!=", "<", ">", "<=", ">="), $._expr)),
        prec.left(3, seq($._expr, choice("+", "-"), $._expr)),
        prec.left(4, seq($._expr, choice("*", "/"), $._expr)),
      ),

    unary_expr: ($) => prec(5, seq("not", $._expr)),
    membership_expr: ($) => prec.left(2, seq($._expr, choice("in", seq("not", "in")), $._expr)),
    cardinality_expr: ($) => prec(6, seq("#", $._expr)),
    pre_state_expr: ($) => seq("pre", "(", $._expr, ")"),
    post_state_expr: ($) => seq($.identifier, "'"),
    index_expr: ($) => prec.left(7, seq($._expr, "[", $._expr, "]")),
    field_access_expr: ($) => prec.left(7, seq($._expr, ".", $.identifier)),
    call_expr: ($) => prec(7, seq($.identifier, "(", optional($.arg_list), ")")),
    quantifier_expr: ($) =>
      seq(choice("all", "some", "no"), $.identifier, "in", $._expr, "|", $._expr),
    paren_expr: ($) => seq("(", $._expr, ")"),

    arg_list: ($) => seq($._expr, repeat(seq(",", $._expr))),

    identifier: ($) => /[a-zA-Z_][a-zA-Z0-9_]*/,
    string: ($) => /"([^"\\]|\\.)*"/,
    number: ($) => /\d+(\.\d+)?/,
    boolean: ($) => choice("true", "false"),
    comment: ($) => choice(seq("//", /.*/), seq("/*", /[^*]*\*+([^/*][^*]*\*+)*/, "/")),
  },
});
```

**IDE integration:** Tree-sitter grammars automatically enable syntax highlighting, code folding,
and incremental parsing in editors that support tree-sitter (Neovim, Helix, Zed, and VS Code via the
tree-sitter extension). The incremental parsing is especially valuable for an IDE/LSP scenario where
specs are edited live.

---

### 2.4 PEG Parsers

**pest (Rust) grammar snippet:**

```text
operation_decl = {
    "operation" ~ ident ~ "{" ~ operation_body* ~ "}"
}

operation_body = {
    input_block | output_block | requires_block | ensures_block
}

input_block = {
    "input" ~ ":" ~ param_list
}

requires_block = {
    "requires" ~ ":" ~ expr_list
}

expr_list = {
    expr ~ (NEWLINE ~ expr)*
}

expr = {
    prefix_expr ~ (binary_op ~ prefix_expr)*
}

prefix_expr = {
    "#" ~ atom_expr
  | "not" ~ atom_expr
  | "pre" ~ "(" ~ expr ~ ")"
  | atom_expr
}

atom_expr = {
    ident ~ "'" |               // post-state
    ident ~ "(" ~ arg_list? ~ ")" |  // function call
    ident ~ "[" ~ expr ~ "]" |  // index
    ident |                     // variable
    string | number | boolean |
    "(" ~ expr ~ ")"
}

ident = @{ ASCII_ALPHA ~ (ASCII_ALPHANUMERIC | "_")* }
```

**Limitations:** PEG parsers cannot handle left recursion (`expr = expr "+" expr`). This requires
restructuring the grammar into precedence-climbing or Pratt-style layers. Backtracking in ordered
choice (`/`) can cause exponential parse times on pathological inputs, though pest mitigates this
with packrat memoization.

---

### 2.5 Xtext (Eclipse)

**Grammar snippet:**

```text
grammar org.spectorest.Spec with org.eclipse.xtext.common.Terminals

generate spec "http://www.spectorest.org/Spec"

SpecFile:
    service=ServiceDecl;

ServiceDecl:
    'service' name=ID '{' body+=ServiceBody* '}';

ServiceBody:
    EntityDecl | StateDecl | OperationDecl | InvariantDecl | ConventionsDecl;

OperationDecl:
    'operation' name=ID '{'
        (inputs=InputBlock)?
        (outputs=OutputBlock)?
        (requires=RequiresBlock)?
        (ensures=EnsuresBlock)?
    '}';

InputBlock:
    'input' ':' params+=Param (',' params+=Param)*;

RequiresBlock:
    'requires' ':' constraints+=Expr+;
```

**Pros:** Xtext generates a full Eclipse IDE (editor with syntax highlighting, content assist,
outline view, error markers, quick fixes) automatically from the grammar. It also generates an LSP
server for VS Code. This is the lowest-effort path to a complete IDE experience.

**Cons:** Heavyweight Eclipse/OSGi dependency. The generated LSP server requires a JVM. The Xtext
ecosystem is Eclipse-centric and has declined in mindshare since 2020. Not recommended unless the
team is already in the Eclipse ecosystem.

---

### 2.6 Parser Technology Recommendation

**Decision: ANTLR4 via the `antlr-ng` TypeScript target.**

ANTLR4 is the most mature and capable parser generator available. The `antlr-ng` project provides a
native TypeScript target, enabling direct integration with the TypeScript compiler codebase without
JVM dependencies at build time.

> **Note on Langium:** The DSL framework evaluation (`09_dsl_compiler_frameworks.md`) initially
> recommended Langium (TypeScript-based language workbench by TypeFox) for its integrated LSP
> server, cross-reference resolution, and grammar-to-AST generation. A subsequent devil's advocate
> audit found critical risks: (1) Typir (Langium's companion type system library) cannot handle our
> refinement types, relation types, or quantified expressions, so ~80% of the type checker must be
> built custom regardless; (2) Langium's community is very thin (985 stars, 22 contributors); (3)
> TypeFox's Fastbelt announcement (Go-based, 21-33x faster) signals strategic drift from Langium.
> ANTLR4/antlr-ng was chosen for its battle-tested stability (17k+ stars, 20+ year track record), no
> framework lock-in, and the fact that we must build scoping, type checking, and validation
> ourselves in either case. Langium remains a future option if IDE support (LSP with rich
> completions) becomes a top-3 user requirement and the framework matures further.

| Factor                 | ANTLR4 (antlr-ng)      | Hand-written          | tree-sitter          |
| ---------------------- | ---------------------- | --------------------- | -------------------- |
| Development speed      | High (grammar file)    | Medium (write code)   | High (grammar.js)    |
| Error message quality  | Good (customizable)    | Best (full control)   | Basic                |
| IDE support generation | Good (TextMate export) | Manual                | Excellent (native)   |
| Maintenance burden     | Low (change grammar)   | Medium (change code)  | Low (change grammar) |
| Grammar evolution      | Easy (regenerate)      | Requires code changes | Easy (regenerate)    |
| TypeScript target      | Native (antlr-ng)      | N/A                   | Bindings exist       |

**Phase plan:**

- All phases: Use the ANTLR4 grammar (Section 2.2) with the `antlr-ng` TypeScript runtime. ANTLR4
  generates a robust parser with visitor/listener patterns that integrate cleanly with TypeScript
  interfaces for the IR.
- IDE: Generate a tree-sitter grammar from the ANTLR4 grammar (tools exist for this conversion) for
  editor syntax highlighting. The LSP server will use the ANTLR4 parser directly for completions and
  diagnostics.

---

## 3. Intermediate Representation (IR) Design

The IR is the central nervous system of the compiler. Every stage reads from or writes to the IR. It
must be:

- **Complete**: represent everything in the spec
- **Annotatable**: the convention engine and verifier add information
- **Serializable**: for debugging, caching, and incremental compilation
- **Traversable**: visitors/pattern matching for each compiler stage

### 3.1 Complete IR Type Definitions

```python
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import List, Optional, Dict, Any
import json

# ===== Source Location =====

@dataclass(frozen=True)
class Span:
    start_line: int
    start_col: int
    end_line: int
    end_col: int

# ===== Top-Level =====

@dataclass
class ServiceIR:
    name: str
    entities: List[EntityDecl]
    state: StateDecl
    operations: List[OperationDecl]
    invariants: List[InvariantDecl]
    conventions: Optional[ConventionsDecl]
    # -- Added by convention engine --
    annotations: Dict[str, Any] = field(default_factory=dict)
    span: Optional[Span] = None

# ===== Entities =====

@dataclass
class EntityDecl:
    name: str
    fields: List[FieldDecl]
    invariants: List[Expr]
    span: Optional[Span] = None

@dataclass
class FieldDecl:
    name: str
    type_expr: TypeExpr
    constraints: List[Expr] = field(default_factory=list)
    span: Optional[Span] = None

# ===== Types =====

class TypeKind(Enum):
    SIMPLE = auto()      # String, Int, ShortCode
    OPTIONAL = auto()    # String?
    SET = auto()         # Set<ShortCode>
    LIST = auto()        # List<String>
    MAP = auto()         # Map<String, Int>

@dataclass
class TypeExpr:
    kind: TypeKind
    name: Optional[str] = None          # for SIMPLE
    inner: Optional[TypeExpr] = None    # for OPTIONAL, SET, LIST
    key: Optional[TypeExpr] = None      # for MAP
    value: Optional[TypeExpr] = None    # for MAP
    span: Optional[Span] = None

# ===== State =====

@dataclass
class StateDecl:
    relations: List[RelationDecl]
    span: Optional[Span] = None

class Multiplicity(Enum):
    ONE = "one"        # exactly one (total function)
    LONE = "lone"      # zero or one (partial function)
    SOME = "some"      # one or more
    SET = "set"        # zero or more (relation)

@dataclass
class RelationDecl:
    name: str
    from_type: TypeExpr
    to_type: TypeExpr
    multiplicity: Multiplicity
    span: Optional[Span] = None

# ===== Operations =====

@dataclass
class OperationDecl:
    name: str
    inputs: List[ParamDecl]
    outputs: List[ParamDecl]
    requires: List[Expr]
    ensures: List[Expr]
    span: Optional[Span] = None
    # -- Added by convention engine --
    http_method: Optional[str] = None
    http_path: Optional[str] = None
    http_status_success: Optional[int] = None
    http_status_errors: Dict[str, int] = field(default_factory=dict)

@dataclass
class ParamDecl:
    name: str
    type_expr: TypeExpr
    span: Optional[Span] = None

# ===== Invariants =====

@dataclass
class InvariantDecl:
    expr: Expr
    span: Optional[Span] = None

# ===== Conventions =====

@dataclass
class ConventionsDecl:
    entries: List[ConventionEntry]
    span: Optional[Span] = None

@dataclass
class ConventionEntry:
    target: str         # e.g., "Resolve.http_method"
    key: Optional[str]  # e.g., "Location" for http_header
    value: Expr
    span: Optional[Span] = None

# ===== Expression AST =====

class ExprKind(Enum):
    BINARY_OP = auto()
    UNARY_OP = auto()
    QUANTIFIER = auto()
    FIELD_ACCESS = auto()
    INDEX = auto()
    FUNCTION_CALL = auto()
    LITERAL = auto()
    VARIABLE = auto()
    PRE_STATE = auto()
    POST_STATE = auto()
    SET_OP = auto()
    CARDINALITY = auto()
    MEMBERSHIP = auto()

class BinaryOp(Enum):
    AND = "and"
    OR = "or"
    EQ = "="
    NEQ = "!="
    LT = "<"
    GT = ">"
    LTE = "<="
    GTE = ">="
    ADD = "+"
    SUB = "-"
    MUL = "*"
    DIV = "/"

class UnaryOp(Enum):
    NOT = "not"
    NEGATE = "-"

class QuantifierKind(Enum):
    FORALL = "all"
    EXISTS = "some"
    NONE = "no"

class LiteralKind(Enum):
    STRING = auto()
    INT = auto()
    FLOAT = auto()
    BOOL = auto()

@dataclass
class Expr:
    """Tagged union for expression nodes."""
    kind: ExprKind
    span: Optional[Span] = None

    # -- BINARY_OP --
    binary_op: Optional[BinaryOp] = None
    left: Optional[Expr] = None
    right: Optional[Expr] = None

    # -- UNARY_OP --
    unary_op: Optional[UnaryOp] = None
    operand: Optional[Expr] = None

    # -- QUANTIFIER --
    quantifier_kind: Optional[QuantifierKind] = None
    quantifier_var: Optional[str] = None
    quantifier_domain: Optional[Expr] = None
    quantifier_body: Optional[Expr] = None

    # -- FIELD_ACCESS --
    base: Optional[Expr] = None
    field_name: Optional[str] = None

    # -- INDEX --
    index_base: Optional[Expr] = None
    index_key: Optional[Expr] = None

    # -- FUNCTION_CALL --
    func_name: Optional[str] = None
    func_args: List[Expr] = field(default_factory=list)

    # -- LITERAL --
    literal_kind: Optional[LiteralKind] = None
    literal_value: Any = None

    # -- VARIABLE --
    var_name: Optional[str] = None

    # -- PRE_STATE / POST_STATE --
    state_expr: Optional[Expr] = None

    # -- CARDINALITY --
    card_expr: Optional[Expr] = None

    # -- MEMBERSHIP (a in b, a not in b) --
    membership_negated: bool = False
    membership_element: Optional[Expr] = None
    membership_collection: Optional[Expr] = None
```

**TypeScript equivalent using discriminated unions (production implementation):**

```typescript
interface Span {
  readonly startLine: number;
  readonly startCol: number;
  readonly endLine: number;
  readonly endCol: number;
}

type Expr =
  | { kind: "BinaryOp"; op: BinaryOperator; left: Expr; right: Expr; span?: Span }
  | { kind: "UnaryOp"; op: UnaryOperator; operand: Expr; span?: Span }
  | {
      kind: "Quantifier";
      quantifierKind: QuantifierKind;
      variable: string;
      domain: Expr;
      body: Expr;
      span?: Span;
    }
  | { kind: "FieldAccess"; base: Expr; field: string; span?: Span }
  | { kind: "Index"; base: Expr; key: Expr; span?: Span }
  | { kind: "FunctionCall"; name: string; args: Expr[]; span?: Span }
  | { kind: "Literal"; value: unknown; literalKind: LiteralKind; span?: Span }
  | { kind: "Variable"; name: string; span?: Span }
  | { kind: "PreState"; expr: Expr; span?: Span }
  | { kind: "PostState"; expr: Expr; span?: Span }
  | { kind: "Cardinality"; expr: Expr; span?: Span }
  | { kind: "Membership"; element: Expr; collection: Expr; negated: boolean; span?: Span };
```

The TypeScript discriminated union provides exhaustive checking via `switch` on the `kind` field,
and each variant carries only its own data. This is the IR representation used by the production
compiler.

### 3.2 Complete IR for the URL Shortener Spec

```python
url_shortener_ir = ServiceIR(
    name="UrlShortener",
    entities=[
        EntityDecl(
            name="ShortCode",
            fields=[FieldDecl(name="value", type_expr=TypeExpr(kind=TypeKind.SIMPLE, name="String"))],
            invariants=[
                # len(value) >= 6 and len(value) <= 10
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.AND,
                    left=Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.GTE,
                        left=Expr(kind=ExprKind.FUNCTION_CALL, func_name="len",
                            func_args=[Expr(kind=ExprKind.VARIABLE, var_name="value")]),
                        right=Expr(kind=ExprKind.LITERAL, literal_kind=LiteralKind.INT, literal_value=6)),
                    right=Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.LTE,
                        left=Expr(kind=ExprKind.FUNCTION_CALL, func_name="len",
                            func_args=[Expr(kind=ExprKind.VARIABLE, var_name="value")]),
                        right=Expr(kind=ExprKind.LITERAL, literal_kind=LiteralKind.INT, literal_value=10))),
                # value matches /^[a-zA-Z0-9]+$/
                Expr(kind=ExprKind.FUNCTION_CALL, func_name="matches",
                    func_args=[
                        Expr(kind=ExprKind.VARIABLE, var_name="value"),
                        Expr(kind=ExprKind.LITERAL, literal_kind=LiteralKind.STRING,
                             literal_value="[a-zA-Z0-9]+")]),
            ],
        ),
        EntityDecl(
            name="LongURL",
            fields=[FieldDecl(name="value", type_expr=TypeExpr(kind=TypeKind.SIMPLE, name="String"))],
            invariants=[
                Expr(kind=ExprKind.FUNCTION_CALL, func_name="isValidURI",
                    func_args=[Expr(kind=ExprKind.VARIABLE, var_name="value")]),
            ],
        ),
    ],
    state=StateDecl(relations=[
        RelationDecl(
            name="store",
            from_type=TypeExpr(kind=TypeKind.SIMPLE, name="ShortCode"),
            to_type=TypeExpr(kind=TypeKind.SIMPLE, name="LongURL"),
            multiplicity=Multiplicity.LONE,
        ),
        RelationDecl(
            name="created_at",
            from_type=TypeExpr(kind=TypeKind.SIMPLE, name="ShortCode"),
            to_type=TypeExpr(kind=TypeKind.SIMPLE, name="DateTime"),
            multiplicity=Multiplicity.ONE,
        ),
    ]),
    operations=[
        OperationDecl(
            name="Shorten",
            inputs=[ParamDecl(name="url", type_expr=TypeExpr(kind=TypeKind.SIMPLE, name="LongURL"))],
            outputs=[
                ParamDecl(name="code", type_expr=TypeExpr(kind=TypeKind.SIMPLE, name="ShortCode")),
                ParamDecl(name="short_url", type_expr=TypeExpr(kind=TypeKind.SIMPLE, name="String")),
            ],
            requires=[
                # isValidURI(url.value)
                Expr(kind=ExprKind.FUNCTION_CALL, func_name="isValidURI",
                    func_args=[Expr(kind=ExprKind.FIELD_ACCESS,
                        base=Expr(kind=ExprKind.VARIABLE, var_name="url"),
                        field_name="value")]),
            ],
            ensures=[
                # code not in pre(store)
                Expr(kind=ExprKind.MEMBERSHIP, membership_negated=True,
                    membership_element=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                    membership_collection=Expr(kind=ExprKind.PRE_STATE,
                        state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store"))),
                # store'[code] = url
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.INDEX,
                        index_base=Expr(kind=ExprKind.POST_STATE,
                            state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store")),
                        index_key=Expr(kind=ExprKind.VARIABLE, var_name="code")),
                    right=Expr(kind=ExprKind.VARIABLE, var_name="url")),
                # short_url = base_url + "/" + code.value
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.VARIABLE, var_name="short_url"),
                    right=Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.ADD,
                        left=Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.ADD,
                            left=Expr(kind=ExprKind.VARIABLE, var_name="base_url"),
                            right=Expr(kind=ExprKind.LITERAL, literal_kind=LiteralKind.STRING,
                                       literal_value="/")),
                        right=Expr(kind=ExprKind.FIELD_ACCESS,
                            base=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                            field_name="value"))),
                # #store' = #store + 1
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.CARDINALITY,
                        card_expr=Expr(kind=ExprKind.POST_STATE,
                            state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store"))),
                    right=Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.ADD,
                        left=Expr(kind=ExprKind.CARDINALITY,
                            card_expr=Expr(kind=ExprKind.VARIABLE, var_name="store")),
                        right=Expr(kind=ExprKind.LITERAL, literal_kind=LiteralKind.INT, literal_value=1))),
            ],
        ),
        OperationDecl(
            name="Resolve",
            inputs=[ParamDecl(name="code", type_expr=TypeExpr(kind=TypeKind.SIMPLE, name="ShortCode"))],
            outputs=[ParamDecl(name="url", type_expr=TypeExpr(kind=TypeKind.SIMPLE, name="LongURL"))],
            requires=[
                # code in store
                Expr(kind=ExprKind.MEMBERSHIP, membership_negated=False,
                    membership_element=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                    membership_collection=Expr(kind=ExprKind.VARIABLE, var_name="store")),
            ],
            ensures=[
                # url = store[code]
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.VARIABLE, var_name="url"),
                    right=Expr(kind=ExprKind.INDEX,
                        index_base=Expr(kind=ExprKind.VARIABLE, var_name="store"),
                        index_key=Expr(kind=ExprKind.VARIABLE, var_name="code"))),
                # store' = store
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.POST_STATE,
                        state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store")),
                    right=Expr(kind=ExprKind.VARIABLE, var_name="store")),
            ],
        ),
        OperationDecl(
            name="Delete",
            inputs=[ParamDecl(name="code", type_expr=TypeExpr(kind=TypeKind.SIMPLE, name="ShortCode"))],
            outputs=[],
            requires=[
                # code in store
                Expr(kind=ExprKind.MEMBERSHIP, membership_negated=False,
                    membership_element=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                    membership_collection=Expr(kind=ExprKind.VARIABLE, var_name="store")),
            ],
            ensures=[
                # code not in store'
                Expr(kind=ExprKind.MEMBERSHIP, membership_negated=True,
                    membership_element=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                    membership_collection=Expr(kind=ExprKind.POST_STATE,
                        state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store"))),
                # #store' = #store - 1
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.CARDINALITY,
                        card_expr=Expr(kind=ExprKind.POST_STATE,
                            state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store"))),
                    right=Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.SUB,
                        left=Expr(kind=ExprKind.CARDINALITY,
                            card_expr=Expr(kind=ExprKind.VARIABLE, var_name="store")),
                        right=Expr(kind=ExprKind.LITERAL, literal_kind=LiteralKind.INT, literal_value=1))),
            ],
        ),
    ],
    invariants=[
        # all c in store | isValidURI(store[c].value)
        InvariantDecl(expr=Expr(kind=ExprKind.QUANTIFIER,
            quantifier_kind=QuantifierKind.FORALL,
            quantifier_var="c",
            quantifier_domain=Expr(kind=ExprKind.VARIABLE, var_name="store"),
            quantifier_body=Expr(kind=ExprKind.FUNCTION_CALL, func_name="isValidURI",
                func_args=[Expr(kind=ExprKind.FIELD_ACCESS,
                    base=Expr(kind=ExprKind.INDEX,
                        index_base=Expr(kind=ExprKind.VARIABLE, var_name="store"),
                        index_key=Expr(kind=ExprKind.VARIABLE, var_name="c")),
                    field_name="value")]))),
        # all c in store | c in created_at
        InvariantDecl(expr=Expr(kind=ExprKind.QUANTIFIER,
            quantifier_kind=QuantifierKind.FORALL,
            quantifier_var="c",
            quantifier_domain=Expr(kind=ExprKind.VARIABLE, var_name="store"),
            quantifier_body=Expr(kind=ExprKind.MEMBERSHIP, membership_negated=False,
                membership_element=Expr(kind=ExprKind.VARIABLE, var_name="c"),
                membership_collection=Expr(kind=ExprKind.VARIABLE, var_name="created_at")))),
    ],
    conventions=ConventionsDecl(entries=[
        ConventionEntry(target="Resolve.http_method", key=None,
            value=Expr(kind=ExprKind.VARIABLE, var_name="GET")),
        ConventionEntry(target="Resolve.http_status_success", key=None,
            value=Expr(kind=ExprKind.LITERAL, literal_kind=LiteralKind.INT, literal_value=302)),
        ConventionEntry(target="Resolve.http_header", key="Location",
            value=Expr(kind=ExprKind.FIELD_ACCESS,
                base=Expr(kind=ExprKind.VARIABLE, var_name="output"),
                field_name="url")),
    ]),
)
```

### 3.3 Design Decisions

**Mutable vs immutable IR nodes:**

The IR uses mutable nodes for the initial implementation. The convention engine mutates
`OperationDecl` in-place by setting `http_method`, `http_path`, etc. This is pragmatic for the MVP
but has known downsides:

- Difficult to implement undo/rollback
- Hard to cache intermediate states
- Debugging "who set this field?" requires tracing

For the production TypeScript compiler, use `readonly` interfaces with spread-based copies. The
convention engine produces a new IR with annotations applied, leaving the original intact. This
enables diffing (before/after convention application) and caching.

**Visitor pattern vs pattern matching for IR traversal:**

For Python: use a `match` statement (Python 3.10+) on `ExprKind` for simple traversals, and a
`Visitor` base class for multi-pass transformations:

```python
class ExprVisitor:
    def visit(self, expr: Expr) -> Any:
        method_name = f"visit_{expr.kind.name.lower()}"
        visitor = getattr(self, method_name, self.generic_visit)
        return visitor(expr)

    def generic_visit(self, expr: Expr) -> Any:
        raise NotImplementedError(f"No visitor for {expr.kind}")

class ExprPrinter(ExprVisitor):
    def visit_binary_op(self, expr: Expr) -> str:
        left = self.visit(expr.left)
        right = self.visit(expr.right)
        return f"({left} {expr.binary_op.value} {right})"

    def visit_variable(self, expr: Expr) -> str:
        return expr.var_name

    def visit_literal(self, expr: Expr) -> str:
        if expr.literal_kind == LiteralKind.STRING:
            return f'"{expr.literal_value}"'
        return str(expr.literal_value)

    def visit_cardinality(self, expr: Expr) -> str:
        return f"#{self.visit(expr.card_expr)}"

    def visit_pre_state(self, expr: Expr) -> str:
        return f"pre({self.visit(expr.state_expr)})"

    def visit_post_state(self, expr: Expr) -> str:
        return f"{self.visit(expr.state_expr)}'"
```

For Kotlin: exhaustive `when` expressions on sealed classes are both cleaner and compiler-checked:

```kotlin
fun printExpr(expr: Expr): String = when (expr) {
    is Expr.BinaryOp -> "(${printExpr(expr.left)} ${expr.op.symbol} ${printExpr(expr.right)})"
    is Expr.Variable -> expr.name
    is Expr.Literal -> if (expr.kind == LiteralKind.STRING) "\"${expr.value}\"" else "${expr.value}"
    is Expr.Cardinality -> "#${printExpr(expr.expr)}"
    is Expr.PreState -> "pre(${printExpr(expr.expr)})"
    is Expr.PostState -> "${printExpr(expr.expr)}'"
    // ... all variants must be handled or the compiler errors
}
```

**Convention engine annotation strategy:**

Use a **separate annotation layer** rather than inlining annotations into the IR nodes. The
annotation layer is a map from IR node identity (by path or ID) to annotation records:

```python
@dataclass
class ConventionAnnotations:
    """Annotations applied by the convention engine."""
    operation_annotations: Dict[str, OperationAnnotation]  # keyed by operation name
    entity_annotations: Dict[str, EntityAnnotation]        # keyed by entity name
    relation_annotations: Dict[str, RelationAnnotation]    # keyed by relation name

@dataclass
class OperationAnnotation:
    http_method: str              # GET, POST, PUT, PATCH, DELETE
    http_path: str                # /shorten, /{code}
    http_status_success: int      # 200, 201, 204, 302
    http_status_errors: Dict[str, int]  # "not_found" -> 404, "validation" -> 422
    request_body_model: Optional[str]   # Pydantic model name
    response_body_model: Optional[str]
    path_params: List[str]
    query_params: List[str]
    operation_kind: str           # "create", "read", "update", "delete", "action"
    is_idempotent: bool
    headers: Dict[str, str]       # response headers

@dataclass
class EntityAnnotation:
    table_name: str
    model_class_name: str
    columns: List[ColumnAnnotation]

@dataclass
class ColumnAnnotation:
    column_name: str
    sql_type: str
    nullable: bool
    primary_key: bool
    check_constraint: Optional[str]

@dataclass
class RelationAnnotation:
    table_name: str
    from_column: str
    to_column: str
    foreign_key: bool
    junction_table: Optional[str]  # for many-to-many
```

This separation means the original IR from parsing is never mutated. The convention engine produces
`ConventionAnnotations` as a separate artifact. The emitter consumes both the IR and the
annotations. This enables:

- Diffing: compare annotations from different convention profiles
- Override: user convention overrides modify the annotation layer only
- Caching: the annotation layer can be cached independently of parsing

**IR serialization:**

Serialize to JSON for debugging and caching. Use a custom encoder that handles Enum values and
dataclass nesting:

```python
import json
from dataclasses import asdict

class IREncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, Enum):
            return {"__enum__": f"{type(obj).__name__}.{obj.name}"}
        if hasattr(obj, "__dataclass_fields__"):
            return asdict(obj)
        return super().default(obj)

def serialize_ir(ir: ServiceIR) -> str:
    return json.dumps(ir, cls=IREncoder, indent=2)

def deserialize_ir(text: str) -> ServiceIR:
    # Custom decoder that reconstructs dataclass instances
    data = json.loads(text)
    return _reconstruct_service_ir(data)
```

---

## 4. Solver Integration Architecture

### 4.1 Z3 Integration

**Translation from IR constraints to Z3:**

The key insight is that our spec constraints map to Z3 constructs as follows:

| Spec construct       | Z3 construct                                    |
| -------------------- | ----------------------------------------------- |
| `entity` field type  | Z3 Sort (IntSort, StringSort, or uninterpreted) |
| `state` relation     | Z3 Array or Function                            |
| `invariant`          | Z3 assertion (universal quantifier over state)  |
| `requires`           | Z3 precondition (existential satisfiability)    |
| `ensures`            | Z3 postcondition (post-state implies ensures)   |
| `#collection`        | Z3 custom cardinality function                  |
| `all x in S \| P(x)` | `ForAll(x, Implies(member(x, S), P(x)))`        |

**Complete example -- URL shortener invariant checking:**

```python
from z3 import (
    Solver, DeclareSort, Function, BoolSort, IntSort, StringSort,
    ForAll, Exists, Implies, And, Or, Not, Const, sat, unsat, unknown
)

def check_url_shortener_invariants():
    """
    Verify that the URL shortener spec's invariants are consistent
    (not mutually contradictory) and that operations preserve them.
    """
    s = Solver()
    s.set("timeout", 30000)  # 30 second timeout

    # Declare sorts for entity types
    ShortCode = DeclareSort("ShortCode")
    LongURL = DeclareSort("LongURL")

    # State relations as functions
    # store: ShortCode -> lone LongURL (partial function)
    store = Function("store", ShortCode, LongURL)
    store_defined = Function("store_defined", ShortCode, BoolSort())

    # created_at: ShortCode -> DateTime
    created_at_defined = Function("created_at_defined", ShortCode, BoolSort())

    # Helper: valid_uri as uninterpreted predicate
    valid_uri = Function("valid_uri", LongURL, BoolSort())

    # Helper: code_len as uninterpreted function
    code_len = Function("code_len", ShortCode, IntSort())

    # --- Entity invariants ---
    c = Const("c", ShortCode)

    # ShortCode.invariant: len(value) >= 6 and len(value) <= 10
    s.add(ForAll(c, And(code_len(c) >= 6, code_len(c) <= 10)))

    # --- Global invariants ---

    # Invariant 1: all c in store | isValidURI(store[c].value)
    s.add(ForAll(c, Implies(store_defined(c), valid_uri(store(c)))))

    # Invariant 2: all c in store | c in created_at
    s.add(ForAll(c, Implies(store_defined(c), created_at_defined(c))))

    # --- Check consistency ---
    # The invariants should be satisfiable (not contradictory)
    result = s.check()

    if result == sat:
        print("Invariants are consistent (satisfiable)")
        model = s.model()
        print(f"  Example model: {model}")
        return True
    elif result == unsat:
        print("ERROR: Invariants are contradictory!")
        print(f"  Unsat core: {s.unsat_core()}")
        return False
    else:
        print("WARNING: Solver returned unknown (timeout or undecidable)")
        return None

def check_operation_preserves_invariants():
    """
    Verify that the Shorten operation preserves the global invariant
    'all c in store | isValidURI(store[c].value)'.

    Encoding: if invariant holds in pre-state AND requires holds AND
    ensures holds, THEN invariant holds in post-state.
    """
    s = Solver()
    s.set("timeout", 60000)

    ShortCode = DeclareSort("ShortCode")
    LongURL = DeclareSort("LongURL")

    # Pre-state
    store_pre = Function("store_pre", ShortCode, LongURL)
    store_pre_defined = Function("store_pre_defined", ShortCode, BoolSort())

    # Post-state
    store_post = Function("store_post", ShortCode, LongURL)
    store_post_defined = Function("store_post_defined", ShortCode, BoolSort())

    valid_uri = Function("valid_uri", LongURL, BoolSort())

    c = Const("c", ShortCode)
    new_code = Const("new_code", ShortCode)
    new_url = Const("new_url", LongURL)

    # Assume: invariant holds in pre-state
    s.add(ForAll(c, Implies(store_pre_defined(c), valid_uri(store_pre(c)))))

    # Assume: requires holds (isValidURI(url.value))
    s.add(valid_uri(new_url))

    # Assume: ensures holds
    # code not in pre(store)
    s.add(Not(store_pre_defined(new_code)))
    # store'[code] = url
    s.add(store_post(new_code) == new_url)
    s.add(store_post_defined(new_code))
    # Frame condition: everything else unchanged
    s.add(ForAll(c, Implies(
        c != new_code,
        And(
            store_post_defined(c) == store_pre_defined(c),
            store_post(c) == store_pre(c)
        )
    )))

    # Try to VIOLATE the invariant in post-state
    violation = Const("violation", ShortCode)
    s.add(store_post_defined(violation))
    s.add(Not(valid_uri(store_post(violation))))

    result = s.check()
    if result == unsat:
        print("VERIFIED: Shorten preserves the isValidURI invariant")
        return True
    elif result == sat:
        model = s.model()
        print("COUNTEREXAMPLE: Shorten can violate the invariant")
        print(f"  Violating code: {model[violation]}")
        return False
    else:
        print("WARNING: Solver returned unknown")
        return None
```

**Timeout management:** Z3 timeouts are set per-solver instance via
`s.set("timeout", milliseconds)`. Our compiler should use a tiered approach:

1. Quick check (5s): basic consistency of invariants
2. Medium check (30s): operation preservation of invariants
3. Deep check (120s): cross-operation interaction analysis
4. User-configurable via `--verify-timeout`

If Z3 returns `unknown`, report a warning rather than an error. The user can increase the timeout or
accept the risk.

---

### 4.2 Alloy Integration

**How to invoke AlloyAPI from JVM (Kotlin):**

```kotlin
import edu.mit.csail.sdg.alloy4.A4Reporter
import edu.mit.csail.sdg.alloy4compiler.ast.Command
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod

class AlloyBackend {
    fun checkSpec(alloySource: String): AlloyResult {
        val reporter = A4Reporter()
        val world = CompUtil.parseEverything_fromString(reporter, alloySource)

        val options = A4Options().apply {
            solver = A4Options.SatSolver.SAT4J
            skolemDepth = 2
        }

        val results = mutableListOf<CommandResult>()
        for (command in world.allCommands) {
            val solution = TranslateAlloyToKodkod.execute_command(
                reporter, world.allReachableSigs, command, options
            )
            results.add(CommandResult(
                command = command.label,
                satisfiable = solution.satisfiable(),
                counterexample = if (solution.satisfiable()) solution.toString() else null
            ))
        }
        return AlloyResult(results)
    }
}
```

**How to translate IR to Alloy source text:**

```python
class AlloyTranslator:
    def translate(self, ir: ServiceIR) -> str:
        lines = []

        # Entities become sigs
        for entity in ir.entities:
            fields = ", ".join(
                f"{f.name}: {self.translate_type(f.type_expr)}"
                for f in entity.fields
            )
            lines.append(f"sig {entity.name} {{ {fields} }}")
            for inv in entity.invariants:
                lines.append(f"fact {{ all self: {entity.name} | {self.translate_expr(inv, 'self')} }}")
            lines.append("")

        # State relations become a single State sig
        lines.append("one sig State {")
        for rel in ir.state.relations:
            alloy_mult = {"one": "one", "lone": "lone", "some": "some", "set": "set"}
            lines.append(f"  {rel.name}: {rel.from_type.name} -> {alloy_mult[rel.multiplicity.value]} {rel.to_type.name},")
        lines.append("}")
        lines.append("")

        # Global invariants become facts
        for inv in ir.invariants:
            lines.append(f"fact {{ {self.translate_expr(inv.expr)} }}")

        # Operations become predicates
        for op in ir.operations:
            params = ", ".join(f"{p.name}: {p.type_expr.name}" for p in op.inputs + op.outputs)
            lines.append(f"pred {op.name}[{params}, s, s': State] {{")
            for req in op.requires:
                lines.append(f"  {self.translate_expr(req, state_var='s')}")
            for ens in op.ensures:
                lines.append(f"  {self.translate_expr(ens, state_var='s', post_state_var=\"s'\")}")
            lines.append("}")
            lines.append("")

        # Check that operations preserve invariants
        for op in ir.operations:
            lines.append(f"check {op.name}_preserves_invariants {{")
            lines.append(f"  all s, s': State, {self._op_params(op)} |")
            lines.append(f"    {op.name}[{self._op_args(op)}, s, s'] implies {{")
            for inv in ir.invariants:
                lines.append(f"      {self.translate_expr(inv.expr, state_var=\"s'\")}")
            lines.append(f"    }}")
            lines.append(f"}} for 5")
            lines.append("")

        return "\n".join(lines)
```

**How to parse Alloy Analyzer output:**

Alloy Analyzer returns `A4Solution` objects. Key fields:

- `solution.satisfiable()`: boolean -- was a counterexample found?
- `solution.getAllAtoms()`: all atoms in the counterexample instance
- `solution.eval(expr)`: evaluate an expression in the counterexample
- `solution.toString()`: human-readable counterexample text

For subprocess-based integration (TypeScript), run `java -jar alloy.jar` and parse stdout. The
output format is XML (`-xml` flag) or text. XML parsing gives structured access to atoms and tuples.

---

### 4.3 Dafny Integration

**How to invoke the Dafny compiler programmatically:**

Dafny is distributed as a .NET tool (`dotnet tool install dafny`). Invocation is via subprocess:

```python
import subprocess
import json
import re
from dataclasses import dataclass
from typing import List, Optional

@dataclass
class DafnyVerificationResult:
    verified: bool
    errors: List["DafnyError"]
    counterexamples: List[str]

@dataclass
class DafnyError:
    file: str
    line: int
    col: int
    message: str
    related: List[str]

class DafnyBackend:
    def __init__(self, dafny_path: str = "dafny"):
        self.dafny_path = dafny_path

    def verify(self, dafny_source: str, timeout: int = 120) -> DafnyVerificationResult:
        """Write source to temp file, invoke dafny verify, parse results."""
        import tempfile, os
        with tempfile.NamedTemporaryFile(mode="w", suffix=".dfy", delete=False) as f:
            f.write(dafny_source)
            temp_path = f.name

        try:
            result = subprocess.run(
                [self.dafny_path, "verify", "--verification-time-limit", str(timeout),
                 "--json-diagnostics", temp_path],
                capture_output=True, text=True, timeout=timeout + 30
            )
            return self._parse_output(result.stdout, result.stderr, result.returncode)
        finally:
            os.unlink(temp_path)

    def compile_to_python(self, dafny_source: str) -> str:
        """Compile verified Dafny to Python."""
        import tempfile, os
        with tempfile.NamedTemporaryFile(mode="w", suffix=".dfy", delete=False) as f:
            f.write(dafny_source)
            temp_path = f.name

        try:
            out_dir = tempfile.mkdtemp()
            result = subprocess.run(
                [self.dafny_path, "translate", "py",
                 "--output", os.path.join(out_dir, "output"),
                 temp_path],
                capture_output=True, text=True, timeout=120
            )
            if result.returncode != 0:
                raise RuntimeError(f"Dafny compilation failed: {result.stderr}")

            # Read the generated Python file
            py_files = [f for f in os.listdir(out_dir) if f.endswith(".py")]
            if not py_files:
                raise RuntimeError("No Python output generated")
            with open(os.path.join(out_dir, py_files[0])) as f:
                return f.read()
        finally:
            os.unlink(temp_path)

    def _parse_output(self, stdout: str, stderr: str, returncode: int) -> DafnyVerificationResult:
        errors = []
        counterexamples = []

        # Parse JSON diagnostics if available
        for line in stderr.splitlines():
            m = re.match(r"(.+)\((\d+),(\d+)\): Error: (.+)", line)
            if m:
                errors.append(DafnyError(
                    file=m.group(1), line=int(m.group(2)),
                    col=int(m.group(3)), message=m.group(4), related=[]
                ))

            # Extract counterexample values
            if "counterexample" in line.lower():
                counterexamples.append(line.strip())

        return DafnyVerificationResult(
            verified=(returncode == 0 and len(errors) == 0),
            errors=errors,
            counterexamples=counterexamples,
        )
```

**Complete example -- generate Dafny from IR and verify:**

```python
class DafnyGenerator:
    def generate_operation(self, op: OperationDecl, entities: List[EntityDecl],
                           state: StateDecl) -> str:
        """Generate a Dafny method from a spec operation."""
        lines = []

        # Generate state type
        lines.append("// Auto-generated from spec")
        lines.append("type ShortCode = s: string | 6 <= |s| <= 10 && forall i :: 0 <= i < |s| ==> s[i] in 'a'..'z' + 'A'..'Z' + '0'..'9'")
        lines.append("type LongURL = string  // simplified; full validation in runtime")
        lines.append("")
        lines.append("class Store {")
        lines.append("  var mapping: map<ShortCode, LongURL>")
        lines.append("")

        # Generate method with pre/postconditions
        input_params = ", ".join(f"{p.name}: {self._dafny_type(p.type_expr)}" for p in op.inputs)
        output_params = ", ".join(f"{p.name}: {self._dafny_type(p.type_expr)}" for p in op.outputs)

        returns_clause = f" returns ({output_params})" if op.outputs else ""

        lines.append(f"  method {op.name}({input_params}){returns_clause}")
        lines.append(f"    modifies this")

        # Requires clauses
        for req in op.requires:
            lines.append(f"    requires {self._dafny_expr(req)}")

        # Ensures clauses
        for ens in op.ensures:
            lines.append(f"    ensures {self._dafny_expr(ens)}")

        lines.append("  {")
        lines.append("    // TODO: LLM-synthesized body")
        lines.append("  }")
        lines.append("}")

        return "\n".join(lines)
```

---

### 4.4 LLM API Integration

**Architecture:**

````python
import anthropic
import time
import hashlib
from typing import Optional
from dataclasses import dataclass, field

@dataclass
class SynthesisConfig:
    model: str = "claude-sonnet-4-20250514"
    max_iterations: int = 8
    temperature: float = 0.3
    max_tokens: int = 4096
    cost_budget_usd: float = 5.0
    cache_dir: Optional[str] = ".spec-to-rest-cache/synthesis"

@dataclass
class SynthesisResult:
    code: str
    verified: bool
    iterations: int
    total_tokens: int
    cost_usd: float
    verification_errors: List[str] = field(default_factory=list)

class LLMSynthesisEngine:
    def __init__(self, config: SynthesisConfig, dafny: DafnyBackend):
        self.config = config
        self.dafny = dafny
        self.client = anthropic.Anthropic()
        self.total_tokens = 0
        self.total_cost = 0.0

    def synthesize_operation(self, op: OperationDecl, context: ServiceIR) -> SynthesisResult:
        """
        CEGIS loop: generate Dafny code via LLM, verify, feed errors back.
        """
        # Check cache first
        cache_key = self._cache_key(op, context)
        cached = self._load_cache(cache_key)
        if cached:
            return cached

        prompt = self._build_initial_prompt(op, context)
        errors_history = []

        for iteration in range(self.config.max_iterations):
            # Check cost budget
            if self.total_cost >= self.config.cost_budget_usd:
                return SynthesisResult(
                    code="// Budget exhausted -- manual implementation needed",
                    verified=False, iterations=iteration,
                    total_tokens=self.total_tokens, cost_usd=self.total_cost,
                    verification_errors=errors_history[-1] if errors_history else [],
                )

            # Generate candidate
            if iteration == 0:
                messages = [{"role": "user", "content": prompt}]
            else:
                messages = self._build_repair_messages(prompt, errors_history)

            response = self.client.messages.create(
                model=self.config.model,
                max_tokens=self.config.max_tokens,
                temperature=self.config.temperature,
                messages=messages,
            )

            # Track costs
            input_tokens = response.usage.input_tokens
            output_tokens = response.usage.output_tokens
            self.total_tokens += input_tokens + output_tokens
            self.total_cost += self._estimate_cost(input_tokens, output_tokens)

            # Extract Dafny code from response
            dafny_code = self._extract_dafny_code(response.content[0].text)
            if not dafny_code:
                errors_history.append(["Failed to extract Dafny code from response"])
                continue

            # Verify with Dafny
            verification = self.dafny.verify(dafny_code)
            if verification.verified:
                result = SynthesisResult(
                    code=dafny_code, verified=True,
                    iterations=iteration + 1,
                    total_tokens=self.total_tokens, cost_usd=self.total_cost,
                )
                self._save_cache(cache_key, result)
                return result

            # Collect errors for next iteration
            error_msgs = [e.message for e in verification.errors]
            if verification.counterexamples:
                error_msgs.extend(verification.counterexamples)
            errors_history.append(error_msgs)

        # Exhausted iterations
        return SynthesisResult(
            code=dafny_code if dafny_code else "",
            verified=False, iterations=self.config.max_iterations,
            total_tokens=self.total_tokens, cost_usd=self.total_cost,
            verification_errors=errors_history[-1] if errors_history else [],
        )

    def _build_initial_prompt(self, op: OperationDecl, context: ServiceIR) -> str:
        return f"""You are generating a verified Dafny implementation for a REST service operation.

## Service Context
Service: {context.name}
Entities: {', '.join(e.name for e in context.entities)}

## Operation: {op.name}

Inputs:
{chr(10).join(f'  {p.name}: {self._type_str(p.type_expr)}' for p in op.inputs)}

Outputs:
{chr(10).join(f'  {p.name}: {self._type_str(p.type_expr)}' for p in op.outputs)}

Preconditions (requires):
{chr(10).join(f'  {self._expr_str(r)}' for r in op.requires)}

Postconditions (ensures):
{chr(10).join(f'  {self._expr_str(e)}' for e in op.ensures)}

## Task
Generate a complete Dafny method that:
1. Has the exact requires/ensures clauses above
2. Contains an implementation body that satisfies all postconditions
3. Compiles and verifies with `dafny verify`

Return ONLY the Dafny code in a ```dafny code block."""

    def _build_repair_messages(self, initial_prompt: str,
                                errors_history: List[List[str]]) -> list:
        messages = [{"role": "user", "content": initial_prompt}]
        for i, errors in enumerate(errors_history):
            messages.append({"role": "assistant", "content": f"[Previous attempt {i+1}]"})
            messages.append({"role": "user", "content":
                f"The Dafny verifier reported these errors:\n" +
                "\n".join(f"- {e}" for e in errors) +
                "\n\nPlease fix the implementation and try again. Return ONLY the corrected Dafny code."
            })
        return messages

    def _estimate_cost(self, input_tokens: int, output_tokens: int) -> float:
        # Approximate costs for Claude Sonnet (adjust per model)
        return (input_tokens * 3.0 + output_tokens * 15.0) / 1_000_000

    def _cache_key(self, op: OperationDecl, context: ServiceIR) -> str:
        content = f"{context.name}:{op.name}:{serialize_ir(context)}"
        return hashlib.sha256(content.encode()).hexdigest()[:16]

    def _load_cache(self, key: str) -> Optional[SynthesisResult]:
        if not self.config.cache_dir:
            return None
        import os, pickle
        path = os.path.join(self.config.cache_dir, f"{key}.pkl")
        if os.path.exists(path):
            with open(path, "rb") as f:
                return pickle.load(f)
        return None

    def _save_cache(self, key: str, result: SynthesisResult):
        if not self.config.cache_dir:
            return
        import os, pickle
        os.makedirs(self.config.cache_dir, exist_ok=True)
        path = os.path.join(self.config.cache_dir, f"{key}.pkl")
        with open(path, "wb") as f:
            pickle.dump(result, f)

    def _extract_dafny_code(self, text: str) -> Optional[str]:
        import re
        match = re.search(r"```dafny\n(.*?)```", text, re.DOTALL)
        if match:
            return match.group(1).strip()
        match = re.search(r"```\n(.*?)```", text, re.DOTALL)
        if match:
            return match.group(1).strip()
        return None
````

**Token budget management:** The `SynthesisConfig.cost_budget_usd` field caps total LLM spending per
compilation. Each operation synthesis tracks cumulative cost. When the budget is exhausted,
remaining operations get TODO stubs. A typical URL shortener spec with 3 operations costs
approximately $0.10-0.50 depending on iteration count.

**Retry logic:** The CEGIS loop itself provides semantic retry (verification error feedback). For
API-level failures (rate limits, network errors), add exponential backoff:

```python
import time

def call_with_retry(fn, max_retries=3, base_delay=1.0):
    for attempt in range(max_retries):
        try:
            return fn()
        except anthropic.RateLimitError:
            delay = base_delay * (2 ** attempt)
            time.sleep(delay)
        except anthropic.APIConnectionError:
            delay = base_delay * (2 ** attempt)
            time.sleep(delay)
    raise RuntimeError(f"API call failed after {max_retries} retries")
```

---

## 5. Project Structure

```
spec-to-rest/
├── README.md
├── LICENSE
├── package.json                        # TypeScript build config
├── .github/
│   └── workflows/
│       ├── ci.yml                      # lint, typecheck, test on every push
│       └── release.yml                 # build + publish on tag
├── src/
│   └── spec_to_rest/
│       ├── index.ts                    # main module entry point
│       ├── cli.ts                      # CLI argument parsing (commander/yargs)
│       ├── config.py                   # configuration management
│       │
│       ├── parser/
│       │   ├── __init__.py
│       │   ├── lexer.py                # tokenizer: spec text -> token stream
│       │   ├── tokens.py               # token kind enum and Token dataclass
│       │   ├── parser.py               # recursive descent parser: tokens -> AST
│       │   ├── ast.py                  # AST node definitions (raw parse tree)
│       │   └── errors.py               # parse error types with span info
│       │
│       ├── ir/
│       │   ├── __init__.py
│       │   ├── types.py                # IR type definitions (Section 3.1)
│       │   ├── builder.py              # AST -> IR transformation + desugaring
│       │   ├── printer.py              # IR -> human-readable format (for --verbose)
│       │   ├── serializer.py           # IR -> JSON (for caching, debugging)
│       │   └── validator.py            # IR well-formedness checks (type resolution, etc.)
│       │
│       ├── verify/
│       │   ├── __init__.py
│       │   ├── engine.py               # verification orchestrator
│       │   ├── z3_backend.py           # IR -> Z3 translation and checking
│       │   ├── alloy_backend.py        # IR -> Alloy source and subprocess invocation
│       │   ├── consistency.py          # invariant consistency checks
│       │   ├── preservation.py         # operation-preserves-invariant checks
│       │   └── errors.py               # verification error types + rendering
│       │
│       ├── conventions/
│       │   ├── __init__.py
│       │   ├── engine.py               # convention application orchestrator
│       │   ├── http.py                 # HTTP method, path, status code mapping
│       │   ├── database.py             # DB schema, table, column mapping
│       │   ├── validation.py           # validation rule extraction from requires
│       │   ├── naming.py               # naming conventions (snake_case, pluralize, etc.)
│       │   ├── overrides.py            # user convention override application
│       │   └── profiles/
│       │       ├── __init__.py
│       │       ├── base.py             # abstract profile interface
│       │       ├── python_fastapi.py   # Python/FastAPI/SQLAlchemy/PostgreSQL
│       │       ├── go_chi.py           # Go/chi/sqlx/PostgreSQL
│       │       └── ts_express.py       # TypeScript/Express/Prisma/PostgreSQL
│       │
│       ├── synthesis/
│       │   ├── __init__.py
│       │   ├── engine.py               # LLM synthesis orchestrator (CEGIS loop)
│       │   ├── dafny_gen.py            # IR -> Dafny source generation
│       │   ├── dafny_backend.py        # Dafny compiler invocation
│       │   ├── prompts.py              # prompt templates for LLM
│       │   ├── feedback.py             # Dafny error parsing -> LLM feedback
│       │   └── cache.py                # synthesis result caching
│       │
│       ├── emit/
│       │   ├── __init__.py
│       │   ├── engine.py               # code emission orchestrator
│       │   ├── openapi.py              # OpenAPI 3.1 spec generation
│       │   ├── sql.py                  # SQL DDL / migration generation
│       │   ├── dockerfile.py           # Dockerfile + docker-compose generation
│       │   ├── ci.py                   # GitHub Actions workflow generation
│       │   └── templates/
│       │       ├── python/
│       │       │   ├── main.py.j2
│       │       │   ├── route.py.j2
│       │       │   ├── model.py.j2
│       │       │   ├── schema.py.j2
│       │       │   ├── database.py.j2
│       │       │   ├── config.py.j2
│       │       │   ├── requirements.txt.j2
│       │       │   └── Dockerfile.j2
│       │       ├── go/
│       │       │   ├── main.go.j2
│       │       │   ├── handler.go.j2
│       │       │   ├── model.go.j2
│       │       │   ├── repository.go.j2
│       │       │   ├── go.mod.j2
│       │       │   └── Dockerfile.j2
│       │       └── typescript/
│       │           ├── app.ts.j2
│       │           ├── route.ts.j2
│       │           ├── model.ts.j2
│       │           ├── repository.ts.j2
│       │           ├── package.json.j2
│       │           └── Dockerfile.j2
│       │
│       └── test_gen/
│           ├── __init__.py
│           ├── engine.py               # test generation orchestrator
│           ├── property.py             # ensures -> Hypothesis property tests
│           ├── stateful.py             # operations -> Hypothesis state machine
│           ├── schemathesis_cfg.py     # OpenAPI -> Schemathesis config
│           └── templates/
│               ├── test_property.py.j2
│               ├── test_stateful.py.j2
│               └── conftest.py.j2
│
├── tests/
│   ├── conftest.py                     # shared fixtures
│   ├── fixtures/
│   │   ├── url_shortener.spec
│   │   ├── todo_list.spec
│   │   ├── ecommerce.spec
│   │   └── expected/                   # golden file outputs
│   │       ├── url_shortener/
│   │       │   ├── openapi.yaml
│   │       │   ├── migrations/
│   │       │   └── app/
│   │       └── ...
│   ├── unit/
│   │   ├── test_lexer.py
│   │   ├── test_parser.py
│   │   ├── test_ir_builder.py
│   │   ├── test_conventions_http.py
│   │   ├── test_conventions_database.py
│   │   ├── test_emitter_openapi.py
│   │   ├── test_emitter_python.py
│   │   └── test_z3_backend.py
│   ├── integration/
│   │   ├── test_parse_to_ir.py         # spec text -> IR round-trip
│   │   ├── test_conventions_to_emit.py # IR -> annotated IR -> generated code
│   │   └── test_end_to_end.py          # spec -> docker-compose up -> tests pass
│   └── golden/
│       └── test_golden_files.py        # snapshot testing against expected/ outputs
│
├── examples/
│   ├── url_shortener/
│   │   └── spec.rest
│   ├── todo_list/
│   │   └── spec.rest
│   └── ecommerce/
│       └── spec.rest
│
├── research/                           # design documents (this directory)
│   ├── 00_comprehensive_analysis.md
│   ├── spec_to_test_tools_research.md
│   └── 07_implementation_architecture.md
│
└── docs/
    ├── language_reference.md
    ├── convention_rules.md
    └── tutorial.md
```

**Key design choices in the project structure:**

1. **`src/spec_to_rest/` layout**: Uses the `src/` layout recommended by the Python Packaging
   Authority. This prevents accidental imports of the source tree during testing (tests import the
   installed package, not the local directory).

2. **Templates as package data**: The `emit/templates/` directory contains Jinja2 templates that are
   installed as package data. This ensures they are available when the compiler is installed via
   pip.

3. **Separate `ast.py` and `ir/types.py`**: The AST (raw parse tree) and IR (desugared, resolved)
   are distinct types. The `ir/builder.py` transforms one to the other. This separation allows AST
   changes (syntax evolution) without breaking the rest of the compiler.

4. **`conventions/profiles/`**: Each target platform (Python/FastAPI, Go/chi, TypeScript/Express)
   has its own convention profile module. The profile defines target-specific naming, typing, and
   structure conventions. This is the extension point for adding new targets.

5. **`tests/fixtures/expected/`**: Golden file outputs for snapshot testing. When code generation
   changes, `pytest --update-snapshots` regenerates these.

---

## 6. CLI Design

### 6.1 Command Structure

```
spec-to-rest <command> [options] <spec-file>

Commands:
  check       Parse and verify the spec (no code generation)
  generate    Generate the complete service
  test        Generate and run tests against an existing service
  diff        Show what would change if the spec were regenerated
  inspect     Print the IR or convention annotations (for debugging)

Global Options:
  --verbose, -v             Show detailed compilation progress
  --quiet, -q               Suppress non-error output
  --color / --no-color      Force color output on/off
  --config <path>           Path to config file (default: .spec-to-rest.toml)

check Options:
  --verify-level <n>        Verification depth:
                              0 = parse only
                              1 = type check + name resolution
                              2 = invariant consistency (Z3)
                              3 = operation preservation (Z3 + frame analysis)
  --verify-timeout <secs>   Z3 timeout per check (default: 30)

generate Options:
  --target <profile>        Target platform:
                              python-fastapi (default)
                              go-chi
                              ts-express
  --output <dir>            Output directory (default: ./generated)
  --llm <model>             LLM model for synthesis (default: claude-sonnet-4-20250514)
  --no-synthesis            Skip LLM synthesis (emit TODOs for complex operations)
  --no-verify               Skip spec verification before generation
  --no-tests                Skip test generation
  --no-infra                Skip Dockerfile/docker-compose generation
  --dry-run                 Show what would be generated without writing files

test Options:
  --server-url <url>        URL of the running service (default: http://localhost:8000)
  --test-kind <kind>        Test kind to run:
                              all (default)
                              property
                              stateful
                              schemathesis

inspect Options:
  --format <fmt>            Output format: ir, json, alloy, dafny, openapi
  --stage <stage>           Pipeline stage: parse, conventions, verify

diff Options:
  --output <dir>            Directory of previously generated code
  --format <fmt>            Diff format: unified (default), summary
```

### 6.2 Implementation

```python
# cli.py
import click
import sys
from pathlib import Path

@click.group()
@click.version_option()
@click.option("--verbose", "-v", is_flag=True)
@click.option("--quiet", "-q", is_flag=True)
@click.option("--color/--no-color", default=True)
@click.option("--config", type=click.Path(), default=None)
@click.pass_context
def cli(ctx, verbose, quiet, color, config):
    ctx.ensure_object(dict)
    ctx.obj["verbose"] = verbose
    ctx.obj["quiet"] = quiet
    ctx.obj["color"] = color
    ctx.obj["config"] = config

@cli.command()
@click.argument("spec_file", type=click.Path(exists=True))
@click.option("--verify-level", type=int, default=2)
@click.option("--verify-timeout", type=int, default=30)
@click.pass_context
def check(ctx, spec_file, verify_level, verify_timeout):
    """Parse and verify a spec file."""
    from .parser import parse_spec_file
    from .ir import build_ir
    from .verify import verify_spec

    spec_text = Path(spec_file).read_text()

    # Stage 1: Parse
    _log(ctx, f"Parsing {spec_file}...")
    ast = parse_spec_file(spec_text, filename=spec_file)

    # Stage 2: Build IR
    _log(ctx, "Building IR...")
    ir = build_ir(ast)

    # Stage 3: Verify
    if verify_level > 0:
        _log(ctx, f"Verifying (level {verify_level})...")
        result = verify_spec(ir, level=verify_level, timeout=verify_timeout)
        if result.errors:
            for err in result.errors:
                _error(ctx, err.render(spec_text))
            sys.exit(1)
        if result.warnings:
            for warn in result.warnings:
                _warn(ctx, warn.render(spec_text))

    _success(ctx, f"Spec is valid ({len(ir.operations)} operations, "
                   f"{len(ir.invariants)} invariants)")

@cli.command()
@click.argument("spec_file", type=click.Path(exists=True))
@click.option("--target", type=click.Choice(["python-fastapi", "go-chi", "ts-express"]),
              default="python-fastapi")
@click.option("--output", "-o", type=click.Path(), default="./generated")
@click.option("--llm", default="claude-sonnet-4-20250514")
@click.option("--no-synthesis", is_flag=True)
@click.option("--no-verify", is_flag=True)
@click.option("--no-tests", is_flag=True)
@click.option("--no-infra", is_flag=True)
@click.option("--dry-run", is_flag=True)
@click.pass_context
def generate(ctx, spec_file, target, output, llm, no_synthesis, no_verify,
             no_tests, no_infra, dry_run):
    """Generate a complete service from a spec file."""
    from .parser import parse_spec_file
    from .ir import build_ir
    from .verify import verify_spec
    from .conventions import apply_conventions
    from .synthesis import synthesize_operations
    from .emit import emit_project
    from .test_gen import generate_tests

    spec_text = Path(spec_file).read_text()

    # Parse
    _log(ctx, f"Parsing {spec_file}...")
    ast = parse_spec_file(spec_text, filename=spec_file)
    ir = build_ir(ast)

    # Verify
    if not no_verify:
        _log(ctx, "Verifying spec consistency...")
        result = verify_spec(ir, level=2, timeout=30)
        if result.errors:
            for err in result.errors:
                _error(ctx, err.render(spec_text))
            sys.exit(1)

    # Apply conventions
    _log(ctx, f"Applying conventions for {target}...")
    annotations = apply_conventions(ir, profile=target)

    # LLM Synthesis
    if not no_synthesis:
        _log(ctx, "Synthesizing operation implementations...")
        synthesis_results = synthesize_operations(ir, annotations, model=llm)
        for name, result in synthesis_results.items():
            status = "verified" if result.verified else "UNVERIFIED"
            _log(ctx, f"  {name}: {status} ({result.iterations} iterations, ${result.cost_usd:.2f})")
    else:
        synthesis_results = {}

    # Emit
    _log(ctx, f"Generating project in {output}/...")
    manifest = emit_project(ir, annotations, synthesis_results,
                            target=target, output_dir=output, dry_run=dry_run)

    # Tests
    if not no_tests:
        _log(ctx, "Generating tests...")
        test_manifest = generate_tests(ir, annotations, target=target,
                                       output_dir=output, dry_run=dry_run)
        manifest.extend(test_manifest)

    # Infrastructure
    if not no_infra:
        _log(ctx, "Generating infrastructure files...")
        # Dockerfile, docker-compose, CI config
        # (handled by emit_project based on flags)

    # Summary
    if dry_run:
        _log(ctx, "Dry run -- files that would be generated:")
        for entry in manifest:
            _log(ctx, f"  {entry.action:8s} {entry.path}")
    else:
        _success(ctx, f"Generated {len(manifest)} files in {output}/")
        _log(ctx, f"Run: cd {output} && docker-compose up")
```

### 6.3 Example Workflows

**First-time generation:**

```bash
# Generate a URL shortener service
$ spec-to-rest generate --target python-fastapi --output ./url-shortener url_shortener.spec

Parsing url_shortener.spec...
Building IR...
Verifying spec consistency...
Applying conventions for python-fastapi...
Synthesizing operation implementations...
  Shorten: verified (3 iterations, $0.12)
  Resolve: verified (1 iteration, $0.02)
  Delete: verified (1 iteration, $0.02)
Generating project in ./url-shortener/...
Generating tests...
Generating infrastructure files...
Generated 18 files in ./url-shortener/

Run: cd ./url-shortener && docker-compose up

# Run the generated service
$ cd url-shortener
$ docker-compose up -d
$ curl -X POST http://localhost:8000/shorten -d '{"url": "https://example.com"}'
{"code": "abc123", "short_url": "http://localhost:8000/abc123"}
```

**Iterative development (spec changes -> regenerate):**

```bash
# Edit the spec to add a Stats operation
$ vim url_shortener.spec

# See what would change
$ spec-to-rest diff --output ./url-shortener url_shortener.spec
  added    url-shortener/app/routes/stats.py
  modified url-shortener/app/main.py (1 route added)
  modified url-shortener/openapi.yaml (1 endpoint added)
  added    url-shortener/tests/test_stats_property.py
  modified url-shortener/tests/test_stateful.py (1 rule added)

# Regenerate
$ spec-to-rest generate --target python-fastapi --output ./url-shortener url_shortener.spec

# The generated service includes the new Stats operation
```

**CI/CD integration:**

```yaml
# .github/workflows/spec-check.yml
name: Spec Verification
on: [push, pull_request]
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: spec-to-rest/setup@v1 # installs compiler + Z3 + Dafny
      - run: spec-to-rest check --verify-level 3 spec.rest
      - run: spec-to-rest generate --target python-fastapi --output ./generated spec.rest
      - run: cd generated && docker-compose up -d && sleep 5
      - run: cd generated && pytest tests/ -v
      - run: cd generated && schemathesis run openapi.yaml --base-url http://localhost:8000
```

**Running in a Docker container:**

```bash
# The compiler itself runs in Docker (includes Z3, Dafny, LLM SDK)
$ docker run --rm -v $(pwd):/workspace -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY \
    spec-to-rest/compiler:latest \
    generate --target python-fastapi --output /workspace/generated /workspace/spec.rest
```

---

## 7. Testing Strategy for the Compiler Itself

### 7.1 Unit Tests

**Parser tests (spec text -> expected AST):**

```python
# tests/unit/test_parser.py
import pytest
from spec_to_rest.parser import parse_spec_file
from spec_to_rest.parser.ast import *

class TestParseEntity:
    def test_simple_entity(self):
        ast = parse_spec_file("""
            service Test {
                entity Foo {
                    name: String
                    age: Int
                }
            }
        """)
        assert len(ast.entities) == 1
        entity = ast.entities[0]
        assert entity.name == "Foo"
        assert len(entity.fields) == 2
        assert entity.fields[0].name == "name"
        assert entity.fields[0].type_expr.name == "String"

    def test_entity_with_invariant(self):
        ast = parse_spec_file("""
            service Test {
                entity Code {
                    value: String
                    invariant: len(value) >= 6
                }
            }
        """)
        entity = ast.entities[0]
        assert len(entity.invariants) == 1
        inv = entity.invariants[0]
        assert inv.kind == ExprKind.BINARY_OP
        assert inv.binary_op == BinaryOp.GTE

    def test_parse_error_missing_brace(self):
        with pytest.raises(ParseError) as exc_info:
            parse_spec_file("""
                service Test {
                    entity Foo {
                        name: String
                    # missing closing brace
                }
            """)
        error = exc_info.value
        assert "expected '}'" in str(error)
        assert error.span.start_line > 0

class TestParseOperation:
    def test_operation_with_requires_ensures(self):
        ast = parse_spec_file("""
            service Test {
                state { items: String -> set Int }
                operation Add {
                    input: key: String, value: Int
                    requires: key not in items
                    ensures: items'[key] = value
                }
            }
        """)
        op = ast.operations[0]
        assert op.name == "Add"
        assert len(op.inputs) == 2
        assert len(op.requires) == 1
        assert len(op.ensures) == 1

class TestParseExpressions:
    def test_quantifier(self):
        expr = parse_expr("all x in store | valid(x)")
        assert expr.kind == ExprKind.QUANTIFIER
        assert expr.quantifier_kind == QuantifierKind.FORALL
        assert expr.quantifier_var == "x"

    def test_cardinality(self):
        expr = parse_expr("#store + 1")
        assert expr.kind == ExprKind.BINARY_OP
        assert expr.left.kind == ExprKind.CARDINALITY

    def test_pre_state(self):
        expr = parse_expr("code not in pre(store)")
        assert expr.kind == ExprKind.MEMBERSHIP
        assert expr.membership_negated == True
        assert expr.membership_collection.kind == ExprKind.PRE_STATE

    def test_post_state(self):
        expr = parse_expr("store'[code] = url")
        assert expr.kind == ExprKind.BINARY_OP
        assert expr.left.kind == ExprKind.INDEX
        assert expr.left.index_base.kind == ExprKind.POST_STATE

    def test_operator_precedence(self):
        # 'and' binds tighter than 'or'
        expr = parse_expr("a or b and c")
        assert expr.kind == ExprKind.BINARY_OP
        assert expr.binary_op == BinaryOp.OR
        assert expr.right.binary_op == BinaryOp.AND
```

**Convention engine tests (IR -> expected HTTP mapping):**

```python
# tests/unit/test_conventions_http.py
from spec_to_rest.conventions.http import HttpConventionEngine
from spec_to_rest.ir.types import *

class TestHttpMethodMapping:
    def test_operation_with_state_mutation_is_post(self):
        """Operation that adds to state -> POST."""
        op = OperationDecl(
            name="Shorten",
            inputs=[ParamDecl("url", TypeExpr(TypeKind.SIMPLE, name="LongURL"))],
            outputs=[ParamDecl("code", TypeExpr(TypeKind.SIMPLE, name="ShortCode"))],
            requires=[],
            ensures=[
                # store'[code] = url  (state mutation)
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.INDEX,
                        index_base=Expr(kind=ExprKind.POST_STATE,
                            state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store")),
                        index_key=Expr(kind=ExprKind.VARIABLE, var_name="code")),
                    right=Expr(kind=ExprKind.VARIABLE, var_name="url")),
            ],
        )
        engine = HttpConventionEngine()
        annotation = engine.annotate_operation(op)
        assert annotation.http_method == "POST"
        assert annotation.http_status_success == 201

    def test_operation_with_no_mutation_is_get(self):
        """Operation where store' = store -> GET."""
        op = OperationDecl(
            name="Resolve",
            inputs=[ParamDecl("code", TypeExpr(TypeKind.SIMPLE, name="ShortCode"))],
            outputs=[ParamDecl("url", TypeExpr(TypeKind.SIMPLE, name="LongURL"))],
            requires=[],
            ensures=[
                # store' = store  (no mutation)
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.POST_STATE,
                        state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store")),
                    right=Expr(kind=ExprKind.VARIABLE, var_name="store")),
            ],
        )
        engine = HttpConventionEngine()
        annotation = engine.annotate_operation(op)
        assert annotation.http_method == "GET"
        assert annotation.http_status_success == 200

    def test_operation_with_removal_is_delete(self):
        """Operation where element removed from state -> DELETE."""
        op = OperationDecl(
            name="Delete",
            inputs=[ParamDecl("code", TypeExpr(TypeKind.SIMPLE, name="ShortCode"))],
            outputs=[],
            requires=[],
            ensures=[
                # code not in store'
                Expr(kind=ExprKind.MEMBERSHIP, membership_negated=True,
                    membership_element=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                    membership_collection=Expr(kind=ExprKind.POST_STATE,
                        state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store"))),
            ],
        )
        engine = HttpConventionEngine()
        annotation = engine.annotate_operation(op)
        assert annotation.http_method == "DELETE"
        assert annotation.http_status_success == 204

    def test_requires_membership_maps_to_404(self):
        """requires: code in store -> 404 when code not found."""
        op = OperationDecl(
            name="Resolve",
            inputs=[ParamDecl("code", TypeExpr(TypeKind.SIMPLE, name="ShortCode"))],
            outputs=[ParamDecl("url", TypeExpr(TypeKind.SIMPLE, name="LongURL"))],
            requires=[
                Expr(kind=ExprKind.MEMBERSHIP, membership_negated=False,
                    membership_element=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                    membership_collection=Expr(kind=ExprKind.VARIABLE, var_name="store")),
            ],
            ensures=[],
        )
        engine = HttpConventionEngine()
        annotation = engine.annotate_operation(op)
        assert "not_found" in annotation.http_status_errors
        assert annotation.http_status_errors["not_found"] == 404
```

**Emitter tests (IR -> expected code snippets):**

```python
# tests/unit/test_emitter_python.py
from spec_to_rest.emit.engine import PythonFastAPIEmitter
from spec_to_rest.ir.types import *
from spec_to_rest.conventions.engine import ConventionAnnotations, OperationAnnotation

class TestPythonRouteEmission:
    def test_post_route(self):
        ir = make_test_ir()  # fixture helper
        annotations = ConventionAnnotations(
            operation_annotations={
                "Shorten": OperationAnnotation(
                    http_method="POST", http_path="/shorten",
                    http_status_success=201,
                    http_status_errors={"validation": 422},
                    request_body_model="ShortenRequest",
                    response_body_model="ShortenResponse",
                    path_params=[], query_params=[],
                    operation_kind="create", is_idempotent=False,
                    headers={},
                ),
            },
            entity_annotations={}, relation_annotations={},
        )
        emitter = PythonFastAPIEmitter(ir, annotations)
        code = emitter.emit_route("Shorten")

        assert '@app.post("/shorten", status_code=201)' in code
        assert "async def shorten(body: ShortenRequest) -> ShortenResponse:" in code
        assert "HTTPException" in code  # for validation errors
```

### 7.2 Integration Tests

**End-to-end test:**

```python
# tests/integration/test_end_to_end.py
import subprocess
import time
import requests
import pytest
from pathlib import Path

@pytest.fixture(scope="module")
def generated_service(tmp_path_factory):
    """Generate a service from a fixture spec and start it with docker-compose."""
    output_dir = tmp_path_factory.mktemp("generated")
    spec_path = Path(__file__).parent.parent / "fixtures" / "url_shortener.spec"

    # Generate
    result = subprocess.run(
        ["python", "-m", "spec_to_rest", "generate",
         "--target", "python-fastapi",
         "--output", str(output_dir),
         "--no-synthesis",  # use TODOs for speed in CI
         str(spec_path)],
        capture_output=True, text=True
    )
    assert result.returncode == 0, f"Generation failed: {result.stderr}"

    # Start service
    subprocess.run(
        ["docker-compose", "up", "-d", "--build"],
        cwd=str(output_dir),
        capture_output=True, text=True,
        check=True
    )

    # Wait for service to be ready
    for _ in range(30):
        try:
            resp = requests.get("http://localhost:8000/health")
            if resp.status_code == 200:
                break
        except requests.ConnectionError:
            time.sleep(1)
    else:
        pytest.fail("Service failed to start within 30 seconds")

    yield output_dir

    # Cleanup
    subprocess.run(
        ["docker-compose", "down", "-v"],
        cwd=str(output_dir),
        capture_output=True
    )

def test_generated_code_compiles(generated_service):
    """The generated Python code should have no syntax errors."""
    result = subprocess.run(
        ["python", "-m", "py_compile", "app/main.py"],
        cwd=str(generated_service),
        capture_output=True, text=True
    )
    assert result.returncode == 0

def test_openapi_spec_valid(generated_service):
    """The generated OpenAPI spec should be valid."""
    resp = requests.get("http://localhost:8000/openapi.json")
    assert resp.status_code == 200
    spec = resp.json()
    assert "paths" in spec
    assert "/shorten" in spec["paths"]

def test_crud_operations(generated_service):
    """Basic CRUD cycle should work."""
    # Create
    resp = requests.post("http://localhost:8000/shorten",
                         json={"url": "https://example.com"})
    assert resp.status_code == 201
    data = resp.json()
    code = data["code"]

    # Read
    resp = requests.get(f"http://localhost:8000/{code}",
                        allow_redirects=False)
    assert resp.status_code in (200, 302)

    # Delete
    resp = requests.delete(f"http://localhost:8000/{code}")
    assert resp.status_code == 204

    # Read after delete -> 404
    resp = requests.get(f"http://localhost:8000/{code}")
    assert resp.status_code == 404
```

### 7.3 Golden File Tests

```python
# tests/golden/test_golden_files.py
import pytest
from pathlib import Path
from spec_to_rest.parser import parse_spec_file
from spec_to_rest.ir import build_ir
from spec_to_rest.conventions import apply_conventions
from spec_to_rest.emit import emit_project

FIXTURES_DIR = Path(__file__).parent.parent / "fixtures"
EXPECTED_DIR = FIXTURES_DIR / "expected"

@pytest.mark.parametrize("spec_name", ["url_shortener", "todo_list"])
def test_golden_openapi(spec_name, snapshot):
    spec_text = (FIXTURES_DIR / f"{spec_name}.spec").read_text()
    ast = parse_spec_file(spec_text)
    ir = build_ir(ast)
    annotations = apply_conventions(ir, profile="python-fastapi")
    openapi = emit_openapi(ir, annotations)
    snapshot.assert_match(openapi, f"{spec_name}_openapi.yaml")

@pytest.mark.parametrize("spec_name", ["url_shortener", "todo_list"])
def test_golden_sql(spec_name, snapshot):
    spec_text = (FIXTURES_DIR / f"{spec_name}.spec").read_text()
    ast = parse_spec_file(spec_text)
    ir = build_ir(ast)
    annotations = apply_conventions(ir, profile="python-fastapi")
    sql = emit_sql(ir, annotations)
    snapshot.assert_match(sql, f"{spec_name}_migration.sql")
```

### 7.4 Property-Based Tests for the Compiler

```python
# tests/unit/test_parser_properties.py
from hypothesis import given, strategies as st, assume
from spec_to_rest.parser import parse_spec_file
from spec_to_rest.ir import build_ir
from spec_to_rest.ir.printer import print_ir

# Strategy: generate random valid spec text
@st.composite
def valid_entity_name(draw):
    first = draw(st.sampled_from("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    rest = draw(st.text(alphabet="abcdefghijklmnopqrstuvwxyz0123456789_", min_size=0, max_size=20))
    return first + rest

@st.composite
def valid_field_type(draw):
    return draw(st.sampled_from(["String", "Int", "Float", "Bool", "DateTime"]))

@st.composite
def valid_entity(draw):
    name = draw(valid_entity_name())
    num_fields = draw(st.integers(min_value=1, max_value=5))
    fields = []
    used_names = set()
    for _ in range(num_fields):
        fname = draw(st.text(alphabet="abcdefghijklmnopqrstuvwxyz", min_size=1, max_size=10))
        assume(fname not in used_names)
        used_names.add(fname)
        ftype = draw(valid_field_type())
        fields.append(f"    {fname}: {ftype}")
    return f"  entity {name} {{\n" + "\n".join(fields) + "\n  }"

@st.composite
def valid_spec(draw):
    name = draw(valid_entity_name())
    num_entities = draw(st.integers(min_value=1, max_value=3))
    entities = [draw(valid_entity()) for _ in range(num_entities)]
    return f"service {name} {{\n" + "\n\n".join(entities) + "\n}"

@given(spec_text=valid_spec())
def test_parse_roundtrip(spec_text):
    """Any valid spec should parse without error."""
    ast = parse_spec_file(spec_text)
    ir = build_ir(ast)
    assert ir.name is not None
    assert len(ir.entities) >= 1

@given(spec_text=valid_spec())
def test_ir_serialization_roundtrip(spec_text):
    """IR -> JSON -> IR should be lossless."""
    ast = parse_spec_file(spec_text)
    ir = build_ir(ast)
    json_text = serialize_ir(ir)
    ir2 = deserialize_ir(json_text)
    assert ir.name == ir2.name
    assert len(ir.entities) == len(ir2.entities)
```

---

## 8. Dependency Management and Distribution

### 8.1 Dependencies (Python examples shown for illustrative reference)

```toml
# pyproject.toml
[project]
name = "spec-to-rest"
version = "0.1.0"
description = "Compile formal behavioral specs to verified REST services"
requires-python = ">=3.11"
dependencies = [
    "click>=8.1",            # CLI framework
    "jinja2>=3.1",           # template engine for code emission
    "z3-solver>=4.12",       # Z3 SMT solver (spec verification)
    "anthropic>=0.40",       # Anthropic API client (LLM synthesis)
    "pyyaml>=6.0",           # YAML generation (OpenAPI)
    "rich>=13.0",            # terminal output formatting
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "pytest-snapshot>=0.9",
    "hypothesis>=6.100",
    "mypy>=1.9",
    "ruff>=0.4",
]

[project.scripts]
spec-to-rest = "spec_to_rest.cli:cli"

[tool.mypy]
strict = true
```

### 8.2 External Tool Dependencies

| Tool         | Version | Purpose                         | Size    | Bundling Strategy                   |
| ------------ | ------- | ------------------------------- | ------- | ----------------------------------- |
| Z3           | 4.13+   | Spec verification               | ~50 MB  | Bundled via `z3-solver` pip package |
| Dafny        | 4.9+    | Code verification + compilation | ~200 MB | User install or Docker image        |
| Python 3.11+ | -       | Target language toolchain       | -       | User's machine                      |
| Go 1.22+     | -       | Target language toolchain       | -       | User's machine (if targeting Go)    |
| Node 20+     | -       | Target language toolchain       | -       | User's machine (if targeting TS)    |
| Docker       | 24+     | Running generated services      | -       | User's machine                      |
| PostgreSQL   | 16+     | Database for generated services | -       | Via docker-compose                  |

### 8.3 Distribution Options

**Option 1: pip install (Python users)**

```bash
pip install spec-to-rest
# or with pipx for isolation:
pipx install spec-to-rest
```

Pros: Simple, familiar to Python users. Cons: Requires Python 3.11+, does not include Dafny.

**Option 2: Docker image (most reliable)**

```dockerfile
FROM python:3.12-slim

# Install Z3 (included via pip)
# Install Dafny
RUN apt-get update && apt-get install -y dotnet-sdk-8.0 && \
    dotnet tool install --global dafny --version 4.9.0

# Install compiler
COPY . /app
RUN pip install /app

ENTRYPOINT ["spec-to-rest"]
```

```bash
docker pull spec-to-rest/compiler:latest
docker run --rm -v $(pwd):/workspace spec-to-rest/compiler:latest generate spec.rest
```

Pros: Includes all dependencies (Z3, Dafny, SDKs). Works everywhere. Cons: Requires Docker, ~500 MB
image.

**Option 3: GitHub Action (CI users)**

```yaml
- uses: spec-to-rest/action@v1
  with:
    spec-file: spec.rest
    target: python-fastapi
    output: ./generated
```

The action uses the Docker image internally.

**Option 4: Single binary (future, Kotlin/GraalVM path)**

```bash
# macOS
brew install spec-to-rest

# Linux
curl -sSL https://get.spec-to-rest.dev | sh

# Windows
scoop install spec-to-rest
```

GraalVM native-image produces a single binary that bundles the JVM, Z3 (via JNI), and Alloy. Dafny
still requires a separate install or bundled .NET runtime.

### 8.4 LLM API Key Management

The compiler needs an Anthropic API key for the synthesis stage. Key management follows standard
practices:

```
# Priority order:
1. --llm-api-key CLI flag (not recommended -- visible in shell history)
2. ANTHROPIC_API_KEY environment variable (recommended for CI)
3. .spec-to-rest.toml config file (recommended for local dev)
4. ~/.config/spec-to-rest/config.toml (user-level default)
```

```toml
# .spec-to-rest.toml
[synthesis]
model = "claude-sonnet-4-20250514"
api_key_env = "ANTHROPIC_API_KEY"  # name of env var to read
cost_budget_usd = 5.0
cache_enabled = true
cache_dir = ".spec-to-rest-cache"
```

The compiler should never log or display the API key. If `--no-synthesis` is set, no API key is
needed.

---

## 9. Build Plan with Milestones

### Phase 1: Parser + Convention Engine MVP (~4 weeks)

**Goal:** `spec-to-rest generate spec.rest` produces a running Python/FastAPI service for the URL
shortener spec.

**Week 1-2: Parser**

- Implement lexer (tokenizer) for the DSL
- Implement recursive descent parser for: entities, state, operations, invariants
- Implement AST -> IR builder
- Implement IR printer (for `--verbose` and debugging)
- Write unit tests for all parser features
- Deliverable: `spec-to-rest inspect --format ir url_shortener.spec` prints the IR

**Week 2-3: Convention Engine**

- Implement HTTP method/path/status mapping from IR analysis
  - State mutation detection (pre-state vs post-state comparison in ensures)
  - Operation kind classification (create, read, update, delete, action)
  - Path parameter extraction from inputs
  - Error status code mapping from requires clauses
- Implement database schema mapping
  - Entity -> table, field -> column
  - State relation -> foreign key or junction table
  - Constraint -> CHECK constraint
  - Type mapping (String -> TEXT, Int -> INTEGER, etc.)
- Implement naming conventions (snake_case, pluralize, etc.)
- Deliverable: `spec-to-rest inspect --format openapi url_shortener.spec` prints valid OpenAPI

**Week 3-4: Code Emitter**

- Implement Jinja2 templates for Python/FastAPI target:
  - `main.py` (FastAPI app, router registration)
  - `routes/{operation}.py` (route handlers)
  - `models.py` (Pydantic request/response models)
  - `schemas.py` (SQLAlchemy ORM models)
  - `database.py` (connection, session management)
  - `config.py` (settings from environment)
  - `requirements.txt`
  - `Dockerfile` + `docker-compose.yml` (app + PostgreSQL)
  - `alembic/` (database migrations)
- Implement SQL DDL generation for initial migration
- Implement OpenAPI spec generation
- Write golden file tests for all generated output
- Deliverable: `spec-to-rest generate url_shortener.spec && cd generated && docker-compose up`

**Phase 1 exit criteria:**

- URL shortener spec -> running service that responds to requests
- CRUD operations work (create short URL, resolve, delete)
- Business logic is stubbed (TODO comments for non-trivial operations)
- Generated code passes linting (ruff) and type checking (mypy)
- 80%+ test coverage on the compiler itself

---

### Phase 2: Spec Verification (~3 weeks)

**Goal:** `spec-to-rest check spec.rest` catches spec errors before code generation.

**Week 5: Z3 Backend**

- Implement IR -> Z3 constraint translation
  - Entity type invariants -> Z3 assertions
  - State relation multiplicities -> Z3 cardinality constraints
  - Global invariants -> Z3 universal quantifier assertions
- Implement invariant consistency checking (are all invariants simultaneously satisfiable?)
- Implement basic counterexample rendering
- Deliverable: `spec-to-rest check --verify-level 2 spec.rest` reports contradictory invariants

**Week 6: Operation Preservation**

- Implement pre/post-state encoding for Z3
  - Pre-state as "given" variables
  - Post-state as "exists" variables
  - Ensures as constraints linking pre and post
  - Frame condition inference (what does NOT change)
- Implement operation-preserves-invariant checking
  - For each (operation, invariant) pair: verify that if invariant holds in pre-state and ensures
    hold, then invariant holds in post-state
- Deliverable: `spec-to-rest check --verify-level 3 spec.rest` reports operations that violate
  invariants

**Week 7: Error Reporting**

- Implement rich error messages with source location highlighting
  - "Invariant on line 12 conflicts with invariant on line 15"
  - "Operation 'Shorten' on line 20 may violate invariant on line 30"
  - Counterexample rendering: "When store contains {abc -> x.com}, calling Delete(abc) leaves store
    empty but invariant requires..."
- Implement `--verify-timeout` and graceful timeout handling
- Write integration tests for all verification scenarios

---

### Phase 3: Test Generation (~3 weeks)

**Goal:** Generated service includes comprehensive tests derived from the spec.

**Week 8: Property Tests**

- Translate `ensures` clauses to Hypothesis property tests
  - Each ensures clause -> one test function
  - Input parameters generated by Hypothesis strategies
  - Test body: call the API endpoint, assert ensures holds on response
- Generate `conftest.py` with database fixtures and test client setup
- Deliverable: `cd generated && pytest tests/test_property.py` runs ensures-derived tests

**Week 9: Stateful Tests**

- Translate operations to Hypothesis `RuleBasedStateMachine`
  - Each operation -> one `@rule`
  - Each `requires` clause -> one `@precondition`
  - Each global invariant -> one `@invariant`
  - Operation inputs -> rule parameters with appropriate strategies
  - Bundles for cross-operation data flow (e.g., created codes fed to resolve/delete)
- Deliverable: `cd generated && pytest tests/test_stateful.py` runs state machine tests

**Week 10: Schemathesis Integration**

- Generate Schemathesis configuration from OpenAPI spec
- Generate a `schemathesis.yml` config file with:
  - Target URL
  - Authentication (if any)
  - Stateful test options
  - Custom checks from spec invariants
- Deliverable: `cd generated && schemathesis run openapi.yaml` runs API fuzzing tests

---

### Phase 4: LLM Synthesis (~4 weeks)

**Goal:** Non-trivial operations are synthesized and verified via LLM + Dafny.

**Week 11: Dafny Generation**

- Implement IR -> Dafny source translation
  - Entity types -> Dafny type definitions
  - State relations -> Dafny class fields
  - Operations -> Dafny methods with requires/ensures
  - Invariants -> Dafny class invariants
- Implement Dafny compiler invocation and error parsing
- Deliverable: `spec-to-rest inspect --format dafny spec.rest` emits valid Dafny

**Week 12: LLM Integration**

- Implement Anthropic API client with:
  - Prompt construction from IR + Dafny skeleton
  - Response parsing (extract code blocks)
  - Token tracking and cost budgeting
  - Cache layer (hash of operation spec -> cached result)
- Deliverable: LLM generates Dafny method bodies from spec operations

**Week 13: CEGIS Feedback Loop**

- Implement the generate-verify-repair loop:
  - LLM generates candidate Dafny
  - Dafny verifier checks it
  - On failure: extract error messages and counterexamples
  - Feed errors back to LLM as repair context
  - Repeat up to N iterations
- Implement fallback: if CEGIS fails, emit TODO stub
- Deliverable: Complex operations are verified and compiled

**Week 14: Dafny -> Target Language**

- Implement Dafny -> Python compilation integration
  - Invoke `dafny translate py`
  - Post-process generated Python to integrate with FastAPI app
  - Handle Dafny runtime library dependency
- Implement integration of synthesized code with convention-generated scaffolding
- Deliverable: Synthesized code plugs into generated service seamlessly

---

### Phase 5: Multi-Target Support (~4 weeks)

**Goal:** Generate services for Go/chi and TypeScript/Express in addition to Python/FastAPI.

**Week 15-16: Go/chi Target**

- Create Go convention profile (naming, types, imports)
- Create Go templates:
  - `main.go` (chi router, middleware)
  - `handler.go` (request handlers)
  - `model.go` (structs)
  - `repository.go` (database access via sqlx)
  - `go.mod`, `go.sum`
  - `Dockerfile`
- Implement Dafny -> Go compilation integration
- Write golden file tests for Go output
- Deliverable: `spec-to-rest generate --target go-chi spec.rest`

**Week 17-18: TypeScript/Express Target**

- Create TypeScript convention profile
- Create TypeScript templates:
  - `app.ts` (Express app, middleware)
  - `routes/{operation}.ts` (route handlers)
  - `models.ts` (TypeScript interfaces + Zod schemas)
  - `repository.ts` (Prisma ORM)
  - `package.json`, `tsconfig.json`
  - `Dockerfile`
- Implement Dafny -> JavaScript compilation integration
- Write golden file tests for TypeScript output
- Deliverable: `spec-to-rest generate --target ts-express spec.rest`

---

### Phase 6: Polish (~2 weeks)

**Week 19: Error Messages and Documentation**

- Audit all error messages for clarity
  - Every error should include: what went wrong, where (line:col), and what to do
  - Example: "error[E012]: Operation 'Shorten' requires 'code' to not exist in store, but 'code' is
    not defined as an input parameter. Did you mean to add 'code' to the output? (spec.rest:22:5)"
- Write language reference documentation
- Write convention rules documentation
- Write getting-started tutorial
- Deliverable: `docs/` directory with complete documentation

**Week 20: Performance and Release**

- Profile compiler on large specs (10+ entities, 20+ operations)
- Optimize hot paths (parser, convention engine, template rendering)
- Implement `--dry-run` and `diff` commands
- Set up release pipeline:
  - PyPI publish on git tag
  - Docker image publish on git tag
  - GitHub Action publish
- Deliverable: v0.1.0 release

---

## 10. Risk Mitigation

### Risk 1: Parser is Too Hard or Grammar Keeps Changing

**Probability:** Medium **Impact:** Delays Phase 1 by 1-2 weeks

**Mitigation strategies:**

1. Start with a minimal grammar subset (entities + operations only, no conventions block). Add
   features incrementally.
2. Use hand-written recursive descent for maximum flexibility during grammar iteration. Do not
   invest in ANTLR4 until the grammar is stable.
3. Keep a "parse test for every grammar feature" discipline. Any grammar change must update tests
   first.
4. If stuck on expression parsing, use a Pratt parser (well-documented, 200 lines of code, handles
   precedence automatically).

**Escalation:** If grammar design takes more than 2 weeks, freeze the grammar and revisit in
Phase 6. A slightly awkward grammar that works is better than a perfect grammar that doesn't ship.

---

### Risk 2: Z3 Integration is Fragile or Slow

**Probability:** Medium-High **Impact:** Delays Phase 2 or produces unreliable verification

**Mitigation strategies:**

1. Start with simple consistency checks (are invariants satisfiable?) before attempting operation
   preservation proofs. The simple checks use basic Z3 and are reliable.
2. Use Z3 timeouts aggressively (5s for quick checks, 30s for deep checks). Report "unknown" as a
   warning, not an error.
3. Use uninterpreted functions for complex predicates (like `isValidURI`). This makes Z3's job
   easier at the cost of weaker verification.
4. If Z3 Python bindings cause packaging issues, fall back to subprocess invocation of the Z3 CLI
   with SMT-LIB2 input format.

**Escalation:** If Z3 integration takes more than 2 weeks, ship Phase 2 with parse-level checks only
(type resolution, name resolution, basic consistency) and defer SMT-based verification.

---

### Risk 3: Dafny is Too Slow or Produces Non-Idiomatic Code

**Probability:** Medium **Impact:** Phase 4 is less useful; synthesized code needs manual cleanup

**Mitigation strategies:**

1. Make LLM synthesis entirely optional (`--no-synthesis` flag). The compiler is useful without it
   -- convention-based generation covers CRUD.
2. For simple operations where the body is deterministic from the spec (e.g., `url = store[code]` is
   just a database lookup), emit code directly without LLM/Dafny. Only invoke the LLM for operations
   with non-trivial computation.
3. Post-process Dafny-generated code to make it more idiomatic:
   - Rename variables to match conventions
   - Replace Dafny runtime library calls with native equivalents where possible
   - Format with the target language's formatter (black, gofmt, prettier)
4. Cache Dafny verification results aggressively. The same spec operation should not be re-verified
   unless the spec changes.

**Escalation:** If Dafny integration takes more than 3 weeks, ship Phase 4 with LLM synthesis only
(no formal verification). The LLM generates target-language code directly, checked by type system
and tests rather than Dafny proofs.

---

### Risk 4: LLM Costs Too High

**Probability:** Low-Medium **Impact:** Users avoid synthesis; compiler value proposition weakened

**Mitigation strategies:**

1. Cache all synthesis results. A spec that hasn't changed should never re-invoke the LLM.
2. Skip LLM for CRUD operations (the majority of REST API operations). Only invoke for operations
   with non-trivial computation.
3. Use the smallest effective model. Start with Claude Sonnet (cheaper) and only escalate to Opus
   for operations that fail verification.
4. Implement a cost budget (`--llm-budget 5.00`) that caps total spending per compilation. Default
   to $5.00 which is sufficient for 10-20 complex operations.
5. Support local LLM models via OpenAI-compatible API (Ollama, vLLM) for cost-sensitive users
   willing to accept lower synthesis quality.

**Estimated costs per compilation:**

| Spec complexity        | Operations     | LLM calls   | Estimated cost |
| ---------------------- | -------------- | ----------- | -------------- |
| URL shortener (3 ops)  | 1 synthesized  | 3-8 calls   | $0.05-0.20     |
| Todo list (5 ops)      | 2 synthesized  | 6-16 calls  | $0.10-0.40     |
| E-commerce (15 ops)    | 5 synthesized  | 15-40 calls | $0.30-1.20     |
| Large service (30 ops) | 10 synthesized | 30-80 calls | $0.60-2.50     |

---

### Risk 5: Generated Code Quality is Poor

**Probability:** Medium **Impact:** Users don't trust the compiler; adoption stalls

**Mitigation strategies:**

1. Extensive golden file tests. Every example spec has a "known-good" output that is manually
   reviewed. Any change to code generation is caught immediately.
2. Generate code that passes the target language's strictest linting:
   - Python: ruff + mypy --strict
   - Go: golangci-lint with all checks
   - TypeScript: eslint + tsc --strict
3. Generate code that is formatted by the target language's canonical formatter (black, gofmt,
   prettier). Never emit unformatted code.
4. Limit the number of supported targets. Three well-tested targets (Python, Go, TypeScript) are
   better than ten mediocre ones.
5. Include the spec as a comment in the generated code. Users can see exactly which spec element
   produced which code, making manual adjustments easier.

---

### Risk 6: Convention Engine Makes Wrong Decisions

**Probability:** Medium **Impact:** Generated HTTP API doesn't match user expectations

**Mitigation strategies:**

1. Make every convention decision overridable via the `conventions` block in the spec. The engine's
   defaults are suggestions, not mandates.
2. Implement `spec-to-rest inspect --stage conventions spec.rest` to show all convention decisions
   before code generation. Users can preview and override.
3. Document every convention rule in `docs/convention_rules.md` with rationale.
4. Use conservative defaults that match REST API community norms (POST for create, GET for read,
   etc.).
5. When the convention engine is uncertain (e.g., an operation that both reads and writes state),
   emit a warning suggesting the user add an explicit convention override.

---

### Risk 7: TypeScript Ecosystem Limitations

**Probability:** Low (TypeScript ecosystem is mature) **Impact:** May need to shell out to native
binaries for some integrations

**Mitigation strategies:**

1. TypeScript is the sole implementation language (no rewrite needed). The compiler is shippable
   from Phase 1.
2. The TypeScript ecosystem has mature tooling for every integration point (ANTLR4 via antlr-ng, Z3
   via z3-solver npm, LLM SDKs).
3. Use Kotlin idiomatically from the start:
   - Sealed classes for IR (not Java-style class hierarchies)
   - Extension functions for utilities (not inheritance)
   - Coroutines for concurrent LLM calls (not threads)
4. If Kotlin is off the table, the Python compiler can be optimized with:
   - Mypyc compilation for hot paths (2-5x speedup)
   - multiprocessing for parallel verification and LLM calls
   - C extension for the parser (via tree-sitter Python bindings)

---

### Risk 8: Spec Language Design is Wrong

**Probability:** Medium-High (most likely risk) **Impact:** Fundamental rework needed; wasted effort
on parser/IR

**Mitigation strategies:**

1. Design the spec language iteratively. Start with the URL shortener example and add features only
   when a real spec needs them.
2. Write 5+ example specs before finalizing the grammar. Each spec will reveal missing features or
   awkward syntax.
3. Get user feedback on the spec language early (before building the code emitter). Show developers
   the spec and ask: "Does this make sense? Would you write it this way?"
4. Keep the parser/IR loosely coupled from the rest of the compiler. A grammar change should require
   updating `parser/` and `ir/builder.py` only -- the convention engine and emitters work on the IR,
   which is more stable than the syntax.
5. Study Quint's evolution carefully. Quint is the most recent formal spec DSL designed for
   developer adoption (not academics), and it went through multiple syntax iterations based on user
   feedback.

---

### Summary Decision Matrix

| Risk                                    | Probability | Impact   | Mitigation Cost          | Priority |
| --------------------------------------- | ----------- | -------- | ------------------------ | -------- |
| Spec language design is wrong           | High        | Critical | Low (iterate)            | P0       |
| Generated code quality is poor          | Medium      | High     | Medium (golden tests)    | P0       |
| Z3 integration is fragile               | Medium-High | Medium   | Low (timeouts, fallback) | P1       |
| Convention engine makes wrong decisions | Medium      | Medium   | Low (overrides)          | P1       |
| Dafny is too slow                       | Medium      | Medium   | Low (optional)           | P2       |
| LLM costs too high                      | Low-Medium  | Low      | Low (caching, budgets)   | P2       |
| Parser is too hard                      | Medium      | Low      | Low (hand-write)         | P3       |
| Team can't learn Kotlin                 | Variable    | Low      | Low (stay on Python)     | P3       |

The highest-priority risk is getting the spec language design right. This is addressed by aggressive
prototyping: build the simplest possible end-to-end flow (Phase 1) and iterate on the language based
on real usage. Every other risk has a fallback that keeps the project moving forward.

---

<!-- Added: gradual adoption strategy (gap analysis) -->

## 11. Gradual Adoption Strategy

A common adoption barrier for spec-driven tools is the "all or nothing" problem: developers feel
they must specify their entire service before getting any value. The spec-to-REST compiler is
designed for incremental adoption from day one.

### 11.1 Start with One Endpoint

A developer can write a minimal spec with a single entity and a single operation:

```
service MyService {
  entity Item {
    id: Int
    name: String
    invariant: len(name) >= 1
  }

  state {
    items: Int -> one Item
  }

  operation CreateItem {
    input:  name: String
    output: item: Item

    requires: len(name) >= 1
    ensures:  item.name = name and items' = pre(items) + {item.id -> item}
  }
}
```

Running `spec-to-rest compile myservice.spec` produces a working project with one POST endpoint, one
database table, one migration, validation, and tests. The developer can then add operations
incrementally -- each `spec-to-rest compile` regenerates only the affected files (see incremental
regeneration in the code generation pipeline).

### 11.2 Coexistence with Hand-Written Code

The generated project includes clearly marked `USER CODE` regions in service files where developers
can add hand-written logic that survives regeneration:

```python
# === USER CODE START: custom_middleware ===
# Add your custom middleware here. This block is preserved on regeneration.
# === USER CODE END: custom_middleware ===
```

Developers can also add entirely hand-written endpoints to the router by placing files in a
`custom/` directory that the generated main module auto-discovers. This means a team can
spec-generate their core CRUD while keeping complex endpoints hand-written, migrating them to the
spec one at a time.

### 11.3 Adopt Verification Gradually

The compiler pipeline has clear skip points:

| Stage                              | Can Skip?                | Effect of Skipping                                           |
| ---------------------------------- | ------------------------ | ------------------------------------------------------------ |
| Spec parsing + convention engine   | No (core)                | --                                                           |
| Model checking (spec verification) | Yes (`--skip-verify`)    | Spec may have inconsistencies; code still generates          |
| Dafny synthesis (business logic)   | Yes (`--skip-synthesis`) | Complex operations get `TODO` stubs instead of verified code |
| Conformance test generation        | Yes (`--skip-tests`)     | No auto-generated test suite                                 |

A team can start with `--skip-verify --skip-synthesis` and get the structural benefits (HTTP
routing, DB schema, validation, OpenAPI) immediately. As they gain confidence, they enable
verification and synthesis one operation at a time.

### 11.4 Migration from Existing Services

For teams with existing REST services, the recommended path is:

1. Write a spec for one resource/endpoint group
2. Generate the code and compare it against the existing implementation
3. Use the generated conformance tests to validate the existing service
4. Replace the existing implementation with the generated one, or keep both running behind a feature
   flag

The spec-to-REST compiler does not require a greenfield project. It is designed to be adopted
incrementally, one endpoint at a time, within an existing codebase.
