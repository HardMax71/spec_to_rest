package specrest.verify.alloy

import specrest.ir.generated.SpecRestGenerated.*

import cats.effect.IO
import specrest.ir.*
import specrest.verify.Classifier

import scala.collection.mutable
import scala.util.boundary

private type AlloyLabel = boundary.Label[Either[VerifyError.AlloyTranslator, Nothing]]

private def failAlloy(msg: String)(using AlloyLabel): Nothing =
  boundary.break(Left(VerifyError.AlloyTranslator(msg)))

object Translator:

  final private case class Ctx(
      ir: ServiceIRFull,
      stateFields: Map[String, type_expr_full],
      inputFields: Map[String, type_expr_full] = Map.empty,
      currentStateSig: String = "State",
      postStateSig: String = "State",
      boundVars: Set[String] = Set.empty
  )

  def translateGlobal(
      ir: ServiceIRFull,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, AlloyModule]] =
    IO.delay {
      boundary:
        val ctx = buildCtx(ir)
        Right(
          AlloyModule(
            name = sanitizeName(ir.name),
            sigs = buildSigs(ctx),
            k = invariantFacts(ctx, ir),
            commands = List(AlloyCommand("global", AlloyCommandKind.Run, "", scope))
          )
        )
    }

  enum TemporalKind derives CanEqual:
    case Always, Eventually

  final case class TemporalTranslation(kind: TemporalKind, module: AlloyModule)

  def translateTemporal(
      ir: ServiceIRFull,
      decl: TemporalDeclFull,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, TemporalTranslation]] =
    IO.delay {
      boundary:
        val ctx = buildCtx(ir)
        Right(decl.expr match
          case CallF(IdentifierF("always", _), arg :: Nil, _) =>
            val body = renderExpr(ctx, arg)
            val module = AlloyModule(
              name = sanitizeName(ir.name),
              sigs = buildSigs(ctx),
              k = invariantFacts(ctx, ir) :+
                AlloyFact(Some(s"${decl.name}_counterexample"), s"not ($body)", decl.span),
              commands = List(AlloyCommand(decl.name, AlloyCommandKind.Run, "", scope))
            )
            TemporalTranslation(TemporalKind.Always, module)
          case CallF(IdentifierF("eventually", _), arg :: Nil, _) =>
            val module = AlloyModule(
              name = sanitizeName(ir.name),
              sigs = buildSigs(ctx),
              k = invariantFacts(ctx, ir) :+
                AlloyFact(Some(s"${decl.name}_witness"), renderExpr(ctx, arg), decl.span),
              commands = List(AlloyCommand(decl.name, AlloyCommandKind.Run, "", scope))
            )
            TemporalTranslation(TemporalKind.Eventually, module)
          case CallF(IdentifierF("fairness", _), _, _) =>
            failAlloy(
              s"temporal '${decl.name}': fairness(...) is not supported in v1; it requires trace-based " +
                "verification via Alloy's `var` sig mode which is future work"
            )
          case _ =>
            failAlloy(
              s"temporal '${decl.name}': only 'always(P)' and 'eventually(P)' are supported in v1; got " +
                s"${decl.expr.getClass.getSimpleName}"
            )
        )
    }

  def translateOperationRequires(
      ir: ServiceIRFull,
      op: OperationDeclFull,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, AlloyModule]] =
    IO.delay {
      boundary:
        val ctx = buildCtxWithInputs(ir, op)
        Right(AlloyModule(
          name = sanitizeName(ir.name),
          sigs = buildSigs(ctx),
          k = op.d.zipWithIndex.map: (r, i) =>
            AlloyFact(Some(s"${op.name}_requires_$i"), renderExpr(ctx, r), r.spanOpt),
          commands = List(AlloyCommand(s"${op.name}_requires", AlloyCommandKind.Run, "", scope))
        ))
    }

  def translateOperationEnabled(
      ir: ServiceIRFull,
      op: OperationDeclFull,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, AlloyModule]] =
    IO.delay {
      boundary:
        val ctx = buildCtxWithInputs(ir, op)
        val reqFacts = op.d.zipWithIndex.map: (r, i) =>
          AlloyFact(Some(s"${op.name}_requires_$i"), renderExpr(ctx, r), r.spanOpt)
        Right(AlloyModule(
          name = sanitizeName(ir.name),
          sigs = buildSigs(ctx),
          k = invariantFacts(ctx, ir) ++ reqFacts,
          commands = List(AlloyCommand(s"${op.name}_enabled", AlloyCommandKind.Run, "", scope))
        ))
    }

  private def buildCtxWithInputs(ir: ServiceIRFull, op: OperationDeclFull): Ctx =
    val stateFields = ir.state.map(_.fields).getOrElse(Nil).map: sf =>
      sf.name -> sf.typeExpr
    val inputFields = op.b.map(p => p.name -> p.typeExpr)
    Ctx(ir, stateFields.toMap, inputFields.toMap)

  private def invariantFacts(ctx: Ctx, ir: ServiceIRFull)(using AlloyLabel): List[AlloyFact] =
    ir.invariants.zipWithIndex.map: (inv, i) =>
      val name = inv.name.getOrElse(s"inv_$i")
      AlloyFact(Some(name), renderExpr(ctx, inv.expr), inv.span)

  def translateOperationPreservation(
      ir: ServiceIRFull,
      op: OperationDeclFull,
      inv: InvariantDeclFull,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, AlloyModule]] =
    IO.delay {
      boundary:
        val preCtx  = buildCtxWithInputs(ir, op)
        val postCtx = preCtx.copy(postStateSig = "StatePost")
        val sigs    = buildPreservationSigs(preCtx)

        val invariantsPre = ir.invariants.zipWithIndex.map: (i, idx) =>
          val name = i.name.getOrElse(s"inv_$idx")
          AlloyFact(Some(s"${name}_pre"), renderExpr(preCtx, i.expr), i.span)

        val requiresFacts = op.d.zipWithIndex.map: (r, i) =>
          AlloyFact(Some(s"${op.name}_requires_$i"), renderExpr(preCtx, r), r.spanOpt)

        val ensuresFacts = op.e.zipWithIndex.map: (e, i) =>
          AlloyFact(Some(s"${op.name}_ensures_$i"), renderExpr(postCtx, e), e.spanOpt)

        val mentionedInEnsures = primedStateFields(op.e)
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

        val k = invariantsPre ++ requiresFacts ++ ensuresFacts ++ frameFacts :+ postViolation
        val cmdName = s"${op.name}_preserves_$invariantName"
        Right(AlloyModule(
          name = sanitizeName(ir.name),
          sigs = sigs,
          k = facts,
          commands = List(AlloyCommand(cmdName, AlloyCommandKind.Run, "", scope))
        ))
    }

  private def buildPreservationSigs(ctx: Ctx)(using AlloyLabel): List[AlloySig] =
    val baseSigs = buildSigs(ctx)
    if ctx.stateFields.nonEmpty then
      val stateFields = ctx.stateFields.toList.map: (name, typ) =>
        val (mult, elem) = alloyFieldTypeOf(typ)
        AlloyField(name, mult, elem)
      baseSigs :+ AlloySig("StatePost", isOne = true, fields = stateFields)
    else baseSigs

  private def primedStateFields(ensures: List[expr_full]): Set[String] =
    val mentioned = mutable.Set.empty[String]
    def walk(e: expr_full, underPrime: Boolean): Unit = e match
      case PrimeF(inner, _) => walk(inner, underPrime = true)
      case PreF(inner, _)   => walk(inner, underPrime = false)
      case IdentifierF(name, _) =>
        if underPrime then mentioned += name
      case _ =>
        Classifier.childExprs(e).foreach(walk(_, underPrime))
    ensures.foreach(walk(_, underPrime = false))
    mentioned.toSet

  private def buildCtx(ir: ServiceIRFull): Ctx =
    val stateFields = ir.state.map(_.fields).getOrElse(Nil).map: sf =>
      sf.name -> sf.typeExpr
    Ctx(ir, stateFields.toMap)

  private def buildSigs(ctx: Ctx)(using AlloyLabel): List[AlloySig] =
    val sigs = mutable.ArrayBuffer.empty[AlloySig]
    if needsBoolSig(ctx) then
      sigs += AlloySig("Bool", abstract_ = true)
      sigs += AlloySig("True", isOne = true, extends_ = Some("Bool"))
      sigs += AlloySig("False", isOne = true, extends_ = Some("Bool"))
    for entity <- ctx.ir.c do
      val fields = entity.fields.map: f =>
        val (mult, elem) = alloyFieldTypeOf(f.typeExpr)
        AlloyField(f.name, mult, elem)
      sigs += AlloySig(entity.name, fields = fields)
    for en <- ctx.ir.d do
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
    def typeUsesBool(t: type_expr_full): Boolean = t match
      case NamedTypeF("Bool", _) => true
      case SetTypeF(inner, _)    => typeUsesBool(inner)
      case OptionTypeF(inner, _) => typeUsesBool(inner)
      case _                     => false
    def exprUsesBoolLit(e: expr_full): Boolean = e match
      case BoolLitF(_, _) => true
      case _              => Classifier.childExprs(e).exists(exprUsesBoolLit)
    val inFields =
      ctx.ir.c.exists(_.fields.exists(f => typeUsesBool(f.typeExpr))) ||
        ctx.stateFields.values.exists(typeUsesBool) ||
        ctx.inputFields.values.exists(typeUsesBool)
    val inExprs =
      ctx.ir.invariants.exists(i => exprUsesBoolLit(i.expr)) ||
        ctx.ir.j.exists(t => exprUsesBoolLit(t.expr)) ||
        ctx.ir.g.exists(op =>
          op.d.exists(exprUsesBoolLit) || op.e.exists(exprUsesBoolLit)
        )
    inFields || inExprs

  private def alloyFieldTypeOf(t: type_expr_full)(using
      AlloyLabel
  ): (AlloyFieldMultiplicity, String) =
    t match
      case NamedTypeF(name, _) =>
        (AlloyFieldMultiplicity.One, mapPrimitive(name))
      case SetTypeF(inner, _) =>
        val elem = typeToSigName(inner)
        (AlloyFieldMultiplicity.Set, elem)
      case OptionTypeF(inner, _) =>
        (AlloyFieldMultiplicity.Lone, typeToSigName(inner))
      case other =>
        failAlloy(
          s"unsupported Alloy field type (supported: NamedType, Set[T], Option[T]); got $other"
        )

  private def typeToSigName(t: type_expr_full)(using AlloyLabel): String = t match
    case NamedTypeF(name, _) => mapPrimitive(name)
    case other =>
      failAlloy(s"nested type not supported as Alloy element sort: $other")

  private def mapPrimitive(name: String): String = name match
    case "Int"    => "Int"
    case "Bool"   => "Bool"
    case "String" => "String"
    case other    => other

  private def renderExpr(ctx: Ctx, e: expr_full)(using AlloyLabel): String = e match
    case BinaryOpF(op, l, r, _)         => renderBinaryOp(ctx, op, l, r)
    case UnaryOpF(UNot(), x, _)         => s"not (${renderExpr(ctx, x)})"
    case UnaryOpF(UCardinality(), x, _) => s"#(${renderExpr(ctx, x)})"
    case UnaryOpF(UNegate(), x, _)      => s"minus[0, ${renderExpr(ctx, x)}]"
    case UnaryOpF(UPower(), _, _) =>
      failAlloy(
        "standalone powerset '^s' is only supported as a binder domain (e.g. 'some t in ^s | ...')"
      )
    case q @ QuantifierF(_, _, _, _) => renderQuantifier(ctx, q)
    case FieldAccessF(b, f, _)       => s"(${renderExpr(ctx, b)}).$f"
    case EnumAccessF(_, m, _)        => m
    case IdentifierF(name, _) =>
      if ctx.boundVars.contains(name) then name
      else if ctx.stateFields.contains(name) then s"${ctx.currentStateSig}.$name"
      else if ctx.inputFields.contains(name) then s"Inputs.$name"
      else name
    case PrimeF(inner, _) =>
      renderExpr(ctx.copy(currentStateSig = ctx.postStateSig), inner)
    case PreF(inner, _) =>
      renderExpr(ctx.copy(currentStateSig = "State"), inner)
    case IntLitF(v, _) => v.toString
    case BoolLitF(v, _) =>
      if v then "(True = True)" else "(True = False)"
    case StringLitF(s, _) =>
      failAlloy(s"string literal '$s' is not supported in Alloy translation")
    case SetLiteralF(Nil, _)    => "none"
    case SetLiteralF(elems, _)  => elems.map(renderExpr(ctx, _)).mkString(" + ")
    case IndexF(b, i, _)        => s"(${renderExpr(ctx, b)})[${renderExpr(ctx, i)}]"
    case CallF(callee, args, _) => renderCall(ctx, callee, args)
    case other =>
      failAlloy(s"Alloy translator does not support expression: ${other.getClass.getSimpleName}")

  private def renderBinaryOp(ctx: Ctx, op: bin_op_full, l: expr_full, r: expr_full)(using
      AlloyLabel
  ): String =
    val lr = renderExpr(ctx, l)
    val rr = renderExpr(ctx, r)
    op match
      case BAnd()       => s"(($lr) and ($rr))"
      case BOr()        => s"(($lr) or ($rr))"
      case BImplies()   => s"(($lr) implies ($rr))"
      case BIff()       => s"(($lr) iff ($rr))"
      case BEq()        => s"($lr = $rr)"
      case BNeq()       => s"($lr != $rr)"
      case BLt()        => s"($lr < $rr)"
      case BLe()        => s"($lr <= $rr)"
      case BGt()        => s"($lr > $rr)"
      case BGe()        => s"($lr >= $rr)"
      case BIn()        => s"($lr in $rr)"
      case BNotIn()     => s"($lr !in $rr)"
      case BSubset()    => s"($lr in $rr)"
      case BUnion()     => s"($lr + $rr)"
      case BIntersect() => s"($lr & $rr)"
      case BDiff()      => s"($lr - $rr)"
      case BAdd()       => s"plus[$lr, $rr]"
      case BSub()       => s"minus[$lr, $rr]"
      case BMul()       => s"mul[$lr, $rr]"
      case BDiv()       => s"div[$lr, $rr]"

  private def renderQuantifier(ctx: Ctx, q: QuantifierF)(using AlloyLabel): String =
    val hasPowersetBinder = q.b.exists(_.domain match
      case UnaryOpF(UPower(), _, _) => true
      case _                        => false
    )
    if hasPowersetBinder && q.quantifier == QAll() then
      failAlloy(
        "universal quantification over a powerset ('all t in ^s | ...') requires higher-order " +
          "reasoning that Alloy rejects as non-skolemizable. Rewrite as an existential " +
          "('some t in ^s | ...') or as a first-order statement about s (e.g. 'all x in s | ...')."
      )
    if hasPowersetBinder && q.quantifier == QNo() then
      failAlloy(
        "'no t in ^s | ...' is a negated universal over a powerset; Alloy rejects it as " +
          "higher-order for the same reason as 'all'. Rewrite to a first-order statement."
      )
    val keyword = q.quantifier match
      case QAll()    => "all"
      case QSome()   => "some"
      case QExists() => "some"
      case QNo()     => "no"
    val (binderParts, extraConstraints) = q.b.map(buildBinding(ctx, _)).unzip
    val bindings                        = binderParts.mkString(", ")
    val innerCtx                        = ctx.copy(boundVars = ctx.boundVars ++ q.b.map(_.a))
    val bodyInner                       = renderExpr(innerCtx, q.body)
    val extras                          = extraConstraints.flatten
    val body =
      if extras.isEmpty then bodyInner
      else
        val joiner = q.quantifier match
          case QAll() => " implies "
          case _      => " and "
        val guard = extras.mkString(" and ")
        s"($guard)$joiner($bodyInner)"
    s"($keyword $bindings | $body)"

  private def buildBinding(ctx: Ctx, b: QuantifierBindingFull)(using
      AlloyLabel
  ): (String, Option[String]) =
    b.domain match
      case UnaryOpF(UPower(), inner, _) =>
        val innerType   = domainSigName(ctx, inner)
        val containment = s"${b.a} in ${renderExpr(ctx, inner)}"
        (s"${b.a}: set $innerType", Some(containment))
      case IdentifierF(name, _) =>
        val t = ctx.ir.c.find(_.name == name)
          .map(_.name)
          .orElse(ctx.ir.d.find(_.name == name).map(_.name))
        t match
          case Some(sigName) => (s"${b.a}: $sigName", None)
          case None =>
            if ctx.stateFields.contains(name) || ctx.inputFields.contains(name) then
              val elem = domainSigName(ctx, b.domain)
              (s"${b.a}: $elem", Some(s"${b.a} in ${renderExpr(ctx, b.domain)}"))
            else (s"${b.a}: $name", None)
      case _ =>
        (s"${b.a}: ${renderExpr(ctx, b.domain)}", None)

  private def domainSigName(ctx: Ctx, e: expr_full)(using AlloyLabel): String = e match
    case IdentifierF(name, _) =>
      ctx.stateFields.get(name).orElse(ctx.inputFields.get(name)) match
        case Some(t) => fieldElementSigName(t)
        case None =>
          ctx.ir.c.find(_.name == name).map(_.name)
            .orElse(ctx.ir.d.find(_.name == name).map(_.name))
            .getOrElse(name)
    case _ =>
      failAlloy(
        "powerset binder domain must be an identifier referring to an entity or set-typed state"
      )

  private def fieldElementSigName(t: type_expr_full)(using AlloyLabel): String = t match
    case NamedTypeF(name, _)                 => mapPrimitive(name)
    case SetTypeF(NamedTypeF(name, _), _)    => mapPrimitive(name)
    case OptionTypeF(NamedTypeF(name, _), _) => mapPrimitive(name)
    case other =>
      failAlloy(s"unsupported quantifier domain field type: $other")

  private def renderCall(ctx: Ctx, callee: expr_full, args: List[expr_full])(using
      AlloyLabel
  ): String =
    callee match
      case IdentifierF(name, _) =>
        val rendered = args.map(renderExpr(ctx, _)).mkString(", ")
        s"$name[$rendered]"
      case _ =>
        failAlloy(s"Alloy translator only supports identifier-called functions; got $callee")

  private def sanitizeName(name: String): String =
    name.filter(c => c.isLetterOrDigit || c == '_')
