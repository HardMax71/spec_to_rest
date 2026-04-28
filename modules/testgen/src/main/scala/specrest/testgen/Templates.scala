package specrest.testgen

import specrest.convention.Naming
import specrest.ir.FunctionDecl
import specrest.ir.PredicateDecl
import specrest.ir.ServiceIR

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object Templates:

  private val Root = "testgen-templates/python-fastapi-postgres"

  lazy val conftest: String                 = loadResource("tests/conftest.py")
  lazy val predicatesStaticTemplate: String = loadResource("tests/predicates.py")
  lazy val pytestIni: String                = loadResource("tests/pytest.ini")
  lazy val runConformance: String           = loadResource("tests/run_conformance.py")

  def predicates(ir: ServiceIR): String =
    val userBlock = renderUserDefinitions(ir)
    if userBlock.isEmpty then predicatesStaticTemplate
    else predicatesStaticTemplate + "\n\n" + userBlock

  private def renderUserDefinitions(ir: ServiceIR): String =
    val parts = ir.functions.map(renderFunction(_, ir)) ++
      ir.predicates.map(renderPredicate(_, ir))
    parts.mkString("")

  private def renderFunction(fn: FunctionDecl, ir: ServiceIR): String =
    val pyName = Naming.toSnakeCase(fn.name)
    val params = fn.params.map(p => Naming.toSnakeCase(p.name)).mkString(", ")
    val ctx    = predicateBodyCtx(fn.params.map(_.name).toSet, ir)
    ExprToPython.translate(fn.body, ctx) match
      case ExprPy.Py(text) =>
        s"def $pyName($params):\n    return $text\n\n"
      case ExprPy.Skip(reason, _) =>
        s"def $pyName($params):\n" +
          s"    raise NotImplementedError(${ExprToPython.pyString(s"testgen: cannot translate body of '${fn.name}': $reason")})\n\n"

  private def renderPredicate(pr: PredicateDecl, ir: ServiceIR): String =
    val pyName = Naming.toSnakeCase(pr.name)
    val params = pr.params.map(p => Naming.toSnakeCase(p.name)).mkString(", ")
    val ctx    = predicateBodyCtx(pr.params.map(_.name).toSet, ir)
    ExprToPython.translate(pr.body, ctx) match
      case ExprPy.Py(text) =>
        s"def $pyName($params):\n    return $text\n\n"
      case ExprPy.Skip(reason, _) =>
        s"def $pyName($params):\n" +
          s"    raise NotImplementedError(${ExprToPython.pyString(s"testgen: cannot translate body of '${pr.name}': $reason")})\n\n"

  private def predicateBodyCtx(params: Set[String], ir: ServiceIR): TestCtx =
    TestCtx(
      inputs = params,
      outputs = Set.empty,
      stateFields = Set.empty,
      mapStateFields = Set.empty,
      enumValues = ir.enums.map(e => e.name -> e.values.toSet).toMap,
      knownPredicates = TestCtx.DefaultPredicates,
      userFunctions = ir.functions.map(f => f.name -> f).toMap,
      userPredicates = ir.predicates.map(p => p.name -> p).toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState
    )

  private def loadResource(relPath: String): String =
    val resourcePath = s"$Root/$relPath"
    val is           = getClass.getClassLoader.getResourceAsStream(resourcePath)
    if is == null then
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
