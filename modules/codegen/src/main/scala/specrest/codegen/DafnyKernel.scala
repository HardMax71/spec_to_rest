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

  def empty: DafnyKernel = DafnyKernel(PythonDefaultPackagePath, Map.empty, Nil)

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
