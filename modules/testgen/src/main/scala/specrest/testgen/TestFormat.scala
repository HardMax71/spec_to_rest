package specrest.testgen

import specrest.ir.PrettyPrint
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

private[testgen] object TestFormat:

  def invName(inv: invariant_decl, idx: Int): String =
    SpecRestGenerated.invName(inv).getOrElse(s"anon_$idx")

  def prettyOneLine(e: expr): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  def escapeDocstring(s: String): String =
    s.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")
