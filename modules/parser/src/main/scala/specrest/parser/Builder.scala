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
    BigInt(start.getLine),
    BigInt(start.getCharPositionInLine),
    BigInt(stop.getLine),
    BigInt(stop.getCharPositionInLine + Option(stop.getText).map(_.length).getOrElse(1))
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
    val raw =
      new IRBuilder().buildService(tree.serviceDecl).map(_.copy(b = imports)).map {
        ir => flattenInheritance(ir) match { case s: ServiceIRFull => s }
      }
    if mergePreamble then raw.map(mergeWithPreamble) else raw

  private def mergeWithPreamble(ir: ServiceIRFull): ServiceIRFull =
    val userNames = svcPredicates(ir).map(prdName).toSet
    val toAdd =
      Preamble.predicates.filterNot(p => userNames.contains(prdName(p)))
    if toAdd.isEmpty then ir
    else ir.copy(m = svcPredicates(ir) ++ toAdd)

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
    n: Option[ConventionsDeclFull] = None,
    security: List[SecuritySchemeDeclFull] = Nil
)

@SuppressWarnings(Array("org.wartremover.warts.Null"))
final private class IRBuilder extends SpecBaseVisitor[BuildResult[expr]]:

  override def defaultResult(): BuildResult[expr] =
    Left(VerifyError.Build("IRBuilder: unhandled expression node", None))

  private def expr(ctx: ExprContext): BuildResult[expr] = visit(ctx)

  private def binOp(
      ctx: ParserRuleContext,
      l: ExprContext,
      r: ExprContext,
      op: bin_op
  ): BuildResult[expr] =
    for
      lE <- expr(l)
      rE <- expr(r)
    yield BinaryOpF(op, lE, rE, sp(ctx))

  private def unaryOp(
      ctx: ParserRuleContext,
      arg: ExprContext,
      op: un_op
  ): BuildResult[expr] =
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
        o = acc.security.reverse,
        p = sp(ctx)
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
    else if m.securityBlock ne null then
      m.securityBlock.securitySchemeDecl.asScala.toList
        .traverseB(buildSecurityScheme)
        .map(ss => acc.copy(security = ss.reverse ::: acc.security))
    else Right(acc)

  private def buildEntity(ctx: EntityDeclContext): BuildResult[EntityDeclFull] =
    val idents     = ctx.UPPER_IDENT
    val name       = idents.get(0).getText
    val extendsOpt = if ctx.EXTENDS ne null then Some(idents.get(1).getText) else None
    val members    = ctx.entityMember.asScala.toList
    val parts =
      members.foldLeft[BuildResult[(List[FieldDeclFull], List[expr])]](Right((Nil, Nil))):
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
    val constraintE: BuildResult[Option[expr]] =
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
    val constraintE: BuildResult[Option[expr]] =
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
        List[expr],
        List[expr],
        Option[List[String]]
    )] =
      Right((Nil, Nil, Nil, Nil, None))
    val collected = clauses.foldLeft(acc0):
      case (accE, clause) =>
        accE.flatMap: (ins, outs, reqs, ens, auth) =>
          if clause.inputClause ne null then
            clause.inputClause.paramList.param.asScala.toList
              .traverseB(buildParam)
              .map(ps => (ins ++ ps, outs, reqs, ens, auth))
          else if clause.outputClause ne null then
            clause.outputClause.paramList.param.asScala.toList
              .traverseB(buildParam)
              .map(ps => (ins, outs ++ ps, reqs, ens, auth))
          else if clause.requiresClause ne null then
            clause.requiresClause.expr.asScala.toList
              .traverseB(expr)
              .map(es => (ins, outs, reqs ++ scopeLetsOverBlock(es), ens, auth))
          else if clause.ensuresClause ne null then
            clause.ensuresClause.expr.asScala.toList
              .traverseB(expr)
              .map(es => (ins, outs, reqs, ens ++ scopeLetsOverBlock(es), auth))
          else if clause.requiresAuthClause ne null then
            val names = clause.requiresAuthClause.lowerIdent.asScala.toList.map(_.getText)
            Right((ins, outs, reqs, ens, Some(auth.getOrElse(Nil) ++ names)))
          else Right((ins, outs, reqs, ens, auth))
    collected.map: (ins, outs, reqs, ens, auth) =>
      OperationDeclFull(name, ins, outs, reqs, ens, auth, sp(ctx))

  // A `let x = v in ...` written above newline-separated clauses reads, by its indentation, as
  // binding `x` over the whole block. The grammar (`letExpr : LET id EQ expr IN expr`) scopes the
  // let body to a single clause, so any later clause sees `x` as a free identifier. Fold the
  // following clauses into the let's innermost body so the binding spans the block as written.
  private def scopeLetsOverBlock(clauses: List[expr]): List[expr] = clauses match
    case Nil => Nil
    case (lf: LetF) :: rest if rest.nonEmpty =>
      List(extendLetBody(lf, scopeLetsOverBlock(rest)))
    case c :: rest => c :: scopeLetsOverBlock(rest)

  private def extendLetBody(lf: LetF, extra: List[expr]): LetF = lf match
    case LetF(x, v, inner: LetF, d) => LetF(x, v, extendLetBody(inner, extra), d)
    case LetF(x, v, body, d) =>
      LetF(x, v, (body :: extra).reduceRight((a, b) => BinaryOpF(BAnd(), a, b, d)), d)

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
    val guardE: BuildResult[Option[expr]] =
      if ctx.WHEN ne null then expr(ctx.expr).map(Some(_)) else Right(None)
    guardE.map(g => TransitionRuleFull(from, to, via, g, sp(ctx)))

  private def buildInvariant(ctx: InvariantDeclContext): BuildResult[InvariantDeclFull] =
    val name = Option(ctx.lowerIdent).map(_.getText)
    expr(ctx.expr).map(e => InvariantDeclFull(name, e, sp(ctx)))

  private def buildTemporal(ctx: TemporalDeclContext): BuildResult[TemporalDeclFull] =
    val name = ctx.lowerIdent.getText
    expr(ctx.expr).map(e => TemporalDeclFull(name, parseTemporalBody(e), sp(ctx)))

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

  private def buildSecurityScheme(
      ctx: SecuritySchemeDeclContext
  ): BuildResult[SecuritySchemeDeclFull] =
    val name = ctx.lowerIdent.getText
    val kind = ctx.UPPER_IDENT.getText
    val args = ctx.securitySchemeArg.asScala.toList
      .map(a => a.lowerIdent.getText -> unquote(a.STRING_LIT.getText))
    def only(allowed: String*): BuildResult[Map[String, String]] =
      args.find(a => !allowed.contains(a._1)) match
        case Some((bad, _)) =>
          Left(buildErr(
            s"security scheme '$name': unknown $kind argument '$bad'" +
              (if allowed.isEmpty then "" else s"; expected ${allowed.mkString(", ")}"),
            ctx
          ))
        case None =>
          args.groupBy(_._1).collectFirst { case (k, vs) if vs.sizeIs > 1 => k } match
            case Some(k) =>
              Left(buildErr(s"security scheme '$name': duplicate argument '$k'", ctx))
            case None => Right(args.toMap)
    val kindE: BuildResult[security_scheme_kind] = kind match
      case "Bearer" => only("bearer_format").map(m => SsBearer(m.get("bearer_format")))
      case "ApiKey" =>
        only("header", "query", "cookie").flatMap: m =>
          m.toList match
            case List((location, keyName)) => Right(SsApiKey(location, keyName))
            case Nil =>
              Left(buildErr(
                s"security scheme '$name': ApiKey needs a location argument (header, query, or cookie)",
                ctx
              ))
            case _ =>
              Left(buildErr(s"security scheme '$name': ApiKey takes exactly one location", ctx))
      case "Basic" => only().map(_ => SsBasic())
      case other =>
        Left(buildErr(
          s"unknown security scheme kind '$other'; expected Bearer, ApiKey, or Basic",
          ctx
        ))
    kindE.map(k => SecuritySchemeDeclFull(name, k, sp(ctx)))

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
    yield
    // Parse-don't-validate: dispatch on property name → typed
    // convention_value at IR construction time. Unrecognised property
    // names / shape-or-range failures flow through CvUnknown / CvBad
    // variants for the validator pass to surface as diagnostics.
    ConventionRuleFull(target, p, qual, parseConventionValue(p, v), sp(ctx))

  private def buildTypeExpr(ctx: TypeExprContext): BuildResult[type_expr] =
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

  private def buildBaseType(ctx: BaseTypeContext): BuildResult[type_expr] =
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

  override def visitMulExpr(ctx: MulExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BMul())
  override def visitDivExpr(ctx: DivExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BDiv())
  override def visitAddExpr(ctx: AddExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BAdd())
  override def visitSubExpr(ctx: SubExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BSub())
  override def visitUnionExpr(ctx: UnionExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BUnion())
  override def visitIntersectExpr(ctx: IntersectExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BIntersect())
  override def visitMinusExpr(ctx: MinusExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BDiff())
  override def visitEqExpr(ctx: EqExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BEq())
  override def visitNeqExpr(ctx: NeqExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BNeq())
  override def visitLtExpr(ctx: LtExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BLt())
  override def visitGtExpr(ctx: GtExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BGt())
  override def visitLteExpr(ctx: LteExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BLe())
  override def visitGteExpr(ctx: GteExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BGe())
  override def visitInExpr(ctx: InExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BIn())
  override def visitNotInExpr(ctx: NotInExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BNotIn())
  override def visitSubsetExpr(ctx: SubsetExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BSubset())
  override def visitImpliesExpr(ctx: ImpliesExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BImplies())
  override def visitIffExpr(ctx: IffExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BIff())
  override def visitAndExpr(ctx: AndExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BAnd())
  override def visitOrExpr(ctx: OrExprContext): BuildResult[expr] =
    binOp(ctx, ctx.expr(0), ctx.expr(1), BOr())

  override def visitCardinalityExpr(ctx: CardinalityExprContext): BuildResult[expr] =
    unaryOp(ctx, ctx.expr, UCardinality())
  override def visitNegExpr(ctx: NegExprContext): BuildResult[expr] =
    unaryOp(ctx, ctx.expr, UNegate())
  override def visitPowerExpr(ctx: PowerExprContext): BuildResult[expr] =
    unaryOp(ctx, ctx.expr, UPower())
  override def visitNotExpr(ctx: NotExprContext): BuildResult[expr] =
    unaryOp(ctx, ctx.expr, UNot())

  override def visitPrimeExpr(ctx: PrimeExprContext): BuildResult[expr] =
    expr(ctx.expr).map(e => PrimeF(e, sp(ctx)))

  override def visitFieldAccessExpr(ctx: FieldAccessExprContext): BuildResult[expr] =
    expr(ctx.expr).map(e => FieldAccessF(e, ctx.lowerIdent.getText, sp(ctx)))

  override def visitEnumAccessExpr(ctx: EnumAccessExprContext): BuildResult[expr] =
    expr(ctx.expr).map(e => EnumAccessF(e, ctx.UPPER_IDENT.getText, sp(ctx)))

  override def visitIndexExpr(ctx: IndexExprContext): BuildResult[expr] =
    for
      a <- expr(ctx.expr(0))
      b <- expr(ctx.expr(1))
    yield IndexF(a, b, sp(ctx))

  override def visitCallExpr(ctx: CallExprContext): BuildResult[expr] =
    val argsE: BuildResult[List[expr]] =
      Option(ctx.argList).map(_.expr.asScala.toList.traverseB(expr)).getOrElse(Right(Nil))
    for
      fn   <- expr(ctx.expr)
      args <- argsE
    yield CallF(fn, args, sp(ctx))

  override def visitWithExpr(ctx: WithExprContext): BuildResult[expr] =
    for
      base    <- expr(ctx.expr)
      updates <- ctx.fieldAssign.asScala.toList.traverseB(buildFieldAssign)
    yield WithF(base, updates, sp(ctx))

  override def visitMatchesExpr(ctx: MatchesExprContext): BuildResult[expr] =
    expr(ctx.expr).map(e => MatchesF(e, unslashRegex(ctx.REGEX_LIT.getText), sp(ctx)))

  override def visitParenExpr(ctx: ParenExprContext): BuildResult[expr] = expr(ctx.expr)
  override def visitQuantExpr(ctx: QuantExprContext): BuildResult[expr] =
    buildQuantifier(ctx.quantifierExpr)
  override def visitSomeWrapE(ctx: SomeWrapEContext): BuildResult[expr] =
    buildSomeWrap(ctx.someWrapExpr)
  override def visitTheE(ctx: TheEContext): BuildResult[expr] = buildThe(ctx.theExpr)
  override def visitIfE(ctx: IfEContext): BuildResult[expr]   = buildIf(ctx.ifExpr)
  override def visitLetE(ctx: LetEContext): BuildResult[expr] = buildLet(ctx.letExpr)
  override def visitLambdaE(ctx: LambdaEContext): BuildResult[expr] =
    buildLambda(ctx.lambdaExpr)
  override def visitConstructorE(ctx: ConstructorEContext): BuildResult[expr] =
    buildConstructor(ctx.constructorExpr)
  override def visitSetOrMapE(ctx: SetOrMapEContext): BuildResult[expr] =
    buildSetOrMap(ctx.setOrMapLiteral)
  override def visitSeqE(ctx: SeqEContext): BuildResult[expr] = buildSeq(ctx.seqLiteral)

  override def visitPreExpr(ctx: PreExprContext): BuildResult[expr] =
    expr(ctx.expr).map(e => PreF(e, sp(ctx)))

  override def visitIntLitExpr(ctx: IntLitExprContext): BuildResult[expr] =
    Right(IntLitF(BigInt(ctx.INT_LIT.getText), sp(ctx)))

  override def visitFloatLitExpr(ctx: FloatLitExprContext): BuildResult[expr] =
    Right(FloatLitF(ctx.FLOAT_LIT.getText, sp(ctx)))

  override def visitStringLitExpr(ctx: StringLitExprContext): BuildResult[expr] =
    Right(StringLitF(unquote(ctx.STRING_LIT.getText), sp(ctx)))

  override def visitTrueLitExpr(ctx: TrueLitExprContext): BuildResult[expr] =
    Right(BoolLitF(true, sp(ctx)))
  override def visitFalseLitExpr(ctx: FalseLitExprContext): BuildResult[expr] =
    Right(BoolLitF(false, sp(ctx)))
  override def visitNoneLitExpr(ctx: NoneLitExprContext): BuildResult[expr] =
    Right(NoneLitF(sp(ctx)))

  override def visitUpperIdentExpr(ctx: UpperIdentExprContext): BuildResult[expr] =
    Right(IdentifierF(ctx.UPPER_IDENT.getText, sp(ctx)))

  override def visitLowerIdentExpr(ctx: LowerIdentExprContext): BuildResult[expr] =
    Right(IdentifierF(ctx.lowerIdent.getText, sp(ctx)))

  private def buildQuantifier(ctx: QuantifierExprContext): BuildResult[expr] =
    val qCtx = ctx.quantifier
    val quantifier: quant_kind =
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

  private def buildSomeWrap(ctx: SomeWrapExprContext): BuildResult[expr] =
    expr(ctx.expr).map(e => SomeWrapF(e, sp(ctx)))

  private def buildThe(ctx: TheExprContext): BuildResult[expr] =
    val exprs = ctx.expr
    for
      a <- expr(exprs.get(0))
      b <- expr(exprs.get(1))
    yield TheF(ctx.lowerIdent.getText, a, b, sp(ctx))

  private def buildIf(ctx: IfExprContext): BuildResult[expr] =
    val exprs = ctx.expr
    for
      c <- expr(exprs.get(0))
      t <- expr(exprs.get(1))
      e <- expr(exprs.get(2))
    yield IfF(c, t, e, sp(ctx))

  private def buildLet(ctx: LetExprContext): BuildResult[expr] =
    val exprs = ctx.expr
    for
      v <- expr(exprs.get(0))
      b <- expr(exprs.get(1))
    yield LetF(ctx.lowerIdent.getText, v, b, sp(ctx))

  private def buildLambda(ctx: LambdaExprContext): BuildResult[expr] =
    expr(ctx.expr).map(b => LambdaF(ctx.lowerIdent.getText, b, sp(ctx)))

  private def buildConstructor(ctx: ConstructorExprContext): BuildResult[expr] =
    ctx.fieldAssign.asScala.toList
      .traverseB(buildFieldAssign)
      .map(fs => ConstructorF(ctx.UPPER_IDENT.getText, fs, sp(ctx)))

  private def buildSetOrMap(ctx: SetOrMapLiteralContext): BuildResult[expr] =
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

  private def buildSeq(ctx: SeqLiteralContext): BuildResult[expr] =
    ctx.expr.asScala.toList.traverseB(expr).map(es => SeqLiteralF(es, sp(ctx)))

  private def buildFieldAssign(ctx: FieldAssignContext): BuildResult[FieldAssignFull] =
    expr(ctx.expr).map(e => FieldAssignFull(ctx.lowerIdent.getText, e, sp(ctx)))
