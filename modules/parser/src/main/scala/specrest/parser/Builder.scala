package specrest.parser

import org.antlr.v4.runtime.ParserRuleContext
import specrest.ir.*
import specrest.parser.generated.SpecBaseVisitor
import specrest.parser.generated.SpecParser.*

import scala.jdk.CollectionConverters.*

final class BuildError(msg: String, ctx: ParserRuleContext)
    extends RuntimeException({
      val line = Option(ctx.getStart).map(_.getLine).getOrElse(0)
      val col  = Option(ctx.getStart).map(_.getCharPositionInLine).getOrElse(0)
      s"Build error at $line:$col: $msg"
    })

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

object Builder:
  def buildIR(tree: SpecFileContext): ServiceIR =
    val imports =
      tree.importDecl.asScala.map(imp => unquote(imp.STRING_LIT.getText)).toList
    val ir = new IRBuilder().buildService(tree.serviceDecl)
    ir.copy(imports = imports)

final private class IRBuilder extends SpecBaseVisitor[Expr]:

  override def defaultResult(): Expr =
    throw new RuntimeException("IRBuilder: unhandled expression node")

  private def expr(ctx: ExprContext): Expr = visit(ctx)

  private def binOp(ctx: ParserRuleContext, l: ExprContext, r: ExprContext, op: BinOp): Expr =
    Expr.BinaryOp(op, expr(l), expr(r), sp(ctx))

  private def unaryOp(ctx: ParserRuleContext, arg: ExprContext, op: UnOp): Expr =
    Expr.UnaryOp(op, expr(arg), sp(ctx))

  // ═══ Declarations ═══

  def buildService(ctx: ServiceDeclContext): ServiceIR =
    val name                                 = ctx.UPPER_IDENT.getText
    val entities                             = List.newBuilder[EntityDecl]
    val enums                                = List.newBuilder[EnumDecl]
    val typeAliases                          = List.newBuilder[TypeAliasDecl]
    var state: Option[StateDecl]             = None
    val operations                           = List.newBuilder[OperationDecl]
    val transitions                          = List.newBuilder[TransitionDecl]
    val invariants                           = List.newBuilder[InvariantDecl]
    val facts                                = List.newBuilder[FactDecl]
    val functions                            = List.newBuilder[FunctionDecl]
    val predicates                           = List.newBuilder[PredicateDecl]
    var conventions: Option[ConventionsDecl] = None

    for member <- ctx.serviceMember.asScala do
      if member.entityDecl != null then entities += buildEntity(member.entityDecl)
      else if member.enumDecl != null then enums += buildEnum(member.enumDecl)
      else if member.typeAlias != null then typeAliases += buildTypeAlias(member.typeAlias)
      else if member.stateDecl != null then
        if state.isDefined then throw new BuildError("duplicate state block", member.stateDecl)
        state = Some(buildState(member.stateDecl))
      else if member.operationDecl != null then operations += buildOperation(member.operationDecl)
      else if member.transitionDecl != null then
        transitions += buildTransition(member.transitionDecl)
      else if member.invariantDecl != null then invariants += buildInvariant(member.invariantDecl)
      else if member.factDecl != null then facts += buildFact(member.factDecl)
      else if member.functionDecl != null then functions += buildFunction(member.functionDecl)
      else if member.predicateDecl != null then predicates += buildPredicate(member.predicateDecl)
      else if member.conventionBlock != null then
        if conventions.isDefined then
          throw new BuildError("duplicate conventions block", member.conventionBlock)
        conventions = Some(buildConventions(member.conventionBlock))

    ServiceIR(
      name = name,
      imports = Nil,
      entities = entities.result(),
      enums = enums.result(),
      typeAliases = typeAliases.result(),
      state = state,
      operations = operations.result(),
      transitions = transitions.result(),
      invariants = invariants.result(),
      facts = facts.result(),
      functions = functions.result(),
      predicates = predicates.result(),
      conventions = conventions,
      span = sp(ctx)
    )

  private def buildEntity(ctx: EntityDeclContext): EntityDecl =
    val idents     = ctx.UPPER_IDENT
    val name       = idents.get(0).getText
    val extendsOpt = if ctx.EXTENDS != null then Some(idents.get(1).getText) else None
    val fields     = List.newBuilder[FieldDecl]
    val invariants = List.newBuilder[Expr]
    for member <- ctx.entityMember.asScala do
      if member.fieldDecl != null then fields += buildField(member.fieldDecl)
      else if member.entityInvariant != null then
        invariants += expr(member.entityInvariant.expr)
    EntityDecl(name, extendsOpt, fields.result(), invariants.result(), sp(ctx))

  private def buildField(ctx: FieldDeclContext): FieldDecl =
    val name       = ctx.lowerIdent.getText
    val typeExprV  = buildTypeExpr(ctx.typeExpr)
    val constraint = if ctx.WHERE != null then Some(expr(ctx.expr)) else None
    FieldDecl(name, typeExprV, constraint, sp(ctx))

  private def buildEnum(ctx: EnumDeclContext): EnumDecl =
    val name   = ctx.UPPER_IDENT.getText
    val values = ctx.enumValue.asScala.map(_.UPPER_IDENT.getText).toList
    EnumDecl(name, values, sp(ctx))

  private def buildTypeAlias(ctx: TypeAliasContext): TypeAliasDecl =
    val name       = ctx.UPPER_IDENT.getText
    val typeExprV  = buildTypeExpr(ctx.typeExpr)
    val constraint = if ctx.WHERE != null then Some(expr(ctx.expr)) else None
    TypeAliasDecl(name, typeExprV, constraint, sp(ctx))

  private def buildState(ctx: StateDeclContext): StateDecl =
    val fields = ctx.stateField.asScala.map(buildStateField).toList
    StateDecl(fields, sp(ctx))

  private def buildStateField(ctx: StateFieldContext): StateFieldDecl =
    StateFieldDecl(ctx.lowerIdent.getText, buildTypeExpr(ctx.typeExpr), sp(ctx))

  private def buildOperation(ctx: OperationDeclContext): OperationDecl =
    val name     = ctx.UPPER_IDENT.getText
    val inputs   = List.newBuilder[ParamDecl]
    val outputs  = List.newBuilder[ParamDecl]
    val requires = List.newBuilder[Expr]
    val ensures  = List.newBuilder[Expr]
    for clause <- ctx.operationClause.asScala do
      if clause.inputClause != null then
        for p <- clause.inputClause.paramList.param.asScala do inputs += buildParam(p)
      else if clause.outputClause != null then
        for p <- clause.outputClause.paramList.param.asScala do outputs += buildParam(p)
      else if clause.requiresClause != null then
        for e <- clause.requiresClause.expr.asScala do requires += expr(e)
      else if clause.ensuresClause != null then
        for e <- clause.ensuresClause.expr.asScala do ensures += expr(e)
    OperationDecl(
      name,
      inputs.result(),
      outputs.result(),
      requires.result(),
      ensures.result(),
      sp(ctx)
    )

  private def buildParam(ctx: ParamContext): ParamDecl =
    ParamDecl(ctx.lowerIdent.getText, buildTypeExpr(ctx.typeExpr), sp(ctx))

  private def buildTransition(ctx: TransitionDeclContext): TransitionDecl =
    val idents     = ctx.UPPER_IDENT
    val name       = idents.get(0).getText
    val entityName = idents.get(1).getText
    val fieldName  = ctx.lowerIdent.getText
    val rules      = ctx.transitionRule.asScala.map(buildTransitionRule).toList
    TransitionDecl(name, entityName, fieldName, rules, sp(ctx))

  private def buildTransitionRule(ctx: TransitionRuleContext): TransitionRule =
    val idents = ctx.UPPER_IDENT
    val from   = idents.get(0).getText
    val to     = idents.get(1).getText
    val via    = idents.get(2).getText
    val guard  = if ctx.WHEN != null then Some(expr(ctx.expr)) else None
    TransitionRule(from, to, via, guard, sp(ctx))

  private def buildInvariant(ctx: InvariantDeclContext): InvariantDecl =
    val name = Option(ctx.lowerIdent).map(_.getText)
    InvariantDecl(name, expr(ctx.expr), sp(ctx))

  private def buildFact(ctx: FactDeclContext): FactDecl =
    val name = Option(ctx.lowerIdent).map(_.getText)
    FactDecl(name, expr(ctx.expr), sp(ctx))

  private def buildFunction(ctx: FunctionDeclContext): FunctionDecl =
    val name       = ctx.lowerIdent.getText
    val params     = Option(ctx.paramList).map(_.param.asScala.map(buildParam).toList).getOrElse(Nil)
    val returnType = buildTypeExpr(ctx.typeExpr)
    val body       = expr(ctx.expr)
    FunctionDecl(name, params, returnType, body, sp(ctx))

  private def buildPredicate(ctx: PredicateDeclContext): PredicateDecl =
    val name   = ctx.lowerIdent.getText
    val params = Option(ctx.paramList).map(_.param.asScala.map(buildParam).toList).getOrElse(Nil)
    val body   = expr(ctx.expr)
    PredicateDecl(name, params, body, sp(ctx))

  private def buildConventions(ctx: ConventionBlockContext): ConventionsDecl =
    val rules = ctx.conventionRule.asScala.map(buildConventionRule).toList
    ConventionsDecl(rules, sp(ctx))

  private def buildConventionRule(ctx: ConventionRuleContext): ConventionRule =
    val target    = ctx.UPPER_IDENT.getText
    val property  = ctx.lowerIdent.getText
    val qualifier = Option(ctx.STRING_LIT).map(s => unquote(s.getText))
    val value     = expr(ctx.expr)
    ConventionRule(target, property, qualifier, value, sp(ctx))

  // ═══ Type expressions ═══

  private def buildTypeExpr(ctx: TypeExprContext): TypeExpr =
    val baseTypes = ctx.baseType
    if ctx.ARROW != null then
      val fromType = buildBaseType(baseTypes.get(0))
      val multiplicity = Option(ctx.multiplicity) match
        case None => Multiplicity.One
        case Some(m) =>
          m.getText match
            case "one"  => Multiplicity.One
            case "lone" => Multiplicity.Lone
            case "some" => Multiplicity.Some
            case "set"  => Multiplicity.Set
            case other  => throw new BuildError(s"unknown multiplicity: $other", m)
      val toType = buildBaseType(baseTypes.get(1))
      TypeExpr.RelationType(fromType, multiplicity, toType, sp(ctx))
    else buildBaseType(baseTypes.get(0))

  private def buildBaseType(ctx: BaseTypeContext): TypeExpr =
    if ctx.primitiveType != null then
      TypeExpr.NamedType(ctx.primitiveType.getText, sp(ctx))
    else if ctx.SET != null then
      TypeExpr.SetType(buildTypeExpr(ctx.typeExpr(0)), sp(ctx))
    else if ctx.MAP != null then
      TypeExpr.MapType(buildTypeExpr(ctx.typeExpr(0)), buildTypeExpr(ctx.typeExpr(1)), sp(ctx))
    else if ctx.SEQ != null then
      TypeExpr.SeqType(buildTypeExpr(ctx.typeExpr(0)), sp(ctx))
    else if ctx.OPTION != null then
      TypeExpr.OptionType(buildTypeExpr(ctx.typeExpr(0)), sp(ctx))
    else TypeExpr.NamedType(ctx.UPPER_IDENT.getText, sp(ctx))

  // ═══ Expression visitor overrides ═══

  override def visitMulExpr(ctx: MulExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Mul)
  override def visitDivExpr(ctx: DivExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Div)
  override def visitAddExpr(ctx: AddExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Add)
  override def visitSubExpr(ctx: SubExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Sub)
  override def visitUnionExpr(ctx: UnionExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Union)
  override def visitIntersectExpr(ctx: IntersectExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Intersect)
  override def visitMinusExpr(ctx: MinusExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Diff)
  override def visitEqExpr(ctx: EqExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Eq)
  override def visitNeqExpr(ctx: NeqExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Neq)
  override def visitLtExpr(ctx: LtExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Lt)
  override def visitGtExpr(ctx: GtExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Gt)
  override def visitLteExpr(ctx: LteExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Le)
  override def visitGteExpr(ctx: GteExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Ge)
  override def visitInExpr(ctx: InExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.In)
  override def visitNotInExpr(ctx: NotInExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.NotIn)
  override def visitSubsetExpr(ctx: SubsetExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Subset)
  override def visitImpliesExpr(ctx: ImpliesExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Implies)
  override def visitIffExpr(ctx: IffExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Iff)
  override def visitAndExpr(ctx: AndExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.And)
  override def visitOrExpr(ctx: OrExprContext): Expr =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BinOp.Or)

  override def visitCardinalityExpr(ctx: CardinalityExprContext): Expr =
    unaryOp(ctx, ctx.expr, UnOp.Cardinality)
  override def visitNegExpr(ctx: NegExprContext): Expr     = unaryOp(ctx, ctx.expr, UnOp.Negate)
  override def visitPowerExpr(ctx: PowerExprContext): Expr = unaryOp(ctx, ctx.expr, UnOp.Power)
  override def visitNotExpr(ctx: NotExprContext): Expr     = unaryOp(ctx, ctx.expr, UnOp.Not)

  override def visitPrimeExpr(ctx: PrimeExprContext): Expr =
    Expr.Prime(expr(ctx.expr), sp(ctx))

  override def visitFieldAccessExpr(ctx: FieldAccessExprContext): Expr =
    Expr.FieldAccess(expr(ctx.expr), ctx.lowerIdent.getText, sp(ctx))

  override def visitEnumAccessExpr(ctx: EnumAccessExprContext): Expr =
    Expr.EnumAccess(expr(ctx.expr), ctx.UPPER_IDENT.getText, sp(ctx))

  override def visitIndexExpr(ctx: IndexExprContext): Expr =
    Expr.Index(expr(ctx.expr(0)), expr(ctx.expr(1)), sp(ctx))

  override def visitCallExpr(ctx: CallExprContext): Expr =
    val args = Option(ctx.argList).map(_.expr.asScala.map(expr).toList).getOrElse(Nil)
    Expr.Call(expr(ctx.expr), args, sp(ctx))

  override def visitWithExpr(ctx: WithExprContext): Expr =
    val updates = ctx.fieldAssign.asScala.map(buildFieldAssign).toList
    Expr.With(expr(ctx.expr), updates, sp(ctx))

  override def visitMatchesExpr(ctx: MatchesExprContext): Expr =
    Expr.Matches(expr(ctx.expr), unslashRegex(ctx.REGEX_LIT.getText), sp(ctx))

  override def visitParenExpr(ctx: ParenExprContext): Expr = expr(ctx.expr)
  override def visitQuantExpr(ctx: QuantExprContext): Expr = buildQuantifier(ctx.quantifierExpr)
  override def visitSomeWrapE(ctx: SomeWrapEContext): Expr = buildSomeWrap(ctx.someWrapExpr)
  override def visitTheE(ctx: TheEContext): Expr           = buildThe(ctx.theExpr)
  override def visitIfE(ctx: IfEContext): Expr             = buildIf(ctx.ifExpr)
  override def visitLetE(ctx: LetEContext): Expr           = buildLet(ctx.letExpr)
  override def visitLambdaE(ctx: LambdaEContext): Expr     = buildLambda(ctx.lambdaExpr)
  override def visitConstructorE(ctx: ConstructorEContext): Expr =
    buildConstructor(ctx.constructorExpr)
  override def visitSetOrMapE(ctx: SetOrMapEContext): Expr = buildSetOrMap(ctx.setOrMapLiteral)
  override def visitSeqE(ctx: SeqEContext): Expr           = buildSeq(ctx.seqLiteral)

  override def visitPreExpr(ctx: PreExprContext): Expr =
    Expr.Pre(expr(ctx.expr), sp(ctx))

  override def visitIntLitExpr(ctx: IntLitExprContext): Expr =
    Expr.IntLit(ctx.INT_LIT.getText.toLong, sp(ctx))

  override def visitFloatLitExpr(ctx: FloatLitExprContext): Expr =
    Expr.FloatLit(ctx.FLOAT_LIT.getText.toDouble, sp(ctx))

  override def visitStringLitExpr(ctx: StringLitExprContext): Expr =
    Expr.StringLit(unquote(ctx.STRING_LIT.getText), sp(ctx))

  override def visitTrueLitExpr(ctx: TrueLitExprContext): Expr   = Expr.BoolLit(true, sp(ctx))
  override def visitFalseLitExpr(ctx: FalseLitExprContext): Expr = Expr.BoolLit(false, sp(ctx))
  override def visitNoneLitExpr(ctx: NoneLitExprContext): Expr   = Expr.NoneLit(sp(ctx))

  override def visitUpperIdentExpr(ctx: UpperIdentExprContext): Expr =
    Expr.Identifier(ctx.UPPER_IDENT.getText, sp(ctx))

  override def visitLowerIdentExpr(ctx: LowerIdentExprContext): Expr =
    Expr.Identifier(ctx.lowerIdent.getText, sp(ctx))

  // ═══ Expression sub-rules ═══

  private def buildQuantifier(ctx: QuantifierExprContext): Expr =
    val qCtx = ctx.quantifier
    val quantifier: QuantKind =
      if qCtx.ALL != null then QuantKind.All
      else if qCtx.SOME != null then QuantKind.Some
      else if qCtx.NO != null then QuantKind.No
      else QuantKind.Exists
    val bindings = ctx.quantBinding.asScala.map: b =>
      val bk = if b.IN != null then BindingKind.In else BindingKind.Colon
      QuantifierBinding(b.lowerIdent.getText, expr(b.expr), bk, sp(b))
    .toList
    Expr.Quantifier(quantifier, bindings, expr(ctx.expr), sp(ctx))

  private def buildSomeWrap(ctx: SomeWrapExprContext): Expr =
    Expr.SomeWrap(expr(ctx.expr), sp(ctx))

  private def buildThe(ctx: TheExprContext): Expr =
    val exprs = ctx.expr
    Expr.The(ctx.lowerIdent.getText, expr(exprs.get(0)), expr(exprs.get(1)), sp(ctx))

  private def buildIf(ctx: IfExprContext): Expr =
    val exprs = ctx.expr
    Expr.If(expr(exprs.get(0)), expr(exprs.get(1)), expr(exprs.get(2)), sp(ctx))

  private def buildLet(ctx: LetExprContext): Expr =
    val exprs = ctx.expr
    Expr.Let(ctx.lowerIdent.getText, expr(exprs.get(0)), expr(exprs.get(1)), sp(ctx))

  private def buildLambda(ctx: LambdaExprContext): Expr =
    Expr.Lambda(ctx.lowerIdent.getText, expr(ctx.expr), sp(ctx))

  private def buildConstructor(ctx: ConstructorExprContext): Expr =
    val fields = ctx.fieldAssign.asScala.map(buildFieldAssign).toList
    Expr.Constructor(ctx.UPPER_IDENT.getText, fields, sp(ctx))

  private def buildSetOrMap(ctx: SetOrMapLiteralContext): Expr =
    val exprs = ctx.expr
    val span  = sp(ctx)
    if exprs.isEmpty && ctx.lowerIdent == null then Expr.SetLiteral(Nil, span)
    else if ctx.PIPE != null then
      Expr.SetComprehension(
        ctx.lowerIdent.getText,
        expr(exprs.get(0)),
        expr(exprs.get(1)),
        span
      )
    else if !ctx.ARROW.isEmpty then
      if exprs.size % 2 != 0 then
        throw new BuildError("map literal requires key/value pairs", ctx)
      val entries = List.newBuilder[MapEntry]
      var i       = 0
      while i < exprs.size do
        entries += MapEntry(expr(exprs.get(i)), expr(exprs.get(i + 1)), sp(exprs.get(i)))
        i += 2
      Expr.MapLiteral(entries.result(), span)
    else Expr.SetLiteral(exprs.asScala.map(expr).toList, span)

  private def buildSeq(ctx: SeqLiteralContext): Expr =
    Expr.SeqLiteral(ctx.expr.asScala.map(expr).toList, sp(ctx))

  private def buildFieldAssign(ctx: FieldAssignContext): FieldAssign =
    FieldAssign(ctx.lowerIdent.getText, expr(ctx.expr), sp(ctx))
