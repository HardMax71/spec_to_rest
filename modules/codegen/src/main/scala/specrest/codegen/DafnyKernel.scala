package specrest.codegen

final case class DafnyKernel(
    packagePath: String,
    files: Map[String, String],
    bindings: List[OperationBinding]
):
  def fileFor(relPath: String): Option[String] = files.get(relPath)

final case class OperationBinding(
    operationName: String,
    pythonCallable: String
)

object DafnyKernel:

  val PythonDefaultPackagePath: String = "app/dafny_kernel"
  val GoDefaultPackagePath: String     = "internal/dafnykernel"
  val JsDefaultPackagePath: String     = "src/dafnyKernel"

  def empty: DafnyKernel = DafnyKernel(PythonDefaultPackagePath, Map.empty, Nil)

  private val GoBareImport = """^(\s*)(\w+\s+)?"(dafny|System_)"\s*$""".r

  def rewriteGoImports(
      files: Map[String, String],
      moduleName: String,
      packagePath: String
  ): Map[String, String] =
    val importBase = s"$moduleName/${packagePath.stripSuffix("/")}"
    files.map: (relPath, content) =>
      stripSrcPrefix(relPath) -> rewriteGoFile(content, importBase)

  private def stripSrcPrefix(relPath: String): String =
    if relPath.startsWith("src/") then relPath.drop("src/".length) else relPath

  private def rewriteGoFile(content: String, importBase: String): String =
    content.linesWithSeparators.map(line => rewriteGoLine(line, importBase)).mkString

  private def rewriteGoLine(line: String, importBase: String): String =
    val stripped = if line.endsWith("\n") then line.dropRight(1) else line
    val term     = if line.endsWith("\n") then "\n" else ""
    stripped match
      case GoBareImport(indent, alias, name) =>
        val pre = Option(alias).getOrElse("")
        s"$indent$pre\"$importBase/$name\"$term"
      case _ => line

  def rewriteJsKernel(files: Map[String, String]): Map[String, String] =
    files.map: (relPath, content) =>
      if relPath.endsWith(".js") then
        relPath.dropRight(".js".length) + ".cjs" -> appendJsExports(content)
      else relPath                               -> content

  // Dafny's JS backend wraps each module in a module-private IIFE (`let _module = (function(){...})()`)
  // and emits no `module.exports`, so nothing is reachable via `require`. Re-export the module object
  // (methods + ServiceState) and the runtime (Seq/Map helpers the adapter needs). The `.cjs` extension
  // keeps it CommonJS inside the emitted ESM project (`"type": "module"`).
  private def appendJsExports(content: String): String =
    if content.contains("module.exports") then content
    else s"$content\nmodule.exports = { _module, _dafny };\n"

  // Extern builtins (now, hash, the time units) compile to bare global
  // references like `specrest_externs_now()`; the shim defines them and this
  // injects the import where any are referenced.
  private val ExternNames = List(
    "specrest_externs_now",
    "specrest_externs_days",
    "specrest_externs_hours",
    "specrest_externs_minutes",
    "specrest_externs_seconds",
    "specrest_externs_hash_hex",
    "specrest_externs_abs_int"
  )

  // Runtime implementations of the proof-abstract extern builtins, emitted
  // into the kernel package. Semantics must match each backend's own builtin
  // rendering (hash is sha256 hex; time units are integer seconds) so the
  // kernel and the conformance oracle agree on every value.
  val PythonExternShim: String =
    """import hashlib
      |import time
      |
      |from . import _dafny
      |
      |
      |def _plain_str(s):
      |    return str(s.VerbatimString(False))
      |
      |
      |def _to_dafny_str(s):
      |    return _dafny.SeqWithoutIsStrInference(map(_dafny.CodePoint, s))
      |
      |
      |def specrest_externs_now():
      |    return int(time.time())
      |
      |
      |def specrest_externs_days(n):
      |    return n * 86400
      |
      |
      |def specrest_externs_hours(n):
      |    return n * 3600
      |
      |
      |def specrest_externs_minutes(n):
      |    return n * 60
      |
      |
      |def specrest_externs_seconds(n):
      |    return n
      |
      |
      |def specrest_externs_hash_hex(s):
      |    return _to_dafny_str(hashlib.sha256(_plain_str(s).encode()).hexdigest())
      |
      |
      |def specrest_externs_abs_int(n):
      |    return n if n >= 0 else -n
      |""".stripMargin

  def rewritePythonImports(files: Map[String, String]): Map[String, String] =
    files.map: (relPath, content) =>
      val subdirDepth = relPath.count(_ == '/')
      val dots        = "." * (subdirDepth + 1)
      val rewritten   = rewritePyFile(content, dots)
      val used        = ExternNames.filter(n => rewritten.contains(n + "("))
      val withExterns =
        if used.isEmpty then rewritten
        else
          val importLine = s"from ${dots}_externs import ${used.mkString(", ")}\n"
          rewritten.linesWithSeparators.toList match
            case head :: tail if head.startsWith("import sys") =>
              (head :: importLine :: tail).mkString
            case lines => (importLine :: lines).mkString
      relPath -> withExterns

  private def rewritePyFile(content: String, dots: String): String =
    content.linesWithSeparators.map(line => rewriteLine(line, dots)).mkString

  private val AbsoluteImport = """^(\s*)import\s+(_dafny|module_|System_)\s+as\s+(\2)\s*(#.*)?$""".r

  private def rewriteLine(line: String, dots: String): String =
    val stripped = if line.endsWith("\n") then line.dropRight(1) else line
    val term     = if line.endsWith("\n") then "\n" else ""
    stripped match
      case AbsoluteImport(indent, name, _, comment) =>
        val tail = Option(comment).map(c => s"  $c").getOrElse("")
        s"${indent}from $dots import $name as $name$tail$term"
      case _ => line
