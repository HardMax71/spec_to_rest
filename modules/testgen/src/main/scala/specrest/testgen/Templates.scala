package specrest.testgen

import specrest.convention.Naming
import specrest.ir.generated.SpecRestGenerated.*

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.Null"))
object Templates:

  private val Root = "testgen-templates/python-fastapi-postgres"

  lazy val conftest: String       = loadResource("tests/conftest.py")
  lazy val pytestIni: String      = loadResource("tests/pytest.ini")
  lazy val redaction: String      = loadResource("tests/redaction.py")
  lazy val runConformance: String = loadResource("tests/run_conformance.py")
  lazy val strategiesUser: String = loadResource("tests/strategies_user.py")

  private val PredicatesHeader: String =
    """|import datetime
       |import itertools
       |import re
       |
       |
       |def _powerset(s):
       |    items = list(s)
       |    return frozenset(
       |        frozenset(c)
       |        for r in range(len(items) + 1)
       |        for c in itertools.combinations(items, r)
       |    )
       |
       |""".stripMargin

  def predicates(ir: ServiceIRFull): String =
    PredicatesHeader + "\n" + renderUserDefinitions(ir)

  private def renderUserDefinitions(ir: ServiceIRFull): String =
    val parts = ir.l.collect { case _f: FunctionDeclFull => renderFunction(_f, ir) } ++
      ir.m.collect { case _p: PredicateDeclFull => renderPredicate(_p, ir) }
    parts.mkString("")

  private def renderFunction(fn: FunctionDeclFull, ir: ServiceIRFull): String =
    renderUserDef(fn.a, fn.b.collect { case ParamDeclFull(_n, _, _) => _n }, fn.d, ir)

  private def renderPredicate(pr: PredicateDeclFull, ir: ServiceIRFull): String =
    renderUserDef(pr.a, pr.b.collect { case ParamDeclFull(_n, _, _) => _n }, pr.c, ir)

  private def renderUserDef(
      specName: String,
      paramNames: List[String],
      body: expr_full,
      ir: ServiceIRFull
  ): String =
    val pyName        = Naming.toSnakeCase(specName)
    val safePyName    = if PythonReservedNames.contains(pyName) then s"${pyName}_" else pyName
    val safeParams    = paramNames.map(p => if PythonReservedNames.contains(p) then s"${p}_" else p)
    val sigParams     = safeParams.mkString(", ")
    val nameReserved  = PythonReservedNames.contains(pyName)
    val firstResParam = paramNames.find(PythonReservedNames.contains)

    if nameReserved then
      s"def $safePyName($sigParams):\n" +
        s"    raise NotImplementedError(${ExprToPython.pyString(s"testgen: '$specName' (snake-cased to '$pyName') is a Python-reserved name")})\n\n"
    else
      firstResParam match
        case Some(p) =>
          s"def $safePyName($sigParams):\n" +
            s"    raise NotImplementedError(${ExprToPython.pyString(s"testgen: parameter '$p' of '$specName' is a Python-reserved name")})\n\n"
        case None =>
          val ctx = predicateBodyCtx(paramNames.toSet, ir)
          ExprToPython.translate(body, ctx) match
            case ExprPy.Py(text) =>
              s"def $safePyName($sigParams):\n    return $text\n\n"
            case ExprPy.Skip(reason, _) =>
              s"def $safePyName($sigParams):\n" +
                s"    raise NotImplementedError(${ExprToPython.pyString(s"testgen: cannot translate body of '$specName': $reason")})\n\n"

  private def predicateBodyCtx(params: Set[String], ir: ServiceIRFull): TestCtx =
    TestCtx(
      inputs = params,
      outputs = Set.empty,
      stateFields = Set.empty,
      mapStateFields = Set.empty,
      enumValues = ir.d.collect { case e: EnumDeclFull => e.a -> e.b.toSet }.toMap,
      userFunctions = ir.l.collect { case f: FunctionDeclFull => f.a -> f }.toMap,
      userPredicates = ir.m.collect { case p: PredicateDeclFull => p.a -> p }.toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState
    )

  private def loadResource(relPath: String): String =
    val resourcePath = s"$Root/$relPath"
    val is           = getClass.getClassLoader.getResourceAsStream(resourcePath)
    if is eq null then
      throw new RuntimeException(s"testgen template resource missing: $resourcePath")
    try
      val out    = new ByteArrayOutputStream()
      val buffer = new Array[Byte](8192)
      var read   = is.read(buffer)
      while read != -1 do
        out.write(buffer, 0, read)
        read = is.read(buffer)
      new String(out.toByteArray, StandardCharsets.UTF_8)
    finally is.close()
