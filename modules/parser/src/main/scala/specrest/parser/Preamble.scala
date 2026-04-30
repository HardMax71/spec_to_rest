package specrest.parser

import specrest.ir.PredicateDecl

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object Preamble:

  private val ResourcePath = "specrest/parser/preamble.spec"

  lazy val predicates: List[PredicateDecl] =
    val text = loadResource(ResourcePath)
    val parsed = Parse
      .parseSpecCore(text)
      .fold(err => sys.error(s"specrest preamble.spec parse failure: $err"), identity)
    val ir = Builder
      .buildIRCore(parsed.tree, mergePreamble = false)
      .fold(err => sys.error(s"specrest preamble.spec build failure: $err"), identity)
    ir.predicates

  private def loadResource(path: String): String =
    val is = getClass.getClassLoader.getResourceAsStream(path)
    if is == null then sys.error(s"specrest preamble resource missing: $path")
    try
      val out    = new ByteArrayOutputStream()
      val buffer = new Array[Byte](8192)
      var read   = is.read(buffer)
      while read != -1 do
        out.write(buffer, 0, read)
        read = is.read(buffer)
      new String(out.toByteArray, StandardCharsets.UTF_8)
    finally is.close()
