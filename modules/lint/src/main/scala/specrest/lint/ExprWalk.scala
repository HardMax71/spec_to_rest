package specrest.lint

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

object ExprWalk:

  def foreach(expr: expr_full)(visit: expr_full => Unit): Unit =
    visit(expr)
    SpecRestGenerated.subexprs(expr).foreach(foreach(_)(visit))
