/**
 * CST-to-IR builder — ANTLR4 visitor that transforms a parse tree into typed IR nodes.
 *
 * Only the `expr` rule uses visitor dispatch (labeled alternatives). All other
 * CST-to-IR conversions are typed private methods called directly — no casts.
 */

import type { ParserRuleContext } from "antlr4ng";
import { SpecVisitor } from "../parser/generated/SpecVisitor.js";
import type {
  // Top-level / declarations
  SpecFileContext,
  ServiceDeclContext,
  EntityDeclContext,
  FieldDeclContext,
  EnumDeclContext,
  TypeAliasContext,
  TypeExprContext,
  BaseTypeContext,
  StateDeclContext,
  StateFieldContext,
  OperationDeclContext,
  ParamContext,
  TransitionDeclContext,
  TransitionRuleContext,
  InvariantDeclContext,
  FactDeclContext,
  FunctionDeclContext,
  PredicateDeclContext,
  ConventionBlockContext,
  ConventionRuleContext,
  // Expression base + labeled alternatives
  ExprContext,
  MulExprContext,
  DivExprContext,
  AddExprContext,
  SubExprContext,
  UnionExprContext,
  IntersectExprContext,
  MinusExprContext,
  EqExprContext,
  NeqExprContext,
  LtExprContext,
  GtExprContext,
  LteExprContext,
  GteExprContext,
  InExprContext,
  NotInExprContext,
  SubsetExprContext,
  ImpliesExprContext,
  IffExprContext,
  AndExprContext,
  OrExprContext,
  CardinalityExprContext,
  NegExprContext,
  PowerExprContext,
  NotExprContext,
  PrimeExprContext,
  FieldAccessExprContext,
  EnumAccessExprContext,
  IndexExprContext,
  CallExprContext,
  WithExprContext,
  MatchesExprContext,
  ParenExprContext,
  PreExprContext,
  QuantExprContext,
  SomeWrapEContext,
  TheEContext,
  IfEContext,
  LetEContext,
  LambdaEContext,
  ConstructorEContext,
  SetOrMapEContext,
  SeqEContext,
  IntLitExprContext,
  FloatLitExprContext,
  StringLitExprContext,
  TrueLitExprContext,
  FalseLitExprContext,
  NoneLitExprContext,
  UpperIdentExprContext,
  LowerIdentExprContext,
  // Expression sub-rules
  FieldAssignContext,
  QuantifierExprContext,
  SomeWrapExprContext,
  TheExprContext,
  IfExprContext,
  LetExprContext,
  LambdaExprContext,
  ConstructorExprContext,
  SetOrMapLiteralContext,
  SeqLiteralContext,
  LowerIdentContext,
} from "../parser/generated/SpecParser.js";
import type {
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
  TransitionRule as IRTransitionRule,
  InvariantDecl,
  FactDecl,
  FunctionDecl,
  PredicateDecl,
  ConventionsDecl,
  ConventionRule as IRConventionRule,
  TypeExpr,
  Multiplicity,
  Expr,
  BinaryOp,
  QuantifierKind,
  QuantifierBinding,
  FieldAssign,
  MapEntry,
} from "./types.js";

// ─── Error type ─────────────────────────────────────────────

export class BuildError extends Error {
  readonly line: number;
  readonly column: number;

  constructor(message: string, ctx: ParserRuleContext) {
    const line = ctx.start?.line ?? 0;
    const col = ctx.start?.column ?? 0;
    super(`Build error at ${line}:${col}: ${message}`);
    this.line = line;
    this.column = col;
  }
}

// ─── Helpers ────────────────────────────────────────────────

function spanFrom(ctx: ParserRuleContext): Span {
  const start = ctx.start!;
  const stop = ctx.stop ?? start;
  return {
    startLine: start.line,
    startCol: start.column,
    endLine: stop.line,
    endCol: stop.column + (stop.text?.length ?? 1),
  };
}

function unquote(raw: string): string {
  return raw.slice(1, -1).replace(/\\(.)/g, (_, c: string) => {
    switch (c) {
      case "n": return "\n";
      case "t": return "\t";
      case "r": return "\r";
      default: return c;
    }
  });
}

function unslashRegex(raw: string): string {
  return raw.slice(1, -1);
}

function identText(ctx: LowerIdentContext): string {
  return ctx.getText();
}

const MULTIPLICITY_MAP: Record<string, Multiplicity> = {
  one: "one", lone: "lone", some: "some", set: "set",
};

// ─── Builder ────────────────────────────────────────────────
//
// Extends SpecVisitor<Expr> so that visitor dispatch (used only for
// the `expr` rule) is properly typed. All declaration / type-expression
// building uses typed private methods — no casts needed.

class IRBuilder extends SpecVisitor<Expr> {

  // If visitor dispatch reaches a node with no visitor method, visitChildren()
  // calls defaultResult(). Throwing here surfaces missing visitor methods as
  // clear errors instead of silent nulls.
  protected override defaultResult(): Expr {
    throw new Error("IRBuilder: unhandled expression node");
  }

  // The single bridge between visitor dispatch and typed code.
  // Non-null assertion is safe: every expr alternative has a visitor,
  // and defaultResult() throws for any that don't.
  private expr(ctx: ExprContext): Expr {
    return this.visit(ctx)!;
  }

  // ── Binary / unary helpers ──
  // Structural types let every binary/unary context pass through without casts.

  private binOp(ctx: ParserRuleContext & { expr(): ExprContext[] }, op: BinaryOp): Expr {
    const exprs = ctx.expr();
    return { kind: "BinaryOp", op, left: this.expr(exprs[0]), right: this.expr(exprs[1]), span: spanFrom(ctx) };
  }

  private unaryOp(ctx: ParserRuleContext & { expr(): ExprContext }, op: "not" | "negate" | "cardinality" | "power"): Expr {
    return { kind: "UnaryOp", op, operand: this.expr(ctx.expr()), span: spanFrom(ctx) };
  }

  // ═══════════════════════════════════════════════════════════
  // DECLARATIONS — typed private methods, no visitor dispatch
  // ═══════════════════════════════════════════════════════════

  buildService(ctx: ServiceDeclContext): ServiceIR {
    const name = ctx.UPPER_IDENT().getText();
    const entities: EntityDecl[] = [];
    const enums: EnumDecl[] = [];
    const typeAliases: TypeAliasDecl[] = [];
    let state: StateDecl | null = null;
    const operations: OperationDecl[] = [];
    const transitions: TransitionDecl[] = [];
    const invariants: InvariantDecl[] = [];
    const facts: FactDecl[] = [];
    const functions: FunctionDecl[] = [];
    const predicates: PredicateDecl[] = [];
    let conventions: ConventionsDecl | null = null;

    for (const member of ctx.serviceMember()) {
      if (member.entityDecl()) {
        entities.push(this.buildEntity(member.entityDecl()!));
      } else if (member.enumDecl()) {
        enums.push(this.buildEnum(member.enumDecl()!));
      } else if (member.typeAlias()) {
        typeAliases.push(this.buildTypeAlias(member.typeAlias()!));
      } else if (member.stateDecl()) {
        if (state) throw new BuildError("duplicate state block", member.stateDecl()!);
        state = this.buildState(member.stateDecl()!);
      } else if (member.operationDecl()) {
        operations.push(this.buildOperation(member.operationDecl()!));
      } else if (member.transitionDecl()) {
        transitions.push(this.buildTransition(member.transitionDecl()!));
      } else if (member.invariantDecl()) {
        invariants.push(this.buildInvariant(member.invariantDecl()!));
      } else if (member.factDecl()) {
        facts.push(this.buildFact(member.factDecl()!));
      } else if (member.functionDecl()) {
        functions.push(this.buildFunction(member.functionDecl()!));
      } else if (member.predicateDecl()) {
        predicates.push(this.buildPredicate(member.predicateDecl()!));
      } else if (member.conventionBlock()) {
        if (conventions) throw new BuildError("duplicate conventions block", member.conventionBlock()!);
        conventions = this.buildConventions(member.conventionBlock()!);
      }
    }

    return {
      kind: "Service", name, imports: [], entities, enums, typeAliases,
      state, operations, transitions, invariants, facts, functions,
      predicates, conventions, span: spanFrom(ctx),
    };
  }

  private buildEntity(ctx: EntityDeclContext): EntityDecl {
    const upperIdents = ctx.UPPER_IDENT();
    const name = upperIdents[0].getText();
    const extends_ = ctx.EXTENDS() ? upperIdents[1].getText() : null;
    const fields: FieldDecl[] = [];
    const invariants: Expr[] = [];

    for (const member of ctx.entityMember()) {
      if (member.fieldDecl()) {
        fields.push(this.buildField(member.fieldDecl()!));
      } else if (member.entityInvariant()) {
        invariants.push(this.expr(member.entityInvariant()!.expr()));
      }
    }

    return { kind: "Entity", name, extends_, fields, invariants, span: spanFrom(ctx) };
  }

  private buildField(ctx: FieldDeclContext): FieldDecl {
    const name = identText(ctx.lowerIdent());
    const typeExpr = this.buildTypeExpr(ctx.typeExpr());
    const constraint = ctx.WHERE() ? this.expr(ctx.expr()!) : null;
    return { kind: "Field", name, typeExpr, constraint, span: spanFrom(ctx) };
  }

  private buildEnum(ctx: EnumDeclContext): EnumDecl {
    const name = ctx.UPPER_IDENT().getText();
    const values = ctx.enumValue().map(ev => ev.UPPER_IDENT().getText());
    return { kind: "Enum", name, values, span: spanFrom(ctx) };
  }

  private buildTypeAlias(ctx: TypeAliasContext): TypeAliasDecl {
    const name = ctx.UPPER_IDENT().getText();
    const typeExpr = this.buildTypeExpr(ctx.typeExpr());
    const constraint = ctx.WHERE() ? this.expr(ctx.expr()!) : null;
    return { kind: "TypeAlias", name, typeExpr, constraint, span: spanFrom(ctx) };
  }

  private buildState(ctx: StateDeclContext): StateDecl {
    const fields = ctx.stateField().map(sf => this.buildStateField(sf));
    return { kind: "State", fields, span: spanFrom(ctx) };
  }

  private buildStateField(ctx: StateFieldContext): StateFieldDecl {
    return {
      kind: "StateField",
      name: identText(ctx.lowerIdent()),
      typeExpr: this.buildTypeExpr(ctx.typeExpr()),
      span: spanFrom(ctx),
    };
  }

  private buildOperation(ctx: OperationDeclContext): OperationDecl {
    const name = ctx.UPPER_IDENT().getText();
    const inputs: ParamDecl[] = [];
    const outputs: ParamDecl[] = [];
    const requires: Expr[] = [];
    const ensures: Expr[] = [];

    for (const clause of ctx.operationClause()) {
      if (clause.inputClause()) {
        for (const p of clause.inputClause()!.paramList().param()) {
          inputs.push(this.buildParam(p));
        }
      } else if (clause.outputClause()) {
        for (const p of clause.outputClause()!.paramList().param()) {
          outputs.push(this.buildParam(p));
        }
      } else if (clause.requiresClause()) {
        for (const e of clause.requiresClause()!.expr()) {
          requires.push(this.expr(e));
        }
      } else if (clause.ensuresClause()) {
        for (const e of clause.ensuresClause()!.expr()) {
          ensures.push(this.expr(e));
        }
      }
    }

    return { kind: "Operation", name, inputs, outputs, requires, ensures, span: spanFrom(ctx) };
  }

  private buildParam(ctx: ParamContext): ParamDecl {
    return {
      kind: "Param",
      name: identText(ctx.lowerIdent()),
      typeExpr: this.buildTypeExpr(ctx.typeExpr()),
      span: spanFrom(ctx),
    };
  }

  private buildTransition(ctx: TransitionDeclContext): TransitionDecl {
    const upperIdents = ctx.UPPER_IDENT();
    const name = upperIdents[0].getText();
    const entityName = upperIdents[1].getText();
    const fieldName = identText(ctx.lowerIdent());
    const rules = ctx.transitionRule().map(r => this.buildTransitionRule(r));
    return { kind: "Transition", name, entityName, fieldName, rules, span: spanFrom(ctx) };
  }

  private buildTransitionRule(ctx: TransitionRuleContext): IRTransitionRule {
    const upperIdents = ctx.UPPER_IDENT();
    const from = upperIdents[0].getText();
    const to = upperIdents[1].getText();
    const via = upperIdents[2].getText();
    const guard = ctx.WHEN() ? this.expr(ctx.expr()!) : null;
    return { kind: "TransitionRule", from, to, via, guard, span: spanFrom(ctx) };
  }

  private buildInvariant(ctx: InvariantDeclContext): InvariantDecl {
    const name = ctx.lowerIdent() ? identText(ctx.lowerIdent()!) : null;
    return { kind: "Invariant", name, expr: this.expr(ctx.expr()), span: spanFrom(ctx) };
  }

  private buildFact(ctx: FactDeclContext): FactDecl {
    const name = ctx.lowerIdent() ? identText(ctx.lowerIdent()!) : null;
    return { kind: "Fact", name, expr: this.expr(ctx.expr()), span: spanFrom(ctx) };
  }

  private buildFunction(ctx: FunctionDeclContext): FunctionDecl {
    const name = identText(ctx.lowerIdent());
    const params = ctx.paramList()
      ? ctx.paramList()!.param().map(p => this.buildParam(p))
      : [];
    const returnType = this.buildTypeExpr(ctx.typeExpr());
    const body = this.expr(ctx.expr());
    return { kind: "Function", name, params, returnType, body, span: spanFrom(ctx) };
  }

  private buildPredicate(ctx: PredicateDeclContext): PredicateDecl {
    const name = identText(ctx.lowerIdent());
    const params = ctx.paramList()
      ? ctx.paramList()!.param().map(p => this.buildParam(p))
      : [];
    const body = this.expr(ctx.expr());
    return { kind: "Predicate", name, params, body, span: spanFrom(ctx) };
  }

  private buildConventions(ctx: ConventionBlockContext): ConventionsDecl {
    const rules = ctx.conventionRule().map(r => this.buildConventionRule(r));
    return { kind: "Conventions", rules, span: spanFrom(ctx) };
  }

  private buildConventionRule(ctx: ConventionRuleContext): IRConventionRule {
    const target = ctx.UPPER_IDENT().getText();
    const property = identText(ctx.lowerIdent());
    const strLit = ctx.STRING_LIT();
    const qualifier = strLit ? unquote(strLit.getText()) : null;
    const value = this.expr(ctx.expr());
    return { kind: "ConventionRule", target, property, qualifier, value, span: spanFrom(ctx) };
  }

  // ═══════════════════════════════════════════════════════════
  // TYPE EXPRESSIONS — typed private methods
  // ═══════════════════════════════════════════════════════════

  private buildTypeExpr(ctx: TypeExprContext): TypeExpr {
    const baseTypes = ctx.baseType();
    if (ctx.ARROW()) {
      const fromType = this.buildBaseType(baseTypes[0]);
      const multCtx = ctx.multiplicity();
      const multiplicity: Multiplicity = multCtx
        ? MULTIPLICITY_MAP[multCtx.getText()]
        : "one";
      const toType = this.buildBaseType(baseTypes[1]);
      return { kind: "RelationType", fromType, multiplicity, toType, span: spanFrom(ctx) };
    }
    return this.buildBaseType(baseTypes[0]);
  }

  private buildBaseType(ctx: BaseTypeContext): TypeExpr {
    if (ctx.primitiveType()) {
      return { kind: "NamedType", name: ctx.primitiveType()!.getText(), span: spanFrom(ctx) };
    }
    if (ctx.SET()) {
      return { kind: "SetType", elementType: this.buildTypeExpr(ctx.typeExpr(0)!), span: spanFrom(ctx) };
    }
    if (ctx.MAP()) {
      return {
        kind: "MapType",
        keyType: this.buildTypeExpr(ctx.typeExpr(0)!),
        valueType: this.buildTypeExpr(ctx.typeExpr(1)!),
        span: spanFrom(ctx),
      };
    }
    if (ctx.SEQ()) {
      return { kind: "SeqType", elementType: this.buildTypeExpr(ctx.typeExpr(0)!), span: spanFrom(ctx) };
    }
    if (ctx.OPTION()) {
      return { kind: "OptionType", innerType: this.buildTypeExpr(ctx.typeExpr(0)!), span: spanFrom(ctx) };
    }
    return { kind: "NamedType", name: ctx.UPPER_IDENT()!.getText(), span: spanFrom(ctx) };
  }

  // ═══════════════════════════════════════════════════════════
  // EXPRESSION VISITORS — the only part using visitor dispatch.
  // Parameter types are inferred from generated SpecVisitor<Expr>
  // property signatures, so no explicit types or casts needed.
  // ═══════════════════════════════════════════════════════════

  // ── Binary operators (20) ──

  visitMulExpr = (ctx: MulExprContext) => this.binOp(ctx, "*");
  visitDivExpr = (ctx: DivExprContext) => this.binOp(ctx, "/");
  visitAddExpr = (ctx: AddExprContext) => this.binOp(ctx, "+");
  visitSubExpr = (ctx: SubExprContext) => this.binOp(ctx, "-");
  visitUnionExpr = (ctx: UnionExprContext) => this.binOp(ctx, "union");
  visitIntersectExpr = (ctx: IntersectExprContext) => this.binOp(ctx, "intersect");
  visitMinusExpr = (ctx: MinusExprContext) => this.binOp(ctx, "minus");
  visitEqExpr = (ctx: EqExprContext) => this.binOp(ctx, "=");
  visitNeqExpr = (ctx: NeqExprContext) => this.binOp(ctx, "!=");
  visitLtExpr = (ctx: LtExprContext) => this.binOp(ctx, "<");
  visitGtExpr = (ctx: GtExprContext) => this.binOp(ctx, ">");
  visitLteExpr = (ctx: LteExprContext) => this.binOp(ctx, "<=");
  visitGteExpr = (ctx: GteExprContext) => this.binOp(ctx, ">=");
  visitInExpr = (ctx: InExprContext) => this.binOp(ctx, "in");
  visitNotInExpr = (ctx: NotInExprContext) => this.binOp(ctx, "not_in");
  visitSubsetExpr = (ctx: SubsetExprContext) => this.binOp(ctx, "subset");
  visitImpliesExpr = (ctx: ImpliesExprContext) => this.binOp(ctx, "implies");
  visitIffExpr = (ctx: IffExprContext) => this.binOp(ctx, "iff");
  visitAndExpr = (ctx: AndExprContext) => this.binOp(ctx, "and");
  visitOrExpr = (ctx: OrExprContext) => this.binOp(ctx, "or");

  // ── Unary operators (4) ──

  visitCardinalityExpr = (ctx: CardinalityExprContext) => this.unaryOp(ctx, "cardinality");
  visitNegExpr = (ctx: NegExprContext) => this.unaryOp(ctx, "negate");
  visitPowerExpr = (ctx: PowerExprContext) => this.unaryOp(ctx, "power");
  visitNotExpr = (ctx: NotExprContext) => this.unaryOp(ctx, "not");

  // ── Postfix operators ──

  visitPrimeExpr = (ctx: PrimeExprContext): Expr => {
    return { kind: "Prime", expr: this.expr(ctx.expr()), span: spanFrom(ctx) };
  };

  visitFieldAccessExpr = (ctx: FieldAccessExprContext): Expr => {
    return {
      kind: "FieldAccess",
      base: this.expr(ctx.expr()),
      field: identText(ctx.lowerIdent()),
      span: spanFrom(ctx),
    };
  };

  visitEnumAccessExpr = (ctx: EnumAccessExprContext): Expr => {
    return {
      kind: "EnumAccess",
      base: this.expr(ctx.expr()),
      member: ctx.UPPER_IDENT().getText(),
      span: spanFrom(ctx),
    };
  };

  visitIndexExpr = (ctx: IndexExprContext): Expr => {
    return {
      kind: "Index",
      base: this.expr(ctx.expr(0)!),
      index: this.expr(ctx.expr(1)!),
      span: spanFrom(ctx),
    };
  };

  visitCallExpr = (ctx: CallExprContext): Expr => {
    const args = ctx.argList()?.expr().map(e => this.expr(e)) ?? [];
    return { kind: "Call", callee: this.expr(ctx.expr()), args, span: spanFrom(ctx) };
  };

  visitWithExpr = (ctx: WithExprContext): Expr => {
    const updates = ctx.fieldAssign().map(fa => this.buildFieldAssign(fa));
    return { kind: "With", base: this.expr(ctx.expr()), updates, span: spanFrom(ctx) };
  };

  visitMatchesExpr = (ctx: MatchesExprContext): Expr => {
    return {
      kind: "Matches",
      expr: this.expr(ctx.expr()),
      pattern: unslashRegex(ctx.REGEX_LIT().getText()),
      span: spanFrom(ctx),
    };
  };

  // ── Transparent delegators → typed build methods ──

  visitParenExpr = (ctx: ParenExprContext): Expr => this.expr(ctx.expr());
  visitQuantExpr = (ctx: QuantExprContext): Expr => this.buildQuantifier(ctx.quantifierExpr());
  visitSomeWrapE = (ctx: SomeWrapEContext): Expr => this.buildSomeWrap(ctx.someWrapExpr());
  visitTheE = (ctx: TheEContext): Expr => this.buildThe(ctx.theExpr());
  visitIfE = (ctx: IfEContext): Expr => this.buildIf(ctx.ifExpr());
  visitLetE = (ctx: LetEContext): Expr => this.buildLet(ctx.letExpr());
  visitLambdaE = (ctx: LambdaEContext): Expr => this.buildLambda(ctx.lambdaExpr());
  visitConstructorE = (ctx: ConstructorEContext): Expr => this.buildConstructor(ctx.constructorExpr());
  visitSetOrMapE = (ctx: SetOrMapEContext): Expr => this.buildSetOrMap(ctx.setOrMapLiteral());
  visitSeqE = (ctx: SeqEContext): Expr => this.buildSeq(ctx.seqLiteral());

  visitPreExpr = (ctx: PreExprContext): Expr => {
    return { kind: "Pre", expr: this.expr(ctx.expr()), span: spanFrom(ctx) };
  };

  // ── Literals ──

  visitIntLitExpr = (ctx: IntLitExprContext): Expr => {
    return { kind: "IntLit", value: parseInt(ctx.INT_LIT().getText(), 10), span: spanFrom(ctx) };
  };

  visitFloatLitExpr = (ctx: FloatLitExprContext): Expr => {
    return { kind: "FloatLit", value: parseFloat(ctx.FLOAT_LIT().getText()), span: spanFrom(ctx) };
  };

  visitStringLitExpr = (ctx: StringLitExprContext): Expr => {
    return { kind: "StringLit", value: unquote(ctx.STRING_LIT().getText()), span: spanFrom(ctx) };
  };

  visitTrueLitExpr = (ctx: TrueLitExprContext): Expr => ({ kind: "BoolLit", value: true, span: spanFrom(ctx) });
  visitFalseLitExpr = (ctx: FalseLitExprContext): Expr => ({ kind: "BoolLit", value: false, span: spanFrom(ctx) });
  visitNoneLitExpr = (ctx: NoneLitExprContext): Expr => ({ kind: "NoneLit", span: spanFrom(ctx) });

  visitUpperIdentExpr = (ctx: UpperIdentExprContext): Expr => {
    return { kind: "Identifier", name: ctx.UPPER_IDENT().getText(), span: spanFrom(ctx) };
  };

  visitLowerIdentExpr = (ctx: LowerIdentExprContext): Expr => {
    return { kind: "Identifier", name: identText(ctx.lowerIdent()), span: spanFrom(ctx) };
  };

  // ═══════════════════════════════════════════════════════════
  // EXPRESSION SUB-RULES — typed private methods
  // ═══════════════════════════════════════════════════════════

  private buildQuantifier(ctx: QuantifierExprContext): Expr {
    const qCtx = ctx.quantifier();
    let quantifier: QuantifierKind;
    if (qCtx.ALL()) quantifier = "all";
    else if (qCtx.SOME()) quantifier = "some";
    else if (qCtx.NO()) quantifier = "no";
    else quantifier = "exists";

    const bindings: QuantifierBinding[] = ctx.quantBinding().map(b => ({
      variable: identText(b.lowerIdent()),
      domain: this.expr(b.expr()),
      bindingKind: b.IN() ? "in" as const : "colon" as const,
      span: spanFrom(b),
    }));

    return { kind: "Quantifier", quantifier, bindings, body: this.expr(ctx.expr()), span: spanFrom(ctx) };
  }

  private buildSomeWrap(ctx: SomeWrapExprContext): Expr {
    return { kind: "SomeWrap", expr: this.expr(ctx.expr()), span: spanFrom(ctx) };
  }

  private buildThe(ctx: TheExprContext): Expr {
    const exprs = ctx.expr();
    return {
      kind: "The",
      variable: identText(ctx.lowerIdent()),
      domain: this.expr(exprs[0]),
      body: this.expr(exprs[1]),
      span: spanFrom(ctx),
    };
  }

  private buildIf(ctx: IfExprContext): Expr {
    const exprs = ctx.expr();
    return {
      kind: "If",
      condition: this.expr(exprs[0]),
      then: this.expr(exprs[1]),
      else_: this.expr(exprs[2]),
      span: spanFrom(ctx),
    };
  }

  private buildLet(ctx: LetExprContext): Expr {
    const exprs = ctx.expr();
    return {
      kind: "Let",
      variable: identText(ctx.lowerIdent()),
      value: this.expr(exprs[0]),
      body: this.expr(exprs[1]),
      span: spanFrom(ctx),
    };
  }

  private buildLambda(ctx: LambdaExprContext): Expr {
    return {
      kind: "Lambda",
      param: identText(ctx.lowerIdent()),
      body: this.expr(ctx.expr()),
      span: spanFrom(ctx),
    };
  }

  private buildConstructor(ctx: ConstructorExprContext): Expr {
    return {
      kind: "Constructor",
      typeName: ctx.UPPER_IDENT().getText(),
      fields: ctx.fieldAssign().map(fa => this.buildFieldAssign(fa)),
      span: spanFrom(ctx),
    };
  }

  private buildSetOrMap(ctx: SetOrMapLiteralContext): Expr {
    const exprs = ctx.expr();
    const span = spanFrom(ctx);

    // Empty set: { }
    if (exprs.length === 0 && !ctx.lowerIdent()) {
      return { kind: "SetLiteral", elements: [], span };
    }

    // Set comprehension: { x in S | P }
    if (ctx.PIPE()) {
      return {
        kind: "SetComprehension",
        variable: identText(ctx.lowerIdent()!),
        domain: this.expr(exprs[0]),
        predicate: this.expr(exprs[1]),
        span,
      };
    }

    // Map literal: { k -> v, ... }
    if (ctx.ARROW().length > 0) {
      if (exprs.length % 2 !== 0) {
        throw new BuildError("map literal requires key/value pairs", ctx);
      }
      const entries: MapEntry[] = [];
      for (let i = 0; i < exprs.length; i += 2) {
        entries.push({
          key: this.expr(exprs[i]),
          value: this.expr(exprs[i + 1]),
          span: spanFrom(exprs[i]),
        });
      }
      return { kind: "MapLiteral", entries, span };
    }

    // Set literal: { a, b, c }
    return { kind: "SetLiteral", elements: exprs.map(e => this.expr(e)), span };
  }

  private buildSeq(ctx: SeqLiteralContext): Expr {
    return {
      kind: "SeqLiteral",
      elements: ctx.expr().map(e => this.expr(e)),
      span: spanFrom(ctx),
    };
  }

  private buildFieldAssign(ctx: FieldAssignContext): FieldAssign {
    return {
      name: identText(ctx.lowerIdent()),
      value: this.expr(ctx.expr()),
      span: spanFrom(ctx),
    };
  }
}

// ─── Public API ─────────────────────────────────────────────

export function buildIR(tree: SpecFileContext): ServiceIR {
  const builder = new IRBuilder();
  const imports = tree.importDecl().map(imp => unquote(imp.STRING_LIT().getText()));
  const ir = builder.buildService(tree.serviceDecl());
  return { ...ir, imports };
}
