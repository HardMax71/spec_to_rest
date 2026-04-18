import type { Span } from "#ir/types.js";

export type Z3Sort =
  | { readonly kind: "Int" }
  | { readonly kind: "Bool" }
  | { readonly kind: "Uninterp"; readonly name: string };

export const Z3_INT: Z3Sort = { kind: "Int" };
export const Z3_BOOL: Z3Sort = { kind: "Bool" };
export function uninterp(name: string): Z3Sort {
  return { kind: "Uninterp", name };
}

export function sortKey(s: Z3Sort): string {
  return s.kind === "Uninterp" ? `U:${s.name}` : s.kind;
}

export function sortEq(a: Z3Sort, b: Z3Sort): boolean {
  return sortKey(a) === sortKey(b);
}

export interface Z3FunctionDecl {
  readonly kind: "FuncDecl";
  readonly name: string;
  readonly argSorts: readonly Z3Sort[];
  readonly resultSort: Z3Sort;
}

export interface Z3Binding {
  readonly name: string;
  readonly sort: Z3Sort;
}

export type CmpOp = "=" | "!=" | "<" | "<=" | ">" | ">=";
export type ArithOp = "+" | "-" | "*" | "/";

export type Z3Expr =
  | { readonly kind: "Var"; readonly name: string; readonly sort: Z3Sort; readonly span?: Span }
  | { readonly kind: "App"; readonly func: string; readonly args: readonly Z3Expr[]; readonly span?: Span }
  | { readonly kind: "IntLit"; readonly value: number; readonly span?: Span }
  | { readonly kind: "BoolLit"; readonly value: boolean; readonly span?: Span }
  | { readonly kind: "And"; readonly args: readonly Z3Expr[]; readonly span?: Span }
  | { readonly kind: "Or"; readonly args: readonly Z3Expr[]; readonly span?: Span }
  | { readonly kind: "Not"; readonly arg: Z3Expr; readonly span?: Span }
  | { readonly kind: "Implies"; readonly lhs: Z3Expr; readonly rhs: Z3Expr; readonly span?: Span }
  | { readonly kind: "Cmp"; readonly op: CmpOp; readonly lhs: Z3Expr; readonly rhs: Z3Expr; readonly span?: Span }
  | { readonly kind: "Arith"; readonly op: ArithOp; readonly args: readonly Z3Expr[]; readonly span?: Span }
  | {
      readonly kind: "Quantifier";
      readonly q: "ForAll" | "Exists";
      readonly bindings: readonly Z3Binding[];
      readonly body: Z3Expr;
      readonly span?: Span;
    };

export function withSpan<E extends Z3Expr>(expr: E, span: Span | undefined): E {
  if (span === undefined) return expr;
  return Object.assign({}, expr, { span });
}

export function getSpan(expr: Z3Expr): Span | undefined {
  return expr.span;
}

export interface Z3Script {
  readonly sorts: readonly Z3Sort[];
  readonly funcs: readonly Z3FunctionDecl[];
  readonly assertions: readonly Z3Expr[];
  readonly artifact: TranslatorArtifact;
}

export interface ArtifactEntityField {
  readonly name: string;
  readonly sort: Z3Sort;
  readonly funcName: string;
}

export interface ArtifactEntity {
  readonly name: string;
  readonly sort: Z3Sort;
  readonly fields: readonly ArtifactEntityField[];
}

export interface ArtifactEnumMember {
  readonly name: string;
  readonly funcName: string;
}

export interface ArtifactEnum {
  readonly name: string;
  readonly sort: Z3Sort;
  readonly members: readonly ArtifactEnumMember[];
}

export interface ArtifactStateRelation {
  readonly kind: "Relation";
  readonly name: string;
  readonly keySort: Z3Sort;
  readonly valueSort: Z3Sort;
  readonly domFunc: string;
  readonly mapFunc: string;
  readonly domFuncPost: string;
  readonly mapFuncPost: string;
}

export interface ArtifactStateConstant {
  readonly kind: "Const";
  readonly name: string;
  readonly sort: Z3Sort;
  readonly funcName: string;
  readonly funcNamePost: string;
}

export type ArtifactStateEntry = ArtifactStateRelation | ArtifactStateConstant;

export interface ArtifactBinding {
  readonly name: string;
  readonly funcName: string;
  readonly sort: Z3Sort;
}

export interface TranslatorArtifact {
  readonly entities: readonly ArtifactEntity[];
  readonly enums: readonly ArtifactEnum[];
  readonly state: readonly ArtifactStateEntry[];
  readonly inputs: readonly ArtifactBinding[];
  readonly outputs: readonly ArtifactBinding[];
  readonly hasPostState: boolean;
}
