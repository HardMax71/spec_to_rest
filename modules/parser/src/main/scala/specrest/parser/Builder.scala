package specrest.parser

import cats.effect.IO
import org.antlr.v4.runtime.ParserRuleContext
import specrest.ir.*
import specrest.parser.generated.SpecBaseVisitor
import specrest.parser.generated.SpecParser.*

import scala.jdk.CollectionConverters.*

private type BuildResult[A] = Either[VerifyError.Build, A]

private def spanFrom(ctx: ParserRuleContext): Span =
  val start = ctx.getStart
  val stop  = Option(ctx.getStop).getOrElse(start)
  Span(
    startLine = start.getLine,
    startCol = start.getCharPositionInLine,
    endLine = stop.getLine,
    endCol = stop.getCharPositionInLine + Option(stop.getText).map(_.length).getOrElse(1)
  )

private def sp(ctx: ParserRuleContext): Option[Span] = Some(spanFrom(ctx))

private def buildErr(msg: String, ctx: ParserRuleContext): VerifyError.Build =
  VerifyError.Build(msg, sp(ctx))

private def unquote(raw: String): String =
  val inner = raw.substring(1, raw.length - 1)
  val sb    = new StringBuilder
  var i     = 0
  while i < inner.length do
    val c = inner.charAt(i)
    if c == '\\' && i + 1 < inner.length then
      inner.charAt(i + 1) match
        case 'n'   => sb.append('\n')
        case 't'   => sb.append('\t')
        case 'r'   => sb.append('\r')
        case other => sb.append(other)
      i += 2
    else
      sb.append(c)
      i += 1
  sb.toString

private def unslashRegex(raw: String): String = raw.substring(1, raw.length - 1)

extension [A](list: List[BuildResult[A]])
  private def sequenceB: BuildResult[List[A]] =
    list.foldRight[BuildResult[List[A]]](Right(Nil)):
      case (eh, et) => eh.flatMap(h => et.map(h :: _))

extension [A, B](list: List[A])
  private def traverseB(f: A => BuildResult[B]): BuildResult[List[B]] =
    list.map(f).sequenceB

object Builder:
  def buildIR(tree: SpecFileContext): IO[Either[VerifyError.Build, ServiceIR]] =
    IO.delay(buildIRSync(tree))

  private[specrest] def buildIRSync(tree: SpecFileContext): Either[VerifyError.Build, ServiceIR] =
    val imports = tree.importDecl.asScala.map(imp => unquote(imp.STRING_LIT.getText)).toList
    new IRBuilder().buildService(tree.serviceDecl).map(_.copy(imports = imports))

final private case class ServiceAcc(
    entities: List[EntityDecl] = Nil,
    enums: List[EnumDecl] = Nil,
    typeAliases: List[TypeAliasDecl] = Nil,
    state: Option[StateDecl] = None,
    operations: List[OperationDecl] = Nil,
    transitions: List[TransitionDecl] = Nil,
    invariants: List[InvariantDecl] = Nil,
    temporals: List[TemporalDecl] = Nil,
    facts: List[FactDecl] = Nil,
    functions: List[FunctionDecl] = Nil,
    predicates: List[PredicateDecl] = Nil,
    conventions: Option[ConventionsDecl] = None
)

final private class IRBuilder extends SpecBaseVisitor[BuildResult[Expr]]:

  override def defaultResult(): BuildResult[Expr] =
    Left(VerifyError.Build("IRBuilder: unhandled expression node", None))

  private def expr(ctx: ExprContext): BuildResult[Expr] = visit(ctx)

  private def binOp(
      ctx: ParserRuleContext,
      l: ExprContext,
      r: ExprContext,
      op: BinOp
  ): BuildResult[Expr] =
    for
      lE <- expr(l)
      rE <- expr(r)
    yield Expr.BinaryOp(op, lE, rE, sp(ctx))

  private def unaryOp(ctx: ParserRuleContext, arg: ExprContext, op: UnOp): BuildResult[Expr] =
    expr(arg).map(a => Expr.UnaryOp(op, a, sp(ctx)))

  def buildService(ctx: ServiceDeclContext): BuildResult[ServiceIR] =
    val name = ctx.UPPER_IDENT.getText
    val finalAcc = ctx.serviceMember.asScala.toList
      .foldLeft[BuildResult[ServiceAcc]](Right(ServiceAcc())):
        case (accE, member) => accE.flatMap(acc => processMember(acc, member))
    finalAcc.map: acc =>
      ServiceIR(
        name = name,
        imports = Nil,
        entities = acc.entities.reverse,
        enums = acc.enums.reverse,
        typeAliases = acc.typeAliases.reverse,
        state = acc.state,
        operations = acc.operations.reverse,
        transitions = acc.transitions.reverse,
        invariants = acc.invariants.reverse,
        temporals = acc.temporals.reverse,
        facts = acc.facts.reverse,
        functions = acc.functions.reverse,
        predicates = acc.predicates.reverse,
        conventions = acc.conventions,
        span = sp(ctx)
      )

  private def processMember(acc: ServiceAcc, m: ServiceMemberContext): BuildResult[ServiceAcc] =
    if m.entityDecl != null then
      buildEntity(m.entityDecl).map(e => acc.copy(entities = e :: acc.entities))
    else if m.enumDecl != null then
      Right(acc.copy(enums = buildEnum(m.enumDecl) :: acc.enums))
    else if m.typeAlias != null then
      buildTypeAlias(m.typeAlias).map(t => acc.copy(typeAliases = t :: acc.typeAliases))
    else if m.stateDecl != null then
      if acc.state.isDefined then Left(buildErr("duplicate state block", m.stateDecl))
      else buildState(m.stateDecl).map(s => acc.copy(state = Some(s)))
    else if m.operationDecl != null then
      buildOperation(m.operationDecl).map(o => acc.copy(operations = o :: acc.operations))
    else if m.transitionDecl != null then
      buildTransition(m.transitionDecl).map(t => acc.copy(transitions = t :: acc.transitions))
    else if m.invariantDecl != null then
      buildInvariant(m.invariantDecl).map(i => acc.copy(invariants = i :: acc.invariants))
    else if m.temporalDecl != null then
      buildTemporal(m.temporalDecl).map(t => acc.copy(temporals = t :: acc.temporals))
    else if m.factDecl != null then
      buildFact(m.factDecl).map(f => acc.copy(facts = f :: acc.facts))
    else if m.functionDecl != null then
      buildFunction(m.functionDecl).map(f => acc.copy(functions = f :: acc.functions))
    else if m.predicateDecl != null then
      buildPredicate(m.predicateDecl).map(p => acc.copy(predicates = p :: acc.predicates))
    else if m.conventionBlock != null then
      if acc.conventions.isDefined then
        Left(buildErr("duplicate conventions block", m.conventionBlock))
      else buildConventions(m.conventionBlock).map(c => acc.copy(conventions = Some(c)))
    else Right(acc)

  private def buildEntity(ctx: EntityDeclContext): BuildResult[EntityDecl] =
    val idents     = ctx.UPPER_IDENT
    val name       = idents.get(0).getText
    val extendsOpt = if ctx.EXTENDS != null then Some(idents.get(1).getText) else None
    val members    = ctx.entityMember.asScala.toList
    val parts = members.foldLeft[BuildResult[(List[FieldDecl], List[Expr])]](Right((Nil, Nil))):
      case (accE, member) =>
        accE.flatMap: (fs, invs) =>
          if member.fieldDecl != null then
            buildField(member.fieldDecl).map(f => (f :: fs, invs))
          else if member.entityInvariant != null then
            expr(member.entityInvariant.expr).map(e => (fs, e :: invs))
          else Right((fs, invs))
    parts.map: (fs, invs) =>
      EntityDecl(name, extendsOpt, fs.reverse, invs.reverse, sp(ctx))

  private def buildField(ctx: FieldDeclContext): BuildResult[FieldDecl] =
    val name      = ctx.lowerIdent.getText
    val typeExprV = buildTypeExpr(ctx.typeExpr)
    val constraintE: BuildResult[Option[Expr]] =
      if ctx.WHERE != null then expr(ctx.expr).map(Some(_)) else Right(None)
    for
      t <- typeExprV
      c <- constraintE
    yield FieldDecl(name, t, c, sp(ctx))

  private def buildEnum(ctx: EnumDeclContext): EnumDecl =
    val name   = ctx.UPPER_IDENT.getText
    val values = ctx.enumValue.asScala.map(_.UPPER_IDENT.getText).toList
    EnumDecl(name, values, sp(ctx))

  private def buildTypeAlias(ctx: TypeAliasContext): BuildResult[TypeAliasDecl] =
    val name      = ctx.UPPER_IDENT.getText
    val typeExprV = buildTypeExpr(ctx.typeExpr)
    val constraintE: BuildResult[Option[Expr]] =
      if ctx.WHERE != null then expr(ctx.expr).map(Some(_)) else Right(None)
    for
      t <- typeExprV
      c <- constraintE
    yield TypeAliasDecl(name, t, c, sp(ctx))

  private def buildState(ctx: StateDeclContext): BuildResult[StateDecl] =
    ctx.stateField.asScala.toList
      .traverseB(buildStateField)
      .map(fields => StateDecl(fields, sp(ctx)))

  private def buildStateField(ctx: StateFieldContext): BuildResult[StateFieldDecl] =
    buildTypeExpr(ctx.typeExpr).map(t => StateFieldDecl(ctx.lowerIdent.getText, t, sp(ctx)))

  private def buildOperation(ctx: OperationDeclContext): BuildResult[OperationDecl] =
    val name    = ctx.UPPER_IDENT.getText
    val clauses = ctx.operationClause.asScala.toList
    val acc0: BuildResult[(List[ParamDecl], List[ParamDecl], List[Expr], List[Expr])] =
      Right((Nil, Nil, Nil, Nil))
    val collected = clauses.foldLeft(acc0):
      case (accE, clause) =>
        accE.flatMap: (ins, outs, reqs, ens) =>
          if clause.inputClause != null then
            clause.inputClause.paramList.param.asScala.toList
              .traverseB(buildParam)
              .map(ps => (ins ++ ps, outs, reqs, ens))
          else if clause.outputClause != null then
            clause.outputClause.paramList.param.asScala.toList
              .traverseB(buildParam)
              .map(ps => (ins, outs ++ ps, reqs, ens))
          else if clause.requiresClause != null then
            clause.requiresClause.expr.asScala.toList
              .traverseB(expr)
              .map(es => (ins, outs, reqs ++ es, ens))
          else if clause.ensuresClause != null then
            clause.ensuresClause.expr.asScala.toList
              .traverseB(expr)
              .map(es => (ins, outs, reqs, ens ++ es))
          else Right((ins, outs, reqs, ens))
    collected.map: (ins, outs, reqs, ens) =>
      OperationDecl(name, ins, outs, reqs, ens, sp(ctx))

  private def buildParam(ctx: ParamContext): BuildResult[ParamDecl] =
    buildTypeExpr(ctx.typeExpr).map(t => ParamDecl(ctx.lowerIdent.getText, t, sp(ctx)))

  private def buildTransition(ctx: TransitionDeclContext): BuildResult[TransitionDecl] =
    val idents     = ctx.UPPER_IDENT
    val name       = idents.get(0).getText
    val entityName = idents.get(1).getText
    val fieldName  = ctx.lowerIdent.getText
    ctx.transitionRule.asScala.toList
      .traverseB(buildTransitionRule)
      .map(rules => TransitionDecl(name, entityName, fieldName, rules, sp(ctx)))

  private def buildTransitionRule(ctx: TransitionRuleContext): BuildResult[TransitionRule] =
    val idents = ctx.UPPER_IDENT
    val from   = idents.get(0).getText
    val to     = idents.get(1).getText
    val via    = idents.get(2).getText
    val guardE: BuildResult[Option[Expr]] =
      if ctx.WHEN != null then expr(ctx.expr).map(Some(_)) else Right(None)
    guardE.map(g => TransitionRule(from, to, via, g, sp(ctx)))

  private def buildInvariant(ctx: InvariantDeclContext): BuildResult[InvariantDecl] =
    val name = Option(ctx.lowerIdent).map(_.getText)
    expr(ctx.expr).map(e => InvariantDecl(name, e, sp(ctx)))

  private def buildTemporal(ctx: TemporalDeclContext): BuildResult[TemporalDecl] =
    val name = ctx.lowerIdent.getText
    expr(ctx.expr).map(e => TemporalDecl(name, e, sp(ctx)))

  private def buildFact(ctx: FactDeclContext): BuildResult[FactDecl] =
    val name = Option(ctx.lowerIdent).map(_.getText)
    expr(ctx.expr).map(e => FactDecl(name, e, sp(ctx)))

  private def buildFunction(ctx: FunctionDeclContext): BuildResult[FunctionDecl] =
    val name       = ctx.lowerIdent.getText
    val returnType = buildTypeExpr(ctx.typeExpr)
    val paramsE: BuildResult[List[ParamDecl]] =
      Option(ctx.paramList).map(_.param.asScala.toList.traverseB(buildParam)).getOrElse(Right(Nil))
    for
      ps <- paramsE
      rt <- returnType
      b  <- expr(ctx.expr)
    yield FunctionDecl(name, ps, rt, b, sp(ctx))

  private def buildPredicate(ctx: PredicateDeclContext): BuildResult[PredicateDecl] =
    val name = ctx.lowerIdent.getText
    val paramsE: BuildResult[List[ParamDecl]] =
      Option(ctx.paramList).map(_.param.asScala.toList.traverseB(buildParam)).getOrElse(Right(Nil))
    for
      ps <- paramsE
      b  <- expr(ctx.expr)
    yield PredicateDecl(name, ps, b, sp(ctx))

  private def buildConventions(ctx: ConventionBlockContext): BuildResult[ConventionsDecl] =
    ctx.conventionRule.asScala.toList
      .traverseB(buildConventionRule)
      .map(rules => ConventionsDecl(rules, sp(ctx)))

  private def buildConventionRule(ctx: ConventionRuleContext): BuildResult[ConventionRule] =
    val target    = ctx.UPPER_IDENT.getText
    val property  = ctx.lowerIdent.getText
    val qualifier = Option(ctx.STRING_LIT).map(s => unquote(s.getText))
    expr(ctx.expr).map(v => ConventionRule(target, property, qualifier, v, sp(ctx)))

  private def buildTypeExpr(ctx: TypeExprContext): BuildResult[TypeExpr] =
    val baseTypes = ctx.baseType
    if ctx.ARROW != null then
      for
        fromType <- buildBaseType(baseTypes.get(0))
        toType   <- buildBaseType(baseTypes.get(1))
        mult <- Option(ctx.multiplicity) match
                  case None => Right(Multiplicity.One)
                  case Some(m) =>
                    m.getText match
                      case "one"  => Right(Multiplicity.One)
                      case "lone" => Right(Multiplicity.Lone)
                      case "some" => Right(Multiplicity.Some)
                      case "set"  => Right(Multiplicity.Set)
                      case other  => Left(buildErr(s"unknown multiplicity: $other", m))
      yield TypeExpr.RelationType(fromType, mult, toType, sp(ctx))
    else buildBaseType(baseTypes.get(0))

  private def buildBaseType(ctx: BaseTypeContext): BuildResult[TypeExpr] =
    if ctx.primitiveType != null then
      Right(TypeExpr.NamedType(ctx.primitiveType.getText, sp(ctx)))
    else if ctx.SET != null then
      buildTypeExpr(ctx.typeExpr(0)).map(t => TypeExpr.SetType(t, sp(ctx)))
    else if ctx.MAP != null then
      for
        k <- buildTypeExpr(ctx.typeExpr(0))
        v <- buildTypeExpr(ctx.typeExpr(1))
      yield TypeExpr.MapType(k, v, sp(ctx))
    else if ctx.SEQ != null then
      buildTypeExpr(ctx.typeExpr(0)).map(t => TypeExpr.SeqType(t, sp(ctx)))
    else if ctx.OPTION != null then
      buildTypeExpr(ctx.typeExpr(0)).map(t => TypeExpr.OptionType(t, sp(ctx)))
    else Right(TypeExpr.NamedType(ctx.UPPER_IDENT.getText, sp(ctx)))

  override def visitMulExpr(ctx: MulExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Mul)
  override def visitDivExpr(ctx: DivExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Div)
  override def visitAddExpr(ctx: AddExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Add)
  override def visitSubExpr(ctx: SubExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Sub)
  override def visitUnionExpr(ctx: UnionExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Union)
  override def visitIntersectExpr(ctx: IntersectExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Intersect)
  override def visitMinusExpr(ctx: MinusExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Diff)
  override def visitEqExpr(ctx: EqExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Eq)
  override def visitNeqExpr(ctx: NeqExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Neq)
  override def visitLtExpr(ctx: LtExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Lt)
  override def visitGtExpr(ctx: GtExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Gt)
  override def visitLteExpr(ctx: LteExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Le)
  override def visitGteExpr(ctx: GteExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Ge)
  override def visitInExpr(ctx: InExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.In)
  override def visitNotInExpr(ctx: NotInExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.NotIn)
  override def visitSubsetExpr(ctx: SubsetExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Subset)
  override def visitImpliesExpr(ctx: ImpliesExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Implies)
  override def visitIffExpr(ctx: IffExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Iff)
  override def visitAndExpr(ctx: AndExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.And)
  override def visitOrExpr(ctx: OrExprContext): BuildResult[Expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Or)

  override def visitCardinalityExpr(ctx: CardinalityExprContext): BuildResult[Expr] =
    unaryOp(ctx, ctx.expr, UnOp.Cardinality)
  override def visitNegExpr(ctx: NegExprContext): BuildResult[Expr] =
    unaryOp(ctx, ctx.expr, UnOp.Negate)
  override def visitPowerExpr(ctx: PowerExprContext): BuildResult[Expr] =
    unaryOp(ctx, ctx.expr, UnOp.Power)
  override def visitNotExpr(ctx: NotExprContext): BuildResult[Expr] =
    unaryOp(ctx, ctx.expr, UnOp.Not)

  override def visitPrimeExpr(ctx: PrimeExprContext): BuildResult[Expr] =
    expr(ctx.expr).map(e => Expr.Prime(e, sp(ctx)))

  override def visitFieldAccessExpr(ctx: FieldAccessExprContext): BuildResult[Expr] =
    expr(ctx.expr).map(e => Expr.FieldAccess(e, ctx.lowerIdent.getText, sp(ctx)))

  override def visitEnumAccessExpr(ctx: EnumAccessExprContext): BuildResult[Expr] =
    expr(ctx.expr).map(e => Expr.EnumAccess(e, ctx.UPPER_IDENT.getText, sp(ctx)))

  override def visitIndexExpr(ctx: IndexExprContext): BuildResult[Expr] =
    for
      a <- expr(ctx.expr(0))
      b <- expr(ctx.expr(1))
    yield Expr.Index(a, b, sp(ctx))

  override def visitCallExpr(ctx: CallExprContext): BuildResult[Expr] =
    val argsE: BuildResult[List[Expr]] =
      Option(ctx.argList).map(_.expr.asScala.toList.traverseB(expr)).getOrElse(Right(Nil))
    for
      fn   <- expr(ctx.expr)
      args <- argsE
    yield Expr.Call(fn, args, sp(ctx))

  override def visitWithExpr(ctx: WithExprContext): BuildResult[Expr] =
    for
      base    <- expr(ctx.expr)
      updates <- ctx.fieldAssign.asScala.toList.traverseB(buildFieldAssign)
    yield Expr.With(base, updates, sp(ctx))

  override def visitMatchesExpr(ctx: MatchesExprContext): BuildResult[Expr] =
    expr(ctx.expr).map(e => Expr.Matches(e, unslashRegex(ctx.REGEX_LIT.getText), sp(ctx)))

  override def visitParenExpr(ctx: ParenExprContext): BuildResult[Expr] = expr(ctx.expr)
  override def visitQuantExpr(ctx: QuantExprContext): BuildResult[Expr] =
    buildQuantifier(ctx.quantifierExpr)
  override def visitSomeWrapE(ctx: SomeWrapEContext): BuildResult[Expr] =
    buildSomeWrap(ctx.someWrapExpr)
  override def visitTheE(ctx: TheEContext): BuildResult[Expr]       = buildThe(ctx.theExpr)
  override def visitIfE(ctx: IfEContext): BuildResult[Expr]         = buildIf(ctx.ifExpr)
  override def visitLetE(ctx: LetEContext): BuildResult[Expr]       = buildLet(ctx.letExpr)
  override def visitLambdaE(ctx: LambdaEContext): BuildResult[Expr] = buildLambda(ctx.lambdaExpr)
  override def visitConstructorE(ctx: ConstructorEContext): BuildResult[Expr] =
    buildConstructor(ctx.constructorExpr)
  override def visitSetOrMapE(ctx: SetOrMapEContext): BuildResult[Expr] =
    buildSetOrMap(ctx.setOrMapLiteral)
  override def visitSeqE(ctx: SeqEContext): BuildResult[Expr] = buildSeq(ctx.seqLiteral)

  override def visitPreExpr(ctx: PreExprContext): BuildResult[Expr] =
    expr(ctx.expr).map(e => Expr.Pre(e, sp(ctx)))

  override def visitIntLitExpr(ctx: IntLitExprContext): BuildResult[Expr] =
    Right(Expr.IntLit(ctx.INT_LIT.getText.toLong, sp(ctx)))

  override def visitFloatLitExpr(ctx: FloatLitExprContext): BuildResult[Expr] =
    Right(Expr.FloatLit(ctx.FLOAT_LIT.getText.toDouble, sp(ctx)))

  override def visitStringLitExpr(ctx: StringLitExprContext): BuildResult[Expr] =
    Right(Expr.StringLit(unquote(ctx.STRING_LIT.getText), sp(ctx)))

  override def visitTrueLitExpr(ctx: TrueLitExprContext): BuildResult[Expr] =
    Right(Expr.BoolLit(true, sp(ctx)))
  override def visitFalseLitExpr(ctx: FalseLitExprContext): BuildResult[Expr] =
    Right(Expr.BoolLit(false, sp(ctx)))
  override def visitNoneLitExpr(ctx: NoneLitExprContext): BuildResult[Expr] =
    Right(Expr.NoneLit(sp(ctx)))

  override def visitUpperIdentExpr(ctx: UpperIdentExprContext): BuildResult[Expr] =
    Right(Expr.Identifier(ctx.UPPER_IDENT.getText, sp(ctx)))

  override def visitLowerIdentExpr(ctx: LowerIdentExprContext): BuildResult[Expr] =
    Right(Expr.Identifier(ctx.lowerIdent.getText, sp(ctx)))

  private def buildQuantifier(ctx: QuantifierExprContext): BuildResult[Expr] =
    val qCtx = ctx.quantifier
    val quantifier: QuantKind =
      if qCtx.ALL != null then QuantKind.All
      else if qCtx.SOME != null then QuantKind.Some
      else if qCtx.NO != null then QuantKind.No
      else QuantKind.Exists
    val bindingsE: BuildResult[List[QuantifierBinding]] =
      ctx.quantBinding.asScala.toList.traverseB: b =>
        val bk = if b.IN != null then BindingKind.In else BindingKind.Colon
        expr(b.expr).map(d => QuantifierBinding(b.lowerIdent.getText, d, bk, sp(b)))
    for
      bs   <- bindingsE
      body <- expr(ctx.expr)
    yield Expr.Quantifier(quantifier, bs, body, sp(ctx))

  private def buildSomeWrap(ctx: SomeWrapExprContext): BuildResult[Expr] =
    expr(ctx.expr).map(e => Expr.SomeWrap(e, sp(ctx)))

  private def buildThe(ctx: TheExprContext): BuildResult[Expr] =
    val exprs = ctx.expr
    for
      a <- expr(exprs.get(0))
      b <- expr(exprs.get(1))
    yield Expr.The(ctx.lowerIdent.getText, a, b, sp(ctx))

  private def buildIf(ctx: IfExprContext): BuildResult[Expr] =
    val exprs = ctx.expr
    for
      c <- expr(exprs.get(0))
      t <- expr(exprs.get(1))
      e <- expr(exprs.get(2))
    yield Expr.If(c, t, e, sp(ctx))

  private def buildLet(ctx: LetExprContext): BuildResult[Expr] =
    val exprs = ctx.expr
    for
      v <- expr(exprs.get(0))
      b <- expr(exprs.get(1))
    yield Expr.Let(ctx.lowerIdent.getText, v, b, sp(ctx))

  private def buildLambda(ctx: LambdaExprContext): BuildResult[Expr] =
    expr(ctx.expr).map(b => Expr.Lambda(ctx.lowerIdent.getText, b, sp(ctx)))

  private def buildConstructor(ctx: ConstructorExprContext): BuildResult[Expr] =
    ctx.fieldAssign.asScala.toList
      .traverseB(buildFieldAssign)
      .map(fs => Expr.Constructor(ctx.UPPER_IDENT.getText, fs, sp(ctx)))

  private def buildSetOrMap(ctx: SetOrMapLiteralContext): BuildResult[Expr] =
    val exprs = ctx.expr
    val span  = sp(ctx)
    if exprs.isEmpty && ctx.lowerIdent == null then Right(Expr.SetLiteral(Nil, span))
    else if ctx.PIPE != null then
      for
        dom  <- expr(exprs.get(0))
        body <- expr(exprs.get(1))
      yield Expr.SetComprehension(ctx.lowerIdent.getText, dom, body, span)
    else if !ctx.ARROW.isEmpty then
      if exprs.size % 2 != 0 then
        Left(buildErr("map literal requires key/value pairs", ctx))
      else
        val pairs: List[(ExprContext, ExprContext)] =
          (0 until exprs.size by 2).map(i => (exprs.get(i), exprs.get(i + 1))).toList
        pairs
          .traverseB: (kc, vc) =>
            for
              k <- expr(kc)
              v <- expr(vc)
            yield MapEntry(k, v, sp(kc))
          .map(entries => Expr.MapLiteral(entries, span))
    else
      exprs.asScala.toList.traverseB(expr).map(es => Expr.SetLiteral(es, span))

  private def buildSeq(ctx: SeqLiteralContext): BuildResult[Expr] =
    ctx.expr.asScala.toList.traverseB(expr).map(es => Expr.SeqLiteral(es, sp(ctx)))

  private def buildFieldAssign(ctx: FieldAssignContext): BuildResult[FieldAssign] =
    expr(ctx.expr).map(e => FieldAssign(ctx.lowerIdent.getText, e, sp(ctx)))
