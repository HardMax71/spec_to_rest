export type {
  Span,
  ServiceIR,
  EntityDecl,
  FieldDecl,
  EnumDecl,
  TypeAliasDecl,
  StateDecl,
  StateFieldDecl,
  OperationDecl,
  ParamDecl,
  TransitionDecl,
  TransitionRule,
  InvariantDecl,
  FactDecl,
  FunctionDecl,
  PredicateDecl,
  ConventionsDecl,
  ConventionRule,
  TypeExpr,
  Multiplicity,
  Expr,
  BinaryOp,
  UnaryOp,
  QuantifierKind,
  QuantifierBinding,
  FieldAssign,
  MapEntry,
  Decl,
} from "#ir/types.js";

export { serializeIR, deserializeIR } from "#ir/serialize.js";
export { validateServiceIR, validateExpr, validateTypeExpr, validateSpan } from "#ir/validate.js";
export { buildIR, BuildError } from "#ir/builder.js";
