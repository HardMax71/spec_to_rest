package specrest.testgen

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object Templates:

  private val Root = "testgen-templates/python-fastapi-postgres"

  lazy val conftest: String       = loadResource("tests/conftest.py")
  lazy val predicates: String     = loadResource("tests/predicates.py")
  lazy val pytestIni: String      = loadResource("tests/pytest.ini")
  lazy val runConformance: String = loadResource("tests/run_conformance.py")

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
