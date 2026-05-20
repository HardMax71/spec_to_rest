package specrest.convention.dafny

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.immutable.ListMap
import scala.collection.mutable
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
    stateFields: ListMap[String, type_expr_full],
    aliasesWithWhere: Set[String],
    externs: ListMap[String, ExternInfo],
    matchPatterns: List[String],
    inputTypes: ListMap[String, type_expr_full] = ListMap.empty,
    outputTypes: ListMap[String, type_expr_full] = ListMap.empty,
    boundVars: Set[String] = Set.empty,
    stateMode: StateMode = StateMode.Direct
):
  def inputNames: Set[String]  = inputTypes.keySet
  def outputNames: Set[String] = outputTypes.keySet

object Generator:

  def generate(ir: ServiceIRFull): Either[DafnyError, DafnyOutput] =
    boundary[Either[DafnyError, DafnyOutput]]:
      val stateFields = ir.f match
        case Some(StateDeclFull(fs, _)) =>
          fs.collect { case StateFieldDeclFull(n, t, _) => n -> t }.to(ListMap)
        case None => ListMap.empty[String, type_expr_full]
      val aliasesWithWhere = ir.e.collect {
        case TypeAliasDeclFull(name, _, Some(_), _) => name
      }.toSet
      val (externs, matchPatterns) = classifyExterns(ir)
      val ctx = Ctx(
        ir = ir,
        stateFields = stateFields,
        aliasesWithWhere = aliasesWithWhere,
        externs = externs,
        matchPatterns = matchPatterns
      )

      val sb = new StringBuilder

      sb ++= header(ir.a)
      sb ++= optionDatatype()

      val enumDecls    = renderEnums(ir.d)
      val typeAliases  = renderTypeAliases(ctx, ir.e)
      val entityDecls  = renderEntities(ctx, ir.c)
      val stateClass   = renderStateClass(ctx)
      val invPredicate = renderInvariantPredicate(ctx, ir.i)

      val methods = ir.g.collect { case op: OperationDeclFull =>
        renderMethod(ctx, op)
      }

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

  private def renderEnums(decls: List[enum_decl_full])(using DafnyLabel): String =
    val parts = decls.collect { case EnumDeclFull(name, vs, _) =>
      val ctors = vs.mkString(" | ")
      s"datatype $name = $ctors\n"
    }
    parts.mkString

  private def renderTypeAliases(ctx: Ctx, decls: List[type_alias_decl_full])(using
      DafnyLabel
  ): String =
    val sb = new StringBuilder
    decls.foreach:
      case TypeAliasDeclFull(name, base, where, _) =>
        val baseStr = renderType(ctx, base)
        sb ++= s"type $name = $baseStr\n"
        where.foreach: predExpr =>
          val whereCtx = ctx.copy(boundVars = ctx.boundVars + "value")
          val rendered = renderExpr(whereCtx, predExpr)
          sb ++= s"predicate ${name}Where(value: $baseStr)\n"
          sb ++= "{\n"
          sb ++= s"  $rendered\n"
          sb ++= "}\n"
    sb.toString

  private def renderEntities(ctx: Ctx, decls: List[entity_decl_full])(using DafnyLabel): String =
    val sb = new StringBuilder
    decls.foreach:
      case EntityDeclFull(name, _, fields, invariants, _) =>
        val ctorFields = fields.collect { case FieldDeclFull(fn, ft, _, _) =>
          s"$fn: ${renderType(ctx, ft)}"
        }
        sb ++= s"datatype $name = $name(${ctorFields.mkString(", ")})\n"

        val fieldWhereClauses = fields.collect {
          case FieldDeclFull(fn, _, Some(whereExpr), _) =>
            val whereCtx = ctx.copy(boundVars = ctx.boundVars + "value" + "x")
            val rebound =
              rewriteValueRef(whereExpr, "value", FieldAccessF(IdentifierF("x", None), fn, None))
            s"(${renderExpr(whereCtx, rebound)})"
        }

        val invClauses =
          if invariants.nonEmpty then
            val invCtx = ctx.copy(boundVars = ctx.boundVars + "x")
            invariants.map(e => s"(${renderEntityInvariant(invCtx, e, name)})")
          else Nil

        val allClauses = fieldWhereClauses ++ invClauses
        if allClauses.nonEmpty then
          sb ++= s"\npredicate ${name}Inv(x: $name)\n{\n"
          sb ++= s"  ${allClauses.mkString("\n  && ")}\n"
          sb ++= "}\n"
    sb.toString

  private def renderEntityInvariant(ctx: Ctx, expr: expr_full, entityName: String)(using
      DafnyLabel
  ): String =
    val rewritten = rewriteEntityFieldRefs(expr, ctx.ir, entityName)
    renderExpr(ctx, rewritten)

  private def rewriteEntityFieldRefs(
      expr: expr_full,
      ir: ServiceIRFull,
      entityName: String
  ): expr_full =
    val entity = ir.c.collectFirst { case e: EntityDeclFull if e.a == entityName => e }
    val fieldNames =
      entity.toList.flatMap(_.c).collect { case FieldDeclFull(n, _, _, _) => n }.toSet
    def go(e: expr_full, bound: Set[String]): expr_full = e match
      case IdentifierF(n, sp) if fieldNames.contains(n) && !bound.contains(n) =>
        FieldAccessF(IdentifierF("x", sp), n, sp)
      case LetF(v, value, body, sp) =>
        LetF(v, go(value, bound), go(body, bound + v), sp)
      case LambdaF(p, body, sp) =>
        LambdaF(p, go(body, bound + p), sp)
      case QuantifierF(q, bs, body, sp) =>
        val bsBound = bs.collect { case b: QuantifierBindingFull => b.a }.toSet
        val bsRewritten = bs.map {
          case QuantifierBindingFull(a, dom, kind, bsp) =>
            QuantifierBindingFull(a, go(dom, bound), kind, bsp)
        }
        QuantifierF(q, bsRewritten, go(body, bound ++ bsBound), sp)
      case SetComprehensionF(v, dom, pred, sp) =>
        SetComprehensionF(v, go(dom, bound), go(pred, bound + v), sp)
      case TheF(v, dom, body, sp) =>
        TheF(v, go(dom, bound), go(body, bound + v), sp)
      case BinaryOpF(op, l, r, sp) => BinaryOpF(op, go(l, bound), go(r, bound), sp)
      case UnaryOpF(op, x, sp)     => UnaryOpF(op, go(x, bound), sp)
      case FieldAccessF(b, f, sp)  => FieldAccessF(go(b, bound), f, sp)
      case IndexF(b, i, sp)        => IndexF(go(b, bound), go(i, bound), sp)
      case CallF(c, args, sp)      => CallF(go(c, bound), args.map(go(_, bound)), sp)
      case PrimeF(x, sp)           => PrimeF(go(x, bound), sp)
      case PreF(x, sp)             => PreF(go(x, bound), sp)
      case IfF(c, t, el, sp)       => IfF(go(c, bound), go(t, bound), go(el, bound), sp)
      case SomeWrapF(x, sp)        => SomeWrapF(go(x, bound), sp)
      case ConstructorF(n, fs, sp) =>
        ConstructorF(
          n,
          fs.map {
            case FieldAssignFull(fn, v, fsp) => FieldAssignFull(fn, go(v, bound), fsp)
          },
          sp
        )
      case WithF(b, fs, sp) =>
        WithF(
          go(b, bound),
          fs.map {
            case FieldAssignFull(fn, v, fsp) => FieldAssignFull(fn, go(v, bound), fsp)
          },
          sp
        )
      case MapLiteralF(es, sp) =>
        MapLiteralF(
          es.map {
            case MapEntryFull(k, v, esp) => MapEntryFull(go(k, bound), go(v, bound), esp)
          },
          sp
        )
      case SetLiteralF(es, sp) => SetLiteralF(es.map(go(_, bound)), sp)
      case SeqLiteralF(es, sp) => SeqLiteralF(es.map(go(_, bound)), sp)
      case other               => other
    go(expr, Set.empty)

  private def renderStateClass(ctx: Ctx)(using DafnyLabel): Option[String] =
    if ctx.stateFields.isEmpty then None
    else
      val sb = new StringBuilder
      sb ++= "class ServiceState\n{\n"
      ctx.stateFields.foreach: (name, t) =>
        sb ++= s"  var $name: ${renderType(ctx, t)}\n"
      sb ++= "}\n"
      Some(sb.toString)

  private def renderInvariantPredicate(ctx: Ctx, invs: List[invariant_decl_full])(using
      DafnyLabel
  ): Option[String] =
    if invs.isEmpty || ctx.stateFields.isEmpty then None
    else
      val invCtx = ctx.copy(stateMode = StateMode.Direct)
      val parts = invs.collect { case InvariantDeclFull(_, e, _) =>
        s"(${renderExpr(invCtx, e)})"
      }
      val body = parts.mkString("\n  && ")
      Some(
        "predicate ServiceStateInv(st: ServiceState)\n" +
          "  reads st\n" +
          "{\n" +
          s"  $body\n" +
          "}\n"
      )

  final private case class RenderedMethod(text: String, header: DafnyMethodHeader)

  private def renderMethod(ctx: Ctx, op: OperationDeclFull)(using DafnyLabel): RenderedMethod =
    val inputs  = op.b.collect { case p: ParamDeclFull => p }
    val outputs = op.c.collect { case p: ParamDeclFull => p }
    val inputTypes = inputs
      .collect { case ParamDeclFull(n, t, _) => n -> t }
      .to(ListMap)
    val outputTypes = outputs
      .collect { case ParamDeclFull(n, t, _) => n -> t }
      .to(ListMap)

    val mctx = ctx.copy(
      inputTypes = inputTypes,
      outputTypes = outputTypes,
      boundVars = ctx.boundVars ++ inputTypes.keySet ++ outputTypes.keySet
    )

    val params = inputs.map { p =>
      s"${p.a}: ${renderType(mctx, p.b)}"
    }
    val stateParam = if ctx.stateFields.nonEmpty then List("st: ServiceState") else Nil
    val sigParams  = (stateParam ++ params).mkString(", ")
    val returns =
      if outputs.isEmpty then ""
      else
        val rs = outputs.map(p => s"${p.a}: ${renderType(mctx, p.b)}").mkString(", ")
        s" returns ($rs)"
    val signature = s"method ${op.a}($sigParams)$returns"

    val mutates = primedStateFields(op.e).intersect(ctx.stateFields.keySet).nonEmpty
    val modifiesClauses =
      if mutates then List("st") else Nil

    val invariantClauses =
      if ctx.stateFields.nonEmpty && ctx.ir.i.nonEmpty then List("ServiceStateInv(st)")
      else Nil

    val aliasInputClauses = inputs.flatMap { p =>
      aliasWhereCall(ctx, p.a, p.b)
    }

    val reqCtx          = mctx.copy(stateMode = StateMode.Direct)
    val rawRequires     = op.d.flatMap(flattenAnd).map(renderExpr(reqCtx, _)).filter(_ != "true")
    val requiresClauses = invariantClauses ++ aliasInputClauses ++ rawRequires

    val ensCtx = mctx.copy(stateMode = StateMode.Old)
    val rawEnsures = op.e
      .map(desugarOptionGuards(_, mctx))
      .flatMap(flattenAnd)
      .map(renderExpr(ensCtx, _))
      .filter(_ != "true")
    val ensuresClauses = rawEnsures ++ invariantClauses

    val sb = new StringBuilder
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
        name = op.a,
        signature = signature,
        requiresClauses = requiresClauses,
        ensuresClauses = ensuresClauses,
        modifiesClauses = modifiesClauses
      )
    )

  private def aliasWhereCall(ctx: Ctx, paramName: String, t: type_expr_full): Option[String] =
    t match
      case NamedTypeF(name, _) if ctx.aliasesWithWhere.contains(name) =>
        Some(s"${name}Where($paramName)")
      case _ => None

  private val knownBuiltins = Set("len", "dom", "ran")

  private def classifyExterns(ir: ServiceIRFull): (ListMap[String, ExternInfo], List[String]) =
    val externs  = mutable.LinkedHashMap.empty[String, ExternInfo]
    val patterns = mutable.LinkedHashSet.empty[String]

    def record(name: String, arity: Int, kind: ExternKind): Unit =
      externs.get(name) match
        case None => externs(name) = ExternInfo(kind, arity)
        case Some(prev) =>
          val newKind =
            if prev.kind == ExternKind.IntFunction || kind == ExternKind.IntFunction then
              ExternKind.IntFunction
            else ExternKind.Predicate
          externs(name) = ExternInfo(newKind, math.max(prev.arity, arity))

    def walk(e: expr_full, expected: ExternKind): Unit = e match
      case CallF(IdentifierF(n, _), args, _) if knownBuiltins.contains(n) =>
        args.foreach(walk(_, ExternKind.IntFunction))
      case CallF(IdentifierF(n, _), args, _) =>
        record(n, args.length, expected)
        args.foreach(walk(_, ExternKind.IntFunction))
      case CallF(_, args, _) =>
        args.foreach(walk(_, ExternKind.IntFunction))
      case BinaryOpF(BAnd() | BOr() | BImplies() | BIff(), l, r, _) =>
        walk(l, ExternKind.Predicate); walk(r, ExternKind.Predicate)
      case UnaryOpF(_: UNot, x, _) => walk(x, ExternKind.Predicate)
      case BinaryOpF(_, l, r, _) =>
        walk(l, ExternKind.IntFunction); walk(r, ExternKind.IntFunction)
      case UnaryOpF(_, x, _)     => walk(x, ExternKind.IntFunction)
      case PrimeF(x, _)          => walk(x, expected)
      case PreF(x, _)            => walk(x, expected)
      case SomeWrapF(x, _)       => walk(x, ExternKind.IntFunction)
      case FieldAccessF(b, _, _) => walk(b, ExternKind.IntFunction)
      case IndexF(b, i, _)       => walk(b, ExternKind.IntFunction); walk(i, ExternKind.IntFunction)
      case MapLiteralF(es, _) =>
        es.foreach { case MapEntryFull(k, v, _) =>
          walk(k, ExternKind.IntFunction); walk(v, ExternKind.IntFunction)
        }
      case SetLiteralF(es, _) => es.foreach(walk(_, ExternKind.IntFunction))
      case SeqLiteralF(es, _) => es.foreach(walk(_, ExternKind.IntFunction))
      case IfF(c, t, e, _) =>
        walk(c, ExternKind.Predicate); walk(t, expected); walk(e, expected)
      case LetF(_, v, b, _) => walk(v, ExternKind.IntFunction); walk(b, expected)
      case QuantifierF(_, bs, body, _) =>
        bs.foreach { case b: QuantifierBindingFull =>
          walk(b.b, ExternKind.IntFunction)
        }
        walk(body, ExternKind.Predicate)
      case SetComprehensionF(_, dom, pred, _) =>
        walk(dom, ExternKind.IntFunction); walk(pred, ExternKind.Predicate)
      case ConstructorF(_, fs, _) =>
        fs.foreach { case FieldAssignFull(_, v, _) => walk(v, ExternKind.IntFunction) }
      case WithF(b, fs, _) =>
        walk(b, ExternKind.IntFunction)
        fs.foreach { case FieldAssignFull(_, v, _) => walk(v, ExternKind.IntFunction) }
      case TheF(_, dom, body, _) =>
        walk(dom, ExternKind.IntFunction); walk(body, ExternKind.Predicate)
      case LambdaF(_, body, _) => walk(body, ExternKind.IntFunction)
      case MatchesF(x, p, _)   => patterns += p; walk(x, ExternKind.IntFunction)
      case _                   => ()

    ir.i.foreach { case InvariantDeclFull(_, e, _) => walk(e, ExternKind.Predicate) }
    ir.j.foreach { case TemporalDeclFull(_, e, _) => walk(e, ExternKind.Predicate) }
    ir.k.foreach { case FactDeclFull(_, e, _) => walk(e, ExternKind.Predicate) }
    ir.c.foreach { case e: EntityDeclFull =>
      e.d.foreach(walk(_, ExternKind.Predicate))
      e.c.foreach {
        case FieldDeclFull(_, _, Some(w), _) => walk(w, ExternKind.Predicate)
        case _                               => ()
      }
    }
    ir.e.foreach {
      case TypeAliasDeclFull(_, _, Some(w), _) => walk(w, ExternKind.Predicate)
      case _                                   => ()
    }
    ir.g.foreach { case op: OperationDeclFull =>
      op.d.foreach(walk(_, ExternKind.Predicate))
      op.e.foreach(walk(_, ExternKind.Predicate))
    }

    (externs.to(ListMap), patterns.toList)

  private def desugarOptionGuards(expr: expr_full, ctx: Ctx): expr_full =
    def isOption(name: String): Boolean =
      ctx.inputTypes.get(name).orElse(ctx.outputTypes.get(name)).exists {
        case _: OptionTypeF => true
        case _              => false
      }
    def unwrap(body: expr_full, p: String): expr_full =
      rewriteValueRef(body, p, FieldAccessF(IdentifierF(p, None), "value", None))
    def go(e: expr_full): expr_full = e match
      case BinaryOpF(
            BImplies(),
            guard @ BinaryOpF(BNeq(), IdentifierF(p, _), NoneLitF(_), _),
            body,
            sp
          ) if isOption(p) =>
        BinaryOpF(BImplies(), guard, go(unwrap(body, p)), sp)
      case BinaryOpF(
            BOr(),
            guard @ BinaryOpF(BEq(), IdentifierF(p, _), NoneLitF(_), _),
            body,
            sp
          ) if isOption(p) =>
        BinaryOpF(BOr(), guard, go(unwrap(body, p)), sp)
      case BinaryOpF(
            BOr(),
            body,
            guard @ BinaryOpF(BEq(), IdentifierF(p, _), NoneLitF(_), _),
            sp
          ) if isOption(p) =>
        BinaryOpF(BOr(), go(unwrap(body, p)), guard, sp)
      case BinaryOpF(op, l, r, sp) => BinaryOpF(op, go(l), go(r), sp)
      case UnaryOpF(op, x, sp)     => UnaryOpF(op, go(x), sp)
      case QuantifierF(q, bs, body, sp) =>
        val bsRewritten = bs.map { case QuantifierBindingFull(a, dom, kind, bsp) =>
          QuantifierBindingFull(a, go(dom), kind, bsp)
        }
        QuantifierF(q, bsRewritten, go(body), sp)
      case other => other
    go(expr)

  private def rewriteValueRef(expr: expr_full, name: String, replacement: expr_full): expr_full =
    def go(e: expr_full, bound: Set[String]): expr_full = e match
      case IdentifierF(n, _) if n == name && !bound.contains(n) => replacement
      case LetF(v, value, body, sp) =>
        LetF(v, go(value, bound), go(body, bound + v), sp)
      case LambdaF(p, body, sp) =>
        LambdaF(p, go(body, bound + p), sp)
      case QuantifierF(q, bs, body, sp) =>
        val bsBound = bs.collect { case b: QuantifierBindingFull => b.a }.toSet
        val bsRewritten = bs.map {
          case QuantifierBindingFull(a, dom, kind, bsp) =>
            QuantifierBindingFull(a, go(dom, bound), kind, bsp)
        }
        QuantifierF(q, bsRewritten, go(body, bound ++ bsBound), sp)
      case SetComprehensionF(v, dom, pred, sp) =>
        SetComprehensionF(v, go(dom, bound), go(pred, bound + v), sp)
      case TheF(v, dom, body, sp) =>
        TheF(v, go(dom, bound), go(body, bound + v), sp)
      case BinaryOpF(op, l, r, sp) => BinaryOpF(op, go(l, bound), go(r, bound), sp)
      case UnaryOpF(op, x, sp)     => UnaryOpF(op, go(x, bound), sp)
      case FieldAccessF(b, f, sp)  => FieldAccessF(go(b, bound), f, sp)
      case IndexF(b, i, sp)        => IndexF(go(b, bound), go(i, bound), sp)
      case CallF(c, args, sp)      => CallF(c, args.map(go(_, bound)), sp)
      case PrimeF(x, sp)           => PrimeF(go(x, bound), sp)
      case PreF(x, sp)             => PreF(go(x, bound), sp)
      case IfF(c, t, el, sp)       => IfF(go(c, bound), go(t, bound), go(el, bound), sp)
      case SomeWrapF(x, sp)        => SomeWrapF(go(x, bound), sp)
      case ConstructorF(n, fs, sp) =>
        ConstructorF(
          n,
          fs.map {
            case FieldAssignFull(fn, v, fsp) => FieldAssignFull(fn, go(v, bound), fsp)
          },
          sp
        )
      case WithF(b, fs, sp) =>
        WithF(
          go(b, bound),
          fs.map {
            case FieldAssignFull(fn, v, fsp) => FieldAssignFull(fn, go(v, bound), fsp)
          },
          sp
        )
      case MapLiteralF(es, sp) =>
        MapLiteralF(
          es.map {
            case MapEntryFull(k, v, esp) => MapEntryFull(go(k, bound), go(v, bound), esp)
          },
          sp
        )
      case SetLiteralF(es, sp) => SetLiteralF(es.map(go(_, bound)), sp)
      case SeqLiteralF(es, sp) => SeqLiteralF(es.map(go(_, bound)), sp)
      case other               => other
    go(expr, Set.empty)

  private def flattenAnd(expr: expr_full): List[expr_full] = SpecRestGenerated.flatten_and(expr)

  private def primedStateFields(ensures: List[expr_full]): Set[String] =
    val out = mutable.Set.empty[String]
    def walk(e: expr_full, primed: Boolean): Unit = e match
      case PrimeF(inner, _)  => walk(inner, primed = true)
      case PreF(inner, _)    => walk(inner, primed = false)
      case IdentifierF(n, _) => if primed then out += n
      case _                 => SpecRestGenerated.subexprs(e).foreach(walk(_, primed))
    ensures.foreach(walk(_, primed = false))
    out.toSet

  private def renderExterns(ctx: Ctx): String =
    val sb = new StringBuilder
    ctx.externs.foreach: (name, info) =>
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

  private def renderType(ctx: Ctx, t: type_expr_full)(using DafnyLabel): String = t match
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

  private def renderExpr(ctx: Ctx, e: expr_full)(using DafnyLabel): String = e match
    case IntLitF(int_of_integer(v), _) => v.toString
    case BoolLitF(v, _)                => v.toString
    case StringLitF(s, _)              => "\"" + escapeString(s) + "\""
    case NoneLitF(_)                   => "None"
    case FloatLitF(v, _)               => v
    case IdentifierF(n, _) =>
      if ctx.boundVars.contains(n) then n
      else if ctx.stateFields.contains(n) then stateRef(n, ctx.stateMode)
      else if ctx.inputNames.contains(n) || ctx.outputNames.contains(n) then n
      else n
    case PrimeF(inner, _) =>
      renderExpr(ctx.copy(stateMode = StateMode.Direct), inner)
    case PreF(inner, _) =>
      renderExpr(ctx.copy(stateMode = StateMode.Old), inner)
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
      val parts = entries.map { case MapEntryFull(k, v, _) =>
        s"${renderExpr(ctx, k)} := ${renderExpr(ctx, v)}"
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
    case WithF(base, fields, _) =>
      val parts = fields.map { case FieldAssignFull(n, v, _) =>
        s"$n := ${renderExpr(ctx, v)}"
      }
      s"${renderExpr(ctx, base)}.(${parts.mkString(", ")})"
    case ConstructorF(name, fields, sp) =>
      val orderedArgs = orderConstructorArgs(ctx, name, fields, sp)
      s"$name(${orderedArgs.map(renderExpr(ctx, _)).mkString(", ")})"
    case LambdaF(p, body, _) =>
      val inner = ctx.copy(boundVars = ctx.boundVars + p)
      s"(($p: int) => ${renderExpr(inner, body)})"
    case other =>
      failDafny(s"unsupported expression in Dafny translation: ${other.getClass.getSimpleName}")

  private def isMapDomain(ctx: Ctx, dom: expr_full): Boolean =
    peel_relation_ref_full(dom).exists(isMapStateField(ctx, _))

  private def isMapStateField(ctx: Ctx, name: String): Boolean =
    ctx.stateFields.get(name).exists {
      case _: MapTypeF | _: RelationTypeF => true
      case _                              => false
    }

  private def orderConstructorArgs(
      ctx: Ctx,
      entityName: String,
      assigns: List[field_assign_full],
      sp: Option[span_t]
  )(using DafnyLabel): List[expr_full] =
    val entity = ctx.ir.c.collectFirst { case e: EntityDeclFull if e.a == entityName => e }
    entity match
      case Some(EntityDeclFull(_, _, fs, _, _)) =>
        val byName = assigns.collect { case FieldAssignFull(n, v, _) => n -> v }.toMap
        fs.collect { case FieldDeclFull(fn, _, _, _) => fn }.map { fn =>
          byName.getOrElse(
            fn,
            failDafny(s"constructor for $entityName missing field $fn", sp)
          )
        }
      case None =>
        failDafny(s"constructor references unknown entity $entityName", sp)

  private def isStateMapRef(ctx: Ctx, e: expr_full): Boolean =
    peel_relation_ref_full(e).exists(ctx.stateFields.contains)

  private def stateRef(name: String, mode: StateMode): String = mode match
    case StateMode.Direct => s"st.$name"
    case StateMode.Old    => s"old(st.$name)"

  private def renderBinary(ctx: Ctx, op: bin_op_full, l: expr_full, r: expr_full)(using
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

  private def renderUnary(ctx: Ctx, op: un_op_full, x: expr_full)(using DafnyLabel): String =
    op match
      case _: UNot         => s"!(${renderExpr(ctx, x)})"
      case _: UNegate      => s"-(${renderExpr(ctx, x)})"
      case _: UCardinality => s"|${renderExpr(ctx, x)}|"
      case _: UPower =>
        failDafny("powerset operator '^' is not supported in Dafny translation")

  private def renderQuantifier(ctx: Ctx, q: QuantifierF)(using DafnyLabel): String =
    val bindings = q.b.collect { case b: QuantifierBindingFull => b }
    val varNames = bindings.map(_.a)
    val innerCtx = ctx.copy(boundVars = ctx.boundVars ++ varNames)
    val membership = bindings.map { b =>
      s"${b.a} in ${renderExpr(ctx, b.b)}"
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
