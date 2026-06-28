---
title: "Host language"
description: "The compiler-host evaluation and the Scala 3 choice"
---

## 1. Language choice for the compiler itself

The compiler implementation language determines development velocity, integration story with formal
tools, and distribution ergonomics. We evaluate five candidates against nine criteria.

### 1.1 Python

#### Parser code example (hand-written recursive descent)

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

#### Template/emitter code example (Jinja2)

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

**Z3 integration.** Native. The `z3-solver` PyPI package ships prebuilt binaries for all platforms.
The Python API is the primary Z3 interface, it is the best-documented and most feature-complete
binding. Example:

```python
from z3 import Solver, Int, And, ForAll, Implies, sat

s = Solver()
code_len = Int("code_len")
s.add(And(code_len >= 6, code_len <= 10))
result = s.check()  # sat
```

**LLM API integration.** The strongest option here. The `anthropic` Python SDK is Anthropic's primary SDK.
OpenAI, Cohere, and every other LLM provider ship Python SDKs first. Streaming, tool use, batch APIs
are all production-grade.

**Distribution/packaging.** Weakest dimension. Python packaging is notoriously fragile. Options:
`pip install spec-to-rest` requires users have a working Python environment. `pipx` improves
isolation. PyInstaller or Nuitka can produce single binaries but add 50-100 MB and have
platform-specific quirks. Docker is the reliable fallback.

**Assessment.** Best for prototyping and integration with Z3/LLM ecosystems. The packaging story is
the main liability, mitigated by Docker distribution. Type annotations with mypy provide adequate
safety for a project of this scale.

### 1.2 Rust

#### Parser code example (using pest PEG parser)

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

#### Template/emitter code example (askama)

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

**Z3 integration.** Available via `z3` crate (Rust bindings to the C API). Functional but less
ergonomic than Python. The bindings are maintained but lag behind the Python API in coverage.
Building from source requires LLVM/Clang toolchain.

**LLM API integration.** The `anthropic` Rust crate exists but is community-maintained, not
official. HTTP-level integration via `reqwest` is straightforward but requires implementing
streaming, retry logic, and error handling manually.

**Distribution/packaging.** Excellent. `cargo install spec-to-rest` produces a single static binary.
Cross-compilation via `cross` gives Linux/macOS/Windows from any host. Binary size ~10-20 MB. No
runtime dependencies.

**Assessment.** Best for production distribution and performance. Slower development velocity (2-3x
vs Python for compiler code). The Z3 and LLM integration stories are adequate but not first-class.
Good choice if the project outlives prototyping and performance matters (large specs with many
operations).

### 1.3 TypeScript/node

#### Parser code example (hand-written with tokenizer)

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

#### Template/emitter code example (EJS)

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

**Z3 integration.** The `z3-solver` npm package provides WASM-compiled Z3. It works but is ~3x
slower than native Z3 and has a 35 MB WASM payload. Alternatively, shell out to the Z3 binary.
Neither option is as clean as the Python bindings.

**LLM API integration.** Good. The `@anthropic-ai/sdk` is an official Anthropic package. OpenAI's
Node SDK is equally mature. Streaming with async iterators works well.

**Distribution/packaging.** Decent. `npm install -g spec-to-rest` works for Node users. `pkg` or
`esbuild` can produce single-binary executables (~50-80 MB). Bun offers faster startup and
single-binary distribution.

**Assessment.** Good middle ground. TypeScript provides static typing with fast iteration. The Z3
story is the weakest link. Tree-sitter has native JS bindings. Best choice if the team is JS-native
and prioritizes development speed over solver integration quality.

### 1.4 Go

#### Parser code example (hand-written)

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

#### Template/emitter code example

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

**Z3 integration.** No maintained Go bindings. Must shell out to the `z3` binary or use CGo with the
C API (fragile, breaks cross-compilation). This is a significant gap.

**LLM API integration.** No official Anthropic Go SDK. Community packages exist but are not
feature-complete. HTTP-level integration is straightforward with `net/http`.

**Distribution/packaging.** Excellent. `go install` produces a single static binary.
Cross-compilation is trivial (`GOOS=linux GOARCH=amd64 go build`). Binary size ~15 MB.

**Assessment.** Excellent distribution but poor integration with formal tools and LLM SDKs. The
verbose error handling makes compiler code significantly more boilerplate. Go's lack of sum types
makes IR representation awkward (must use interfaces with type switches). Not recommended for this
project.

### 1.5 Kotlin/JVM

#### Parser code example (ANTLR4 + Kotlin)

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

#### Template/emitter code example (Kotlin + stringtemplate)

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

**Z3 integration.** Excellent. Z3 ships official Java bindings (`com.microsoft.z3`). Kotlin
interoperates perfectly with Java libraries. The Z3 Java API mirrors the Python API closely.

**LLM API integration.** No official Anthropic Kotlin/Java SDK. However, the HTTP API is trivial to
call via `ktor-client` or `OkHttp`. Alternatively, use `langchain4j` which wraps Anthropic and
OpenAI with a unified interface.

**Distribution/packaging.** Moderate. GraalVM native-image can produce a single binary (~30-50 MB)
with fast startup. Without GraalVM, requires a JVM (~200 MB). Fat JARs via `shadowJar` are the
standard approach but require Java on the user's machine.

**Z3 + Alloy + Dafny synergy.** This is Kotlin's killer advantage. Alloy Analyzer is a Java library
(`edu.mit.csail.sdg:org.alloytools.alloy`). Kodkod (Alloy's SAT backend) is Java. Dafny has a JVM
compilation target and its API is accessible from the JVM. All three formal tools are natively
available without subprocess calls.

**Assessment.** Strongest integration with formal methods tools (Alloy, Z3, Dafny all JVM-native).
ANTLR4 is the gold standard parser generator and it targets Kotlin/Java natively. Kotlin's sealed
classes provide excellent sum type support for IR representation. The main cost is JVM distribution
weight, mitigated by GraalVM native-image. Recommended if the team is JVM-proficient.

### 1.6 Comparative matrix

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

**Original research recommendation: TypeScript.** **Actual decision: Scala 3** (the
shipped compiler lives in Scala 3.6.3 under `modules/`). The TypeScript recommendation
was overruled during M0 in favour of the Kotlin-leaning `38` row of the comparative matrix
above, generalised to the JVM. Drivers:

- Alloy 6 is a Java library (`org.alloytools:org.alloytools.alloy.core`) and Kodkod is
  Java, JVM-native invocation without subprocess fragility was decisive.
- Z3 via `tools.aqua:z3-turnkey` ships `libz3` natively for every supported OS/arch
  (no system install, no WASM step) and exposes the full Z3 Java API.
- Scala 3's `enum` ADTs with `derives CanEqual` give the IR exhaustive pattern-match
  guarantees, with `Mirror`-based JSON via circe.
- ANTLR4 has first-class Scala support via `sbt-antlr4`.
- Cats Effect 3 + decline-effect + munit-cats-effect give the `IO`-typed pipeline,
  per-check `Resource` lifecycle, and `parTraverseN` parallelism, see
  [Concurrency and Cancellation](/pipelines/concurrency).
- Translator soundness is mechanically validated by the universal `soundness` theorem in
  Isabelle/HOL (`proofs/isabelle/SpecRest/Soundness.thy`); `Code_Target_Scala` extracts the
  verified `translate`/`eval`/`smt_eval` plus the canonical IR ADT to
  `modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala`. Pivoted from
  Lean 4 via [#193](https://github.com/HardMax71/spec_to_rest/issues/193); IR canonicalized
  in [#202](https://github.com/HardMax71/spec_to_rest/issues/202).

The generated code targets are unchanged from the original recommendation
(Python/FastAPI shipped today; Go/chi [#33](https://github.com/HardMax71/spec_to_rest/issues/33)
and TS/Express [#35](https://github.com/HardMax71/spec_to_rest/issues/35) tracked as Phase 7
work), those are output targets, rather than the compiler's own implementation language.

The remainder of this document presents design examples in Python and TypeScript for
illustrative comparison; the production compiler is Scala 3, and the live source of
truth for the IR ADT is
[`proofs/isabelle/SpecRest/IR.thy`](https://github.com/HardMax71/spec_to_rest/blob/main/proofs/isabelle/SpecRest/IR.thy)
(extracted to
[`modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala)
by `Code_Target_Scala`).
