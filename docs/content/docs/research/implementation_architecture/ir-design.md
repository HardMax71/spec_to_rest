---
title: "IR design"
description: "The intermediate representation the compiler is built around"
---

## 3. Intermediate representation (ir) design

The IR is the central nervous system of the compiler. Every stage reads from or writes to the IR. It
must be:

- complete enough to represent everything in the spec
- annotatable, so the convention engine and verifier can add information
- serializable for debugging, caching, and incremental compilation
- traversable by visitors or pattern matching at each compiler stage

### 3.1 Complete IR type definitions

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

#### TypeScript equivalent using discriminated unions (production implementation)

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

### 3.2 Complete IR for the URL shortener spec

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

### 3.3 Design decisions

#### Mutable vs immutable IR nodes

The IR uses mutable nodes for the initial implementation. The convention engine mutates
`OperationDecl` in-place by setting `http_method`, `http_path`, etc. This is pragmatic for the MVP
but has known downsides:

- Difficult to implement undo/rollback
- Hard to cache intermediate states
- Debugging "who set this field?" requires tracing

For the production TypeScript compiler, use `readonly` interfaces with spread-based copies. The
convention engine produces a new IR with annotations applied, leaving the original intact. This
enables diffing (before/after convention application) and caching.

#### Visitor pattern vs pattern matching for IR traversal

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

#### Convention engine annotation strategy

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

#### IR serialization

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
