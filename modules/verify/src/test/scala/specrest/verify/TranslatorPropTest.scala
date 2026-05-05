package specrest.verify

import cats.effect.IO
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Test as ScalaCheckTest
import org.scalacheck.effect.PropF
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.alloy.Translator as AlloyTranslator
import specrest.verify.testutil.SpecGen
import specrest.verify.z3.ArtifactStateEntry
import specrest.verify.z3.SmtLib
import specrest.verify.z3.Translator as Z3Translator

object TranslatorPropTest:

  val BuiltinNames: Set[String] = Set(
    "true",
    "false",
    "and",
    "or",
    "not",
    "implies",
    "=",
    "!=",
    "<",
    "<=",
    ">",
    ">=",
    "+",
    "-",
    "*",
    "/"
  )

  def collectFreeIdentifiers(ir: ServiceIRFull): Set[String] =
    val invs   = ir.i.collect { case inv: InvariantDeclFull => inv }
    val ops    = ir.g.collect { case op: OperationDeclFull => op }
    val invIds = invs.flatMap(walkInv).toSet
    val opIds  = ops.flatMap(opIdentifiers).toSet
    invIds ++ opIds

  private def opIdentifiers(op: OperationDeclFull): Set[String] =
    val params = (op.b ++ op.c).collect { case p: ParamDeclFull => p.a }.toSet
    val raw    = (op.d ++ op.e).flatMap(walk).toSet
    raw -- params

  private def walkInv(inv: InvariantDeclFull): Set[String] = walk(inv.b)

  private def walk(e: expr_full): Set[String] = e match
    case IdentifierF(n, _)     => Set(n)
    case BinaryOpF(_, l, r, _) => walk(l) ++ walk(r)
    case UnaryOpF(_, x, _)     => walk(x)
    case QuantifierF(_, bs, body, _) =>
      val bsConcrete = bs.collect { case b: QuantifierBindingFull => b }
      val bound      = bsConcrete.map(_.a).toSet
      val domain     = bsConcrete.flatMap(b => walk(b.b).toList).toSet
      val inner      = walk(body)
      (domain ++ inner) -- bound
    case SomeWrapF(x, _)       => walk(x)
    case TheF(v, d, b, _)      => (walk(d) ++ walk(b)) - v
    case FieldAccessF(b, _, _) => walk(b)
    case EnumAccessF(b, _, _)  => walk(b)
    case IndexF(b, i, _)       => walk(b) ++ walk(i)
    case CallF(c, args, _)     => walk(c) ++ args.flatMap(walk).toSet
    case PrimeF(x, _)          => walk(x)
    case PreF(x, _)            => walk(x)
    case WithF(b, fs, _) =>
      val fas = fs.collect { case fa: FieldAssignFull => fa }
      walk(b) ++ fas.flatMap(f => walk(f.b)).toSet
    case IfF(c, t, el, _)        => walk(c) ++ walk(t) ++ walk(el)
    case LetF(v, value, body, _) => walk(value) ++ (walk(body) - v)
    case LambdaF(p, body, _)     => walk(body) - p
    case ConstructorF(_, fs, _) =>
      val fas = fs.collect { case fa: FieldAssignFull => fa }
      fas.flatMap(f => walk(f.b)).toSet
    case SetLiteralF(xs, _) => xs.flatMap(walk).toSet
    case MapLiteralF(es, _) =>
      val mes = es.collect { case me: MapEntryFull => me }
      mes.flatMap(me => walk(me.a) ++ walk(me.b)).toSet
    case SetComprehensionF(v, d, p, _) => walk(d) ++ (walk(p) - v)
    case SeqLiteralF(xs, _)            => xs.flatMap(walk).toSet
    case MatchesF(x, _, _)             => walk(x)
    case _: IntLitF                    => Set.empty
    case _: FloatLitF                  => Set.empty
    case _: StringLitF                 => Set.empty
    case _: BoolLitF                   => Set.empty
    case _: NoneLitF                   => Set.empty

class TranslatorPropTest extends CatsEffectSuite, ScalaCheckEffectSuite:
  import TranslatorPropTest.*

  override def scalaCheckTestParameters: ScalaCheckTest.Parameters =
    super.scalaCheckTestParameters
      .withMinSuccessfulTests(50)
      .withMaxDiscardRatio(0.1f)

  private def buildIR(spec: SpecGen.GeneratedSpec): IO[ServiceIRFull] =
    val source = spec.render
    Parse.parseSpec(source).flatMap:
      case Left(err) =>
        IO.raiseError(
          new AssertionError(
            "generator emitted spec the parser rejected — fix SpecGen.\n" +
              s"errors=${err.errors}\nspec=$source"
          )
        )
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).flatMap:
          case Left(err) =>
            IO.raiseError(
              new AssertionError(
                "generator emitted spec the IR builder rejected — fix SpecGen.\n" +
                  s"error=${err.message}\nspec=$source"
              )
            )
          case Right(ir) => IO.pure(ir)

  test("property #1 — translate is deterministic"):
    PropF.forAllF(SpecGen.genSpec): spec =>
      buildIR(spec).flatMap: ir =>
        for
          a <- Z3Translator.translate(ir)
          b <- Z3Translator.translate(ir)
        yield (a, b) match
          case (Right(s1), Right(s2)) =>
            assertEquals(SmtLib.renderSmtLib(s1), SmtLib.renderSmtLib(s2))
          case other =>
            fail(
              s"translator did not produce Right twice on the same IR: $other\nspec=${spec.render}"
            )

  test("property #4 — every free Identifier resolves to a translator-declared name"):
    PropF.forAllF(SpecGen.genSpec): spec =>
      buildIR(spec).flatMap: ir =>
        Z3Translator.translate(ir).map:
          case Left(err) =>
            fail(s"translator rejected a generated IR: ${err.message}\nspec=${spec.render}")
          case Right(script) =>
            val artifact     = script.artifact
            val entityNames  = artifact.entities.map(_.name).toSet
            val entityFields = artifact.entities.flatMap(_.fields.map(_.name)).toSet
            val enumNames    = artifact.enums.map(_.name).toSet
            val enumMembers  = artifact.enums.flatMap(_.members.map(_.name)).toSet
            val stateNames = artifact.state.map:
              case ArtifactStateEntry.Const(n, _, _, _)             => n
              case ArtifactStateEntry.Relation(n, _, _, _, _, _, _) => n
            .toSet
            val ioNames    = (artifact.inputs ++ artifact.outputs).map(_.name).toSet
            val predicates = ir.m.collect { case p: PredicateDeclFull => p.a }.toSet
            val freeIds    = collectFreeIdentifiers(ir)
            val resolvable =
              entityNames ++ entityFields ++ enumNames ++ enumMembers ++
                stateNames ++ ioNames ++ predicates ++ BuiltinNames
            val unresolved = freeIds -- resolvable
            assert(
              unresolved.isEmpty,
              s"unresolved identifiers: $unresolved\n" +
                s"state=$stateNames entities=$entityNames enums=$enumNames inputs/outputs=$ioNames\n" +
                s"spec=${spec.render}"
            )

  test("property #3 — Z3 and Alloy translators preserve all IR invariants"):
    PropF.forAllF(SpecGen.genSpec): spec =>
      buildIR(spec).flatMap: ir =>
        for
          z3R    <- Z3Translator.translate(ir)
          alloyR <- AlloyTranslator.translateGlobal(ir, scope = 5)
        yield (z3R, alloyR) match
          case (Right(script), Right(module)) =>
            val invCount = ir.i.length
            assertEquals(
              module.facts.length,
              invCount,
              s"alloy facts=${module.facts.length}, expected $invCount\nspec=${spec.render}"
            )
            if invCount > 0 then
              assert(
                script.assertions.nonEmpty,
                s"z3 produced no assertions for $invCount invariant(s)\nspec=${spec.render}"
              )
          case (Left(z3Err), _) =>
            fail(s"z3 translator rejected a generated IR: ${z3Err.message}\nspec=${spec.render}")
          case (_, Left(alloyErr)) =>
            fail(
              s"alloy translator rejected a generated IR: ${alloyErr.message}\nspec=${spec.render}"
            )
