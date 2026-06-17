package specrest.verify.z3

import munit.CatsEffectSuite
import specrest.ir.VerifyError
import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable
import scala.util.boundary

private object SmtTermBridgeHarness
    extends Z3EncodingSupport
    with SmtTermBridge:

  def script(term: smt_term)(
      configure: TranslateCtx => mutable.Map[String, Z3Expr] = _ =>
        mutable.Map.empty[String, Z3Expr]
  ): Either[VerifyError.Translator, Z3Script] =
    boundary:
      val ctx = new TranslateCtx(summon[TranslateBoundary])
      val env = configure(ctx)
      ctx.assertions += encodeFromSmtTerm(ctx, term, env)
      Right(Z3Script(
        sorts = ctx.sorts.values.toList.sortBy(Z3Sort.key),
        funcs = ctx.funcs.values.toList.sortBy(_.name.toLowerCase),
        assertions = ctx.assertions.toList,
        artifact = TranslatorArtifact(Nil, Nil, Nil, Nil, Nil, ctx.hasPostState)
      ))

class SmtTermBridgeTest extends CatsEffectSuite:

  private def int(n: Int): smt_term =
    ILit(BigInt(n))

  private def intSet(values: Int*): smt_term =
    values.toList.foldRight(TSetEmpty(): smt_term)((value, rest) => TSetInsert(int(value), rest))

  private def render(
      term: smt_term,
      configure: TranslateCtx => mutable.Map[String, Z3Expr] = _ =>
        mutable.Map.empty[String, Z3Expr]
  ): String =
    SmtTermBridgeHarness
      .script(term)(configure)
      .fold(err => fail(s"SmtTermBridge failed: ${err.message}"), SmtLib.renderSmtLib(_))

  test("encodes set union membership from an smt_term"):
    val out = render(TSetMember(int(1), TSetUnion(intSet(1), intSet(2))))
    assert(out.contains("(union "), out)
    assert(out.contains("(select "), out)

  test("encodes option some from an smt_term"):
    val out = render(
      TEq(TVar("opt"), TSome(int(1))),
      ctx =>
        ctx.declareFunc(Z3FunctionDecl("opt", Nil, Z3Sort.OptionOf(Z3Sort.Int)))
        mutable.Map("opt" -> Z3Expr.App("opt", Nil))
    )
    assert(out.contains("declare-datatype Option"), out)
    assert(out.contains("(declare-fun opt () (Option Int))"), out)
    assert(out.contains("(some 1)"), out)

  test("encodes forall over set from an smt_term"):
    val out = render(TForallSet("x", intSet(1, 2), TEq(TVar("x"), int(1))))
    assert(out.contains("(forall "), out)
    assert(out.contains("forall_set_x_0"), out)
    assert(out.contains("(select "), out)

  test("encodes set cardinality from an smt_term"):
    val out = render(TEq(TCard(intSet(1, 2)), int(2)))
    assert(out.contains("(declare-fun setCard_Int ((Set Int)) Int)"), out)
    assert(out.contains("(>= (setCard_Int setcard_s) 0)"), out)
    assert(out.contains("(setCard_Int "), out)

  test("encodes sum aggregate from an smt_term"):
    val out = render(TEq(TSum(intSet(1, 2), TAdd(TVar("i"), int(1))), int(3)))
    assert(out.contains("(declare-fun aggsum_b0_Set_Int ((Set Int)) Int)"), out)
    assert(out.contains("(aggsum_b0_Set_Int "), out)
