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
    val rewritten = files.map: (relPath, content) =>
      stripSrcPrefix(relPath) -> rewriteGoFile(content, importBase)
    val externNames = ExternNames.map("m_" + _)
    if rewritten.values.exists(c => externNames.exists(n => c.contains(n + "("))) then
      rewritten + ("externs.go" -> goExternShim(importBase))
    else rewritten

  // The Go backend calls extern builtins as package-level `m_`-prefixed
  // functions; this file provides them inside the kernel package with the
  // same semantics as the direct-emit builtin renderings.
  private def goExternShim(importBase: String): String =
    s"""package kernel

import (
	"crypto/sha256"
	"encoding/hex"
	"time"

	_dafny "$importBase/dafny"
)

func m_specrest_externs_now() _dafny.Int {
	return _dafny.IntOfInt64(time.Now().Unix())
}

func m_specrest_externs_days(n _dafny.Int) _dafny.Int {
	return n.Times(_dafny.IntOfInt64(86400))
}

func m_specrest_externs_hours(n _dafny.Int) _dafny.Int {
	return n.Times(_dafny.IntOfInt64(3600))
}

func m_specrest_externs_minutes(n _dafny.Int) _dafny.Int {
	return n.Times(_dafny.IntOfInt64(60))
}

func m_specrest_externs_seconds(n _dafny.Int) _dafny.Int {
	return n
}

func m_specrest_externs_hash_hex(s _dafny.Sequence) _dafny.Sequence {
	sum := sha256.Sum256([]byte(_dafny.SequenceVerbatimString(s, false)))
	return _dafny.UnicodeSeqOfUtf8Bytes(hex.EncodeToString(sum[:]))
}

func m_specrest_externs_abs_int(n _dafny.Int) _dafny.Int {
	if n.Sign() < 0 {
		return n.Negated()
	}
	return n
}
"""

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
        relPath.dropRight(".js".length) + ".cjs" -> appendJsExports(prependJsExterns(content))
      else relPath                               -> content

  // The JS backend calls extern builtins as bare file-scope names inside the
  // single-file kernel bundle, so their definitions ride at the top of the
  // same file (the arrow bodies touch _dafny only at call time, after the
  // inlined runtime has initialized).
  private val JsExternShim: String =
    """const { createHash: _sx_createHash } = require('node:crypto');
      |const specrest_externs_now = () => new BigNumber(Math.floor(Date.now() / 1000));
      |const specrest_externs_days = (n) => n.multipliedBy(86400);
      |const specrest_externs_hours = (n) => n.multipliedBy(3600);
      |const specrest_externs_minutes = (n) => n.multipliedBy(60);
      |const specrest_externs_seconds = (n) => n;
      |const specrest_externs_hash_hex = (s) =>
      |  _dafny.Seq.UnicodeFromString(_sx_createHash('sha256').update(s.toVerbatimString(false)).digest('hex'));
      |const specrest_externs_abs_int = (n) => n.abs();
      |""".stripMargin

  private def prependJsExterns(content: String): String =
    if ExternNames.exists(n => content.contains(n + "(")) then JsExternShim + content
    else content

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
