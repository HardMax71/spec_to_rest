package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object TypeMismatch extends LintPass:
  val code = "L01"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val out = List.newBuilder[LintDiagnostic]
    val visit: expr_full => Unit =
      case BinaryOpF(op, left, right, span) =>
        checkBinary(op, left, right, span, out)
      case UnaryOpF(UNot(), operand, span) =>
        litClass(operand) match
          case Some(c) if !isBool(c) =>
            out += LintDiagnostic(
              code,
              LintLevel.Error,
              s"logical 'not' applied to a ${describeLitClass(c)} literal",
              span
            )
          case _ => ()
      case UnaryOpF(UNegate(), operand, span) =>
        litClass(operand) match
          case Some(c) if !isNumeric(c) =>
            out += LintDiagnostic(
              code,
              LintLevel.Error,
              s"arithmetic '-' applied to a ${describeLitClass(c)} literal",
              span
            )
          case _ => ()
      case _ => ()

    def visitAll(e: expr_full): Unit = ExprWalk.foreach(e)(visit)

    for case OperationDeclFull(_, _, _, requires, ensures, _) <- ir.g do
      requires.foreach(visitAll)
      ensures.foreach(visitAll)
    ir.i.foreach { case InvariantDeclFull(_, e, _) => visitAll(e) }
    ir.j.foreach { case TemporalDeclFull(_, b, _) => visitAll(temporalArg(b)) }
    ir.k.foreach { case FactDeclFull(_, e, _) => visitAll(e) }
    ir.l.foreach { case FunctionDeclFull(_, _, _, body, _) => visitAll(body) }
    ir.m.foreach { case PredicateDeclFull(_, _, body, _) => visitAll(body) }
    ir.c.foreach { case EntityDeclFull(_, _, fields, invs, _) =>
      fields.foreach { case FieldDeclFull(_, _, c, _) => c.foreach(visitAll) }
      invs.foreach(visitAll)
    }
    ir.e.foreach { case TypeAliasDeclFull(_, _, c, _) => c.foreach(visitAll) }

    out.result()

  private def isBool(c: lit_class): Boolean = c match
    case LcBool() => true
    case _        => false

  private def isNumeric(c: lit_class): Boolean = c match
    case LcNumeric() => true
    case _           => false

  private def isBoolOrNone(c: lit_class): Boolean = c match
    case LcBool() | LcNone() => true
    case _                   => false

  private def isNonCollection(c: lit_class): Boolean = c match
    case LcNumeric() | LcBool() | LcStringLike() | LcNone() => true
    case _                                                  => false

  private def checkBinary(
      op: bin_op_full,
      left: expr_full,
      right: expr_full,
      span: Option[span_t],
      out: scala.collection.mutable.Builder[LintDiagnostic, List[LintDiagnostic]]
  ): Unit =
    val lc = litClass(left)
    val rc = litClass(right)
    op match
      case BAdd() | BSub() | BMul() | BDiv() =>
        // `+` and `-` are overloaded for set/map union and diff in this DSL,
        // and `+` for string concatenation. Only flag when a literal is clearly
        // never a number (Bool or None) — those cases admit no overload.
        val bad = lc.exists(isBoolOrNone) || rc.exists(isBoolOrNone)
        if bad then
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"arithmetic '${binOpName(op)}' has a ${describeLitClass((lc ++ rc).find(isBoolOrNone).get)} literal operand",
            span
          )
      case BLt() | BGt() | BLe() | BGe() =>
        // Comparisons can apply to numbers, strings (lexicographic), or sets
        // (subset/superset). Only flag on Bool/None literals (never ordered).
        val bad = lc.exists(isBoolOrNone) || rc.exists(isBoolOrNone)
        if bad then
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"comparison '${binOpName(op)}' has a ${describeLitClass((lc ++ rc).find(isBoolOrNone).get)} literal operand",
            span
          )
      case BAnd() | BOr() | BImplies() | BIff() =>
        val bad = lc.exists(!isBool(_)) || rc.exists(!isBool(_))
        if bad then
          val offender = (lc ++ rc).find(!isBool(_)).get
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"logical '${binOpName(op)}' applied to a ${describeLitClass(offender)} literal",
            span
          )
      case BIn() | BNotIn() =>
        val bad = rc.exists(isNonCollection)
        if bad then
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"'${binOpName(op)}' right operand is a ${describeLitClass(rc.get)} literal, not a collection",
            span
          )
      case _ => ()
