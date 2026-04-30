package specrest.verify

import cats.effect.IO
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Test as ScalaCheckTest
import org.scalacheck.effect.PropF
import specrest.ir.Expr as IExpr
import specrest.ir.InvariantDecl
import specrest.ir.OperationDecl
import specrest.ir.ServiceIR
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

  def collectFreeIdentifiers(ir: ServiceIR): Set[String] =
    val invIds = ir.invariants.flatMap(walk).toSet
    val opIds  = ir.operations.flatMap(opIdentifiers).toSet
    invIds ++ opIds

  private def opIdentifiers(op: OperationDecl): Set[String] =
    val params = (op.inputs ++ op.outputs).map(_.name).toSet
    val raw    = (op.requires ++ op.ensures).flatMap(walk).toSet
    raw -- params

  private def walk(inv: InvariantDecl): Set[String] = walk(inv.expr)

  private def walk(e: IExpr): Set[String] = e match
    case IExpr.Identifier(n, _)     => Set(n)
    case IExpr.BinaryOp(_, l, r, _) => walk(l) ++ walk(r)
    case IExpr.UnaryOp(_, x, _)     => walk(x)
    case IExpr.Quantifier(_, bs, body, _) =>
      val bound  = bs.map(_.variable).toSet
      val domain = bs.flatMap(b => walk(b.domain).toList).toSet
      val inner  = walk(body)
      (domain ++ inner) -- bound
    case IExpr.SomeWrap(x, _)         => walk(x)
    case IExpr.The(v, d, b, _)        => (walk(d) ++ walk(b)) - v
    case IExpr.FieldAccess(b, _, _)   => walk(b)
    case IExpr.EnumAccess(b, _, _)    => walk(b)
    case IExpr.Index(b, i, _)         => walk(b) ++ walk(i)
    case IExpr.Call(c, args, _)       => walk(c) ++ args.flatMap(walk).toSet
    case IExpr.Prime(x, _)            => walk(x)
    case IExpr.Pre(x, _)              => walk(x)
    case IExpr.With(b, fs, _)         => walk(b) ++ fs.flatMap(f => walk(f.value)).toSet
    case IExpr.If(c, t, el, _)        => walk(c) ++ walk(t) ++ walk(el)
    case IExpr.Let(v, value, body, _) => walk(value) ++ (walk(body) - v)
    case IExpr.Lambda(p, body, _)     => walk(body) - p
    case IExpr.Constructor(_, fs, _)  => fs.flatMap(f => walk(f.value)).toSet
    case IExpr.SetLiteral(xs, _)      => xs.flatMap(walk).toSet
    case IExpr.MapLiteral(es, _) =>
      es.flatMap(me => walk(me.key) ++ walk(me.value)).toSet
    case IExpr.SetComprehension(v, d, p, _) => walk(d) ++ (walk(p) - v)
    case IExpr.SeqLiteral(xs, _)            => xs.flatMap(walk).toSet
    case IExpr.Matches(x, _, _)             => walk(x)
    case _: IExpr.IntLit                    => Set.empty
    case _: IExpr.FloatLit                  => Set.empty
    case _: IExpr.StringLit                 => Set.empty
    case _: IExpr.BoolLit                   => Set.empty
    case _: IExpr.NoneLit                   => Set.empty

class TranslatorPropTest extends CatsEffectSuite, ScalaCheckEffectSuite:
  import TranslatorPropTest.*

  override def scalaCheckTestParameters: ScalaCheckTest.Parameters =
    super.scalaCheckTestParameters
      .withMinSuccessfulTests(50)
      .withMaxDiscardRatio(5.0f)

  private def buildIR(spec: SpecGen.GeneratedSpec): IO[Option[ServiceIR]] =
    val source = spec.render
    Parse.parseSpec(source).flatMap:
      case Left(_) => IO.pure(None)
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Left(_)   => None
          case Right(ir) => Some(ir)

  test("property #1 — translate is deterministic"):
    PropF.forAllF(SpecGen.genSpec): spec =>
      buildIR(spec).flatMap:
        case None => IO.unit
        case Some(ir) =>
          for
            a <- Z3Translator.translate(ir)
            b <- Z3Translator.translate(ir)
          yield (a, b) match
            case (Right(s1), Right(s2)) =>
              assertEquals(SmtLib.renderSmtLib(s1), SmtLib.renderSmtLib(s2))
            case (Left(_), Left(_)) => ()
            case (l, r) =>
              fail(s"asymmetric translation outcome: lhs=$l rhs=$r\nspec=${spec.render}")

  test("property #4 — every free Identifier resolves to a translator-declared name"):
    PropF.forAllF(SpecGen.genSpec): spec =>
      buildIR(spec).flatMap:
        case None => IO.unit
        case Some(ir) =>
          Z3Translator.translate(ir).map:
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
              val predicates = ir.predicates.map(_.name).toSet
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
            case Left(_) => ()

  test("property #1b — SMT-LIB rendering is idempotent on the same script"):
    PropF.forAllF(SpecGen.genSpec): spec =>
      buildIR(spec).flatMap:
        case None => IO.unit
        case Some(ir) =>
          Z3Translator.translate(ir).map:
            case Right(script) =>
              assertEquals(SmtLib.renderSmtLib(script), SmtLib.renderSmtLib(script))
            case Left(_) => ()

  test("property #3 — Z3 and Alloy translators preserve all IR invariants"):
    PropF.forAllF(SpecGen.genSpec): spec =>
      buildIR(spec).flatMap:
        case None => IO.unit
        case Some(ir) =>
          for
            z3R    <- Z3Translator.translate(ir)
            alloyR <- AlloyTranslator.translateGlobal(ir, scope = 5)
          yield (z3R, alloyR) match
            case (Right(script), Right(module)) =>
              val invCount = ir.invariants.length
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
            case _ => ()
