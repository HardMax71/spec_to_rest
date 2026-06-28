---
title: "Host language"
description: "The compiler-host evaluation and the Scala 3 choice"
---

The compiler is written in Scala 3. That was not the original recommendation, and the evaluation it
came out of is worth keeping, because the criteria that decided it, how cleanly a language talks to
Z3, Alloy, and Dafny, whether it has real sum types for the IR, its parser tooling and its
distribution story, are particular to a verification-heavy compiler and not obvious going in. Five
languages were weighed before the answer landed on a sixth.

## Python

Python is the prototyping sweet spot. The `z3-solver` package ships prebuilt binaries, its Z3 API is
the best-documented binding there is, and every LLM provider ships a Python SDK first. The weak spot
is distribution: Python packaging is fragile, so a single binary means PyInstaller bulk or a Docker
fallback.

```python
@dataclass
class OperationDecl:
    name: str
    inputs: List["Parameter"]
    outputs: List["Parameter"]
    requires: List["Expr"]
    ensures: List["Expr"]

class Parser:
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
```

## Rust

Rust inverts that. `cargo install` produces a single static binary and cross-compiles cleanly, and it
is the fastest of the five, but the `z3` crate lags the Python API, the Anthropic crate is
community-maintained, and compiler code takes two to three times longer to write.

```rust
#[derive(Parser)]
#[grammar = "dsl.pest"]
struct DslParser;

fn parse_operation(pair: pest::iterators::Pair<Rule>) -> OperationDecl {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str().to_string();
    let (mut inputs, mut outputs, mut requires, mut ensures) = (vec![], vec![], vec![], vec![]);
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

## TypeScript

TypeScript is the middle ground: static types with fast iteration, an official Anthropic SDK, and
decent single-binary options through `pkg` or Bun. Its weak link is Z3, available only as a WASM
build (about three times slower, a 35 MB payload) or by shelling out to the binary. This was the
original recommendation.

```typescript
class Parser {
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
}
```

## Go

Go has the best distribution of all, trivial cross-compilation and a small static binary, and the
worst formal-tools story: no maintained Z3 bindings, no official Anthropic SDK, and no sum types,
which forces the IR through interfaces and type switches and makes the parser a wall of error checks.

```go
func (p *Parser) parseOperation() (*OperationDecl, error) {
    if err := p.expect(TokenKeyword, "operation"); err != nil {
        return nil, err
    }
    name, err := p.expectIdent()
    if err != nil {
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
    // ...requires, ensures, closing brace, each with the same err check
    return &OperationDecl{Name: name, Inputs: inputs, Outputs: outputs}, nil
}
```

## Kotlin and the JVM

Kotlin is where the criteria converge. Alloy is a Java library, Kodkod is Java, Z3 ships official
Java bindings, and Dafny targets the JVM, so all three formal tools run in-process rather than as
subprocesses. ANTLR4 targets the JVM natively, and sealed classes model the IR cleanly. The cost is
JVM weight at distribution, softened by GraalVM native-image.

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
}
```

## The scores, and the decision

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
| Total          | 32             | 29             | 26             | 22             | 38             |

The matrix tops out on the JVM, and that is where the
project landed, though not on Kotlin. The shipped compiler is Scala 3. The JVM was decisive for the
reason the Kotlin row scored highest: Alloy 6 is a Java library and Kodkod is Java, so the model
checker runs in-process, and Z3 arrives through `tools.aqua:z3-turnkey`, which bundles `libz3` for
every OS and arch with the full Java API and no system install. Scala 3 over Kotlin came down to the
IR and the effect layer. Its `enum` ADTs with `derives CanEqual` give exhaustive pattern matching and
`Mirror`-based circe JSON, and Cats Effect 3 types the whole pipeline as `IO` with per-check
[`Resource` lifecycles and `parTraverseN` parallelism](/pipelines/concurrency). ANTLR4 plugs in
through `sbt-antlr4`.

```scala
enum Expr derives CanEqual:
  case BinaryOp(left: Expr, op: BinOp, right: Expr)
  case Literal(value: String)
  case Cardinality(of: Expr)

def printExpr(e: Expr): String = e match
  case Expr.BinaryOp(l, op, r) => s"(${printExpr(l)} ${op.symbol} ${printExpr(r)})"
  case Expr.Literal(v)         => v
  case Expr.Cardinality(of)    => s"#${printExpr(of)}"
```

The IR is not hand-written, though: the soundness theorem in Isabelle/HOL extracts the verified
`translate`, `eval`, and `smt_eval` together with the canonical IR ADT into `SpecRestGenerated.scala`
through `Code_Target_Scala`, so the data structure the compiler is built around is a proof artifact,
which the [IR design](/research/implementation_architecture/ir-design) page picks up. The TypeScript
recommendation was overruled in M0. The generated output targets (Python and FastAPI today, Go and
TypeScript tracked) are a separate choice from the compiler's own language.
