package specrest.testgen

import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*

object Templates:

  private val Root = "testgen-templates/python/fastapi"

  lazy val conftest: String       = TemplateResources.load(Root, "tests/conftest.py")
  lazy val pytestIni: String      = TemplateResources.load(Root, "tests/pytest.ini")
  lazy val redaction: String      = TemplateResources.load(Root, "tests/redaction.py")
  lazy val runConformance: String = TemplateResources.load(Root, "tests/run_conformance.py")
  lazy val strategiesUser: String = TemplateResources.load(Root, "tests/strategies_user.py")

  private val PredicatesHeader: String =
    """|import datetime
       |import hashlib
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
    PredicatesHeader + "\n" + UserDefs.renderAll(ir)((n, ps, b) => renderUserDef(n, ps, b, ir))

  private def renderUserDef(
      specName: String,
      paramNames: List[String],
      body: expr,
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
          val ctx = TestCtx.forPredicateBody(paramNames.toSet, ir)
          ExprToPython.translate(body, ctx) match
            case Translated.Emit(text) =>
              s"def $safePyName($sigParams):\n    return $text\n\n"
            case Translated.Skip(reason, _) =>
              s"def $safePyName($sigParams):\n" +
                s"    raise NotImplementedError(${ExprToPython.pyString(s"testgen: cannot translate body of '$specName': $reason")})\n\n"
