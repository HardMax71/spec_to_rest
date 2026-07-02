package specrest

import java.io.PrintWriter
import java.io.StringWriter

package object verify:
  private[verify] def renderStackTrace(e: Throwable): String =
    val sw = new StringWriter
    e.printStackTrace(new PrintWriter(sw))
    sw.toString
