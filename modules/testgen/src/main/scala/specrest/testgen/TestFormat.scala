package specrest.testgen

import specrest.convention.EndpointSpec
import specrest.ir.HttpMethods
import specrest.ir.PrettyPrint
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation

private[testgen] object TestFormat:

  def invName(inv: invariant_decl, idx: Int): String =
    SpecRestGenerated.invName(inv).getOrElse(s"anon_$idx")

  def prettyOneLine(e: expr): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  def escapeDocstring(s: String): String =
    s.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")

  // Names must match the exports of testgen-templates/typescript/express/tests/_runtime.ts.
  val TsRuntimeHelpers: List[String] =
    List("_diff", "_eq", "_in", "_inter", "_len", "_powerset", "_sha256Hex", "_subset", "_union")

  def inputArbs(
      pop: ProfiledOperation,
      ir: ServiceIRFull,
      backend: StrategyBackend
  ): Either[String, List[(String, String)]] =
    val ep     = pop.endpoint
    val params = ep.pathParams ++ ep.bodyParams ++ ep.queryParams
    if params.isEmpty then Right(Nil)
    else
      val overrides = TestStrategyOverrides.from(ir)
      val pairs = params.map: p =>
        val sctx = StrategyCtx.OperationInput(pop.operationName, p.name)
        (p.name, Strategies.expressionFor(p.typeExpr, ir, sctx, overrides, backend))
      pairs.collectFirst { case (n, StrategyExpr.Skip(r)) => s"input '$n': $r" } match
        case Some(reason) => Left(reason)
        case None         => Right(pairs.collect { case (n, StrategyExpr.Code(t)) => (n, t) })

  def requestCallExpr(pop: ProfiledOperation): String =
    val ep              = pop.endpoint
    val method          = HttpMethods.lower(ep.method)
    val bodyParamNames  = ep.bodyParams.map(_.name)
    val queryParamNames = ep.queryParams.map(_.name)
    val pathExpr        = pythonPathLiteral(ep)
    val bodyExpr =
      if bodyParamNames.isEmpty then ""
      else
        val pairs =
          bodyParamNames.map(n => s"${ExprToPython.pyString(n)}: $n").mkString(", ")
        s", json={$pairs}"
    val queryExpr =
      if queryParamNames.isEmpty then ""
      else
        val pairs =
          queryParamNames.map(n => s"${ExprToPython.pyString(n)}: $n").mkString(", ")
        s", params={$pairs}"
    s"client.$method($pathExpr$bodyExpr$queryExpr)"

  def pythonPathLiteral(ep: EndpointSpec): String =
    if ep.pathParams.isEmpty then ExprToPython.pyString(ep.path)
    else "f" + ExprToPython.pyString(ep.path)
