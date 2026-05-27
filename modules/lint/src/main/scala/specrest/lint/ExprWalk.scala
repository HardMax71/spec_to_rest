package specrest.lint

import specrest.ir.generated.*

object ExprWalk:

  def foreach(expr: expr_full)(visit: expr_full => Unit): Unit =
    visit(expr)
    subexprs(expr).foreach(foreach(_)(visit))
