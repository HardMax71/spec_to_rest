package specrest.dafny

import specrest.ir.Builtins
import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.immutable.ListMap
import scala.util.boundary

final case class DafnyError(message: String, span: Option[span_t])

final case class DafnyMethodHeader(
    name: String,
    signature: String,
    requiresClauses: List[String],
    ensuresClauses: List[String],
    modifiesClauses: List[String]
)

final case class DafnyOutput(text: String, methods: List[DafnyMethodHeader])

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
    aliasesWithWhere: Set[String],
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
      val (externs, matchPatterns) = classifyExterns(ir)
      val ctx = Ctx(
        ir = ir,
        stateFields = stateFields,
        aliasesWithWhere = aliasesWithWhere,
        externs = externs,
        matchPatterns = matchPatterns
      )

      val sb = new StringBuilder

      sb ++= header(svcName(ir))
      sb ++= optionDatatype()
      sb ++= theByFunction()

      val enumDecls                      = renderEnums(svcEnums(ir))
      val typeAliases                    = renderTypeAliases(ctx, svcTypeAliases(ir))
      val (entityDecls, entitiesWithInv) = renderEntities(ctx, svcEntities(ir))
      val stateClass                     = renderStateClass(ctx)
      val derived                        = derivedStateClauses(ctx, entitiesWithInv)
      val invPredicate                   = renderInvariantPredicate(ctx, svcInvariants(ir), derived)

      val methods = svcOperations(ir).map(op => renderMethod(ctx, op))

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

      val externDecls = renderExterns(ctx)
      if externDecls.nonEmpty then
        sb ++= "\n"
        sb ++= externDecls

      methods.foreach: rendered =>
        sb ++= "\n"
        sb ++= rendered.text

      Right(DafnyOutput(sb.toString, methods.map(_.header)))

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

  private def renderEntities(
      ctx: Ctx,
      decls: List[entity_decl]
  )(using DafnyLabel): (String, Set[String]) =
    val sb      = new StringBuilder
    val withInv = Set.newBuilder[String]
    decls.foreach: d =>
      val name       = entName(d)
      val fields     = entFields(d)
      val invariants = entInvariants(d)
      val ctorFields = fields.map { f =>
        s"${fldName(f)}: ${renderType(ctx, fldType(f))}"
      }
      sb ++= s"datatype $name = $name(${ctorFields.mkString(", ")})\n"

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

      val invClauses =
        if invariants.nonEmpty then
          val invCtx = ctx.copy(boundVars = ctx.boundVars + "x")
          invariants.map(e => s"(${renderEntityInvariant(invCtx, e, name)})")
        else Nil

      val allClauses = (fieldWhereClauses ++ aliasFieldClauses ++ invClauses).distinct
      if allClauses.nonEmpty then
        withInv += name
        sb ++= s"\npredicate ${name}Inv(x: $name)\n{\n"
        sb ++= s"  ${allClauses.mkString("\n  && ")}\n"
        sb ++= "}\n"
    (sb.toString, withInv.result())

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
  private def derivedStateClauses(ctx: Ctx, entitiesWithInv: Set[String])(using
      DafnyLabel
  ): List[String] =
    ctx.stateFields.toList.flatMap: (name, t) =>
      t match
        case _: NamedTypeF =>
          aliasWhereCall(ctx, s"st.$name", t).map(c => s"($c)").toList
        case RelationTypeF(k, _, v, _) =>
          relationClauses(ctx, name, k, v, entitiesWithInv)
        case MapTypeF(k, v, _) =>
          relationClauses(ctx, name, k, v, entitiesWithInv)
        case _ => Nil

  private def relationClauses(
      ctx: Ctx,
      name: String,
      k: type_expr,
      v: type_expr,
      entitiesWithInv: Set[String]
  )(using DafnyLabel): List[String] =
    val keyClause =
      aliasWhereCall(ctx, "k", k).map(w => s"(forall k :: k in st.$name ==> $w)")
    val valueClause = v match
      case NamedTypeF(vn, _) if entitiesWithInv.contains(vn) =>
        Some(s"(forall k :: k in st.$name ==> ${vn}Inv(st.$name[k]))")
      case _ =>
        aliasWhereCall(ctx, s"st.$name[k]", v)
          .map(w => s"(forall k :: k in st.$name ==> $w)")
    keyClause.toList ++ valueClause.toList

  private def renderInvariantPredicate(
      ctx: Ctx,
      invs: List[invariant_decl],
      derived: List[String]
  )(using DafnyLabel): Option[String] =
    if ctx.stateFields.isEmpty then None
    else
      val invCtx = ctx.copy(stateMode = StateMode.Direct)
      val parts  = invs.map(inv => s"(${renderExpr(invCtx, invBody(inv))})") ++ derived
      val body   = if parts.isEmpty then "true" else parts.mkString("\n  && ")
      Some(
        "predicate ServiceStateInv(st: ServiceState)\n" +
          "  reads st\n" +
          "{\n" +
          s"  $body\n" +
          "}\n"
      )

  final private case class RenderedMethod(text: String, header: DafnyMethodHeader)

  private def renderMethod(ctx: Ctx, op: operation_decl)(using DafnyLabel): RenderedMethod =
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

    val aliasInputClauses = inputs.flatMap { p =>
      aliasWhereCall(ctx, prmName(p), prmType(p))
    }

    val reqCtx          = mctx.copy(stateMode = StateMode.Direct)
    val rawRequires     = flattenAndAll(operRequires(op)).map(renderExpr(reqCtx, _)).filter(_ != "true")
    val requiresClauses = invariantClauses ++ aliasInputClauses ++ rawRequires

    val ensCtx = mctx.copy(stateMode = StateMode.Old)
    val optNames =
      mctx.inputTypes.collect { case (n, _: OptionTypeF) => n }.toList :::
        mctx.outputTypes.collect {
          case (n, _: OptionTypeF) if !mctx.inputTypes.contains(n) => n
        }.toList
    val rawEnsures = flattenAndAll(
      operEnsures(op).map(e =>
        specrest.ir.generated.SpecRestGenerated.desugarOptionGuards(optNames, e)
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
    requiresClauses.foreach: r =>
      sb ++= s"  requires $r\n"
    ensuresClauses.foreach: e =>
      sb ++= s"  ensures $e\n"
    sb ++= "{\n"
    sb ++= "  // YOUR CODE HERE\n"
    sb ++= "}\n"

    RenderedMethod(
      sb.toString,
      DafnyMethodHeader(
        name = operName(op),
        signature = signature,
        requiresClauses = requiresClauses,
        ensuresClauses = ensuresClauses,
        modifiesClauses = modifiesClauses
      )
    )

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
    val externs = ListMap.from(externsAlist.map { case (n, info) => n -> toScalaExternInfo(info) })
    (externs, patterns)

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
      // call to the deterministic spec-level helper TheBy so the SAME witness is
      // referenced across ensures + body within one proof obligation. Inlining
      // `var x :| ...` instead would give Dafny a fresh witness each occurrence
      // and CEGIS cannot converge. Uniqueness + existence are the helper's
      // preconditions, discharged from spec invariants at the call site.
      //
      // The lambda body is guarded with `v in dom &&` because the body usually
      // dereferences `dom[v]` (a partial operation); without the guard, Dafny
      // rejects with "element might not be in domain" at every call site.
      val innerCtx = ctx.copy(boundVars = ctx.boundVars + v)
      val domStr   = renderExpr(ctx, dom)
      val bodyStr  = renderExpr(innerCtx, body)
      val keyType  = theByKeyType(ctx, dom)
      s"TheBy($domStr, ($v: $keyType) => $v in $domStr && $bodyStr)"
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
      case BinaryOpF(_, l, r, _) => walk(l); walk(r)
      case UnaryOpF(_, x, _)     => walk(x)
      case FieldAccessF(b, _, _) => walk(b)
      case IndexF(b, i, _)       => walk(b); walk(i)
      case PrimeF(x, _)          => walk(x)
      case PreF(x, _)            => walk(x)
      case CallF(c, args, _)     => walk(c); args.foreach(walk)
      case ConstructorF(_, fs, _) =>
        fs.foreach(fa => walk(fasValue(fa)))
      case WithF(b, fs, _) =>
        walk(b)
        fs.foreach(fa => walk(fasValue(fa)))
      case MapLiteralF(es, _) =>
        es.foreach { e =>
          walk(mpeKey(e)); walk(mpeValue(e))
        }
      case SetLiteralF(es, _)   => es.foreach(walk)
      case SeqLiteralF(es, _)   => es.foreach(walk)
      case IfF(c, t, e, _)      => walk(c); walk(t); walk(e)
      case SomeWrapF(x, _)      => walk(x)
      case LetF(_, value, _, _) => walk(value)
      case _                    => ()
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
    val lr = renderExpr(ctx, l)
    val rr = renderExpr(ctx, r)
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
