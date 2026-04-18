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
  | { readonly kind: "Var"; readonly name: string; readonly sort: Z3Sort }
  | { readonly kind: "App"; readonly func: string; readonly args: readonly Z3Expr[] }
  | { readonly kind: "IntLit"; readonly value: number }
  | { readonly kind: "BoolLit"; readonly value: boolean }
  | { readonly kind: "And"; readonly args: readonly Z3Expr[] }
  | { readonly kind: "Or"; readonly args: readonly Z3Expr[] }
  | { readonly kind: "Not"; readonly arg: Z3Expr }
  | { readonly kind: "Implies"; readonly lhs: Z3Expr; readonly rhs: Z3Expr }
  | { readonly kind: "Cmp"; readonly op: CmpOp; readonly lhs: Z3Expr; readonly rhs: Z3Expr }
  | { readonly kind: "Arith"; readonly op: ArithOp; readonly args: readonly Z3Expr[] }
  | {
      readonly kind: "Quantifier";
      readonly q: "ForAll" | "Exists";
      readonly bindings: readonly Z3Binding[];
      readonly body: Z3Expr;
    };

export interface Z3Script {
  readonly sorts: readonly Z3Sort[];
  readonly funcs: readonly Z3FunctionDecl[];
  readonly assertions: readonly Z3Expr[];
}
