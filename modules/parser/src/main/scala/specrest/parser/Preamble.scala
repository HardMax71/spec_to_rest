package specrest.parser

import specrest.ir.PredicateDecl

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

final class PreambleLoadException(message: String) extends RuntimeException(message)

object Preamble:

  private val ResourcePath = "specrest/parser/preamble.spec"

  lazy val predicates: List[PredicateDecl] =
    load().fold(err => throw err, identity)

  private[parser] def load(): Either[PreambleLoadException, List[PredicateDecl]] =
    for
      text <- loadResource(ResourcePath)
      parsed <- Parse
                  .parseSpecCore(text)
                  .left
                  .map(err => PreambleLoadException(s"specrest preamble.spec parse failure: $err"))
      ir <- Builder
              .buildIRCore(parsed.tree, mergePreamble = false)
              .left
              .map(err => PreambleLoadException(s"specrest preamble.spec build failure: $err"))
    yield ir.predicates

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private def loadResource(path: String): Either[PreambleLoadException, String] =
    val is = getClass.getClassLoader.getResourceAsStream(path)
    if is == null then Left(PreambleLoadException(s"specrest preamble resource missing: $path"))
    else
      try
        val out    = new ByteArrayOutputStream()
        val buffer = new Array[Byte](8192)
        var read   = is.read(buffer)
        while read != -1 do
          out.write(buffer, 0, read)
          read = is.read(buffer)
        Right(new String(out.toByteArray, StandardCharsets.UTF_8))
      finally is.close()
