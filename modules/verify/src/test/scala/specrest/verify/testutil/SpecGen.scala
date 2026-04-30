package specrest.verify.testutil

import org.scalacheck.Gen

object SpecGen:

  enum AtomicType derives CanEqual:
    case IntT, BoolT

    def render: String = this match
      case IntT  => "Int"
      case BoolT => "Bool"

  final case class StateField(name: String, ty: AtomicType):
    def render: String = s"$name: ${ty.render}"

  enum GExpr derives CanEqual:
    case IntLit(v: Long)
    case BoolLit(v: Boolean)
    case Ident(name: String)
    case Prime(name: String)
    case Pre(name: String)
    case Not(e: GExpr)
    case Neg(e: GExpr)
    case BoolBin(op: BoolBinOp, l: GExpr, r: GExpr)
    case Cmp(op: CmpOp, l: GExpr, r: GExpr)
    case Arith(op: ArithOp, l: GExpr, r: GExpr)

    def render: String = this match
      case IntLit(v)         => v.toString
      case BoolLit(true)     => "true"
      case BoolLit(false)    => "false"
      case Ident(n)          => n
      case Prime(n)          => s"$n'"
      case Pre(n)            => s"pre($n)"
      case Not(e)            => s"not (${e.render})"
      case Neg(e)            => s"-(${e.render})"
      case BoolBin(op, l, r) => s"(${l.render}) ${op.token} (${r.render})"
      case Cmp(op, l, r)     => s"(${l.render}) ${op.token} (${r.render})"
      case Arith(op, l, r)   => s"(${l.render}) ${op.token} (${r.render})"

  enum BoolBinOp(val token: String) derives CanEqual:
    case And     extends BoolBinOp("and")
    case Or      extends BoolBinOp("or")
    case Implies extends BoolBinOp("implies")
    case Iff     extends BoolBinOp("iff")

  enum CmpOp(val token: String) derives CanEqual:
    case Eq  extends CmpOp("=")
    case Neq extends CmpOp("!=")
    case Lt  extends CmpOp("<")
    case Le  extends CmpOp("<=")
    case Gt  extends CmpOp(">")
    case Ge  extends CmpOp(">=")

  enum ArithOp(val token: String) derives CanEqual:
    case Add extends ArithOp("+")
    case Sub extends ArithOp("-")
    case Mul extends ArithOp("*")

  enum ExprSort derives CanEqual:
    case BoolS, IntS

  final case class Operation(
      name: String,
      requires: List[GExpr],
      ensures: List[GExpr]
  ):
    def render: String =
      val req =
        if requires.isEmpty then "      true" else requires.map("      " + _.render).mkString("\n")
      val ens =
        if ensures.isEmpty then "      true" else ensures.map("      " + _.render).mkString("\n")
      s"""  operation $name {
         |    requires:
         |$req
         |    ensures:
         |$ens
         |  }""".stripMargin

  final case class GeneratedSpec(
      name: String,
      state: List[StateField],
      invariants: List[GExpr],
      operations: List[Operation]
  ):
    def render: String =
      val stateBlock = state.map("    " + _.render).mkString("\n")
      val invBlock = invariants.zipWithIndex.map: (e, i) =>
        s"  invariant inv_$i:\n    ${e.render}"
      .mkString("\n\n")
      val opBlock = operations.map(_.render).mkString("\n\n")
      val parts = List(
        s"  state {\n$stateBlock\n  }",
        if operations.isEmpty then "" else opBlock,
        if invariants.isEmpty then "" else invBlock
      ).filter(_.nonEmpty)
      s"service $name {\n${parts.mkString("\n\n")}\n}\n"

  private val genIdent: Gen[String] =
    for
      head <- Gen.oneOf('a' to 'z')
      len  <- Gen.chooseNum(1, 4)
      tail <- Gen.listOfN(len, Gen.oneOf('a' to 'z'))
    yield (head :: tail).mkString

  private val reserved = Set(
    "service",
    "entity",
    "enum",
    "type",
    "state",
    "operation",
    "transition",
    "invariant",
    "temporal",
    "fact",
    "conventions",
    "import",
    "function",
    "predicate",
    "extends",
    "field",
    "input",
    "output",
    "requires",
    "ensures",
    "one",
    "lone",
    "set",
    "and",
    "or",
    "not",
    "implies",
    "iff",
    "in",
    "subset",
    "matches",
    "union",
    "intersect",
    "minus",
    "all",
    "some",
    "no",
    "exists",
    "if",
    "then",
    "else",
    "let",
    "pre",
    "with",
    "the",
    "where",
    "via",
    "when",
    "true",
    "false"
  )

  private val genFreshName: Gen[String] =
    genIdent.suchThat(s => !reserved.contains(s))

  private val genAtomicType: Gen[AtomicType] =
    Gen.oneOf(AtomicType.IntT, AtomicType.BoolT)

  private val genStateFields: Gen[List[StateField]] =
    for
      n     <- Gen.chooseNum(1, 3)
      names <- Gen.listOfN(n, genFreshName).map(_.distinct.take(n))
      tys   <- Gen.listOfN(names.size, genAtomicType)
    yield names.zip(tys).map(StateField.apply.tupled)

  private def genCmpOp: Gen[CmpOp] =
    Gen.oneOf(CmpOp.Eq, CmpOp.Neq, CmpOp.Lt, CmpOp.Le, CmpOp.Gt, CmpOp.Ge)

  private def genCmpBoolOp: Gen[CmpOp] =
    Gen.oneOf(CmpOp.Eq, CmpOp.Neq)

  private def genBoolBinOp: Gen[BoolBinOp] =
    Gen.oneOf(BoolBinOp.And, BoolBinOp.Or, BoolBinOp.Implies, BoolBinOp.Iff)

  private def genArithOp: Gen[ArithOp] =
    Gen.oneOf(ArithOp.Add, ArithOp.Sub, ArithOp.Mul)

  private def intRefs(state: List[StateField], includePrimePre: Boolean): List[GExpr] =
    val ints = state.filter(_.ty == AtomicType.IntT).map(_.name)
    val base = ints.map(GExpr.Ident.apply)
    if includePrimePre then
      base ++ ints.map(GExpr.Prime.apply) ++ ints.map(GExpr.Pre.apply)
    else base

  private def boolRefs(state: List[StateField], includePrimePre: Boolean): List[GExpr] =
    val bools = state.filter(_.ty == AtomicType.BoolT).map(_.name)
    val base  = bools.map(GExpr.Ident.apply)
    if includePrimePre then
      base ++ bools.map(GExpr.Prime.apply) ++ bools.map(GExpr.Pre.apply)
    else base

  def genIntExpr(
      state: List[StateField],
      depth: Int,
      includePrimePre: Boolean
  ): Gen[GExpr] =
    val refs = intRefs(state, includePrimePre)
    val leaf: Gen[GExpr] =
      val opts: List[Gen[GExpr]] =
        Gen.chooseNum(-5L, 5L).map(GExpr.IntLit.apply) ::
          (if refs.isEmpty then Nil else List(Gen.oneOf(refs)))
      Gen.oneOf(opts).flatMap(identity)
    if depth <= 0 then leaf
    else
      Gen.frequency(
        2 -> leaf,
        1 -> (for
          op <- genArithOp
          l  <- genIntExpr(state, depth - 1, includePrimePre)
          r  <- genIntExpr(state, depth - 1, includePrimePre)
        yield GExpr.Arith(op, l, r)),
        1 -> genIntExpr(state, depth - 1, includePrimePre).map(GExpr.Neg.apply)
      )

  def genBoolExpr(
      state: List[StateField],
      depth: Int,
      includePrimePre: Boolean
  ): Gen[GExpr] =
    val brefs = boolRefs(state, includePrimePre)
    val leaf: Gen[GExpr] =
      val baseOpts: List[Gen[GExpr]] = List(
        Gen.const(GExpr.BoolLit(true)),
        Gen.const(GExpr.BoolLit(false))
      ) ++ (if brefs.isEmpty then Nil else List(Gen.oneOf(brefs)))
      Gen.oneOf(baseOpts).flatMap(identity)
    if depth <= 0 then leaf
    else
      val intDepth = math.max(0, depth - 1)
      Gen.frequency(
        2 -> leaf,
        2 -> (for
          op <- genCmpOp
          l  <- genIntExpr(state, intDepth, includePrimePre)
          r  <- genIntExpr(state, intDepth, includePrimePre)
        yield GExpr.Cmp(op, l, r)),
        1 -> (for
          op <- genCmpBoolOp
          l  <- genBoolExpr(state, intDepth, includePrimePre)
          r  <- genBoolExpr(state, intDepth, includePrimePre)
        yield GExpr.Cmp(op, l, r)),
        2 -> (for
          op <- genBoolBinOp
          l  <- genBoolExpr(state, depth - 1, includePrimePre)
          r  <- genBoolExpr(state, depth - 1, includePrimePre)
        yield GExpr.BoolBin(op, l, r)),
        1 -> genBoolExpr(state, depth - 1, includePrimePre).map(GExpr.Not.apply)
      )

  private def genOperation(
      state: List[StateField],
      idx: Int
  ): Gen[Operation] =
    val capName = s"Op${idx + 1}"
    for
      reqN <- Gen.chooseNum(0, 2)
      reqs <- Gen.listOfN(reqN, genBoolExpr(state, depth = 2, includePrimePre = false))
      ensN <- Gen.chooseNum(0, 2)
      enss <- Gen.listOfN(ensN, genBoolExpr(state, depth = 2, includePrimePre = true))
    yield Operation(capName, reqs, enss)

  val genSpec: Gen[GeneratedSpec] =
    for
      svcName <- genFreshName.map(s => s.head.toUpper +: s.tail)
      state   <- genStateFields
      invN    <- Gen.chooseNum(1, 2)
      invs    <- Gen.listOfN(invN, genBoolExpr(state, depth = 2, includePrimePre = false))
      opN     <- Gen.chooseNum(0, 2)
      ops <-
        Gen.sequence[List[Operation], Operation]((0 until opN).toList.map(genOperation(state, _)))
    yield GeneratedSpec(svcName, state, invs, ops)
