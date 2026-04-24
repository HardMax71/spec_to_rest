package specrest.verify.z3

import cats.effect.IO
import cats.effect.Resource
import com.microsoft.z3.ArithExpr
import com.microsoft.z3.ArrayExpr
import com.microsoft.z3.ArraySort
import com.microsoft.z3.BoolExpr
import com.microsoft.z3.BoolSort
import com.microsoft.z3.Context
import com.microsoft.z3.Expr as Z3AstExpr
import com.microsoft.z3.FuncDecl
import com.microsoft.z3.IntSort
import com.microsoft.z3.Model
import com.microsoft.z3.Sort
import com.microsoft.z3.Status
import specrest.ir.VerifyError
import specrest.verify.CheckStatus
import specrest.verify.VerificationConfig

import java.io.PrintWriter
import java.io.StringWriter
import scala.collection.mutable
import scala.util.boundary
import scala.util.control.NonFatal

private type BackendBoundary =
  boundary.Label[Either[VerifyError.Backend, SmokeCheckResult]]

private def backendFail(rctx: RenderCtx, msg: String): Nothing =
  boundary.break(Left(VerifyError.Backend(msg, None)))(using rctx.bnd)

private[z3] def renderStack(e: Throwable): String =
  val sw = new StringWriter
  e.printStackTrace(new PrintWriter(sw))
  sw.toString

final case class SmokeCheckResult(
    status: CheckStatus,
    durationMs: Double,
    model: Option[Model],
    sortMap: Map[String, Sort],
    funcMap: Map[String, FuncDecl[?]],
    unsatCoreTrackers: List[String] = Nil
)

object WasmBackend:

  def apply(): WasmBackend = new WasmBackend()

  def make: Resource[IO, WasmBackend] =
    make(IO.blocking(WasmBackend())): backend =>
      IO.blocking(backend.close())

  private[verify] def make(
      acquire: IO[WasmBackend]
  )(
      release: WasmBackend => IO[Unit]
  ): Resource[IO, WasmBackend] =
    Resource.make(acquire)(release)

final class WasmBackend:

  private val ctx: Context = new Context()

  def close(): Unit = ctx.close()

  def check(
      script: Z3Script,
      cfg: VerificationConfig
  ): IO[Either[VerifyError.Backend, SmokeCheckResult]] =
    IO.blocking {
      try
        boundary:
          val sortMap = declareSorts(ctx, script.sorts)
          val funcMap = declareFuncs(ctx, script.funcs, sortMap)
          val solver  = ctx.mkSolver()
          val params  = ctx.mkParams()
          params.add("random_seed", 0)
          if cfg.timeoutMs > 0 then params.add("timeout", cfg.timeoutMs.toInt)
          solver.setParameters(params)
          val rctx         = new RenderCtx(ctx, sortMap, funcMap, summon[BackendBoundary])
          val trackerNames = scala.collection.mutable.ArrayBuffer.empty[String]
          if cfg.captureCore then
            for (a, idx) <- script.assertions.zipWithIndex do
              val name    = s"_t_$idx"
              val tracker = ctx.mkBoolConst(name)
              solver.assertAndTrack(Backend.renderBool(rctx, a), tracker)
              val _ = trackerNames += name
          else for a <- script.assertions do solver.add(Backend.renderBool(rctx, a))
          val t0       = System.nanoTime()
          val status   = solver.check()
          val duration = (System.nanoTime() - t0) / 1_000_000.0
          val checkStatus = status match
            case Status.SATISFIABLE   => CheckStatus.Sat
            case Status.UNSATISFIABLE => CheckStatus.Unsat
            case Status.UNKNOWN       => CheckStatus.Unknown
          val model =
            if checkStatus == CheckStatus.Sat && cfg.captureModel then Some(solver.getModel)
            else None
          val core =
            if cfg.captureCore && checkStatus == CheckStatus.Unsat then
              solver.getUnsatCore.toList.map(_.toString)
            else Nil
          Right(SmokeCheckResult(
            status = checkStatus,
            durationMs = duration,
            model = model,
            sortMap = sortMap.toMap,
            funcMap = funcMap.toMap,
            unsatCoreTrackers = core
          ))
      catch
        case NonFatal(e) =>
          Left(VerifyError.Backend(
            Option(e.getMessage).getOrElse(e.toString),
            Some(renderStack(e))
          ))
    }

final private class RenderCtx(
    val ctx: Context,
    val sortMap: mutable.Map[String, Sort],
    val funcMap: mutable.Map[String, FuncDecl[?]],
    val bnd: BackendBoundary
):
  val varStack: mutable.ArrayBuffer[mutable.Map[String, Z3AstExpr[?]]] = mutable.ArrayBuffer.empty

private def declareSorts(ctx: Context, sorts: List[Z3Sort]): mutable.Map[String, Sort] =
  val map = mutable.Map.empty[String, Sort]
  for s <- sorts do registerSort(ctx, map, s)
  map

private def registerSort(ctx: Context, map: mutable.Map[String, Sort], s: Z3Sort): Unit =
  s match
    case Z3Sort.Uninterp(name) =>
      val _ = map.getOrElseUpdate(Z3Sort.key(s), ctx.mkUninterpretedSort(name))
    case Z3Sort.SetOf(elem) =>
      registerSort(ctx, map, elem)
    case _ => ()

private def resolveSort(ctx: Context, sortMap: mutable.Map[String, Sort], s: Z3Sort): Sort =
  s match
    case Z3Sort.Int  => ctx.getIntSort
    case Z3Sort.Bool => ctx.getBoolSort
    case Z3Sort.Uninterp(name) =>
      sortMap.getOrElseUpdate(Z3Sort.key(s), ctx.mkUninterpretedSort(name))
    case Z3Sort.SetOf(elem) =>
      sortMap.getOrElseUpdate(
        Z3Sort.key(s), {
          val inner = resolveSort(ctx, sortMap, elem)
          ctx.mkSetSort(inner)
        }
      )

private def declareFuncs(
    ctx: Context,
    funcs: List[Z3FunctionDecl],
    sortMap: mutable.Map[String, Sort]
): mutable.Map[String, FuncDecl[?]] =
  val map = mutable.Map.empty[String, FuncDecl[?]]
  for f <- funcs do
    val argSorts   = f.argSorts.map(s => resolveSort(ctx, sortMap, s)).toArray
    val resultSort = resolveSort(ctx, sortMap, f.resultSort)
    map(f.name) = ctx.mkFuncDecl(f.name, argSorts, resultSort)
  map

private object Backend:

  private def lookupVar(rctx: RenderCtx, name: String): Option[Z3AstExpr[?]] =
    var i = rctx.varStack.length - 1
    while i >= 0 do
      rctx.varStack(i).get(name) match
        case Some(v) => return Some(v)
        case None    => ()
      i -= 1
    None

  def renderExpr(rctx: RenderCtx, e: Z3Expr): Z3AstExpr[?] = e match
    case Z3Expr.Var(name, _, _) =>
      lookupVar(rctx, name).getOrElse(backendFail(rctx, s"unbound Z3 variable '$name'"))
    case Z3Expr.App(func, args, _) =>
      rctx.funcMap.get(func) match
        case None => backendFail(rctx, s"undeclared Z3 function '$func'")
        case Some(decl) =>
          val rendered = args.map(a => renderExpr(rctx, a)).toArray
          decl.asInstanceOf[FuncDecl[Sort]]
            .apply(rendered.asInstanceOf[Array[Z3AstExpr[Sort]]]*)
    case Z3Expr.IntLit(v, _)  => rctx.ctx.mkInt(v)
    case Z3Expr.BoolLit(v, _) => rctx.ctx.mkBool(v)
    case Z3Expr.And(args, _)  => rctx.ctx.mkAnd(args.map(a => renderBool(rctx, a))*)
    case Z3Expr.Or(args, _)   => rctx.ctx.mkOr(args.map(a => renderBool(rctx, a))*)
    case Z3Expr.Not(arg, _)   => rctx.ctx.mkNot(renderBool(rctx, arg))
    case Z3Expr.Implies(l, r, _) =>
      rctx.ctx.mkImplies(renderBool(rctx, l), renderBool(rctx, r))
    case Z3Expr.Cmp(op, l, r, _)           => renderCmp(rctx, op, l, r)
    case Z3Expr.Arith(op, args, _)         => renderArith(rctx, op, args)
    case q @ Z3Expr.Quantifier(_, _, _, _) => renderQuantifier(rctx, q)
    case Z3Expr.EmptySet(elemSort, _) =>
      val sort = resolveSort(rctx.ctx, rctx.sortMap, elemSort)
      rctx.ctx.mkEmptySet(sort)
    case Z3Expr.SetLit(elemSort, members, _) =>
      val sort  = resolveSort(rctx.ctx, rctx.sortMap, elemSort)
      val empty = rctx.ctx.mkEmptySet(sort)
      members.foldLeft[ArrayExpr[Sort, BoolSort]](
        empty.asInstanceOf[ArrayExpr[Sort, BoolSort]]
      ): (acc, m) =>
        rctx.ctx
          .mkSetAdd(acc, renderExpr(rctx, m).asInstanceOf[Z3AstExpr[Sort]])
          .asInstanceOf[ArrayExpr[Sort, BoolSort]]
    case Z3Expr.SetMember(elem, set, _) =>
      val elemZ = renderExpr(rctx, elem).asInstanceOf[Z3AstExpr[Sort]]
      val setZ  = renderSetExpr(rctx, set)
      rctx.ctx.mkSetMembership(elemZ, setZ)
    case Z3Expr.SetBinOp(op, l, r, _) =>
      val lhs = renderSetExpr(rctx, l)
      val rhs = renderSetExpr(rctx, r)
      op match
        case SetOpKind.Union     => rctx.ctx.mkSetUnion(lhs, rhs)
        case SetOpKind.Intersect => rctx.ctx.mkSetIntersection(lhs, rhs)
        case SetOpKind.Diff      => rctx.ctx.mkSetDifference(lhs, rhs)
        case SetOpKind.Subset    => rctx.ctx.mkSetSubset(lhs, rhs)

  def renderBool(rctx: RenderCtx, e: Z3Expr): BoolExpr =
    renderExpr(rctx, e).asInstanceOf[BoolExpr]

  private def renderSetExpr(
      rctx: RenderCtx,
      e: Z3Expr
  ): Z3AstExpr[ArraySort[Sort, BoolSort]] =
    renderExpr(rctx, e).asInstanceOf[Z3AstExpr[ArraySort[Sort, BoolSort]]]

  private def renderArithExpr(rctx: RenderCtx, e: Z3Expr): ArithExpr[IntSort] =
    renderExpr(rctx, e).asInstanceOf[ArithExpr[IntSort]]

  private def renderCmp(rctx: RenderCtx, op: CmpOp, lhs: Z3Expr, rhs: Z3Expr): BoolExpr =
    if op == CmpOp.Eq || op == CmpOp.Neq then
      val l  = renderExpr(rctx, lhs)
      val r  = renderExpr(rctx, rhs)
      val eq = rctx.ctx.mkEq(l, r)
      if op == CmpOp.Eq then eq else rctx.ctx.mkNot(eq)
    else
      val l = renderArithExpr(rctx, lhs)
      val r = renderArithExpr(rctx, rhs)
      op match
        case CmpOp.Lt => rctx.ctx.mkLt(l, r)
        case CmpOp.Le => rctx.ctx.mkLe(l, r)
        case CmpOp.Gt => rctx.ctx.mkGt(l, r)
        case CmpOp.Ge => rctx.ctx.mkGe(l, r)
        case _        => backendFail(rctx, s"unreachable CmpOp: $op")

  private def renderArith(
      rctx: RenderCtx,
      op: ArithOp,
      args: List[Z3Expr]
  ): ArithExpr[IntSort] =
    if args.isEmpty then backendFail(rctx, "Arith with no args")
    val rendered = args.map(a => renderArithExpr(rctx, a))
    op match
      case ArithOp.Add => rctx.ctx.mkAdd(rendered*)
      case ArithOp.Sub => rctx.ctx.mkSub(rendered*)
      case ArithOp.Mul => rctx.ctx.mkMul(rendered*)
      case ArithOp.Div =>
        var acc = rendered.head
        for r <- rendered.tail do acc = rctx.ctx.mkDiv(acc, r)
        acc

  private def renderQuantifier(rctx: RenderCtx, e: Z3Expr.Quantifier): BoolExpr =
    if e.bindings.isEmpty then
      backendFail(rctx, s"Quantifier must have at least one binding (got 0 for ${e.q})")
    val frame  = mutable.Map.empty[String, Z3AstExpr[?]]
    val consts = mutable.ArrayBuffer.empty[Z3AstExpr[?]]
    for b <- e.bindings do
      val sort  = resolveSort(rctx.ctx, rctx.sortMap, b.sort)
      val const = rctx.ctx.mkConst(b.name, sort)
      frame(b.name) = const
      consts += const
    rctx.varStack += frame
    try
      val body     = renderBool(rctx, e.body)
      val boundArr = consts.toArray
      if e.q == QKind.ForAll then
        rctx.ctx.mkForall(boundArr, body, 0, null, null, null, null)
      else
        rctx.ctx.mkExists(boundArr, body, 0, null, null, null, null)
    finally rctx.varStack.dropRightInPlace(1)
