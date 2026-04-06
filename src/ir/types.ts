// ─── Source Location ──────────────────────────────────────────

export interface Span {
  readonly startLine: number;
  readonly startCol: number;
  readonly endLine: number;
  readonly endCol: number;
}

// ─── Top-Level ───────────────────────────────────────────────

export interface ServiceIR {
  readonly kind: "Service";
  readonly name: string;
  readonly imports: readonly string[];
  readonly entities: readonly EntityDecl[];
  readonly enums: readonly EnumDecl[];
  readonly typeAliases: readonly TypeAliasDecl[];
  readonly state: StateDecl | null;
  readonly operations: readonly OperationDecl[];
  readonly transitions: readonly TransitionDecl[];
  readonly invariants: readonly InvariantDecl[];
  readonly facts: readonly FactDecl[];
  readonly functions: readonly FunctionDecl[];
  readonly predicates: readonly PredicateDecl[];
  readonly conventions: ConventionsDecl | null;
  readonly span?: Span;
}

// ─── Declarations ────────────────────────────────────────────

export interface EntityDecl {
  readonly kind: "Entity";
  readonly name: string;
  readonly extends_: string | null;
  readonly fields: readonly FieldDecl[];
  readonly invariants: readonly Expr[];
  readonly span?: Span;
}

export interface FieldDecl {
  readonly kind: "Field";
  readonly name: string;
  readonly typeExpr: TypeExpr;
  readonly constraint: Expr | null;
  readonly span?: Span;
}

export interface EnumDecl {
  readonly kind: "Enum";
  readonly name: string;
  readonly values: readonly string[];
  readonly span?: Span;
}

export interface TypeAliasDecl {
  readonly kind: "TypeAlias";
  readonly name: string;
  readonly typeExpr: TypeExpr;
  readonly constraint: Expr | null;
  readonly span?: Span;
}

export interface StateDecl {
  readonly kind: "State";
  readonly fields: readonly StateFieldDecl[];
  readonly span?: Span;
}

export interface StateFieldDecl {
  readonly kind: "StateField";
  readonly name: string;
  readonly typeExpr: TypeExpr;
  readonly span?: Span;
}

export interface OperationDecl {
  readonly kind: "Operation";
  readonly name: string;
  readonly inputs: readonly ParamDecl[];
  readonly outputs: readonly ParamDecl[];
  readonly requires: readonly Expr[];
  readonly ensures: readonly Expr[];
  readonly span?: Span;
}

export interface ParamDecl {
  readonly kind: "Param";
  readonly name: string;
  readonly typeExpr: TypeExpr;
  readonly span?: Span;
}

export interface TransitionDecl {
  readonly kind: "Transition";
  readonly name: string;
  readonly entityName: string;
  readonly fieldName: string;
  readonly rules: readonly TransitionRule[];
  readonly span?: Span;
}

export interface TransitionRule {
  readonly kind: "TransitionRule";
  readonly from: string;
  readonly to: string;
  readonly via: string;
  readonly guard: Expr | null;
  readonly span?: Span;
}

export interface InvariantDecl {
  readonly kind: "Invariant";
  readonly name: string | null;
  readonly expr: Expr;
  readonly span?: Span;
}

export interface FactDecl {
  readonly kind: "Fact";
  readonly name: string | null;
  readonly expr: Expr;
  readonly span?: Span;
}

export interface FunctionDecl {
  readonly kind: "Function";
  readonly name: string;
  readonly params: readonly ParamDecl[];
  readonly returnType: TypeExpr;
  readonly body: Expr;
  readonly span?: Span;
}

export interface PredicateDecl {
  readonly kind: "Predicate";
  readonly name: string;
  readonly params: readonly ParamDecl[];
  readonly body: Expr;
  readonly span?: Span;
}

export interface ConventionsDecl {
  readonly kind: "Conventions";
  readonly rules: readonly ConventionRule[];
  readonly span?: Span;
}

export interface ConventionRule {
  readonly kind: "ConventionRule";
  readonly target: string;
  readonly property: string;
  readonly qualifier: string | null;
  readonly value: Expr;
  readonly span?: Span;
}

// ─── Type Expressions ────────────────────────────────────────

export type Multiplicity = "one" | "lone" | "some" | "set";

export type TypeExpr =
  | { readonly kind: "NamedType"; readonly name: string; readonly span?: Span }
  | { readonly kind: "SetType"; readonly elementType: TypeExpr; readonly span?: Span }
  | {
      readonly kind: "MapType";
      readonly keyType: TypeExpr;
      readonly valueType: TypeExpr;
      readonly span?: Span;
    }
  | { readonly kind: "SeqType"; readonly elementType: TypeExpr; readonly span?: Span }
  | { readonly kind: "OptionType"; readonly innerType: TypeExpr; readonly span?: Span }
  | {
      readonly kind: "RelationType";
      readonly fromType: TypeExpr;
      readonly multiplicity: Multiplicity;
      readonly toType: TypeExpr;
      readonly span?: Span;
    };

// ─── Expressions ─────────────────────────────────────────────

export type BinaryOp =
  | "and"
  | "or"
  | "implies"
  | "iff"
  | "="
  | "!="
  | "<"
  | ">"
  | "<="
  | ">="
  | "in"
  | "not_in"
  | "subset"
  | "union"
  | "intersect"
  | "minus"
  | "+"
  | "-"
  | "*"
  | "/";

export type UnaryOp = "not" | "negate" | "cardinality" | "power";

export type QuantifierKind = "all" | "some" | "no" | "exists";

export interface FieldAssign {
  readonly name: string;
  readonly value: Expr;
}

export interface MapEntry {
  readonly key: Expr;
  readonly value: Expr;
}

export interface QuantifierBinding {
  readonly variable: string;
  readonly domain: Expr;
  readonly bindingKind: "in" | "colon";
}

export type Expr =
  | { readonly kind: "BinaryOp"; readonly op: BinaryOp; readonly left: Expr; readonly right: Expr; readonly span?: Span }
  | { readonly kind: "UnaryOp"; readonly op: UnaryOp; readonly operand: Expr; readonly span?: Span }
  | { readonly kind: "Quantifier"; readonly quantifier: QuantifierKind; readonly bindings: readonly QuantifierBinding[]; readonly body: Expr; readonly span?: Span }
  | { readonly kind: "SomeWrap"; readonly expr: Expr; readonly span?: Span }
  | { readonly kind: "The"; readonly variable: string; readonly domain: Expr; readonly body: Expr; readonly span?: Span }
  | { readonly kind: "FieldAccess"; readonly base: Expr; readonly field: string; readonly span?: Span }
  | { readonly kind: "EnumAccess"; readonly base: Expr; readonly member: string; readonly span?: Span }
  | { readonly kind: "Index"; readonly base: Expr; readonly index: Expr; readonly span?: Span }
  | { readonly kind: "Call"; readonly callee: Expr; readonly args: readonly Expr[]; readonly span?: Span }
  | { readonly kind: "Prime"; readonly expr: Expr; readonly span?: Span }
  | { readonly kind: "Pre"; readonly expr: Expr; readonly span?: Span }
  | { readonly kind: "With"; readonly base: Expr; readonly updates: readonly FieldAssign[]; readonly span?: Span }
  | { readonly kind: "If"; readonly condition: Expr; readonly then: Expr; readonly else_: Expr; readonly span?: Span }
  | { readonly kind: "Let"; readonly variable: string; readonly value: Expr; readonly body: Expr; readonly span?: Span }
  | { readonly kind: "Lambda"; readonly param: string; readonly body: Expr; readonly span?: Span }
  | { readonly kind: "Constructor"; readonly typeName: string; readonly fields: readonly FieldAssign[]; readonly span?: Span }
  | { readonly kind: "SetLiteral"; readonly elements: readonly Expr[]; readonly span?: Span }
  | { readonly kind: "MapLiteral"; readonly entries: readonly MapEntry[]; readonly span?: Span }
  | { readonly kind: "SetComprehension"; readonly variable: string; readonly domain: Expr; readonly predicate: Expr; readonly span?: Span }
  | { readonly kind: "SeqLiteral"; readonly elements: readonly Expr[]; readonly span?: Span }
  | { readonly kind: "Matches"; readonly expr: Expr; readonly pattern: string; readonly span?: Span }
  | { readonly kind: "IntLit"; readonly value: number; readonly span?: Span }
  | { readonly kind: "FloatLit"; readonly value: number; readonly span?: Span }
  | { readonly kind: "StringLit"; readonly value: string; readonly span?: Span }
  | { readonly kind: "BoolLit"; readonly value: boolean; readonly span?: Span }
  | { readonly kind: "NoneLit"; readonly span?: Span }
  | { readonly kind: "Identifier"; readonly name: string; readonly span?: Span };

// ─── Union of all declarations ───────────────────────────────

export type Decl =
  | EntityDecl
  | EnumDecl
  | TypeAliasDecl
  | StateDecl
  | OperationDecl
  | TransitionDecl
  | InvariantDecl
  | FactDecl
  | FunctionDecl
  | PredicateDecl
  | ConventionsDecl;
