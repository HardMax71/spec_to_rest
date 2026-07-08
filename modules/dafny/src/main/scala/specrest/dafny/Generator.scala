package specrest.dafny

import specrest.ir.Builtins
import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.immutable.ListMap
import scala.util.boundary

final case class DafnyError(message: String, span: Option[span_t])

final case class DafnyCandidate(
    param: String,
    output: String,
    field: Option[String],
    sampleLength: Int,
    sampleCharset: String
)

final case class DafnyMethodHeader(
    name: String,
    signature: String,
    requiresClauses: List[String],
    ensuresClauses: List[String],
    modifiesClauses: List[String],
    candidates: List[DafnyCandidate] = Nil
)

final case class DafnyOutput(
    text: String,
    methods: List[DafnyMethodHeader],
    skipped: List[(String, String)] = Nil
)

private type DafnyLabel = boundary.Label[Either[DafnyError, DafnyOutput]]

private def failDafny(msg: String, sp: Option[span_t] = None)(using DafnyLabel): Nothing =
  boundary.break(Left(DafnyError(msg, sp)))

private enum StateMode derives CanEqual:
  case Direct, Old

private enum ExternKind derives CanEqual:
  case Predicate, IntFunction

final private case class ExternInfo(kind: ExternKind, arity: Int)

final private case class Ctx(
    ir: ServiceIRFull,
    stateFields: ListMap[String, type_expr],
    // (collection field, lambda field) -> element entity, for the sum
    // recognizer: sum(coll.items, i => i.f) lowers to a generated ghost
    // set-sum function instead of an extern.
    sumSpecs: Map[(String, String), String] = Map.empty,
    // Sum-consistency conjuncts call the ghost set-sum functions, so they
    // live in ghost invariant predicates carried on method contracts only;
    // the compiled twins keep every other conjunct.
    hasGhostInv: Boolean = false,
    // Rendered forms of expressions an earlier requires conjunct pins with
    // `!= none`: arithmetic on them unwraps through .value. Dafny re-checks
    // the guard ordering itself (the destructor's well-formedness fails
    // without a preceding Some-ness fact), so a wrong entry cannot verify.
    optGuarded: Set[String] = Set.empty,
    aliasesWithWhere: Set[String],
    samplerSpecs: ListMap[String, (Int, String)],
    externs: ListMap[String, ExternInfo],
    matchPatterns: List[String],
    inputTypes: ListMap[String, type_expr] = ListMap.empty,
    outputTypes: ListMap[String, type_expr] = ListMap.empty,
    boundVars: Set[String] = Set.empty,
    stateMode: StateMode = StateMode.Direct
):
  def inputNames: Set[String]  = inputTypes.keySet
  def outputNames: Set[String] = outputTypes.keySet

object Generator:

  def generate(ir: ServiceIRFull): Either[DafnyError, DafnyOutput] =
    boundary[Either[DafnyError, DafnyOutput]]:
      val stateFields = svcState(ir) match
        case Some(sd) =>
          stdFields(sd).map(sf => stfName(sf) -> stfType(sf)).to(ListMap)
        case None => ListMap.empty[String, type_expr]
      val aliasesWithWhere =
        svcTypeAliases(ir).filter(a => talConstraint(a).isDefined).map(talName).toSet
      val samplerSpecs             = deriveSamplerSpecs(ir)
      val (externs, matchPatterns) = classifyExterns(ir)
      val sumSpecs                 = collectSumSpecs(ir)
      // The ghost predicate only ever gets clauses from service invariants
      // and from entity invariants lifted through STATE relations, so a sum
      // on an entity unreachable from state must not set the flag: methods
      // would reference a predicate that is never emitted.
      def namedType(t: type_expr): Option[String] = t match
        case NamedTypeF(n, _) => Some(n)
        case _                => None
      // Mirrors derivedStateClauses exactly: only map and relation state
      // fields lift entity invariants into the state predicates, so only
      // their value entities can back a ghost clause. A seq-backed entity's
      // sum invariant never renders and must not set the flag.
      def stateValueEntity(t: type_expr): Option[String] = t match
        case MapTypeF(_, v, _)          => namedType(v)
        case RelationTypeF(_, _, to, _) => namedType(to)
        case _                          => None
      val stateEntityNames = stateFields.values.flatMap(stateValueEntity).toSet
      val anyGhostInv =
        svcInvariants(ir).exists(inv => containsSumCall(invBody(inv))) ||
          svcEntities(ir).exists(e =>
            stateEntityNames.contains(entName(e)) && entInvariants(e).exists(containsSumCall)
          )
      val ctx = Ctx(
        ir = ir,
        stateFields = stateFields,
        sumSpecs = sumSpecs,
        hasGhostInv = anyGhostInv && stateFields.nonEmpty,
        aliasesWithWhere = aliasesWithWhere,
        samplerSpecs = samplerSpecs,
        externs = externs,
        matchPatterns = matchPatterns
      )

      val sb = new StringBuilder

      sb ++= header(svcName(ir))
      sb ++= optionDatatype()
      sb ++= theByFunction()
      sb ++= theBySetFunction()
      sb ++= sumFunctions(sumSpecs)

      val enumDecls   = renderEnums(svcEnums(ir))
      val typeAliases = renderTypeAliases(ctx, svcTypeAliases(ir))
      val (entityDecls, entitiesWithInv, entitiesWithGhostInv) =
        renderEntities(ctx, svcEntities(ir))
      val stateClass   = renderStateClass(ctx)
      val derived      = derivedStateClauses(ctx, entitiesWithInv, ghost = false)
      val derivedGhost = derivedStateClauses(ctx, entitiesWithGhostInv, ghost = true)
      val (invPredicate, ghostInvPredicate) =
        renderInvariantPredicate(ctx, svcInvariants(ir), derived, derivedGhost)

      // A construct the transform cannot lower downgrades its own operation
      // (it keeps the fail-loud stub and synthesis skips it) instead of
      // failing the whole spec. The per-op boundary shares failDafny's label
      // type, so the rendered method rides out through the builder and the
      // placeholder Right is never read.
      val attempts = svcOperations(ir).map { op =>
        val rendered = List.newBuilder[RenderedMethod]
        val res = boundary[Either[DafnyError, DafnyOutput]]:
          rendered += renderMethod(ctx, op)
          Right(DafnyOutput("", Nil))
        (operName(op), res, rendered.result())
      }
      val methods = attempts.flatMap(_._3)
      val skipped = attempts.collect { case (name, Left(err), _) => name -> err.message }

      if enumDecls.nonEmpty then
        sb ++= "\n"
        sb ++= enumDecls
      if typeAliases.nonEmpty then
        sb ++= "\n"
        sb ++= typeAliases
      if entityDecls.nonEmpty then
        sb ++= "\n"
        sb ++= entityDecls
      stateClass.foreach: cls =>
        sb ++= "\n"
        sb ++= cls
      invPredicate.foreach: pred =>
        sb ++= "\n"
        sb ++= pred
      ghostInvPredicate.foreach: pred =>
        sb ++= "\n"
        sb ++= pred

      val externDecls = renderExterns(ctx)
      if externDecls.nonEmpty then
        sb ++= "\n"
        sb ++= externDecls

      methods.foreach: rendered =>
        sb ++= "\n"
        sb ++= rendered.text

      Right(DafnyOutput(sb.toString, methods.map(_.header), skipped))

  private def header(serviceName: String): String =
    s"// AUTO-GENERATED Dafny skeleton for service $serviceName.\n" +
      "// Spec-derived signatures and contracts are immutable; only method bodies are synthesized.\n"

  private def optionDatatype(): String =
    "\ndatatype Option<T> = None | Some(value: T)\n"

  // Deterministic spec-level "unique element of a map" used to lower `TheF` in a way
  // Dafny can verify across ensures + body. Same call (same map, same predicate)
  // yields the same key — so a method body that mutates `m[TheBy(m, p)]` discharges
  // an ensures that references `m[TheBy(m, p)]`. The two preconditions force the
  // caller to prove existence + uniqueness from spec invariants; without that, the
  // call site fails to verify (which is the right outcome — `the X | P(X)` is
  // undefined when P matches zero or many elements).
  private def theByFunction(): String =
    "\nghost function TheBy<K, V>(m: map<K, V>, p: K -> bool): K\n" +
      "  requires exists k :: k in m && p(k)\n" +
      "  requires forall k1, k2 :: k1 in m && k2 in m && p(k1) && p(k2) ==> k1 == k2\n" +
      "  ensures TheBy(m, p) in m && p(TheBy(m, p))\n" +
      "{\n" +
      "  var k :| k in m && p(k); k\n" +
      "}\n"

  // Set-domain twin of TheBy for `the x in s | P(x)` where s is a set value
  // (an entity's collection field) rather than a state map: the witness is
  // the element itself, under the same existence + uniqueness preconditions.
  private def theBySetFunction(): String =
    "\nghost function TheBySet<T>(s: set<T>, p: T -> bool): T\n" +
      "  requires exists x :: x in s && p(x)\n" +
      "  requires forall x1, x2 :: x1 in s && x2 in s && p(x1) && p(x2) ==> x1 == x2\n" +
      "  ensures TheBySet(s, p) in s && p(TheBySet(s, p))\n" +
      "{\n" +
      "  var x :| x in s && p(x); x\n" +
      "}\n"

  private def renderEnums(decls: List[enum_decl])(using DafnyLabel): String =
    val parts = decls.map { d =>
      val ctors = enmVariants(d).mkString(" | ")
      s"datatype ${enmName(d)} = $ctors\n"
    }
    parts.mkString

  private def renderTypeAliases(ctx: Ctx, decls: List[type_alias_decl])(using
      DafnyLabel
  ): String =
    val sb = new StringBuilder
    decls.foreach: d =>
      val name    = talName(d)
      val baseStr = renderType(ctx, talType(d))
      sb ++= s"type $name = $baseStr\n"
      talConstraint(d).foreach: predExpr =>
        val whereCtx = ctx.copy(boundVars = ctx.boundVars + "value")
        val rendered = renderExpr(whereCtx, predExpr)
        sb ++= s"predicate ${name}Where(value: $baseStr)\n"
        sb ++= "{\n"
        sb ++= s"  $rendered\n"
        sb ++= "}\n"
    sb.toString

  private def containsSumCall(e: expr): Boolean =
    specrest.ir.generated.SpecRestGenerated.allSubexprs(e).exists {
      case CallF(IdentifierF("sum", _), _, _) => true
      case _                                  => false
    }

  private def renderEntities(
      ctx: Ctx,
      decls: List[entity_decl]
  )(using DafnyLabel): (String, Set[String], Set[String]) =
    val sb           = new StringBuilder
    val withInv      = Set.newBuilder[String]
    val withGhostInv = Set.newBuilder[String]
    val baseClauses = decls.map { d =>
      val name       = entName(d)
      val fields     = entFields(d)
      val invariants = entInvariants(d)

      val fieldWhereClauses = fields.flatMap { f =>
        fldDefault(f).map { whereExpr =>
          val whereCtx = ctx.copy(boundVars = ctx.boundVars + "value" + "x")
          val rebound =
            subst("value", FieldAccessF(IdentifierF("x", None), fldName(f), None), whereExpr)
          s"(${renderExpr(whereCtx, rebound)})"
        }
      }

      // Alias-typed fields carry their type's Where; the database enforces the
      // same refinement as a CHECK constraint, so it must be part of the
      // entity's invariant or verified state mutations could fail at persist.
      val aliasFieldClauses = fields.flatMap { f =>
        aliasWhereCall(ctx, s"x.${fldName(f)}", fldType(f)).map(c => s"($c)")
      }

      val invCtx                             = ctx.copy(boundVars = ctx.boundVars + "x")
      val (ghostInvariants, plainInvariants) = invariants.partition(containsSumCall)
      val invClauses =
        plainInvariants.map(e => s"(${renderEntityInvariant(invCtx, e, name)})")
      val ghostClauses =
        ghostInvariants.map(e => s"(${renderEntityInvariant(invCtx, e, name)})")

      (d, (fieldWhereClauses ++ aliasFieldClauses ++ invClauses).distinct, ghostClauses)
    }
    val invEntityNames = baseClauses.collect {
      case (d, all, _) if all.nonEmpty => entName(d)
    }.toSet

    baseClauses.foreach: (d, base, ghostClauses) =>
      val name   = entName(d)
      val fields = entFields(d)
      val ctorFields = fields.map { f =>
        s"${fldName(f)}: ${renderType(ctx, fldType(f))}"
      }
      sb ++= s"datatype $name = $name(${ctorFields.mkString(", ")})\n"

      // An entity-set field's elements satisfy their own invariant (the
      // runtime marshals every stored element through the same checks), so
      // it lifts into the owner: the state predicate reaches nested items
      // exactly as the typed Z3 encoding already assumes.
      val nestedSetClauses = fields.flatMap { f =>
        fldType(f) match
          case SetTypeF(NamedTypeF(en, _), _) if invEntityNames(en) =>
            Some(s"(forall i :: i in x.${fldName(f)} ==> ${en}Inv(i))")
          case _ => None
      }

      val allClauses = base ++ nestedSetClauses
      if allClauses.nonEmpty then
        withInv += name
        sb ++= s"\npredicate ${name}Inv(x: $name)\n{\n"
        sb ++= s"  ${allClauses.mkString("\n  && ")}\n"
        sb ++= "}\n"
      if ghostClauses.nonEmpty then
        withGhostInv += name
        sb ++= s"\nghost predicate ${name}InvGhost(x: $name)\n{\n"
        sb ++= s"  ${ghostClauses.mkString("\n  && ")}\n"
        sb ++= "}\n"
    (sb.toString, withInv.result(), withGhostInv.result())

  private def renderEntityInvariant(ctx: Ctx, expr: expr, entityName: String)(using
      DafnyLabel
  ): String =
    val rewritten =
      specrest.ir.generated.SpecRestGenerated
        .rewriteEntityFieldRefs(entityFieldNames(svcEntities(ctx.ir), entityName), expr)
    renderExpr(ctx, rewritten)

  private def renderStateClass(ctx: Ctx)(using DafnyLabel): Option[String] =
    if ctx.stateFields.isEmpty then None
    else
      val sb = new StringBuilder
      sb ++= "class ServiceState\n{\n"
      ctx.stateFields.foreach: (name, t) =>
        sb ++= s"  var $name: ${renderType(ctx, t)}\n"
      sb ++= "}\n"
      Some(sb.toString)

  // Refinements the database also enforces (key/value aliases, entity field
  // aliases via <Entity>Inv). Making them part of ServiceStateInv means a
  // state hydrated from valid rows satisfies the methods' preconditions, and
  // invariant preservation forbids mutations the schema's CHECK constraints
  // would reject at persist time.
  private def derivedStateClauses(
      ctx: Ctx,
      entitiesWithInv: Set[String],
      ghost: Boolean
  )(using DafnyLabel): List[String] =
    ctx.stateFields.toList.flatMap: (name, t) =>
      t match
        case _: NamedTypeF =>
          if ghost then Nil
          else aliasWhereCall(ctx, s"st.$name", t).map(c => s"($c)").toList
        case RelationTypeF(k, m, v, _) =>
          // set/some relations render as map<K, set<V>>, so their value
          // clauses quantify over the element set instead of the map image.
          val elementValued = m match
            case MultSet() | MultSome() => true
            case _                      => false
          relationClauses(ctx, name, k, v, entitiesWithInv, elementValued, ghost)
        case MapTypeF(k, v, _) =>
          relationClauses(ctx, name, k, v, entitiesWithInv, elementValued = false, ghost)
        case _ => Nil

  private def relationClauses(
      ctx: Ctx,
      name: String,
      k: type_expr,
      v: type_expr,
      entitiesWithInv: Set[String],
      elementValued: Boolean,
      ghost: Boolean
  )(using DafnyLabel): List[String] =
    val keyClause =
      if ghost then None
      else aliasWhereCall(ctx, "k", k).map(w => s"(forall k :: k in st.$name ==> $w)")
    val invName = if ghost then "InvGhost" else "Inv"
    val (valueRef, wrap) =
      if elementValued then
        ("v", (c: String) => s"(forall k :: k in st.$name ==> forall v :: v in st.$name[k] ==> $c)")
      else (s"st.$name[k]", (c: String) => s"(forall k :: k in st.$name ==> $c)")
    val valueClause = v match
      case NamedTypeF(vn, _) if entitiesWithInv.contains(vn) =>
        Some(wrap(s"$vn$invName($valueRef)"))
      case _ =>
        if ghost then None else aliasWhereCall(ctx, valueRef, v).map(wrap)
    keyClause.toList ++ valueClause.toList

  private def renderInvariantPredicate(
      ctx: Ctx,
      invs: List[invariant_decl],
      derived: List[String],
      derivedGhost: List[String]
  )(using DafnyLabel): (Option[String], Option[String]) =
    if ctx.stateFields.isEmpty then (None, None)
    else
      val invCtx                 = ctx.copy(stateMode = StateMode.Direct)
      val (ghostInvs, plainInvs) = invs.partition(inv => containsSumCall(invBody(inv)))
      val parts                  = plainInvs.map(inv => s"(${renderExpr(invCtx, invBody(inv))})") ++ derived
      val body                   = if parts.isEmpty then "true" else parts.mkString("\n  && ")
      val compiled =
        "predicate ServiceStateInv(st: ServiceState)\n" +
          "  reads st\n" +
          "{\n" +
          s"  $body\n" +
          "}\n"
      val ghostParts =
        ghostInvs.map(inv => s"(${renderExpr(invCtx, invBody(inv))})") ++ derivedGhost
      // Sum-consistency lives here: the compiled twins cannot evaluate the
      // ghost set-sums, so these conjuncts ride on method contracts only.
      // Verified bodies preserve them; hydrated state is trusted for them
      // the way candidate freshness already trusts the sampler.
      val ghost =
        if ghostParts.isEmpty then None
        else
          Some(
            "ghost predicate ServiceStateInvGhost(st: ServiceState)\n" +
              "  reads st\n" +
              "{\n" +
              s"  ${ghostParts.mkString("\n  && ")}\n" +
              "}\n"
          )
      (Some(compiled), ghost)

  final private case class RenderedMethod(text: String, header: DafnyMethodHeader)

  // The proven lowering runs against the dafny-scoped view of the operation
  // only: candidates must never surface in the HTTP contract, so conventions,
  // OpenAPI, and testgen all keep seeing the unlowered IR.
  private def renderMethod(ctx: Ctx, op0: operation_decl)(using DafnyLabel): RenderedMethod =
    val (op, rawCandidates) = specrest.ir.generated.SpecRestGenerated.lowerFreshOutputs(
      ctx.samplerSpecs.keys.toList,
      svcEntities(ctx.ir),
      op0
    )
    val candidates = rawCandidates.flatMap { c =>
      import specrest.ir.generated.SpecRestGenerated.{candAlias, candField, candOutput, candParam}
      ctx.samplerSpecs.get(candAlias(c)).map { case (len, charset) =>
        DafnyCandidate(candParam(c), candOutput(c), candField(c), len, charset)
      }
    }
    val inputs  = operInputs(op)
    val outputs = operOutputs(op)
    val inputTypes = inputs
      .map(p => prmName(p) -> prmType(p))
      .to(ListMap)
    val outputTypes = outputs
      .map(p => prmName(p) -> prmType(p))
      .to(ListMap)

    val mctx = ctx.copy(
      inputTypes = inputTypes,
      outputTypes = outputTypes,
      boundVars = ctx.boundVars ++ inputTypes.keySet ++ outputTypes.keySet
    )

    val params = inputs.map { p =>
      s"${prmName(p)}: ${renderType(mctx, prmType(p))}"
    }
    val stateParam = if ctx.stateFields.nonEmpty then List("st: ServiceState") else Nil
    val sigParams  = (stateParam ++ params).mkString(", ")
    val returns =
      if outputs.isEmpty then ""
      else
        val rs = outputs.map(p => s"${prmName(p)}: ${renderType(mctx, prmType(p))}").mkString(", ")
        s" returns ($rs)"
    val signature = s"method ${operName(op)}($sigParams)$returns"

    val mutates = primedStateFields(operEnsures(op)).intersect(ctx.stateFields.keySet).nonEmpty
    val modifiesClauses =
      if mutates then List("st") else Nil

    val invariantClauses =
      if ctx.stateFields.nonEmpty then List("ServiceStateInv(st)")
      else Nil
    val ghostInvariantClauses =
      if ctx.hasGhostInv then List("ServiceStateInvGhost(st)") else Nil

    val aliasInputClauses = inputs.flatMap { p =>
      aliasWhereCall(ctx, prmName(p), prmType(p))
    }

    val reqCtx = mctx.copy(stateMode = StateMode.Direct)
    // Requires need the option-guard desugar too: a guarded consequent like
    // `title != none implies len(title) >= 1` reads the payload.
    val optInputNames = mctx.inputTypes.collect { case (n, _: OptionTypeF) => n }.toList
    val optFieldNamesReq = svcEntities(ctx.ir)
      .flatMap(entFields)
      .collect {
        case f if fldType(f) match { case OptionTypeF(_, _) => true; case _ => false } =>
          fldName(f)
      }
      .distinct
    val desugaredRequires = flattenAndAll(
      operRequires(op).map(e =>
        specrest.ir.generated.SpecRestGenerated.desugarOptionGuards(
          optInputNames,
          optFieldNamesReq,
          e
        )
      )
    )
    val guardedOpt = desugaredRequires
      .collect { case BinaryOpF(BNeq(), g, NoneLitF(_), _) => g }
      .map(renderExpr(reqCtx, _))
      .toSet
    val reqCtxG = reqCtx.copy(optGuarded = guardedOpt)
    val rawRequires =
      desugaredRequires.map(renderExpr(reqCtxG, _)).filter(_ != "true")
    val requiresClauses = invariantClauses ++ aliasInputClauses ++ rawRequires

    val ensCtx = mctx.copy(stateMode = StateMode.Old)
    val optNames =
      mctx.inputTypes.collect { case (n, _: OptionTypeF) => n }.toList :::
        mctx.outputTypes.collect {
          case (n, _: OptionTypeF) if !mctx.inputTypes.contains(n) => n
        }.toList
    // Field names that are option-typed on any entity: comparing a guarded
    // option input against one keeps both sides wrapped.
    val optFieldNames = svcEntities(ctx.ir)
      .flatMap(entFields)
      .collect {
        case f if fldType(f) match { case OptionTypeF(_, _) => true; case _ => false } => fldName(f)
      }
      .distinct
    val rawEnsures = flattenAndAll(
      operEnsures(op).map(e =>
        specrest.ir.generated.SpecRestGenerated.desugarOptionGuards(optNames, optFieldNames, e)
      )
    )
      .map(injectWFGuards(_, ensCtx))
      .map(renderExpr(ensCtx, _))
      .filter(_ != "true")
    val ensuresClauses = rawEnsures ++ invariantClauses

    val sb = new StringBuilder
    // A compiled twin of the method's contract, rendered from the same clause
    // list, so the runtime guard the generated services call before the kernel
    // method can never drift from what was verified. Compiled Dafny does not
    // check `requires`, so without this a violating request corrupts or halts.
    sb ++= s"predicate Requires${operName(op)}($sigParams)\n"
    if ctx.stateFields.nonEmpty then sb ++= "  reads st\n"
    sb ++= "{\n"
    if requiresClauses.isEmpty then sb ++= "  true\n"
    else
      sb ++= s"  (${requiresClauses.head})\n"
      requiresClauses.tail.foreach: r =>
        sb ++= s"  && ($r)\n"
    sb ++= "}\n"
    sb ++= signature
    sb ++= "\n"
    modifiesClauses.foreach: m =>
      sb ++= s"  modifies $m\n"
    (requiresClauses ++ ghostInvariantClauses).foreach: r =>
      sb ++= s"  requires $r\n"
    (ensuresClauses ++ ghostInvariantClauses).foreach: e =>
      sb ++= s"  ensures $e\n"
    sb ++= "{\n"
    sb ++= "  // YOUR CODE HERE\n"
    sb ++= "}\n"

    RenderedMethod(
      sb.toString,
      DafnyMethodHeader(
        name = operName(op),
        signature = signature,
        requiresClauses = requiresClauses ++ ghostInvariantClauses,
        ensuresClauses = ensuresClauses ++ ghostInvariantClauses,
        modifiesClauses = modifiesClauses,
        candidates = candidates
      )
    )

  private def deriveSamplerSpecs(ir: ServiceIRFull): ListMap[String, (Int, String)] =
    import specrest.ir.generated.SpecRestGenerated.{samplerFor, walkStringConstraint, Nata, nat}
    def codepoints(s: String): List[nat] =
      s.codePoints().toArray.toList.map(cp => Nata(BigInt(cp)))
    svcTypeAliases(ir)
      .flatMap { a =>
        talConstraint(a).flatMap { c =>
          val (sc, skips) = walkStringConstraint(c)
          if skips.nonEmpty then None
          else
            val patterns = sc match
              case specrest.ir.generated.SpecRestGenerated.StringConstraint(_, _, ps, _, _) => ps
            samplerFor(sc, patterns.map(codepoints)).map { case (len, cs) =>
              val charset = cs.map { case Nata(v) =>
                new String(Character.toChars(v.toInt))
              }.mkString
              talName(a) -> (len.toInt, charset)
            }
        }
      }
      .to(ListMap)

  private def aliasWhereCall(ctx: Ctx, paramName: String, t: type_expr): Option[String] =
    t match
      case NamedTypeF(name, _) if ctx.aliasesWithWhere.contains(name) =>
        Some(s"${name}Where($paramName)")
      case _ => None

  private def classifyExterns(ir: ServiceIRFull): (ListMap[String, ExternInfo], List[String]) =
    def items(e: expr): List[extern_item] =
      specrest.ir.generated.SpecRestGenerated.collectExternItems(EkPredicate(), e)
    val all: List[extern_item] =
      svcInvariants(ir).map(invBody).flatMap(items) :::
        svcTemporals(ir).map(td => temporalArg(tmpBody(td))).flatMap(items) :::
        svcFacts(ir).map(fctBody).flatMap(items) :::
        svcEntities(ir).flatMap { e =>
          entInvariants(e).flatMap(items) :::
            entFields(e).flatMap(f => fldDefault(f)).flatMap(items)
        } :::
        svcTypeAliases(ir).flatMap(a => talConstraint(a)).flatMap(items) :::
        svcOperations(ir).flatMap { op =>
          operRequires(op).flatMap(items) ::: operEnsures(op).flatMap(items)
        }
    val (externsAlist, patterns) =
      specrest.ir.generated.SpecRestGenerated.classifyExternItems(all)
    // sum lowers through the generated set-sum functions, never as an extern.
    val externs = ListMap.from(
      externsAlist
        .filter { case (n, _) => n != "sum" }
        .map { case (n, info) => n -> toScalaExternInfo(info) }
    )
    (externs, patterns)

  // Every `sum(<...>.g, i => i.f)` in the spec resolves its element entity
  // structurally: exactly one entity may declare a Set[E] field named g whose
  // element E has a field f. Ambiguity leaves the pair unregistered and the
  // render site fails (downgrading the op, or the spec for invariants).
  private def collectSumSpecs(ir: ServiceIRFull): Map[(String, String), String] =
    val exprs: List[expr] =
      svcInvariants(ir).map(invBody) :::
        svcEntities(ir).flatMap(e => entInvariants(e)) :::
        svcOperations(ir).flatMap(op => operRequires(op) ::: operEnsures(op))
    val shapes = exprs
      .flatMap(specrest.ir.generated.SpecRestGenerated.allSubexprs)
      .collect {
        case CallF(
              IdentifierF("sum", _),
              List(coll, LambdaF(p, FieldAccessF(IdentifierF(p2, _), fld, _), _)),
              _
            ) if p2 == p =>
          collFieldName(coll).map(_ -> fld)
      }
      .flatten
      .distinct
    shapes.flatMap { case (collFld, lamFld) =>
      val elems = svcEntities(ir)
        .flatMap(e => entFields(e).find(f => fldName(f) == collFld).map(fldType))
        .collect { case SetTypeF(NamedTypeF(en, _), _) => en }
        .filter(en =>
          svcEntities(ir)
            .find(e => entName(e) == en)
            .exists(e => entFields(e).exists(f => fldName(f) == lamFld))
        )
        .distinct
      elems match
        case en :: Nil => List((collFld, lamFld) -> en)
        case _         => Nil
    }.toMap

  private def collFieldName(coll: expr): Option[String] = coll match
    case FieldAccessF(_, g, _) => Some(g)
    case _                     => None

  private def sumFnName(elem: String, fld: String): String = s"SumBy_${elem}_$fld"

  // A ghost recursive set-sum per (element entity, field), with the
  // pick-independence lemma SumByEq (the definitional axiom exposes only
  // SOME witness, so unfolding at a chosen element needs the exchange
  // argument) plus the add/remove corollaries method bodies actually use.
  private def sumFunctions(specs: Map[(String, String), String]): String =
    // Two set fields over the same element entity and field would emit the
    // same helper twice; the function is keyed by (element, field) alone.
    val parts = specs.toList.distinctBy { case ((_, fld), elem) => (elem, fld) }.map {
      case ((_, fld), elem) =>
        val fn = sumFnName(elem, fld)
        s"""|
          |ghost function $fn(s: set<$elem>): int
          |  decreases s
          |{
          |  if s == {} then 0
          |  else var x :| x in s; x.$fld + $fn(s - {x})
          |}
          |
          |lemma ${fn}Eq(s: set<$elem>, x: $elem)
          |  requires x in s
          |  ensures $fn(s) == x.$fld + $fn(s - {x})
          |  decreases |s|
          |{
          |  var y :| y in s && $fn(s) == y.$fld + $fn(s - {y});
          |  if y != x {
          |    calc {
          |      $fn(s);
          |      y.$fld + $fn(s - {y});
          |      { ${fn}Eq(s - {y}, x); }
          |      y.$fld + x.$fld + $fn(s - {y} - {x});
          |      { assert s - {y} - {x} == s - {x} - {y}; }
          |      y.$fld + x.$fld + $fn(s - {x} - {y});
          |      { ${fn}Eq(s - {x}, y); }
          |      x.$fld + $fn(s - {x});
          |    }
          |  }
          |}
          |
          |lemma ${fn}Add(s: set<$elem>, x: $elem)
          |  requires x !in s
          |  ensures $fn(s + {x}) == $fn(s) + x.$fld
          |{
          |  ${fn}Eq(s + {x}, x);
          |  assert (s + {x}) - {x} == s;
          |}
          |
          |lemma ${fn}Remove(s: set<$elem>, x: $elem)
          |  requires x in s
          |  ensures $fn(s - {x}) == $fn(s) - x.$fld
          |{
          |  ${fn}Eq(s, x);
          |}
          |
          |lemma ${fn}NonNeg(s: set<$elem>)
          |  requires forall x :: x in s ==> x.$fld >= 0
          |  ensures $fn(s) >= 0
          |  decreases |s|
          |{
          |  if s != {} {
          |    var y :| y in s && $fn(s) == y.$fld + $fn(s - {y});
          |    ${fn}NonNeg(s - {y});
          |  }
          |}
          |""".stripMargin
    }
    parts.mkString

  private def toScalaExternInfo(info: extern_info): ExternInfo = info match
    case ExInfo(k, arity) =>
      val kind = k match
        case _: EkPredicate   => ExternKind.Predicate
        case _: EkIntFunction => ExternKind.IntFunction
      ExternInfo(kind, arity.toInt)

  private def primedStateFields(ensures: List[expr]): Set[String] =
    collectPrimedIdentifiers(ensures).toSet

  private def renderExterns(ctx: Ctx): String =
    val sb = new StringBuilder
    ctx.externs.foreach: (name, info) =>
      // Known builtins (hash, now, time-units, abs) get their canonical Dafny
      // declaration from the Builtins registry — abstract function with the
      // correct return type. The verifier treats them as opaque (no body) so
      // it can reason about determinism without falsely axiomatizing a stub
      // return value (e.g. the old `function hash(...): int { 0 }` made
      // `hash(a) == hash(b)` always provably true — wrong).
      Builtins.byName.get(name).flatMap(_.dafnyDecl) match
        case Some(decl) =>
          sb ++= decl
          sb ++= "\n"
        case None =>
          val params = (1 to info.arity).map(i => s"x$i: string").mkString(", ")
          info.kind match
            case ExternKind.Predicate =>
              sb ++= s"predicate $name($params)\n"
              sb ++= "{\n"
              sb ++= "  true\n"
              sb ++= "}\n"
            case ExternKind.IntFunction =>
              sb ++= s"function $name($params): int\n"
              sb ++= "{\n"
              sb ++= "  0\n"
              sb ++= "}\n"
    ctx.matchPatterns.foreach: pat =>
      sb ++= s"predicate ${matchPredicateName(pat)}(s: string)\n"
      sb ++= "{\n"
      sb ++= "  true\n"
      sb ++= "}\n"
    sb.toString

  private def matchPredicateName(pattern: String): String =
    "matches_" + pattern.flatMap(c => if c.isLetterOrDigit then c.toString else "_")

  private def renderType(ctx: Ctx, t: type_expr)(using DafnyLabel): String = t match
    case NamedTypeF(name, _)   => mapPrimitiveType(name)
    case SetTypeF(inner, _)    => s"set<${renderType(ctx, inner)}>"
    case SeqTypeF(inner, _)    => s"seq<${renderType(ctx, inner)}>"
    case MapTypeF(k, v, _)     => s"map<${renderType(ctx, k)}, ${renderType(ctx, v)}>"
    case OptionTypeF(inner, _) => s"Option<${renderType(ctx, inner)}>"
    case RelationTypeF(from, mult, to, _) =>
      val k = renderType(ctx, from)
      val v = renderType(ctx, to)
      mult match
        case _: MultLone => s"map<$k, $v>"
        case _: MultOne  => s"map<$k, $v>"
        case _: MultSet  => s"map<$k, set<$v>>"
        case _: MultSome => s"map<$k, set<$v>>"

  private def mapPrimitiveType(name: String): String = name match
    case "Int"      => "int"
    case "Bool"     => "bool"
    case "String"   => "string"
    case "Float"    => "real"
    case "Decimal"  => "real"
    case "Money"    => "int"
    case "DateTime" => "int"
    case "Date"     => "int"
    case "UUID"     => "string"
    case "Bytes"    => "seq<int>"
    case other      => other

  private def renderExpr(ctx: Ctx, e: expr)(using DafnyLabel): String = e match
    case IntLitF(v, _)    => v.toString
    case BoolLitF(v, _)   => v.toString
    case StringLitF(s, _) => "\"" + escapeString(s) + "\""
    case NoneLitF(_)      => "None"
    case FloatLitF(v, _)  => v
    case IdentifierF(n, _) =>
      if ctx.boundVars.contains(n) then n
      else if ctx.stateFields.contains(n) then stateRef(n, ctx.stateMode)
      else if ctx.inputNames.contains(n) || ctx.outputNames.contains(n) then n
      else n
    case PrimeF(inner, _) =>
      // Only meaningful in an ensures clause (where the default stateMode is
      // Old). In an invariant or requires (stateMode = Direct), `'` switches
      // to Direct — which is the same as no marker. Dafny doesn't care.
      renderExpr(ctx.copy(stateMode = StateMode.Direct), inner)
    case PreF(inner, sp) =>
      // `pre(...)` only makes sense in a two-state context (operation ensures).
      // In an invariant or requires it would lower to `old(...)` inside a
      // predicate body — illegal in Dafny ("old expressions are not allowed in
      // this context"). Reject loudly so the spec author sees the issue; the
      // intent of a transition invariant belongs in per-operation ensures.
      if ctx.stateMode == StateMode.Direct then
        failDafny(
          "`pre(...)` is not valid in an invariant or requires clause " +
            "(it would lower to Dafny's `old(...)` inside a predicate body, which is ill-formed). " +
            "Move the two-state property to each operation's `ensures` instead.",
          sp
        )
      else renderExpr(ctx.copy(stateMode = StateMode.Old), inner)
    case BinaryOpF(BAdd(), lhs, MapLiteralF(List(MapEntryFull(k, v, _)), _), _)
        if isStateMapRef(ctx, lhs) =>
      s"${renderExpr(ctx, lhs)}[${renderExpr(ctx, k)} := ${renderExpr(ctx, v)}]"
    case BinaryOpF(op, l, r, _) => renderBinary(ctx, op, l, r)
    case UnaryOpF(op, x, _)     => renderUnary(ctx, op, x)
    case FieldAccessF(b, f, _)  => s"${renderExpr(ctx, b)}.$f"
    case EnumAccessF(_, m, _)   => m
    case IndexF(b, i, _)        => s"${renderExpr(ctx, b)}[${renderExpr(ctx, i)}]"
    case CallF(
          IdentifierF("sum", _),
          List(coll, LambdaF(p, FieldAccessF(IdentifierF(p2, _), fld, _), _)),
          sp
        ) if p2 == p =>
      collFieldName(coll).flatMap(cf => ctx.sumSpecs.get((cf, fld))) match
        case Some(elem) => s"${sumFnName(elem, fld)}(${renderExpr(ctx, coll)})"
        case None =>
          failDafny("sum: cannot resolve a unique element entity for the collection", sp)
    case CallF(IdentifierF("len", _), arg :: Nil, _) =>
      s"|${renderExpr(ctx, arg)}|"
    case CallF(IdentifierF("dom", _), arg :: Nil, _) =>
      s"${renderExpr(ctx, arg)}.Keys"
    case CallF(IdentifierF("ran", _), arg :: Nil, _) =>
      s"${renderExpr(ctx, arg)}.Values"
    case CallF(IdentifierF(name, _), args, _) =>
      val rendered = args.map(renderExpr(ctx, _)).mkString(", ")
      s"$name($rendered)"
    case CallF(other, _, sp) =>
      failDafny(s"unsupported callee shape: ${other.getClass.getSimpleName}", sp)
    case SomeWrapF(x, _) =>
      s"Some(${renderExpr(ctx, x)})"
    case MapLiteralF(entries, _) =>
      val parts = entries.map { e =>
        s"${renderExpr(ctx, mpeKey(e))} := ${renderExpr(ctx, mpeValue(e))}"
      }
      s"map[${parts.mkString(", ")}]"
    case SetLiteralF(elems, _) =>
      s"{${elems.map(renderExpr(ctx, _)).mkString(", ")}}"
    case SeqLiteralF(elems, _) =>
      s"[${elems.map(renderExpr(ctx, _)).mkString(", ")}]"
    case IfF(c, t, el, _) =>
      s"(if ${renderExpr(ctx, c)} then ${renderExpr(ctx, t)} else ${renderExpr(ctx, el)})"
    case LetF(v, value, body, _) =>
      val inner = ctx.copy(boundVars = ctx.boundVars + v)
      s"(var $v := ${renderExpr(ctx, value)}; ${renderExpr(inner, body)})"
    case q: QuantifierF => renderQuantifier(ctx, q)
    case MatchesF(x, p, _) =>
      s"${matchPredicateName(p)}(${renderExpr(ctx, x)})"
    case SetComprehensionF(v, dom, pred, _) =>
      val innerCtx = ctx.copy(boundVars = ctx.boundVars + v)
      val domStr   = renderExpr(ctx, dom)
      val predStr  = renderExpr(innerCtx, pred)
      val projection =
        if isMapDomain(ctx, dom) then s"$domStr[$v]" else v
      s"(set $v | $v in $domStr && $predStr :: $projection)"
    case TheF(v, dom, body, _) =>
      // `the v in dom | body` = the unique element satisfying body. Lowered to a
      // call to the deterministic spec-level helper TheBy (or its set twin) so
      // the SAME witness is referenced across ensures + body within one proof
      // obligation. Inlining `var x :| ...` instead would give Dafny a fresh
      // witness each occurrence and CEGIS cannot converge. Uniqueness +
      // existence are the helper's preconditions, discharged from spec
      // invariants at the call site.
      //
      // The lambda body is guarded with `v in dom &&` because a map-domain
      // body usually dereferences `dom[v]` (a partial operation); without the
      // guard, Dafny rejects with "element might not be in domain" at every
      // call site.
      val innerCtx = ctx.copy(boundVars = ctx.boundVars + v)
      val domStr   = renderExpr(ctx, dom)
      val bodyStr  = renderExpr(innerCtx, body)
      if isMapDomain(ctx, dom) then
        val keyType = theByKeyType(ctx, dom)
        s"TheBy($domStr, ($v: $keyType) => $v in $domStr && $bodyStr)"
      else
        exprType(ctx, dom) match
          case Some(SetTypeF(elem, _)) =>
            // No membership guard here: the set element is the value itself,
            // and a domain expression like `old(m)[k].items` inside the
            // lambda would re-read the map where the call site's guard
            // cannot reach (lambda well-formedness is checked standalone).
            s"TheBySet($domStr, ($v: ${renderType(ctx, elem)}) => $bodyStr)"
          case _ =>
            failDafny(
              s"TheBy: unsupported domain — expected a map/relation state field or a set-typed expression, got ${dom.getClass.getSimpleName}"
            )
    case WithF(base, fields, _) =>
      val parts = fields.map { fa =>
        s"${fasName(fa)} := ${renderExpr(ctx, fasValue(fa))}"
      }
      s"${renderExpr(ctx, base)}.(${parts.mkString(", ")})"
    case ConstructorF(name, fields, sp) =>
      val orderedArgs = orderConstructorArgs(ctx, name, fields, sp)
      s"$name(${orderedArgs.map(renderExpr(ctx, _)).mkString(", ")})"
    case LambdaF(p, body, _) =>
      val inner = ctx.copy(boundVars = ctx.boundVars + p)
      s"(($p: int) => ${renderExpr(inner, body)})"

  private def isMapDomain(ctx: Ctx, dom: expr): Boolean =
    peelRelationRef(dom).exists(isMapStateField(ctx, _))

  // The type of a domain expression, for the shapes a TheF domain can take:
  // a named binding, a map/relation read, and an entity field access, through
  // pre()/prime wrappers. Anything else stays None and the op fails loud.
  private def exprType(ctx: Ctx, e: expr): Option[type_expr] = e match
    case PreF(inner, _)   => exprType(ctx, inner)
    case PrimeF(inner, _) => exprType(ctx, inner)
    case IdentifierF(n, _) =>
      ctx.stateFields.get(n).orElse(ctx.inputTypes.get(n)).orElse(ctx.outputTypes.get(n))
    case IndexF(m, _, _) =>
      exprType(ctx, m).flatMap {
        case MapTypeF(_, v, _)          => Some(v)
        case RelationTypeF(_, _, to, _) => Some(to)
        case _                          => None
      }
    case FieldAccessF(base, f, _) =>
      exprType(ctx, base).flatMap {
        case NamedTypeF(en, _) =>
          svcEntities(ctx.ir)
            .find(d => entName(d) == en)
            .flatMap(d => entFields(d).find(fd => fldName(fd) == f))
            .map(fldType)
        case _ => None
      }
    case _ => None

  // Extract the key type of the map/relation `dom` refers to, so the lambda passed
  // to TheBy is fully type-annotated (Dafny cannot infer the parameter type through
  // a generic call).
  private def theByKeyType(ctx: Ctx, dom: expr)(using DafnyLabel): String =
    peelRelationRef(dom).flatMap(ctx.stateFields.get) match
      case Some(MapTypeF(k, _, _))            => renderType(ctx, k)
      case Some(RelationTypeF(from, _, _, _)) => renderType(ctx, from)
      case _ =>
        failDafny(
          s"TheBy: cannot infer key type — expected a map/relation state field, got ${dom.getClass.getSimpleName}"
        )

  private def isMapStateField(ctx: Ctx, name: String): Boolean =
    ctx.stateFields.get(name).exists {
      case _: MapTypeF | _: RelationTypeF => true
      case _                              => false
    }

  private def orderConstructorArgs(
      ctx: Ctx,
      entityName: String,
      assigns: List[field_assign],
      sp: Option[span_t]
  )(using DafnyLabel): List[expr] =
    val entity = entityByName(svcEntities(ctx.ir), entityName)
    entity match
      case Some(e) =>
        val byName = assigns.map(fa => fasName(fa) -> fasValue(fa)).toMap
        entFields(e).map(fldName).map { fn =>
          byName.getOrElse(
            fn,
            failDafny(s"constructor for $entityName missing field $fn", sp)
          )
        }
      case None =>
        failDafny(s"constructor references unknown entity $entityName", sp)

  private def isStateMapRef(ctx: Ctx, e: expr): Boolean =
    peelRelationRef(e).exists(ctx.stateFields.contains)

  // Auto-emit Dafny well-formedness guards for partial-map accesses in spec
  // ensures. The Python/TS/Go backends model `m[k].field` with partial-access
  // semantics (key-error at runtime); Dafny requires `k in m` to be statically
  // provable at every dereference. We walk each conjunct (descending through
  // `let` and `and`) and prepend `k in m` for every `m[k]` access where `m`
  // resolves to a state-map field. Bindings inside quantifiers / `the` /
  // set-comprehensions are skipped because their `k in dom` is already
  // implicit in the binder.
  private def injectWFGuards(e: expr, ctx: Ctx): expr =
    def go(node: expr): expr = node match
      case LetF(v, value, body, sp) =>
        // Guards from the let-VALUE are lifted OUT (the value isn't a boolean
        // conjunct — can't AND `k in m` to it). Guards from the BODY stay
        // inside because they may reference the let-bound `v`.
        val valueGuards = collectWFGuards(value, ctx)
        val newLet      = LetF(v, value, go(body), sp): expr
        valueGuards.foldRight(newLet)((g, acc) => BinaryOpF(BAnd(), g, acc, None))
      case BinaryOpF(BAnd(), l, r, sp) =>
        BinaryOpF(BAnd(), go(l), go(r), sp)
      case other =>
        val guards = collectWFGuards(other, ctx)
        if guards.isEmpty then other
        else guards.foldRight(other)((g, acc) => BinaryOpF(BAnd(), g, acc, None))
    go(e)

  private def collectWFGuards(e: expr, ctx: Ctx): List[expr] =
    val acc  = scala.collection.mutable.ListBuffer.empty[expr]
    val seen = scala.collection.mutable.HashSet.empty[String]
    def add(key: expr, mref: expr, sp: Option[span_t]): Unit =
      val guard = BinaryOpF(BIn(), key, mref, sp)
      val sig   = structuralSig(guard)
      if !seen.contains(sig) then
        seen += sig
        acc += guard
    def walk(node: expr): Unit = node match
      case IndexF(m, k, sp) if isStateMapRef(ctx, m) =>
        walk(m); walk(k); add(k, m, sp)
      // Dafny well-formedness is path-sensitive: the right side of || is
      // checked under the negated left, and ==> under the left, so guards
      // hoisted from there would strengthen the contract.
      case BinaryOpF(BOr(), l, _, _)      => walk(l)
      case BinaryOpF(BImplies(), l, _, _) => walk(l)
      case BinaryOpF(BAnd(), l, _, _)     => walk(l)
      case BinaryOpF(_, l, r, _)          => walk(l); walk(r)
      case UnaryOpF(_, x, _)              => walk(x)
      case FieldAccessF(b, _, _)          => walk(b)
      case IndexF(b, i, _)                => walk(b); walk(i)
      case PrimeF(x, _)                   => walk(x)
      case PreF(x, _)                     => walk(x)
      case CallF(c, args, _)              => walk(c); args.foreach(walk)
      case ConstructorF(_, fs, _) =>
        fs.foreach(fa => walk(fasValue(fa)))
      case WithF(b, fs, _) =>
        walk(b)
        fs.foreach(fa => walk(fasValue(fa)))
      case MapLiteralF(es, _) =>
        es.foreach { e =>
          walk(mpeKey(e)); walk(mpeValue(e))
        }
      case SetLiteralF(es, _) => es.foreach(walk)
      case SeqLiteralF(es, _) => es.foreach(walk)
      // If branches are likewise checked under the branch condition
      // (LoginFailed's fresh-email case became contractually false when the
      // then-branch guard was hoisted), so only the condition contributes.
      case IfF(c, _, _, _)      => walk(c)
      case SomeWrapF(x, _)      => walk(x)
      case LetF(_, value, _, _) => walk(value)
      // The binder's own membership is implicit, but the domain expression
      // may dereference a state map (`the i in pre(orders)[oid].items｜..`),
      // and that read needs its guard like any other.
      case TheF(_, dom, _, _) => walk(dom)
      case _                  => ()
    walk(e)
    acc.toList

  // Span-agnostic structural signature for dedup of generated guards.
  private def structuralSig(e: expr): String = e match
    case IdentifierF(n, _) => s"I($n)"
    case BinaryOpF(op, l, r, _) =>
      s"B(${op.getClass.getSimpleName},${structuralSig(l)},${structuralSig(r)})"
    case UnaryOpF(op, x, _)    => s"U(${op.getClass.getSimpleName},${structuralSig(x)})"
    case FieldAccessF(b, f, _) => s"F(${structuralSig(b)},$f)"
    case IndexF(b, i, _)       => s"X(${structuralSig(b)},${structuralSig(i)})"
    case PrimeF(x, _)          => s"P(${structuralSig(x)})"
    case PreF(x, _)            => s"R(${structuralSig(x)})"
    case CallF(c, args, _)     => s"C(${structuralSig(c)},${args.map(structuralSig).mkString(",")})"
    case IntLitF(v, _)         => s"i$v"
    case StringLitF(s, _)      => s"s${s.hashCode}"
    case BoolLitF(v, _)        => s"b$v"
    case _                     => s"O(${e.getClass.getSimpleName}${e.hashCode})"

  private def stateRef(name: String, mode: StateMode): String = mode match
    case StateMode.Direct => s"st.$name"
    case StateMode.Old    => s"old(st.$name)"

  private def renderBinary(ctx: Ctx, op: bin_op, l: expr, r: expr)(using
      DafnyLabel
  ): String =
    def unwrapIfGuarded(rendered: String): String =
      if ctx.optGuarded.contains(rendered) then s"$rendered.value" else rendered
    val arithmetic = op match
      case _: BAdd | _: BSub | _: BMul | _: BDiv | _: BLt | _: BLe | _: BGt | _: BGe =>
        true
      case _ => false
    val lr =
      if arithmetic then unwrapIfGuarded(renderExpr(ctx, l)) else renderExpr(ctx, l)
    val rr =
      if arithmetic then unwrapIfGuarded(renderExpr(ctx, r)) else renderExpr(ctx, r)
    op match
      case BAnd()       => s"($lr && $rr)"
      case BOr()        => s"($lr || $rr)"
      case BImplies()   => s"($lr ==> $rr)"
      case BIff()       => s"($lr <==> $rr)"
      case BEq()        => s"$lr == $rr"
      case BNeq()       => s"$lr != $rr"
      case BLt()        => s"$lr < $rr"
      case BLe()        => s"$lr <= $rr"
      case BGt()        => s"$lr > $rr"
      case BGe()        => s"$lr >= $rr"
      case BIn()        => s"$lr in $rr"
      case BNotIn()     => s"$lr !in $rr"
      case BSubset()    => s"$lr <= $rr"
      case BUnion()     => s"$lr + $rr"
      case BIntersect() => s"$lr * $rr"
      case BDiff()      => s"$lr - $rr"
      case BAdd()       => s"$lr + $rr"
      case BSub()       => s"$lr - $rr"
      case BMul()       => s"$lr * $rr"
      case BDiv()       => s"$lr / $rr"

  private def renderUnary(ctx: Ctx, op: un_op, x: expr)(using DafnyLabel): String =
    op match
      case _: UNot         => s"!(${renderExpr(ctx, x)})"
      case _: UNegate      => s"-(${renderExpr(ctx, x)})"
      case _: UCardinality => s"|${renderExpr(ctx, x)}|"
      case _: UPower =>
        failDafny("powerset operator '^' is not supported in Dafny translation")

  private def renderQuantifier(ctx: Ctx, q: QuantifierF)(using DafnyLabel): String =
    val bindings = q.b
    val varNames = bindings.map(qbdVar)
    val innerCtx = ctx.copy(boundVars = ctx.boundVars ++ varNames)
    val membership = bindings.map { b =>
      s"${qbdVar(b)} in ${renderExpr(ctx, qbdCollection(b))}"
    }
    val membershipExpr = membership.mkString(" && ")
    val body           = renderExpr(innerCtx, q.c)
    val varList        = varNames.mkString(", ")
    q.a match
      case _: QAll =>
        s"forall $varList :: ($membershipExpr) ==> ($body)"
      case _: QSome | _: QExists =>
        s"exists $varList :: ($membershipExpr) && ($body)"
      case _: QNo =>
        s"!(exists $varList :: ($membershipExpr) && ($body))"

  private def escapeString(s: String): String =
    s.flatMap:
      case '\\' => "\\\\"
      case '"'  => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c    => c.toString
