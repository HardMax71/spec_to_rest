/**
 * CST-to-IR builder — ANTLR4 visitor that transforms a parse tree into typed IR nodes.
 */

import type { ParserRuleContext } from "antlr4ng";
import { SpecVisitor } from "../parser/generated/SpecVisitor.js";
import type {
  SpecFileContext,
  ServiceDeclContext,
  EntityDeclContext,
  FieldDeclContext,
  EntityInvariantContext,
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
  ExprContext,
  FieldAssignContext,
  QuantifierExprContext,
  QuantBindingContext,
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

// ─── Visitor ────────────────────────────────────────────────

class IRBuilderVisitor extends SpecVisitor<unknown> {

  // ── typed wrappers ──

  private expr(ctx: ExprContext): Expr {
    return this.visit(ctx) as Expr;
  }

  private typeExprFrom(ctx: TypeExprContext): TypeExpr {
    return this.visit(ctx) as TypeExpr;
  }

  private baseTypeFrom(ctx: BaseTypeContext): TypeExpr {
    return this.visit(ctx) as TypeExpr;
  }

  private binOp(ctx: ParserRuleContext, op: BinaryOp): Expr {
    const exprs = (ctx as unknown as { expr(): ExprContext[] }).expr();
    return {
      kind: "BinaryOp",
      op,
      left: this.expr(exprs[0]),
      right: this.expr(exprs[1]),
      span: spanFrom(ctx),
    };
  }

  private unaryOp(ctx: ParserRuleContext, op: "not" | "negate" | "cardinality" | "power"): Expr {
    const inner = (ctx as unknown as { expr(): ExprContext }).expr();
    return {
      kind: "UnaryOp",
      op,
      operand: this.expr(inner),
      span: spanFrom(ctx),
    };
  }

  // ── Top-level ──

  visitServiceDecl = (ctx: ServiceDeclContext): ServiceIR => {
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
        entities.push(this.visit(member.entityDecl()!) as EntityDecl);
      } else if (member.enumDecl()) {
        enums.push(this.visit(member.enumDecl()!) as EnumDecl);
      } else if (member.typeAlias()) {
        typeAliases.push(this.visit(member.typeAlias()!) as TypeAliasDecl);
      } else if (member.stateDecl()) {
        if (state) throw new BuildError("duplicate state block", member.stateDecl()!);
        state = this.visit(member.stateDecl()!) as StateDecl;
      } else if (member.operationDecl()) {
        operations.push(this.visit(member.operationDecl()!) as OperationDecl);
      } else if (member.transitionDecl()) {
        transitions.push(this.visit(member.transitionDecl()!) as TransitionDecl);
      } else if (member.invariantDecl()) {
        invariants.push(this.visit(member.invariantDecl()!) as InvariantDecl);
      } else if (member.factDecl()) {
        facts.push(this.visit(member.factDecl()!) as FactDecl);
      } else if (member.functionDecl()) {
        functions.push(this.visit(member.functionDecl()!) as FunctionDecl);
      } else if (member.predicateDecl()) {
        predicates.push(this.visit(member.predicateDecl()!) as PredicateDecl);
      } else if (member.conventionBlock()) {
        if (conventions) throw new BuildError("duplicate conventions block", member.conventionBlock()!);
        conventions = this.visit(member.conventionBlock()!) as ConventionsDecl;
      }
    }

    return {
      kind: "Service",
      name,
      imports: [],
      entities,
      enums,
      typeAliases,
      state,
      operations,
      transitions,
      invariants,
      facts,
      functions,
      predicates,
      conventions,
      span: spanFrom(ctx),
    };
  }

  // ── Entity ──

  visitEntityDecl = (ctx: EntityDeclContext): EntityDecl => {
    const upperIdents = ctx.UPPER_IDENT();
    const name = upperIdents[0].getText();
    const extends_ = ctx.EXTENDS() ? upperIdents[1].getText() : null;
    const fields: FieldDecl[] = [];
    const invariants: Expr[] = [];

    for (const member of ctx.entityMember()) {
      if (member.fieldDecl()) {
        fields.push(this.visit(member.fieldDecl()!) as FieldDecl);
      } else if (member.entityInvariant()) {
        invariants.push(this.visitEntityInvariantExpr(member.entityInvariant()!));
      }
    }

    return { kind: "Entity", name, extends_, fields, invariants, span: spanFrom(ctx) };
  };

  private visitEntityInvariantExpr(ctx: EntityInvariantContext): Expr {
    return this.expr(ctx.expr());
  }

  visitFieldDecl = (ctx: FieldDeclContext): FieldDecl => {
    const name = identText(ctx.lowerIdent());
    const typeExpr = this.typeExprFrom(ctx.typeExpr());
    const constraint = ctx.WHERE() ? this.expr(ctx.expr()!) : null;
    return { kind: "Field", name, typeExpr, constraint, span: spanFrom(ctx) };
  };

  // ── Enum ──

  visitEnumDecl = (ctx: EnumDeclContext): EnumDecl => {
    const name = ctx.UPPER_IDENT().getText();
    const values = ctx.enumValue().map(ev => ev.UPPER_IDENT().getText());
    return { kind: "Enum", name, values, span: spanFrom(ctx) };
  };

  // ── TypeAlias ──

  visitTypeAlias = (ctx: TypeAliasContext): TypeAliasDecl => {
    const name = ctx.UPPER_IDENT().getText();
    const typeExpr = this.typeExprFrom(ctx.typeExpr());
    const constraint = ctx.WHERE() ? this.expr(ctx.expr()!) : null;
    return { kind: "TypeAlias", name, typeExpr, constraint, span: spanFrom(ctx) };
  };

  // ── Type Expressions ──

  visitTypeExpr = (ctx: TypeExprContext): TypeExpr => {
    const baseTypes = ctx.baseType();
    if (ctx.ARROW()) {
      const fromType = this.baseTypeFrom(baseTypes[0]);
      const multCtx = ctx.multiplicity();
      const multiplicity: Multiplicity = multCtx
        ? (multCtx.getText() as Multiplicity)
        : "one";
      const toType = this.baseTypeFrom(baseTypes[1]);
      return { kind: "RelationType", fromType, multiplicity, toType, span: spanFrom(ctx) };
    }
    return this.baseTypeFrom(baseTypes[0]);
  };

  visitBaseType = (ctx: BaseTypeContext): TypeExpr => {
    if (ctx.primitiveType()) {
      return { kind: "NamedType", name: ctx.primitiveType()!.getText(), span: spanFrom(ctx) };
    }
    if (ctx.SET()) {
      return { kind: "SetType", elementType: this.typeExprFrom(ctx.typeExpr(0)!), span: spanFrom(ctx) };
    }
    if (ctx.MAP()) {
      return {
        kind: "MapType",
        keyType: this.typeExprFrom(ctx.typeExpr(0)!),
        valueType: this.typeExprFrom(ctx.typeExpr(1)!),
        span: spanFrom(ctx),
      };
    }
    if (ctx.SEQ()) {
      return { kind: "SeqType", elementType: this.typeExprFrom(ctx.typeExpr(0)!), span: spanFrom(ctx) };
    }
    if (ctx.OPTION()) {
      return { kind: "OptionType", innerType: this.typeExprFrom(ctx.typeExpr(0)!), span: spanFrom(ctx) };
    }
    // UPPER_IDENT — user-defined type
    return { kind: "NamedType", name: ctx.UPPER_IDENT()!.getText(), span: spanFrom(ctx) };
  };

  // ── State ──

  visitStateDecl = (ctx: StateDeclContext): StateDecl => {
    const fields = ctx.stateField().map(sf => this.visit(sf) as StateFieldDecl);
    return { kind: "State", fields, span: spanFrom(ctx) };
  };

  visitStateField = (ctx: StateFieldContext): StateFieldDecl => {
    return {
      kind: "StateField",
      name: identText(ctx.lowerIdent()),
      typeExpr: this.typeExprFrom(ctx.typeExpr()),
      span: spanFrom(ctx),
    };
  };

  // ── Operation ──

  visitOperationDecl = (ctx: OperationDeclContext): OperationDecl => {
    const name = ctx.UPPER_IDENT().getText();
    const inputs: ParamDecl[] = [];
    const outputs: ParamDecl[] = [];
    const requires: Expr[] = [];
    const ensures: Expr[] = [];

    for (const clause of ctx.operationClause()) {
      if (clause.inputClause()) {
        const ic = clause.inputClause()!;
        for (const p of ic.paramList().param()) {
          inputs.push(this.visit(p) as ParamDecl);
        }
      } else if (clause.outputClause()) {
        const oc = clause.outputClause()!;
        for (const p of oc.paramList().param()) {
          outputs.push(this.visit(p) as ParamDecl);
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
  };

  visitParam = (ctx: ParamContext): ParamDecl => {
    return {
      kind: "Param",
      name: identText(ctx.lowerIdent()),
      typeExpr: this.typeExprFrom(ctx.typeExpr()),
      span: spanFrom(ctx),
    };
  };

  // ── Transition ──

  visitTransitionDecl = (ctx: TransitionDeclContext): TransitionDecl => {
    const upperIdents = ctx.UPPER_IDENT();
    const name = upperIdents[0].getText();
    const entityName = upperIdents[1].getText();
    const fieldName = identText(ctx.lowerIdent());
    const rules = ctx.transitionRule().map(r => this.visit(r) as IRTransitionRule);
    return { kind: "Transition", name, entityName, fieldName, rules, span: spanFrom(ctx) };
  };

  visitTransitionRule = (ctx: TransitionRuleContext): IRTransitionRule => {
    const upperIdents = ctx.UPPER_IDENT();
    const from = upperIdents[0].getText();
    const to = upperIdents[1].getText();
    const via = upperIdents[2].getText();
    const guard = ctx.WHEN() ? this.expr(ctx.expr()!) : null;
    return { kind: "TransitionRule", from, to, via, guard, span: spanFrom(ctx) };
  };

  // ── Invariant / Fact ──

  visitInvariantDecl = (ctx: InvariantDeclContext): InvariantDecl => {
    const name = ctx.lowerIdent() ? identText(ctx.lowerIdent()!) : null;
    return { kind: "Invariant", name, expr: this.expr(ctx.expr()), span: spanFrom(ctx) };
  };

  visitFactDecl = (ctx: FactDeclContext): FactDecl => {
    const name = ctx.lowerIdent() ? identText(ctx.lowerIdent()!) : null;
    return { kind: "Fact", name, expr: this.expr(ctx.expr()), span: spanFrom(ctx) };
  };

  // ── Function / Predicate ──

  visitFunctionDecl = (ctx: FunctionDeclContext): FunctionDecl => {
    const name = identText(ctx.lowerIdent());
    const params = ctx.paramList()
      ? ctx.paramList()!.param().map(p => this.visit(p) as ParamDecl)
      : [];
    const returnType = this.typeExprFrom(ctx.typeExpr());
    const body = this.expr(ctx.expr());
    return { kind: "Function", name, params, returnType, body, span: spanFrom(ctx) };
  };

  visitPredicateDecl = (ctx: PredicateDeclContext): PredicateDecl => {
    const name = identText(ctx.lowerIdent());
    const params = ctx.paramList()
      ? ctx.paramList()!.param().map(p => this.visit(p) as ParamDecl)
      : [];
    const body = this.expr(ctx.expr());
    return { kind: "Predicate", name, params, body, span: spanFrom(ctx) };
  };

  // ── Conventions ──

  visitConventionBlock = (ctx: ConventionBlockContext): ConventionsDecl => {
    const rules = ctx.conventionRule().map(r => this.visit(r) as IRConventionRule);
    return { kind: "Conventions", rules, span: spanFrom(ctx) };
  };

  visitConventionRule = (ctx: ConventionRuleContext): IRConventionRule => {
    const target = ctx.UPPER_IDENT().getText();
    const property = identText(ctx.lowerIdent());
    const strLit = ctx.STRING_LIT();
    const qualifier = strLit ? unquote(strLit.getText()) : null;
    const value = this.expr(ctx.expr());
    return { kind: "ConventionRule", target, property, qualifier, value, span: spanFrom(ctx) };
  };

  // ═══════════════════════════════════════════════════════════
  // EXPRESSIONS
  // ═══════════════════════════════════════════════════════════

  // ── Binary operators (20) ──

  visitMulExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "*");
  visitDivExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "/");
  visitAddExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "+");
  visitSubExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "-");
  visitUnionExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "union");
  visitIntersectExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "intersect");
  visitMinusExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "minus");
  visitEqExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "=");
  visitNeqExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "!=");
  visitLtExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "<");
  visitGtExpr = (ctx: ExprContext): Expr => this.binOp(ctx, ">");
  visitLteExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "<=");
  visitGteExpr = (ctx: ExprContext): Expr => this.binOp(ctx, ">=");
  visitInExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "in");
  visitNotInExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "not_in");
  visitSubsetExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "subset");
  visitImpliesExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "implies");
  visitIffExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "iff");
  visitAndExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "and");
  visitOrExpr = (ctx: ExprContext): Expr => this.binOp(ctx, "or");

  // ── Unary operators (4) ──

  visitCardinalityExpr = (ctx: ExprContext): Expr => this.unaryOp(ctx, "cardinality");
  visitNegExpr = (ctx: ExprContext): Expr => this.unaryOp(ctx, "negate");
  visitPowerExpr = (ctx: ExprContext): Expr => this.unaryOp(ctx, "power");
  visitNotExpr = (ctx: ExprContext): Expr => this.unaryOp(ctx, "not");

  // ── Postfix operators ──

  visitPrimeExpr = (ctx: ExprContext): Expr => {
    const inner = (ctx as unknown as { expr(): ExprContext }).expr();
    return { kind: "Prime", expr: this.expr(inner), span: spanFrom(ctx) };
  };

  visitFieldAccessExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as { expr(): ExprContext; lowerIdent(): LowerIdentContext };
    return {
      kind: "FieldAccess",
      base: this.expr(c.expr()),
      field: identText(c.lowerIdent()),
      span: spanFrom(ctx),
    };
  };

  visitEnumAccessExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as { expr(): ExprContext; UPPER_IDENT(): { getText(): string } };
    return {
      kind: "EnumAccess",
      base: this.expr(c.expr()),
      member: c.UPPER_IDENT().getText(),
      span: spanFrom(ctx),
    };
  };

  visitIndexExpr = (ctx: ExprContext): Expr => {
    const exprs = (ctx as unknown as { expr(): ExprContext[]; expr(i: number): ExprContext }).expr() as ExprContext[];
    return {
      kind: "Index",
      base: this.expr(exprs[0]),
      index: this.expr(exprs[1]),
      span: spanFrom(ctx),
    };
  };

  visitCallExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as {
      expr(): ExprContext;
      argList(): { expr(): ExprContext[] } | null;
    };
    const args = c.argList()?.expr().map(e => this.expr(e)) ?? [];
    return { kind: "Call", callee: this.expr(c.expr()), args, span: spanFrom(ctx) };
  };

  visitWithExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as {
      expr(): ExprContext;
      fieldAssign(): FieldAssignContext[];
    };
    const updates = c.fieldAssign().map(fa => this.buildFieldAssign(fa));
    return { kind: "With", base: this.expr(c.expr()), updates, span: spanFrom(ctx) };
  };

  visitMatchesExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as {
      expr(): ExprContext;
      REGEX_LIT(): { getText(): string };
    };
    return {
      kind: "Matches",
      expr: this.expr(c.expr()),
      pattern: unslashRegex(c.REGEX_LIT().getText()),
      span: spanFrom(ctx),
    };
  };

  // ── Transparent delegators ──

  visitParenExpr = (ctx: ExprContext): Expr => {
    const inner = (ctx as unknown as { expr(): ExprContext }).expr();
    return this.expr(inner);
  };

  visitQuantExpr = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { quantifierExpr(): QuantifierExprContext };
    return this.visit(c.quantifierExpr());
  };

  visitSomeWrapE = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { someWrapExpr(): SomeWrapExprContext };
    return this.visit(c.someWrapExpr());
  };

  visitTheE = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { theExpr(): TheExprContext };
    return this.visit(c.theExpr());
  };

  visitIfE = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { ifExpr(): IfExprContext };
    return this.visit(c.ifExpr());
  };

  visitLetE = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { letExpr(): LetExprContext };
    return this.visit(c.letExpr());
  };

  visitLambdaE = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { lambdaExpr(): LambdaExprContext };
    return this.visit(c.lambdaExpr());
  };

  visitConstructorE = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { constructorExpr(): ConstructorExprContext };
    return this.visit(c.constructorExpr());
  };

  visitSetOrMapE = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { setOrMapLiteral(): SetOrMapLiteralContext };
    return this.visit(c.setOrMapLiteral());
  };

  visitSeqE = (ctx: ExprContext): unknown => {
    const c = ctx as unknown as { seqLiteral(): SeqLiteralContext };
    return this.visit(c.seqLiteral());
  };

  visitPreExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as { expr(): ExprContext };
    return { kind: "Pre", expr: this.expr(c.expr()), span: spanFrom(ctx) };
  };

  // ── Compound expression sub-rules ──

  visitQuantifierExpr = (ctx: QuantifierExprContext): Expr => {
    const qCtx = ctx.quantifier();
    let quantifier: QuantifierKind;
    if (qCtx.ALL()) quantifier = "all";
    else if (qCtx.SOME()) quantifier = "some";
    else if (qCtx.NO()) quantifier = "no";
    else quantifier = "exists";

    const bindings: QuantifierBinding[] = ctx.quantBinding().map(b => {
      const bindingKind = b.IN() ? "in" as const : "colon" as const;
      return {
        variable: identText(b.lowerIdent()),
        domain: this.expr(b.expr()),
        bindingKind,
        span: spanFrom(b),
      };
    });

    return {
      kind: "Quantifier",
      quantifier,
      bindings,
      body: this.expr(ctx.expr()),
      span: spanFrom(ctx),
    };
  };

  visitSomeWrapExpr = (ctx: SomeWrapExprContext): Expr => {
    return { kind: "SomeWrap", expr: this.expr(ctx.expr()), span: spanFrom(ctx) };
  };

  visitTheExpr = (ctx: TheExprContext): Expr => {
    const exprs = ctx.expr();
    return {
      kind: "The",
      variable: identText(ctx.lowerIdent()),
      domain: this.expr(exprs[0]),
      body: this.expr(exprs[1]),
      span: spanFrom(ctx),
    };
  };

  visitIfExpr = (ctx: IfExprContext): Expr => {
    const exprs = ctx.expr();
    return {
      kind: "If",
      condition: this.expr(exprs[0]),
      then: this.expr(exprs[1]),
      else_: this.expr(exprs[2]),
      span: spanFrom(ctx),
    };
  };

  visitLetExpr = (ctx: LetExprContext): Expr => {
    const exprs = ctx.expr();
    return {
      kind: "Let",
      variable: identText(ctx.lowerIdent()),
      value: this.expr(exprs[0]),
      body: this.expr(exprs[1]),
      span: spanFrom(ctx),
    };
  };

  visitLambdaExpr = (ctx: LambdaExprContext): Expr => {
    return {
      kind: "Lambda",
      param: identText(ctx.lowerIdent()),
      body: this.expr(ctx.expr()),
      span: spanFrom(ctx),
    };
  };

  visitConstructorExpr = (ctx: ConstructorExprContext): Expr => {
    const updates = ctx.fieldAssign().map(fa => this.buildFieldAssign(fa));
    return {
      kind: "Constructor",
      typeName: ctx.UPPER_IDENT().getText(),
      fields: updates,
      span: spanFrom(ctx),
    };
  };

  visitSetOrMapLiteral = (ctx: SetOrMapLiteralContext): Expr => {
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
    const arrows = ctx.ARROW();
    if (arrows.length > 0) {
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
    return {
      kind: "SetLiteral",
      elements: exprs.map(e => this.expr(e)),
      span,
    };
  };

  visitSeqLiteral = (ctx: SeqLiteralContext): Expr => {
    const exprs = ctx.expr();
    return {
      kind: "SeqLiteral",
      elements: exprs.map(e => this.expr(e)),
      span: spanFrom(ctx),
    };
  };

  // ── Literals ──

  visitIntLitExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as { INT_LIT(): { getText(): string } };
    return { kind: "IntLit", value: parseInt(c.INT_LIT().getText(), 10), span: spanFrom(ctx) };
  };

  visitFloatLitExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as { FLOAT_LIT(): { getText(): string } };
    return { kind: "FloatLit", value: parseFloat(c.FLOAT_LIT().getText()), span: spanFrom(ctx) };
  };

  visitStringLitExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as { STRING_LIT(): { getText(): string } };
    return { kind: "StringLit", value: unquote(c.STRING_LIT().getText()), span: spanFrom(ctx) };
  };

  visitTrueLitExpr = (_ctx: ExprContext): Expr => {
    return { kind: "BoolLit", value: true, span: spanFrom(_ctx) };
  };

  visitFalseLitExpr = (_ctx: ExprContext): Expr => {
    return { kind: "BoolLit", value: false, span: spanFrom(_ctx) };
  };

  visitNoneLitExpr = (_ctx: ExprContext): Expr => {
    return { kind: "NoneLit", span: spanFrom(_ctx) };
  };

  visitUpperIdentExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as { UPPER_IDENT(): { getText(): string } };
    return { kind: "Identifier", name: c.UPPER_IDENT().getText(), span: spanFrom(ctx) };
  };

  visitLowerIdentExpr = (ctx: ExprContext): Expr => {
    const c = ctx as unknown as { lowerIdent(): LowerIdentContext };
    return { kind: "Identifier", name: identText(c.lowerIdent()), span: spanFrom(ctx) };
  };

  // ── FieldAssign helper ──

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
  const visitor = new IRBuilderVisitor();

  // Visit the top-level specFile which contains imports + serviceDecl
  const imports = tree.importDecl().map(imp => unquote(imp.STRING_LIT().getText()));
  const serviceCtx = tree.serviceDecl();
  const ir = visitor.visit(serviceCtx) as ServiceIR;

  // Merge imports into the service IR (imports are at specFile level, not serviceDecl)
  return { ...ir, imports };
}
