package specrest.verify.alloy

import cats.effect.IO
import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable
import scala.util.boundary

private type AlloyLabel = boundary.Label[Either[VerifyError.AlloyTranslator, Nothing]]

private def failAlloy(msg: String)(using AlloyLabel): Nothing =
  boundary.break(Left(VerifyError.AlloyTranslator(msg)))

extension (e: expr_full) private def spanOpt: Option[span_t] = spanOf(e)

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
            name = sanitizeName(ir.a),
            sigs = buildSigs(ctx),
            facts = invariantFacts(ctx, ir),
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
        Right(decl.b match
          case TbAlways(arg) =>
            val body = renderExpr(ctx, arg)
            val module = AlloyModule(
              name = sanitizeName(ir.a),
              sigs = buildSigs(ctx),
              facts = invariantFacts(ctx, ir) :+
                AlloyFact(Some(s"${decl.a}_counterexample"), s"not ($body)", decl.c),
              commands = List(AlloyCommand(decl.a, AlloyCommandKind.Run, "", scope))
            )
            TemporalTranslation(TemporalKind.Always, module)
          case TbEventually(arg) =>
            val module = AlloyModule(
              name = sanitizeName(ir.a),
              sigs = buildSigs(ctx),
              facts = invariantFacts(ctx, ir) :+
                AlloyFact(Some(s"${decl.a}_witness"), renderExpr(ctx, arg), decl.c),
              commands = List(AlloyCommand(decl.a, AlloyCommandKind.Run, "", scope))
            )
            TemporalTranslation(TemporalKind.Eventually, module)
          case TbFairness(_) =>
            failAlloy(
              s"temporal '${decl.a}': fairness(...) is not supported in v1; it requires trace-based " +
                "verification via Alloy's `var` sig mode which is future work"
            )
          case TbInvalid(raw) =>
            failAlloy(
              s"temporal '${decl.a}': only 'always(P)' and 'eventually(P)' are supported in v1; got " +
                s"${raw.getClass.getSimpleName}"
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
          name = sanitizeName(ir.a),
          sigs = buildSigs(ctx),
          facts = op.d.zipWithIndex.map: (r, i) =>
            AlloyFact(Some(s"${op.a}_requires_$i"), renderExpr(ctx, r), r.spanOpt),
          commands = List(AlloyCommand(s"${op.a}_requires", AlloyCommandKind.Run, "", scope))
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
          AlloyFact(Some(s"${op.a}_requires_$i"), renderExpr(ctx, r), r.spanOpt)
        Right(AlloyModule(
          name = sanitizeName(ir.a),
          sigs = buildSigs(ctx),
          facts = invariantFacts(ctx, ir) ++ reqFacts,
          commands = List(AlloyCommand(s"${op.a}_enabled", AlloyCommandKind.Run, "", scope))
        ))
    }

  private def buildCtxWithInputs(ir: ServiceIRFull, op: OperationDeclFull): Ctx =
    val stateFields = ir.f.toList.flatMap { case StateDeclFull(fs, _) => fs }
      .collect { case StateFieldDeclFull(n, t, _) => n -> t }
    val inputFields = op.b.collect { case ParamDeclFull(n, t, _) => n -> t }
    Ctx(ir, stateFields.toMap, inputFields.toMap)

  private def invariantFacts(ctx: Ctx, ir: ServiceIRFull)(using AlloyLabel): List[AlloyFact] =
    ir.i.zipWithIndex.collect { case (InvariantDeclFull(name, expr, sp), i) =>
      AlloyFact(Some(name.getOrElse(s"inv_$i")), renderExpr(ctx, expr), sp)
    }

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

        val invariantsPre = ir.i.zipWithIndex.collect {
          case (InvariantDeclFull(name, expr, sp), idx) =>
            val n = name.getOrElse(s"inv_$idx")
            AlloyFact(Some(s"${n}_pre"), renderExpr(preCtx, expr), sp)
        }

        val requiresFacts = op.d.zipWithIndex.map: (r, i) =>
          AlloyFact(Some(s"${op.a}_requires_$i"), renderExpr(preCtx, r), r.spanOpt)

        val ensuresFacts = op.e.zipWithIndex.map: (e, i) =>
          AlloyFact(Some(s"${op.a}_ensures_$i"), renderExpr(postCtx, e), e.spanOpt)

        val mentionedInEnsures = primedStateFields(op.e)
        val frameFacts = ir.f.toList.flatMap { case StateDeclFull(fs, _) => fs }
          .collect {
            case StateFieldDeclFull(n, _, sp) if !mentionedInEnsures.contains(n) =>
              AlloyFact(
                Some(s"frame_$n"),
                s"StatePost.$n = State.$n",
                sp
              )
          }

        val postStateCtx  = preCtx.copy(currentStateSig = "StatePost")
        val invariantName = inv.a.getOrElse("invariant")
        val postViolation = AlloyFact(
          Some(s"${invariantName}_violated_post"),
          s"not (${renderExpr(postStateCtx, inv.b)})",
          inv.c
        )

        val allFacts = invariantsPre ++ requiresFacts ++ ensuresFacts ++ frameFacts :+ postViolation
        val cmdName  = s"${op.a}_preserves_$invariantName"
        Right(AlloyModule(
          name = sanitizeName(ir.a),
          sigs = sigs,
          facts = allFacts,
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
    collectPrimedIdentifiers(ensures).toSet

  private def buildCtx(ir: ServiceIRFull): Ctx =
    val stateFields = ir.f.toList.flatMap { case StateDeclFull(fs, _) => fs }
      .collect { case StateFieldDeclFull(n, t, _) => n -> t }
    Ctx(ir, stateFields.toMap)

  private def buildSigs(ctx: Ctx)(using AlloyLabel): List[AlloySig] =
    val sigs = mutable.ArrayBuffer.empty[AlloySig]
    if needsBoolSig(ctx) then
      sigs += AlloySig("Bool", abstract_ = true)
      sigs += AlloySig("True", isOne = true, extends_ = Some("Bool"))
      sigs += AlloySig("False", isOne = true, extends_ = Some("Bool"))
    for case EntityDeclFull(name, _, fs, _, _) <- ctx.ir.c do
      val fields = fs.collect { case FieldDeclFull(fn, ft, _, _) =>
        val (mult, elem) = alloyFieldTypeOf(ft)
        AlloyField(fn, mult, elem)
      }
      sigs += AlloySig(name, fields = fields)
    for case EnumDeclFull(name, vs, _) <- ctx.ir.d do
      sigs += AlloySig(name, abstract_ = true)
      for v <- vs do
        sigs += AlloySig(v, isOne = true, extends_ = Some(name))
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
    SpecRestGenerated.needsBoolSig(
      ctx.ir,
      ctx.stateFields.toList,
      ctx.inputFields.toList
    )

  private def alloyFieldTypeOf(t: type_expr_full)(using
      AlloyLabel
  ): (AlloyFieldMultiplicity, String) =
    SpecRestGenerated.alloyFieldTypeOf(t) match
      case Some((m, n)) => (AlloyAdapter.fromLiftedMult(m), n)
      case None =>
        failAlloy(
          s"unsupported Alloy field type (supported: NamedType, Set[T], Option[T]); got $t"
        )

  private def fieldElementSigName(t: type_expr_full)(using AlloyLabel): String =
    SpecRestGenerated.fieldElementSigNameAlloy(t) match
      case Some(n) => n
      case None    => failAlloy(s"unsupported quantifier domain field type: $t")

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
    case IntLitF(int_of_integer(v), _) => v.toString
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
    val bindings0 = q.b.collect { case qb: QuantifierBindingFull => qb }
    val hasPowersetBinder = bindings0.exists {
      case QuantifierBindingFull(_, dom, _, _) =>
        dom match
          case UnaryOpF(UPower(), _, _) => true
          case _                        => false
    }
    val kind  = q.a
    val isAll = kind match { case _: QAll => true; case _ => false }
    val isNo  = kind match { case _: QNo => true; case _ => false }
    if hasPowersetBinder && isAll then
      failAlloy(
        "universal quantification over a powerset ('all t in ^s | ...') requires higher-order " +
          "reasoning that Alloy rejects as non-skolemizable. Rewrite as an existential " +
          "('some t in ^s | ...') or as a first-order statement about s (e.g. 'all x in s | ...')."
      )
    if hasPowersetBinder && isNo then
      failAlloy(
        "'no t in ^s | ...' is a negated universal over a powerset; Alloy rejects it as " +
          "higher-order for the same reason as 'all'. Rewrite to a first-order statement."
      )
    val keyword = kind match
      case _: QAll    => "all"
      case _: QSome   => "some"
      case _: QExists => "some"
      case _: QNo     => "no"
    val (binderParts, extraConstraints) = bindings0.map(buildBinding(ctx, _)).unzip
    val bindings                        = binderParts.mkString(", ")
    val innerCtx                        = ctx.copy(boundVars = ctx.boundVars ++ bindings0.map(_.a))
    val bodyInner                       = renderExpr(innerCtx, q.c)
    val extras                          = extraConstraints.flatten
    val body =
      if extras.isEmpty then bodyInner
      else
        val joiner = kind match
          case _: QAll => " implies "
          case _       => " and "
        val guard = extras.mkString(" and ")
        s"($guard)$joiner($bodyInner)"
    s"($keyword $bindings | $body)"

  private def buildBinding(ctx: Ctx, b: QuantifierBindingFull)(using
      AlloyLabel
  ): (String, Option[String]) =
    b.b match
      case UnaryOpF(UPower(), inner, _) =>
        val innerType   = domainSigName(ctx, inner)
        val containment = s"${b.a} in ${renderExpr(ctx, inner)}"
        (s"${b.a}: set $innerType", Some(containment))
      case IdentifierF(name, _) =>
        val t = entityNameInList(ctx.ir.c, name)
          .orElse(ctx.ir.d.collectFirst { case e: EnumDeclFull if e.a == name => e.a })
        t match
          case Some(sigName) => (s"${b.a}: $sigName", None)
          case None =>
            if ctx.stateFields.contains(name) || ctx.inputFields.contains(name) then
              val elem = domainSigName(ctx, b.b)
              (s"${b.a}: $elem", Some(s"${b.a} in ${renderExpr(ctx, b.b)}"))
            else (s"${b.a}: $name", None)
      case _ =>
        (s"${b.a}: ${renderExpr(ctx, b.b)}", None)

  private def domainSigName(ctx: Ctx, e: expr_full)(using AlloyLabel): String = e match
    case IdentifierF(name, _) =>
      ctx.stateFields.get(name).orElse(ctx.inputFields.get(name)) match
        case Some(t) => fieldElementSigName(t)
        case None =>
          entityNameInList(ctx.ir.c, name)
            .orElse(ctx.ir.d.collectFirst { case e: EnumDeclFull if e.a == name => e.a })
            .getOrElse(name)
    case _ =>
      failAlloy(
        "powerset binder domain must be an identifier referring to an entity or set-typed state"
      )

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
