package specrest.parser

import cats.effect.IO
import org.antlr.v4.runtime.ParserRuleContext
import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.generated.SpecBaseVisitor
import specrest.parser.generated.SpecParser.*

import scala.jdk.CollectionConverters.*

private type BuildResult[A] = Either[VerifyError.Build, A]

private def spanFrom(ctx: ParserRuleContext): SpanT =
  val start = ctx.getStart
  val stop  = Option(ctx.getStop).getOrElse(start)
  SpanT(
    int_of_integer(BigInt(start.getLine)),
    int_of_integer(BigInt(start.getCharPositionInLine)),
    int_of_integer(BigInt(stop.getLine)),
    int_of_integer(
      BigInt(stop.getCharPositionInLine + Option(stop.getText).map(_.length).getOrElse(1))
    )
  )

private def sp(ctx: ParserRuleContext): Option[SpanT] = Some(spanFrom(ctx))

private def buildErr(msg: String, ctx: ParserRuleContext): VerifyError.Build =
  VerifyError.Build(msg, sp(ctx))

@SuppressWarnings(Array("org.wartremover.warts.Var"))
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
  private[parser] def buildIRCore(
      tree: SpecFileContext,
      mergePreamble: Boolean
  ): Either[VerifyError.Build, ServiceIRFull] =
    val imports = tree.importDecl.asScala.map(imp => unquote(imp.STRING_LIT.getText)).toList
    val raw     = new IRBuilder().buildService(tree.serviceDecl).map(_.copy(b = imports))
    if mergePreamble then raw.map(mergeWithPreamble) else raw

  private def mergeWithPreamble(ir: ServiceIRFull): ServiceIRFull =
    val userNames = ir.m.map { case PredicateDeclFull(n, _, _, _) => n }.toSet
    val toAdd =
      Preamble.predicates.filterNot { case PredicateDeclFull(n, _, _, _) => userNames.contains(n) }
    if toAdd.isEmpty then ir
    else ir.copy(m = ir.m ++ toAdd)

  def buildIR(tree: SpecFileContext): IO[Either[VerifyError.Build, ServiceIRFull]] =
    IO.delay(buildIRCore(tree, mergePreamble = true))

final private case class ServiceAcc(
    c: List[EntityDeclFull] = Nil,
    d: List[EnumDeclFull] = Nil,
    e: List[TypeAliasDeclFull] = Nil,
    state: Option[StateDeclFull] = None,
    g: List[OperationDeclFull] = Nil,
    h: List[TransitionDeclFull] = Nil,
    invariants: List[InvariantDeclFull] = Nil,
    j: List[TemporalDeclFull] = Nil,
    k: List[FactDeclFull] = Nil,
    l: List[FunctionDeclFull] = Nil,
    m: List[PredicateDeclFull] = Nil,
    n: Option[ConventionsDeclFull] = None
)

@SuppressWarnings(Array("org.wartremover.warts.Null"))
final private class IRBuilder extends SpecBaseVisitor[BuildResult[expr_full]]:

  override def defaultResult(): BuildResult[expr_full] =
    Left(VerifyError.Build("IRBuilder: unhandled expression node", None))

  private def expr(ctx: ExprContext): BuildResult[expr_full] = visit(ctx)

  private def binOp(
      ctx: ParserRuleContext,
      l: ExprContext,
      r: ExprContext,
      op: bin_op_full
  ): BuildResult[expr_full] =
    for
      lE <- expr(l)
      rE <- expr(r)
    yield BinaryOpF(op, lE, rE, sp(ctx))

  private def unaryOp(
      ctx: ParserRuleContext,
      arg: ExprContext,
      op: un_op_full
  ): BuildResult[expr_full] =
    expr(arg).map(a => UnaryOpF(op, a, sp(ctx)))

  def buildService(ctx: ServiceDeclContext): BuildResult[ServiceIRFull] =
    val name = ctx.UPPER_IDENT.getText
    val finalAcc = ctx.serviceMember.asScala.toList
      .foldLeft[BuildResult[ServiceAcc]](Right(ServiceAcc())):
        case (accE, member) => accE.flatMap(acc => processMember(acc, member))
    finalAcc.map: acc =>
      ServiceIRFull(
        a = name,
        b = Nil,
        c = acc.c.reverse,
        d = acc.d.reverse,
        e = acc.e.reverse,
        f = acc.state,
        g = acc.g.reverse,
        h = acc.h.reverse,
        i = acc.invariants.reverse,
        j = acc.j.reverse,
        k = acc.k.reverse,
        l = acc.l.reverse,
        m = acc.m.reverse,
        n = acc.n,
        o = sp(ctx)
      )

  private def processMember(acc: ServiceAcc, m: ServiceMemberContext): BuildResult[ServiceAcc] =
    if m.entityDecl ne null then
      buildEntity(m.entityDecl).map(e => acc.copy(c = e :: acc.c))
    else if m.enumDecl ne null then
      Right(acc.copy(d = buildEnum(m.enumDecl) :: acc.d))
    else if m.typeAlias ne null then
      buildTypeAlias(m.typeAlias).map(t => acc.copy(e = t :: acc.e))
    else if m.stateDecl ne null then
      if acc.state.isDefined then Left(buildErr("duplicate state block", m.stateDecl))
      else buildState(m.stateDecl).map(s => acc.copy(state = Some(s)))
    else if m.operationDecl ne null then
      buildOperation(m.operationDecl).map(o => acc.copy(g = o :: acc.g))
    else if m.transitionDecl ne null then
      buildTransition(m.transitionDecl).map(t => acc.copy(h = t :: acc.h))
    else if m.invariantDecl ne null then
      buildInvariant(m.invariantDecl).map(i => acc.copy(invariants = i :: acc.invariants))
    else if m.temporalDecl ne null then
      buildTemporal(m.temporalDecl).map(t => acc.copy(j = t :: acc.j))
    else if m.factDecl ne null then
      buildFact(m.factDecl).map(f => acc.copy(k = f :: acc.k))
    else if m.functionDecl ne null then
      buildFunction(m.functionDecl).map(f => acc.copy(l = f :: acc.l))
    else if m.predicateDecl ne null then
      buildPredicate(m.predicateDecl).map(p => acc.copy(m = p :: acc.m))
    else if m.conventionBlock ne null then
      if acc.n.isDefined then
        Left(buildErr("duplicate conventions block", m.conventionBlock))
      else buildConventions(m.conventionBlock).map(c => acc.copy(n = Some(c)))
    else Right(acc)

  private def buildEntity(ctx: EntityDeclContext): BuildResult[EntityDeclFull] =
    val idents     = ctx.UPPER_IDENT
    val name       = idents.get(0).getText
    val extendsOpt = if ctx.EXTENDS ne null then Some(idents.get(1).getText) else None
    val members    = ctx.entityMember.asScala.toList
    val parts =
      members.foldLeft[BuildResult[(List[FieldDeclFull], List[expr_full])]](Right((Nil, Nil))):
        case (accE, member) =>
          accE.flatMap: (fs, invs) =>
            if member.fieldDecl ne null then
              buildField(member.fieldDecl).map(f => (f :: fs, invs))
            else if member.entityInvariant ne null then
              expr(member.entityInvariant.expr).map(e => (fs, e :: invs))
            else Right((fs, invs))
    parts.map: (fs, invs) =>
      EntityDeclFull(name, extendsOpt, fs.reverse, invs.reverse, sp(ctx))

  private def buildField(ctx: FieldDeclContext): BuildResult[FieldDeclFull] =
    val name      = ctx.lowerIdent.getText
    val typeExprV = buildTypeExpr(ctx.typeExpr)
    val constraintE: BuildResult[Option[expr_full]] =
      if ctx.WHERE ne null then expr(ctx.expr).map(Some(_)) else Right(None)
    for
      t <- typeExprV
      c <- constraintE
    yield FieldDeclFull(name, t, c, sp(ctx))

  private def buildEnum(ctx: EnumDeclContext): EnumDeclFull =
    val name   = ctx.UPPER_IDENT.getText
    val values = ctx.enumValue.asScala.map(_.UPPER_IDENT.getText).toList
    EnumDeclFull(name, values, sp(ctx))

  private def buildTypeAlias(ctx: TypeAliasContext): BuildResult[TypeAliasDeclFull] =
    val name      = ctx.UPPER_IDENT.getText
    val typeExprV = buildTypeExpr(ctx.typeExpr)
    val constraintE: BuildResult[Option[expr_full]] =
      if ctx.WHERE ne null then expr(ctx.expr).map(Some(_)) else Right(None)
    for
      t <- typeExprV
      c <- constraintE
    yield TypeAliasDeclFull(name, t, c, sp(ctx))

  private def buildState(ctx: StateDeclContext): BuildResult[StateDeclFull] =
    ctx.stateField.asScala.toList
      .traverseB(buildStateField)
      .map(fields => StateDeclFull(fields, sp(ctx)))

  private def buildStateField(ctx: StateFieldContext): BuildResult[StateFieldDeclFull] =
    buildTypeExpr(ctx.typeExpr).map(t => StateFieldDeclFull(ctx.lowerIdent.getText, t, sp(ctx)))

  private def buildOperation(ctx: OperationDeclContext): BuildResult[OperationDeclFull] =
    val name    = ctx.UPPER_IDENT.getText
    val clauses = ctx.operationClause.asScala.toList
    val acc0: BuildResult[(
        List[ParamDeclFull],
        List[ParamDeclFull],
        List[expr_full],
        List[expr_full]
    )] =
      Right((Nil, Nil, Nil, Nil))
    val collected = clauses.foldLeft(acc0):
      case (accE, clause) =>
        accE.flatMap: (ins, outs, reqs, ens) =>
          if clause.inputClause ne null then
            clause.inputClause.paramList.param.asScala.toList
              .traverseB(buildParam)
              .map(ps => (ins ++ ps, outs, reqs, ens))
          else if clause.outputClause ne null then
            clause.outputClause.paramList.param.asScala.toList
              .traverseB(buildParam)
              .map(ps => (ins, outs ++ ps, reqs, ens))
          else if clause.requiresClause ne null then
            clause.requiresClause.expr.asScala.toList
              .traverseB(expr)
              .map(es => (ins, outs, reqs ++ es, ens))
          else if clause.ensuresClause ne null then
            clause.ensuresClause.expr.asScala.toList
              .traverseB(expr)
              .map(es => (ins, outs, reqs, ens ++ es))
          else Right((ins, outs, reqs, ens))
    collected.map: (ins, outs, reqs, ens) =>
      OperationDeclFull(name, ins, outs, reqs, ens, sp(ctx))

  private def buildParam(ctx: ParamContext): BuildResult[ParamDeclFull] =
    buildTypeExpr(ctx.typeExpr).map(t => ParamDeclFull(ctx.lowerIdent.getText, t, sp(ctx)))

  private def buildTransition(ctx: TransitionDeclContext): BuildResult[TransitionDeclFull] =
    val idents     = ctx.UPPER_IDENT
    val name       = idents.get(0).getText
    val entityName = idents.get(1).getText
    val fieldName  = ctx.lowerIdent.getText
    ctx.transitionRule.asScala.toList
      .traverseB(buildTransitionRule)
      .map(rules => TransitionDeclFull(name, entityName, fieldName, rules, sp(ctx)))

  private def buildTransitionRule(ctx: TransitionRuleContext): BuildResult[TransitionRuleFull] =
    val idents = ctx.UPPER_IDENT
    val from   = idents.get(0).getText
    val to     = idents.get(1).getText
    val via    = idents.get(2).getText
    val guardE: BuildResult[Option[expr_full]] =
      if ctx.WHEN ne null then expr(ctx.expr).map(Some(_)) else Right(None)
    guardE.map(g => TransitionRuleFull(from, to, via, g, sp(ctx)))

  private def buildInvariant(ctx: InvariantDeclContext): BuildResult[InvariantDeclFull] =
    val name = Option(ctx.lowerIdent).map(_.getText)
    expr(ctx.expr).map(e => InvariantDeclFull(name, e, sp(ctx)))

  private def buildTemporal(ctx: TemporalDeclContext): BuildResult[TemporalDeclFull] =
    val name = ctx.lowerIdent.getText
    expr(ctx.expr).map(e => TemporalDeclFull(name, e, sp(ctx)))

  private def buildFact(ctx: FactDeclContext): BuildResult[FactDeclFull] =
    val name = Option(ctx.lowerIdent).map(_.getText)
    expr(ctx.expr).map(e => FactDeclFull(name, e, sp(ctx)))

  private def buildFunction(ctx: FunctionDeclContext): BuildResult[FunctionDeclFull] =
    val name       = ctx.lowerIdent.getText
    val returnType = buildTypeExpr(ctx.typeExpr)
    val paramsE: BuildResult[List[ParamDeclFull]] =
      Option(ctx.paramList).map(_.param.asScala.toList.traverseB(buildParam)).getOrElse(Right(Nil))
    for
      ps <- paramsE
      rt <- returnType
      b  <- expr(ctx.expr)
    yield FunctionDeclFull(name, ps, rt, b, sp(ctx))

  private def buildPredicate(ctx: PredicateDeclContext): BuildResult[PredicateDeclFull] =
    val name = ctx.lowerIdent.getText
    val paramsE: BuildResult[List[ParamDeclFull]] =
      Option(ctx.paramList).map(_.param.asScala.toList.traverseB(buildParam)).getOrElse(Right(Nil))
    for
      ps <- paramsE
      b  <- expr(ctx.expr)
    yield PredicateDeclFull(name, ps, b, sp(ctx))

  private def buildConventions(ctx: ConventionBlockContext): BuildResult[ConventionsDeclFull] =
    ctx.conventionRule.asScala.toList
      .traverseB(buildConventionRule)
      .map(rules => ConventionsDeclFull(rules, sp(ctx)))

  private def buildConventionRule(ctx: ConventionRuleContext): BuildResult[ConventionRuleFull] =
    val target          = ctx.UPPER_IDENT.getText
    val idents          = ctx.lowerIdent.asScala.toList
    val stringQualifier = Option(ctx.STRING_LIT).map(s => unquote(s.getText))
    val resolved = idents match
      case List(p) => Right((stringQualifier, p.getText))
      case List(q, p) =>
        if stringQualifier.isDefined then
          Left(buildErr(
            s"convention rule '$target.${q.getText}.${p.getText}' cannot combine a dotted qualifier with a string qualifier",
            ctx
          ))
        else Right((Some(q.getText), p.getText))
      case _ => Left(buildErr("malformed convention rule", ctx))
    for
      qp       <- resolved
      (qual, p) = qp
      v        <- expr(ctx.expr)
    yield ConventionRuleFull(target, p, qual, v, sp(ctx))

  private def buildTypeExpr(ctx: TypeExprContext): BuildResult[type_expr_full] =
    val baseTypes = ctx.baseType
    if ctx.ARROW ne null then
      for
        fromType <- buildBaseType(baseTypes.get(0))
        toType   <- buildBaseType(baseTypes.get(1))
        mult <- Option(ctx.multiplicity) match
                  case None => Right(MultOne())
                  case Some(m) =>
                    m.getText match
                      case "one"  => Right(MultOne())
                      case "lone" => Right(MultLone())
                      case "some" => Right(MultSome())
                      case "set"  => Right(MultSet())
                      case other  => Left(buildErr(s"unknown multiplicity: $other", m))
      yield RelationTypeF(fromType, mult, toType, sp(ctx))
    else buildBaseType(baseTypes.get(0))

  private def buildBaseType(ctx: BaseTypeContext): BuildResult[type_expr_full] =
    if ctx.primitiveType ne null then
      Right(NamedTypeF(ctx.primitiveType.getText, sp(ctx)))
    else if ctx.SET ne null then
      buildTypeExpr(ctx.typeExpr(0)).map(t => SetTypeF(t, sp(ctx)))
    else if ctx.MAP ne null then
      for
        k <- buildTypeExpr(ctx.typeExpr(0))
        v <- buildTypeExpr(ctx.typeExpr(1))
      yield MapTypeF(k, v, sp(ctx))
    else if ctx.SEQ ne null then
      buildTypeExpr(ctx.typeExpr(0)).map(t => SeqTypeF(t, sp(ctx)))
    else if ctx.OPTION ne null then
      buildTypeExpr(ctx.typeExpr(0)).map(t => OptionTypeF(t, sp(ctx)))
    else Right(NamedTypeF(ctx.UPPER_IDENT.getText, sp(ctx)))

  override def visitMulExpr(ctx: MulExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BMul())
  override def visitDivExpr(ctx: DivExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BDiv())
  override def visitAddExpr(ctx: AddExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BAdd())
  override def visitSubExpr(ctx: SubExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BSub())
  override def visitUnionExpr(ctx: UnionExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BUnion())
  override def visitIntersectExpr(ctx: IntersectExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BIntersect())
  override def visitMinusExpr(ctx: MinusExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BDiff())
  override def visitEqExpr(ctx: EqExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BEq())
  override def visitNeqExpr(ctx: NeqExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BNeq())
  override def visitLtExpr(ctx: LtExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BLt())
  override def visitGtExpr(ctx: GtExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BGt())
  override def visitLteExpr(ctx: LteExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BLe())
  override def visitGteExpr(ctx: GteExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BGe())
  override def visitInExpr(ctx: InExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BIn())
  override def visitNotInExpr(ctx: NotInExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BNotIn())
  override def visitSubsetExpr(ctx: SubsetExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BSubset())
  override def visitImpliesExpr(ctx: ImpliesExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BImplies())
  override def visitIffExpr(ctx: IffExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BIff())
  override def visitAndExpr(ctx: AndExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BAnd())
  override def visitOrExpr(ctx: OrExprContext): BuildResult[expr_full] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BOr())

  override def visitCardinalityExpr(ctx: CardinalityExprContext): BuildResult[expr_full] =
    unaryOp(ctx, ctx.expr, UCardinality())
  override def visitNegExpr(ctx: NegExprContext): BuildResult[expr_full] =
    unaryOp(ctx, ctx.expr, UNegate())
  override def visitPowerExpr(ctx: PowerExprContext): BuildResult[expr_full] =
    unaryOp(ctx, ctx.expr, UPower())
  override def visitNotExpr(ctx: NotExprContext): BuildResult[expr_full] =
    unaryOp(ctx, ctx.expr, UNot())

  override def visitPrimeExpr(ctx: PrimeExprContext): BuildResult[expr_full] =
    expr(ctx.expr).map(e => PrimeF(e, sp(ctx)))

  override def visitFieldAccessExpr(ctx: FieldAccessExprContext): BuildResult[expr_full] =
    expr(ctx.expr).map(e => FieldAccessF(e, ctx.lowerIdent.getText, sp(ctx)))

  override def visitEnumAccessExpr(ctx: EnumAccessExprContext): BuildResult[expr_full] =
    expr(ctx.expr).map(e => EnumAccessF(e, ctx.UPPER_IDENT.getText, sp(ctx)))

  override def visitIndexExpr(ctx: IndexExprContext): BuildResult[expr_full] =
    for
      a <- expr(ctx.expr(0))
      b <- expr(ctx.expr(1))
    yield IndexF(a, b, sp(ctx))

  override def visitCallExpr(ctx: CallExprContext): BuildResult[expr_full] =
    val argsE: BuildResult[List[expr_full]] =
      Option(ctx.argList).map(_.expr.asScala.toList.traverseB(expr)).getOrElse(Right(Nil))
    for
      fn   <- expr(ctx.expr)
      args <- argsE
    yield CallF(fn, args, sp(ctx))

  override def visitWithExpr(ctx: WithExprContext): BuildResult[expr_full] =
    for
      base    <- expr(ctx.expr)
      updates <- ctx.fieldAssign.asScala.toList.traverseB(buildFieldAssign)
    yield WithF(base, updates, sp(ctx))

  override def visitMatchesExpr(ctx: MatchesExprContext): BuildResult[expr_full] =
    expr(ctx.expr).map(e => MatchesF(e, unslashRegex(ctx.REGEX_LIT.getText), sp(ctx)))

  override def visitParenExpr(ctx: ParenExprContext): BuildResult[expr_full] = expr(ctx.expr)
  override def visitQuantExpr(ctx: QuantExprContext): BuildResult[expr_full] =
    buildQuantifier(ctx.quantifierExpr)
  override def visitSomeWrapE(ctx: SomeWrapEContext): BuildResult[expr_full] =
    buildSomeWrap(ctx.someWrapExpr)
  override def visitTheE(ctx: TheEContext): BuildResult[expr_full] = buildThe(ctx.theExpr)
  override def visitIfE(ctx: IfEContext): BuildResult[expr_full]   = buildIf(ctx.ifExpr)
  override def visitLetE(ctx: LetEContext): BuildResult[expr_full] = buildLet(ctx.letExpr)
  override def visitLambdaE(ctx: LambdaEContext): BuildResult[expr_full] =
    buildLambda(ctx.lambdaExpr)
  override def visitConstructorE(ctx: ConstructorEContext): BuildResult[expr_full] =
    buildConstructor(ctx.constructorExpr)
  override def visitSetOrMapE(ctx: SetOrMapEContext): BuildResult[expr_full] =
    buildSetOrMap(ctx.setOrMapLiteral)
  override def visitSeqE(ctx: SeqEContext): BuildResult[expr_full] = buildSeq(ctx.seqLiteral)

  override def visitPreExpr(ctx: PreExprContext): BuildResult[expr_full] =
    expr(ctx.expr).map(e => PreF(e, sp(ctx)))

  override def visitIntLitExpr(ctx: IntLitExprContext): BuildResult[expr_full] =
    Right(IntLitF(int_of_integer(BigInt(ctx.INT_LIT.getText.toLong)), sp(ctx)))

  override def visitFloatLitExpr(ctx: FloatLitExprContext): BuildResult[expr_full] =
    Right(FloatLitF(ctx.FLOAT_LIT.getText, sp(ctx)))

  override def visitStringLitExpr(ctx: StringLitExprContext): BuildResult[expr_full] =
    Right(StringLitF(unquote(ctx.STRING_LIT.getText), sp(ctx)))

  override def visitTrueLitExpr(ctx: TrueLitExprContext): BuildResult[expr_full] =
    Right(BoolLitF(true, sp(ctx)))
  override def visitFalseLitExpr(ctx: FalseLitExprContext): BuildResult[expr_full] =
    Right(BoolLitF(false, sp(ctx)))
  override def visitNoneLitExpr(ctx: NoneLitExprContext): BuildResult[expr_full] =
    Right(NoneLitF(sp(ctx)))

  override def visitUpperIdentExpr(ctx: UpperIdentExprContext): BuildResult[expr_full] =
    Right(IdentifierF(ctx.UPPER_IDENT.getText, sp(ctx)))

  override def visitLowerIdentExpr(ctx: LowerIdentExprContext): BuildResult[expr_full] =
    Right(IdentifierF(ctx.lowerIdent.getText, sp(ctx)))

  private def buildQuantifier(ctx: QuantifierExprContext): BuildResult[expr_full] =
    val qCtx = ctx.quantifier
    val quantifier: quant_kind_full =
      if qCtx.ALL ne null then QAll()
      else if qCtx.SOME ne null then QSome()
      else if qCtx.NO ne null then QNo()
      else QExists()
    val bindingsE: BuildResult[List[QuantifierBindingFull]] =
      ctx.quantBinding.asScala.toList.traverseB: b =>
        val bk = if b.IN ne null then BkIn() else BkColon()
        expr(b.expr).map(d => QuantifierBindingFull(b.lowerIdent.getText, d, bk, sp(b)))
    for
      bs   <- bindingsE
      body <- expr(ctx.expr)
    yield QuantifierF(quantifier, bs, body, sp(ctx))

  private def buildSomeWrap(ctx: SomeWrapExprContext): BuildResult[expr_full] =
    expr(ctx.expr).map(e => SomeWrapF(e, sp(ctx)))

  private def buildThe(ctx: TheExprContext): BuildResult[expr_full] =
    val exprs = ctx.expr
    for
      a <- expr(exprs.get(0))
      b <- expr(exprs.get(1))
    yield TheF(ctx.lowerIdent.getText, a, b, sp(ctx))

  private def buildIf(ctx: IfExprContext): BuildResult[expr_full] =
    val exprs = ctx.expr
    for
      c <- expr(exprs.get(0))
      t <- expr(exprs.get(1))
      e <- expr(exprs.get(2))
    yield IfF(c, t, e, sp(ctx))

  private def buildLet(ctx: LetExprContext): BuildResult[expr_full] =
    val exprs = ctx.expr
    for
      v <- expr(exprs.get(0))
      b <- expr(exprs.get(1))
    yield LetF(ctx.lowerIdent.getText, v, b, sp(ctx))

  private def buildLambda(ctx: LambdaExprContext): BuildResult[expr_full] =
    expr(ctx.expr).map(b => LambdaF(ctx.lowerIdent.getText, b, sp(ctx)))

  private def buildConstructor(ctx: ConstructorExprContext): BuildResult[expr_full] =
    ctx.fieldAssign.asScala.toList
      .traverseB(buildFieldAssign)
      .map(fs => ConstructorF(ctx.UPPER_IDENT.getText, fs, sp(ctx)))

  private def buildSetOrMap(ctx: SetOrMapLiteralContext): BuildResult[expr_full] =
    val exprs = ctx.expr
    val span  = sp(ctx)
    if exprs.isEmpty && (ctx.lowerIdent eq null) then Right(SetLiteralF(Nil, span))
    else if ctx.PIPE ne null then
      for
        dom  <- expr(exprs.get(0))
        body <- expr(exprs.get(1))
      yield SetComprehensionF(ctx.lowerIdent.getText, dom, body, span)
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
            yield MapEntryFull(k, v, sp(kc))
          .map(entries => MapLiteralF(entries, span))
    else
      exprs.asScala.toList.traverseB(expr).map(es => SetLiteralF(es, span))

  private def buildSeq(ctx: SeqLiteralContext): BuildResult[expr_full] =
    ctx.expr.asScala.toList.traverseB(expr).map(es => SeqLiteralF(es, sp(ctx)))

  private def buildFieldAssign(ctx: FieldAssignContext): BuildResult[FieldAssignFull] =
    expr(ctx.expr).map(e => FieldAssignFull(ctx.lowerIdent.getText, e, sp(ctx)))
