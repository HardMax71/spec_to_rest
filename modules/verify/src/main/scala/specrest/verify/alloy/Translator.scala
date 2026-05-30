package specrest.verify.alloy

import cats.effect.IO
import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

import scala.util.boundary

private type AlloyLabel = boundary.Label[Either[VerifyError.AlloyTranslator, Nothing]]

private def failAlloy(msg: String)(using AlloyLabel): Nothing =
  boundary.break(Left(VerifyError.AlloyTranslator(msg)))

extension (e: expr_full) private def spanOpt: Option[span_t] = spanOf(e)

object Translator:

  final private case class Ctx(
      ir: service_ir_full,
      stateFields: Map[String, type_expr_full],
      inputFields: Map[String, type_expr_full] = Map.empty,
      currentStateSig: String = "State",
      postStateSig: String = "State",
      boundVars: Set[String] = Set.empty
  )

  def translateGlobal(
      ir: service_ir_full,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, AlloyModule]] =
    IO.delay {
      boundary:
        val ctx = buildCtx(ir)
        Right(
          AlloyModule(
            name = sanitizeName(svcName(ir)),
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
      ir: service_ir_full,
      decl: temporal_decl_full,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, TemporalTranslation]] =
    IO.delay {
      boundary:
        val ctx = buildCtx(ir)
        Right(tmpBody(decl) match
          case TbAlways(arg) =>
            val body = renderExpr(ctx, arg)
            val module = AlloyModule(
              name = sanitizeName(svcName(ir)),
              sigs = buildSigs(ctx),
              facts = invariantFacts(ctx, ir) :+
                AlloyFact(Some(s"${tmpName(decl)}_counterexample"), s"not ($body)", tmpSpan(decl)),
              commands = List(AlloyCommand(tmpName(decl), AlloyCommandKind.Run, "", scope))
            )
            TemporalTranslation(TemporalKind.Always, module)
          case TbEventually(arg) =>
            val module = AlloyModule(
              name = sanitizeName(svcName(ir)),
              sigs = buildSigs(ctx),
              facts = invariantFacts(ctx, ir) :+
                AlloyFact(Some(s"${tmpName(decl)}_witness"), renderExpr(ctx, arg), tmpSpan(decl)),
              commands = List(AlloyCommand(tmpName(decl), AlloyCommandKind.Run, "", scope))
            )
            TemporalTranslation(TemporalKind.Eventually, module)
          case TbFairness(_) =>
            failAlloy(
              s"temporal '${tmpName(decl)}': fairness(...) is not supported in v1; it requires trace-based " +
                "verification via Alloy's `var` sig mode which is future work"
            )
          case TbInvalid(raw) =>
            failAlloy(
              s"temporal '${tmpName(decl)}': only 'always(P)' and 'eventually(P)' are supported in v1; got " +
                s"${raw.getClass.getSimpleName}"
            )
        )
    }

  def translateOperationRequires(
      ir: service_ir_full,
      op: operation_decl_full,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, AlloyModule]] =
    IO.delay {
      boundary:
        val ctx = buildCtxWithInputs(ir, op)
        Right(AlloyModule(
          name = sanitizeName(svcName(ir)),
          sigs = buildSigs(ctx),
          facts = operRequires(op).zipWithIndex.map: (r, i) =>
            AlloyFact(Some(s"${operName(op)}_requires_$i"), renderExpr(ctx, r), r.spanOpt),
          commands =
            List(AlloyCommand(s"${operName(op)}_requires", AlloyCommandKind.Run, "", scope))
        ))
    }

  def translateOperationEnabled(
      ir: service_ir_full,
      op: operation_decl_full,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, AlloyModule]] =
    IO.delay {
      boundary:
        val ctx = buildCtxWithInputs(ir, op)
        val reqFacts = operRequires(op).zipWithIndex.map: (r, i) =>
          AlloyFact(Some(s"${operName(op)}_requires_$i"), renderExpr(ctx, r), r.spanOpt)
        Right(AlloyModule(
          name = sanitizeName(svcName(ir)),
          sigs = buildSigs(ctx),
          facts = invariantFacts(ctx, ir) ++ reqFacts,
          commands = List(AlloyCommand(s"${operName(op)}_enabled", AlloyCommandKind.Run, "", scope))
        ))
    }

  private def buildCtxWithInputs(ir: service_ir_full, op: operation_decl_full): Ctx =
    val stateFields = irStateFields(ir)
      .map(sf => stfName(sf) -> stfType(sf))
    val inputFields = operInputs(op).map(p => prmName(p) -> prmType(p))
    Ctx(ir, stateFields.toMap, inputFields.toMap)

  private def invariantFacts(ctx: Ctx, ir: service_ir_full)(using AlloyLabel): List[AlloyFact] =
    svcInvariants(ir).zipWithIndex.map { case (inv, i) =>
      AlloyFact(
        Some(invName(inv).getOrElse(s"inv_$i")),
        renderExpr(ctx, invBody(inv)),
        invSpan(inv)
      )
    }

  def translateOperationPreservation(
      ir: service_ir_full,
      op: operation_decl_full,
      inv: invariant_decl_full,
      scope: Int
  ): IO[Either[VerifyError.AlloyTranslator, AlloyModule]] =
    IO.delay {
      boundary:
        val preCtx  = buildCtxWithInputs(ir, op)
        val postCtx = preCtx.copy(postStateSig = "StatePost")
        val sigs    = buildPreservationSigs(preCtx)

        val invariantsPre = svcInvariants(ir).zipWithIndex.map {
          case (preInv, idx) =>
            val n = invName(preInv).getOrElse(s"inv_$idx")
            AlloyFact(Some(s"${n}_pre"), renderExpr(preCtx, invBody(preInv)), invSpan(preInv))
        }

        val requiresFacts = operRequires(op).zipWithIndex.map: (r, i) =>
          AlloyFact(Some(s"${operName(op)}_requires_$i"), renderExpr(preCtx, r), r.spanOpt)

        val ensuresFacts = operEnsures(op).zipWithIndex.map: (e, i) =>
          AlloyFact(Some(s"${operName(op)}_ensures_$i"), renderExpr(postCtx, e), e.spanOpt)

        val mentionedInEnsures = primedStateFields(operEnsures(op))
        val frameFacts = irStateFields(ir)
          .collect {
            case sf if !mentionedInEnsures.contains(stfName(sf)) =>
              AlloyFact(
                Some(s"frame_${stfName(sf)}"),
                s"StatePost.${stfName(sf)} = State.${stfName(sf)}",
                stfSpan(sf)
              )
          }

        val postStateCtx  = preCtx.copy(currentStateSig = "StatePost")
        val invariantName = invName(inv).getOrElse("invariant")
        val postViolation = AlloyFact(
          Some(s"${invariantName}_violated_post"),
          s"not (${renderExpr(postStateCtx, invBody(inv))})",
          invSpan(inv)
        )

        val allFacts = invariantsPre ++ requiresFacts ++ ensuresFacts ++ frameFacts :+ postViolation
        val cmdName  = s"${operName(op)}_preserves_$invariantName"
        Right(AlloyModule(
          name = sanitizeName(svcName(ir)),
          sigs = sigs,
          facts = allFacts,
          commands = List(AlloyCommand(cmdName, AlloyCommandKind.Run, "", scope))
        ))
    }

  private def runLiftedSigBuild(ctx: Ctx, includeStatePost: Boolean)(using
      AlloyLabel
  ): List[AlloySig] =
    SpecRestGenerated.buildAlloySigs(
      needsBoolSig(ctx),
      svcEntities(ctx.ir),
      svcEnums(ctx.ir),
      ctx.stateFields.toList,
      ctx.inputFields.toList,
      includeStatePost
    ) match
      case Some(sigs) => sigs.map(AlloyAdapter.fromLiftedSig)
      case None =>
        failAlloy(
          "unsupported Alloy field type (supported: NamedType, Set[T], Option[T])"
        )

  private def buildPreservationSigs(ctx: Ctx)(using AlloyLabel): List[AlloySig] =
    runLiftedSigBuild(ctx, includeStatePost = true)

  private def primedStateFields(ensures: List[expr_full]): Set[String] =
    collectPrimedIdentifiers(ensures).toSet

  private def buildCtx(ir: service_ir_full): Ctx =
    val stateFields = irStateFields(ir)
      .map(sf => stfName(sf) -> stfType(sf))
    Ctx(ir, stateFields.toMap)

  private def buildSigs(ctx: Ctx)(using AlloyLabel): List[AlloySig] =
    runLiftedSigBuild(ctx, includeStatePost = false)

  private def needsBoolSig(ctx: Ctx): Boolean =
    SpecRestGenerated.needsBoolSig(
      ctx.ir,
      ctx.stateFields.toList,
      ctx.inputFields.toList
    )

  private def renderExpr(ctx: Ctx, e: expr_full)(using AlloyLabel): String = e match
    case BinaryOpF(op, l, r, _) => renderBinaryOp(ctx, op, l, r)
    case UnaryOpF(op, x, _) =>
      alloyUnopShape(op) match
        case _: AusNot         => s"not (${renderExpr(ctx, x)})"
        case _: AusCardinality => s"#(${renderExpr(ctx, x)})"
        case _: AusMinusZero   => s"minus[0, ${renderExpr(ctx, x)}]"
        case _: AusUnsupported =>
          failAlloy(
            "standalone powerset '^s' is only supported as a binder domain (e.g. 'some t in ^s | ...')"
          )
    case q @ QuantifierF(_, _, _, _) => renderQuantifier(ctx, q)
    case FieldAccessF(b, f, _)       => s"(${renderExpr(ctx, b)}).$f"
    case EnumAccessF(_, m, _)        => m
    case IdentifierF(name, _) =>
      classifyAlloyIdentifier(
        name,
        ctx.boundVars.toList,
        ctx.stateFields.toList,
        ctx.inputFields.toList
      ) match
        case _: AikBoundVar   => name
        case _: AikStateField => s"${ctx.currentStateSig}.$name"
        case _: AikInputField => s"Inputs.$name"
        case _: AikPlain      => name
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
    alloyBinopShape(op) match
      case AbsLogical(tok)    => s"(($lr) $tok ($rr))"
      case AbsInfix(tok)      => s"($lr $tok $rr)"
      case AbsPrefixCall(tok) => s"$tok[$lr, $rr]"

  private def renderQuantifier(ctx: Ctx, q: QuantifierF)(using AlloyLabel): String =
    val bindings0 = q.b
    val hasPowersetBinder = bindings0.exists { qb =>
      qbdCollection(qb) match
        case UnaryOpF(UPower(), _, _) => true
        case _                        => false
    }
    val kindClass = alloyQuantifierClass(q.a)
    val isAll     = kindClass match { case _: AqAll => true; case _ => false }
    val isNo      = kindClass match { case _: AqNo => true; case _ => false }
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
    val keyword                         = alloyQuantifierKeyword(kindClass)
    val (binderParts, extraConstraints) = bindings0.map(buildBinding(ctx, _)).unzip
    val bindings                        = binderParts.mkString(", ")
    val innerCtx                        = ctx.copy(boundVars = ctx.boundVars ++ bindings0.map(qbdVar))
    val bodyInner                       = renderExpr(innerCtx, q.c)
    val extras                          = extraConstraints.flatten
    val body =
      if extras.isEmpty then bodyInner
      else
        val joiner = if isAll then " implies " else " and "
        val guard  = extras.mkString(" and ")
        s"($guard)$joiner($bodyInner)"
    s"($keyword $bindings | $body)"

  private def buildBinding(ctx: Ctx, b: quantifier_binding_full)(using
      AlloyLabel
  ): (String, Option[String]) =
    qbdCollection(b) match
      case UnaryOpF(UPower(), inner, _) =>
        val innerType   = domainSigName(ctx, inner)
        val containment = s"${qbdVar(b)} in ${renderExpr(ctx, inner)}"
        (s"${qbdVar(b)}: set $innerType", Some(containment))
      case IdentifierF(name, _) =>
        classifyAlloyBindingIdentifier(
          name,
          svcEntities(ctx.ir),
          svcEnums(ctx.ir),
          ctx.stateFields.toList,
          ctx.inputFields.toList
        ) match
          case AbirEntity(sn) => (s"${qbdVar(b)}: $sn", None)
          case AbirEnum(en)   => (s"${qbdVar(b)}: $en", None)
          case _: AbirStateOrInput =>
            val elem = domainSigName(ctx, qbdCollection(b))
            (s"${qbdVar(b)}: $elem", Some(s"${qbdVar(b)} in ${renderExpr(ctx, qbdCollection(b))}"))
          case _: AbirPlain => (s"${qbdVar(b)}: $name", None)
      case _ =>
        (s"${qbdVar(b)}: ${renderExpr(ctx, qbdCollection(b))}", None)

  private def domainSigName(ctx: Ctx, e: expr_full)(using AlloyLabel): String =
    SpecRestGenerated.domainSigNameAlloy(
      e,
      ctx.stateFields.toList,
      ctx.inputFields.toList,
      svcEntities(ctx.ir),
      svcEnums(ctx.ir)
    ) match
      case Some(n) => n
      case None =>
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
