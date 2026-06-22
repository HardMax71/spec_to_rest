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
      val cjsPath =
        if relPath.endsWith(".js") then relPath.dropRight(".js".length) + ".cjs" else relPath
      cjsPath -> appendJsExports(content)

  // Dafny's JS backend wraps each module in a module-private IIFE (`let _module = (function(){...})()`)
  // and emits no `module.exports`, so nothing is reachable via `require`. Re-export the module object
  // (methods + ServiceState) and the runtime (Seq/Map helpers the adapter needs). The `.cjs` extension
  // keeps it CommonJS inside the emitted ESM project (`"type": "module"`).
  private def appendJsExports(content: String): String =
    if content.contains("module.exports") then content
    else s"$content\nmodule.exports = { _module, _dafny };\n"

  def rewritePythonImports(files: Map[String, String]): Map[String, String] =
    files.map: (relPath, content) =>
      val subdirDepth = relPath.count(_ == '/')
      val dots        = "." * (subdirDepth + 1)
      relPath -> rewritePyFile(content, dots)

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
