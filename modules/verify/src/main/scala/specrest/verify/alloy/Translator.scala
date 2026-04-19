package specrest.verify.alloy

import specrest.ir.*
import specrest.verify.Classifier

import scala.collection.mutable

object Translator:

  final private case class Ctx(
      ir: ServiceIR,
      stateFields: Map[String, TypeExpr],
      inputFields: Map[String, TypeExpr] = Map.empty,
      currentStateSig: String = "State",
      postStateSig: String = "State",
      boundVars: Set[String] = Set.empty
  )

  def translateGlobal(ir: ServiceIR, scope: Int): AlloyModule =
    val ctx = buildCtx(ir)
    AlloyModule(
      name = sanitizeName(ir.name),
      sigs = buildSigs(ctx),
      facts = invariantFacts(ctx, ir),
      commands = List(AlloyCommand("global", AlloyCommandKind.Run, "", scope))
    )

  enum TemporalKind:
    case Always, Eventually

  final case class TemporalTranslation(kind: TemporalKind, module: AlloyModule)

  def translateTemporal(
      ir: ServiceIR,
      decl: TemporalDecl,
      scope: Int
  ): TemporalTranslation =
    val ctx = buildCtx(ir)
    decl.expr match
      case Expr.Call(Expr.Identifier("always", _), arg :: Nil, _) =>
        val body = renderExpr(ctx, arg)
        val module = AlloyModule(
          name = sanitizeName(ir.name),
          sigs = buildSigs(ctx),
          facts = invariantFacts(ctx, ir) :+
            AlloyFact(Some(s"${decl.name}_counterexample"), s"not ($body)", decl.span),
          commands = List(AlloyCommand(decl.name, AlloyCommandKind.Run, "", scope))
        )
        TemporalTranslation(TemporalKind.Always, module)
      case Expr.Call(Expr.Identifier("eventually", _), arg :: Nil, _) =>
        val module = AlloyModule(
          name = sanitizeName(ir.name),
          sigs = buildSigs(ctx),
          facts = invariantFacts(ctx, ir) :+
            AlloyFact(Some(s"${decl.name}_witness"), renderExpr(ctx, arg), decl.span),
          commands = List(AlloyCommand(decl.name, AlloyCommandKind.Run, "", scope))
        )
        TemporalTranslation(TemporalKind.Eventually, module)
      case Expr.Call(Expr.Identifier("fairness", _), _, _) =>
        throw new AlloyTranslatorError(
          s"temporal '${decl.name}': fairness(...) is not supported in v1; it requires trace-based " +
            "verification via Alloy's `var` sig mode which is future work"
        )
      case _ =>
        throw new AlloyTranslatorError(
          s"temporal '${decl.name}': only 'always(P)' and 'eventually(P)' are supported in v1; got " +
            s"${decl.expr.getClass.getSimpleName}"
        )

  def translateOperationRequires(
      ir: ServiceIR,
      op: OperationDecl,
      scope: Int
  ): AlloyModule =
    val ctx = buildCtxWithInputs(ir, op)
    AlloyModule(
      name = sanitizeName(ir.name),
      sigs = buildSigs(ctx),
      facts = op.requires.zipWithIndex.map: (r, i) =>
        AlloyFact(Some(s"${op.name}_requires_$i"), renderExpr(ctx, r), r.spanOpt),
      commands = List(AlloyCommand(s"${op.name}_requires", AlloyCommandKind.Run, "", scope))
    )

  def translateOperationEnabled(
      ir: ServiceIR,
      op: OperationDecl,
      scope: Int
  ): AlloyModule =
    val ctx = buildCtxWithInputs(ir, op)
    val reqFacts = op.requires.zipWithIndex.map: (r, i) =>
      AlloyFact(Some(s"${op.name}_requires_$i"), renderExpr(ctx, r), r.spanOpt)
    AlloyModule(
      name = sanitizeName(ir.name),
      sigs = buildSigs(ctx),
      facts = invariantFacts(ctx, ir) ++ reqFacts,
      commands = List(AlloyCommand(s"${op.name}_enabled", AlloyCommandKind.Run, "", scope))
    )

  private def buildCtxWithInputs(ir: ServiceIR, op: OperationDecl): Ctx =
    val stateFields = ir.state.map(_.fields).getOrElse(Nil).map: sf =>
      sf.name -> sf.typeExpr
    val inputFields = op.inputs.map(p => p.name -> p.typeExpr)
    Ctx(ir, stateFields.toMap, inputFields.toMap)

  private def invariantFacts(ctx: Ctx, ir: ServiceIR): List[AlloyFact] =
    ir.invariants.zipWithIndex.map: (inv, i) =>
      val name = inv.name.getOrElse(s"inv_$i")
      AlloyFact(Some(name), renderExpr(ctx, inv.expr), inv.span)

  def translateOperationPreservation(
      ir: ServiceIR,
      op: OperationDecl,
      inv: InvariantDecl,
      scope: Int
  ): AlloyModule =
    val preCtx  = buildCtxWithInputs(ir, op)
    val postCtx = preCtx.copy(postStateSig = "StatePost")
    val sigs    = buildPreservationSigs(preCtx)

    val invariantsPre = ir.invariants.zipWithIndex.map: (i, idx) =>
      val name = i.name.getOrElse(s"inv_$idx")
      AlloyFact(Some(s"${name}_pre"), renderExpr(preCtx, i.expr), i.span)

    val requiresFacts = op.requires.zipWithIndex.map: (r, i) =>
      AlloyFact(Some(s"${op.name}_requires_$i"), renderExpr(preCtx, r), r.spanOpt)

    val ensuresFacts = op.ensures.zipWithIndex.map: (e, i) =>
      AlloyFact(Some(s"${op.name}_ensures_$i"), renderExpr(postCtx, e), e.spanOpt)

    val mentionedInEnsures = primedStateFields(op.ensures)
    val frameFacts = ir.state.map(_.fields).getOrElse(Nil)
      .filterNot(sf => mentionedInEnsures.contains(sf.name))
      .map: sf =>
        AlloyFact(
          Some(s"frame_${sf.name}"),
          s"StatePost.${sf.name} = State.${sf.name}",
          sf.span
        )

    val postStateCtx  = preCtx.copy(currentStateSig = "StatePost")
    val invariantName = inv.name.getOrElse("invariant")
    val postViolation = AlloyFact(
      Some(s"${invariantName}_violated_post"),
      s"not (${renderExpr(postStateCtx, inv.expr)})",
      inv.span
    )

    val facts   = invariantsPre ++ requiresFacts ++ ensuresFacts ++ frameFacts :+ postViolation
    val cmdName = s"${op.name}_preserves_$invariantName"
    AlloyModule(
      name = sanitizeName(ir.name),
      sigs = sigs,
      facts = facts,
      commands = List(AlloyCommand(cmdName, AlloyCommandKind.Run, "", scope))
    )

  private def buildPreservationSigs(ctx: Ctx): List[AlloySig] =
    val baseSigs = buildSigs(ctx)
    if ctx.stateFields.nonEmpty then
      val stateFields = ctx.stateFields.toList.map: (name, typ) =>
        val (mult, elem) = alloyFieldTypeOf(typ)
        AlloyField(name, mult, elem)
      baseSigs :+ AlloySig("StatePost", isOne = true, fields = stateFields)
    else baseSigs

  private def primedStateFields(ensures: List[Expr]): Set[String] =
    // Any state identifier appearing under any Prime(...) subtree is considered
    // mentioned by ensures — so the frame generator does NOT pin that field.
    // Entering a Prime flips `underPrime` for the whole subtree; Pre() flips it back.
    val mentioned = mutable.Set.empty[String]
    def walk(e: Expr, underPrime: Boolean): Unit = e match
      case Expr.Prime(inner, _) => walk(inner, underPrime = true)
      case Expr.Pre(inner, _)   => walk(inner, underPrime = false)
      case Expr.Identifier(name, _) =>
        if underPrime then mentioned += name
      case _ =>
        Classifier.childExprs(e).foreach(walk(_, underPrime))
    ensures.foreach(walk(_, underPrime = false))
    mentioned.toSet

  private def buildCtx(ir: ServiceIR): Ctx =
    val stateFields = ir.state.map(_.fields).getOrElse(Nil).map: sf =>
      sf.name -> sf.typeExpr
    Ctx(ir, stateFields.toMap)

  private def buildSigs(ctx: Ctx): List[AlloySig] =
    val sigs = mutable.ArrayBuffer.empty[AlloySig]
    if needsBoolSig(ctx) then
      sigs += AlloySig("Bool", abstract_ = true)
      sigs += AlloySig("True", isOne = true, extends_ = Some("Bool"))
      sigs += AlloySig("False", isOne = true, extends_ = Some("Bool"))
    for entity <- ctx.ir.entities do
      val fields = entity.fields.map: f =>
        val (mult, elem) = alloyFieldTypeOf(f.typeExpr)
        AlloyField(f.name, mult, elem)
      sigs += AlloySig(entity.name, fields = fields)
    for en <- ctx.ir.enums do
      sigs += AlloySig(en.name, abstract_ = true)
      for v <- en.values do
        sigs += AlloySig(v, isOne = true, extends_ = Some(en.name))
    if ctx.stateFields.nonEmpty then
      val stateFields = ctx.stateFields.toList.map: (name, typ) =>
        val (mult, elem) = alloyFieldTypeOf(typ)
        AlloyField(name, mult, elem)
      sigs += AlloySig("State", isOne = true, fields = stateFields)
    if ctx.inputFields.nonEmpty then
      val inputFields = ctx.inputFields.toList.map: (name, typ) =>
        val (mult, elem) = alloyFieldTypeOf(typ)
        AlloyField(name, mult, elem)
      sigs += AlloySig("Inputs", isOne = true, fields = inputFields)
    sigs.toList

  private def needsBoolSig(ctx: Ctx): Boolean =
    def typeUsesBool(t: TypeExpr): Boolean = t match
      case TypeExpr.NamedType("Bool", _) => true
      case TypeExpr.SetType(inner, _)    => typeUsesBool(inner)
      case TypeExpr.OptionType(inner, _) => typeUsesBool(inner)
      case _                             => false
    def exprUsesBoolLit(e: Expr): Boolean = e match
      case Expr.BoolLit(_, _) => true
      case _                  => Classifier.childExprs(e).exists(exprUsesBoolLit)
    val inFields =
      ctx.ir.entities.exists(_.fields.exists(f => typeUsesBool(f.typeExpr))) ||
        ctx.stateFields.values.exists(typeUsesBool) ||
        ctx.inputFields.values.exists(typeUsesBool)
    val inExprs =
      ctx.ir.invariants.exists(i => exprUsesBoolLit(i.expr)) ||
        ctx.ir.temporals.exists(t => exprUsesBoolLit(t.expr)) ||
        ctx.ir.operations.exists(op =>
          op.requires.exists(exprUsesBoolLit) || op.ensures.exists(exprUsesBoolLit)
        )
    inFields || inExprs

  private def alloyFieldTypeOf(t: TypeExpr): (AlloyFieldMultiplicity, String) = t match
    case TypeExpr.NamedType(name, _) =>
      (AlloyFieldMultiplicity.One, mapPrimitive(name))
    case TypeExpr.SetType(inner, _) =>
      val elem = typeToSigName(inner)
      (AlloyFieldMultiplicity.Set, elem)
    case TypeExpr.OptionType(inner, _) =>
      (AlloyFieldMultiplicity.Lone, typeToSigName(inner))
    case other =>
      throw new AlloyTranslatorError(
        s"unsupported Alloy field type (supported: NamedType, Set[T], Option[T]); got $other"
      )

  private def typeToSigName(t: TypeExpr): String = t match
    case TypeExpr.NamedType(name, _) => mapPrimitive(name)
    case other =>
      throw new AlloyTranslatorError(
        s"nested type not supported as Alloy element sort: $other"
      )

  private def mapPrimitive(name: String): String = name match
    case "Int"    => "Int"
    case "Bool"   => "Bool"
    case "String" => "String"
    case other    => other

  private def renderExpr(ctx: Ctx, e: Expr): String = e match
    case Expr.BinaryOp(op, l, r, _)           => renderBinaryOp(ctx, op, l, r)
    case Expr.UnaryOp(UnOp.Not, x, _)         => s"not (${renderExpr(ctx, x)})"
    case Expr.UnaryOp(UnOp.Cardinality, x, _) => s"#(${renderExpr(ctx, x)})"
    case Expr.UnaryOp(UnOp.Negate, x, _)      => s"minus[0, ${renderExpr(ctx, x)}]"
    case Expr.UnaryOp(UnOp.Power, _, _) =>
      throw new AlloyTranslatorError(
        "standalone powerset '^s' is only supported as a binder domain (e.g. 'some t in ^s | ...')"
      )
    case q @ Expr.Quantifier(_, _, _, _) => renderQuantifier(ctx, q)
    case Expr.FieldAccess(b, f, _)       => s"(${renderExpr(ctx, b)}).$f"
    case Expr.EnumAccess(_, m, _)        => m
    case Expr.Identifier(name, _) =>
      if ctx.boundVars.contains(name) then name
      else if ctx.stateFields.contains(name) then s"${ctx.currentStateSig}.$name"
      else if ctx.inputFields.contains(name) then s"Inputs.$name"
      else name
    case Expr.Prime(inner, _) =>
      renderExpr(ctx.copy(currentStateSig = ctx.postStateSig), inner)
    case Expr.Pre(inner, _) =>
      renderExpr(ctx.copy(currentStateSig = "State"), inner)
    case Expr.IntLit(v, _)  => v.toString
    case Expr.BoolLit(v, _) =>
      // Universe-independent: (True = True) is always-true, (True = False) always-false.
      // Requires the Bool/True/False sigs emitted by buildSigs when BoolLit is used.
      if v then "(True = True)" else "(True = False)"
    case Expr.StringLit(s, _) =>
      throw new AlloyTranslatorError(s"string literal '$s' is not supported in Alloy translation")
    case Expr.SetLiteral(Nil, _)    => "none"
    case Expr.SetLiteral(elems, _)  => elems.map(renderExpr(ctx, _)).mkString(" + ")
    case Expr.Index(b, i, _)        => s"(${renderExpr(ctx, b)})[${renderExpr(ctx, i)}]"
    case Expr.Call(callee, args, _) => renderCall(ctx, callee, args)
    case other =>
      throw new AlloyTranslatorError(
        s"Alloy translator does not support expression: ${other.getClass.getSimpleName}"
      )

  private def renderBinaryOp(ctx: Ctx, op: BinOp, l: Expr, r: Expr): String =
    val lr = renderExpr(ctx, l)
    val rr = renderExpr(ctx, r)
    op match
      case BinOp.And       => s"(($lr) and ($rr))"
      case BinOp.Or        => s"(($lr) or ($rr))"
      case BinOp.Implies   => s"(($lr) implies ($rr))"
      case BinOp.Iff       => s"(($lr) iff ($rr))"
      case BinOp.Eq        => s"($lr = $rr)"
      case BinOp.Neq       => s"($lr != $rr)"
      case BinOp.Lt        => s"($lr < $rr)"
      case BinOp.Le        => s"($lr <= $rr)"
      case BinOp.Gt        => s"($lr > $rr)"
      case BinOp.Ge        => s"($lr >= $rr)"
      case BinOp.In        => s"($lr in $rr)"
      case BinOp.NotIn     => s"($lr !in $rr)"
      case BinOp.Subset    => s"($lr in $rr)"
      case BinOp.Union     => s"($lr + $rr)"
      case BinOp.Intersect => s"($lr & $rr)"
      case BinOp.Diff      => s"($lr - $rr)"
      case BinOp.Add       => s"plus[$lr, $rr]"
      case BinOp.Sub       => s"minus[$lr, $rr]"
      case BinOp.Mul       => s"mul[$lr, $rr]"
      case BinOp.Div       => s"div[$lr, $rr]"

  private def renderQuantifier(ctx: Ctx, q: Expr.Quantifier): String =
    val hasPowersetBinder = q.bindings.exists(_.domain match
      case Expr.UnaryOp(UnOp.Power, _, _) => true
      case _                              => false
    )
    if hasPowersetBinder && q.quantifier == QuantKind.All then
      throw new AlloyTranslatorError(
        "universal quantification over a powerset ('all t in ^s | ...') requires higher-order " +
          "reasoning that Alloy rejects as non-skolemizable. Rewrite as an existential " +
          "('some t in ^s | ...') or as a first-order statement about s (e.g. 'all x in s | ...')."
      )
    if hasPowersetBinder && q.quantifier == QuantKind.No then
      throw new AlloyTranslatorError(
        "'no t in ^s | ...' is a negated universal over a powerset; Alloy rejects it as " +
          "higher-order for the same reason as 'all'. Rewrite to a first-order statement."
      )
    val keyword = q.quantifier match
      case QuantKind.All    => "all"
      case QuantKind.Some   => "some"
      case QuantKind.Exists => "some"
      case QuantKind.No     => "no"
    val (binderParts, extraConstraints) = q.bindings.map(buildBinding(ctx, _)).unzip
    val bindings                        = binderParts.mkString(", ")
    val innerCtx                        = ctx.copy(boundVars = ctx.boundVars ++ q.bindings.map(_.variable))
    val bodyInner                       = renderExpr(innerCtx, q.body)
    val extras                          = extraConstraints.flatten
    val body =
      if extras.isEmpty then bodyInner
      else
        val joiner = q.quantifier match
          case QuantKind.All => " implies "
          case _             => " and "
        val guard = extras.mkString(" and ")
        s"($guard)$joiner($bodyInner)"
    s"($keyword $bindings | $body)"

  private def buildBinding(ctx: Ctx, b: QuantifierBinding): (String, Option[String]) =
    b.domain match
      case Expr.UnaryOp(UnOp.Power, inner, _) =>
        val innerType   = domainSigName(ctx, inner)
        val containment = s"${b.variable} in ${renderExpr(ctx, inner)}"
        (s"${b.variable}: set $innerType", Some(containment))
      case Expr.Identifier(name, _) =>
        val t = ctx.ir.entities.find(_.name == name)
          .map(_.name)
          .orElse(ctx.ir.enums.find(_.name == name).map(_.name))
        t match
          case Some(sigName) => (s"${b.variable}: $sigName", None)
          case None          =>
            // Field-typed domains (state or op input): bind to the element sig, guard via `in <field>`.
            if ctx.stateFields.contains(name) || ctx.inputFields.contains(name) then
              val elem = domainSigName(ctx, b.domain)
              (s"${b.variable}: $elem", Some(s"${b.variable} in ${renderExpr(ctx, b.domain)}"))
            else (s"${b.variable}: $name", None)
      case _ =>
        (s"${b.variable}: ${renderExpr(ctx, b.domain)}", None)

  private def domainSigName(ctx: Ctx, e: Expr): String = e match
    case Expr.Identifier(name, _) =>
      ctx.stateFields.get(name).orElse(ctx.inputFields.get(name)) match
        case Some(t) => fieldElementSigName(t)
        case None =>
          ctx.ir.entities.find(_.name == name).map(_.name)
            .orElse(ctx.ir.enums.find(_.name == name).map(_.name))
            .getOrElse(name)
    case _ =>
      throw new AlloyTranslatorError(
        s"powerset binder domain must be an identifier referring to an entity or set-typed state"
      )

  private def fieldElementSigName(t: TypeExpr): String = t match
    case TypeExpr.NamedType(name, _)                         => mapPrimitive(name)
    case TypeExpr.SetType(TypeExpr.NamedType(name, _), _)    => mapPrimitive(name)
    case TypeExpr.OptionType(TypeExpr.NamedType(name, _), _) => mapPrimitive(name)
    case other =>
      throw new AlloyTranslatorError(
        s"unsupported quantifier domain field type: $other"
      )

  private def renderCall(ctx: Ctx, callee: Expr, args: List[Expr]): String =
    callee match
      case Expr.Identifier(name, _) =>
        val rendered = args.map(renderExpr(ctx, _)).mkString(", ")
        s"$name[$rendered]"
      case _ =>
        throw new AlloyTranslatorError(
          s"Alloy translator only supports identifier-called functions; got $callee"
        )

  private def sanitizeName(name: String): String =
    name.filter(c => c.isLetterOrDigit || c == '_')
