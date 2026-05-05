package specrest.verify.cert

import specrest.ir.generated.SpecRestGenerated.*

import specrest.ir.*

object VerifiedSubset:

  enum SubsetStatus derives CanEqual:
    case InSubset
    case OutOfSubset(reason: String)

  def classify(expr: expr_full): SubsetStatus = expr match
    case _: BoolLitF    => SubsetStatus.InSubset
    case _: IntLitF     => SubsetStatus.InSubset
    case _: IdentifierF => SubsetStatus.InSubset
    case UnaryOpF(op, operand, _) =>
      op match
        case UNot() | UNegate() => classify(operand)
        case UCardinality()     =>
          // `#rel` is renderable only when the operand is a state-relation identifier —
          // Translator.scala:876-881 enforces the same restriction. We optimistically
          // admit any `IdentifierF` here without threading schema/state into
          // `classify`. If the identifier is actually a scalar (or undefined), both
          // `EvalIR.eval` and Lean's `eval (.cardRel name)` return `none`, and the cert
          // vacuously claims `eval = none` — soundness holds trivially. Tightening to
          // schema-aware classification would require threading `ServiceIRFull` through
          // every `classify` call site (M_L.4-followup).
          operand match
            case _: IdentifierF => SubsetStatus.InSubset
            case _ =>
              SubsetStatus.OutOfSubset(
                "UnaryOp(Cardinality): operand must be a state-relation identifier"
              )
        case other => SubsetStatus.OutOfSubset(s"UnaryOp.$other not in M_L.1 verified subset")
    case BinaryOpF(op, l, r, _) =>
      op match
        case BAnd() | BOr() | BImplies() | BIff() |
            BEq() | BNeq() |
            BLt() | BLe() | BGt() | BGe() |
            BAdd() | BSub() | BMul() | BDiv() =>
          chooseWorse(classify(l), classify(r))
        case BIn() | BNotIn() =>
          // Identifier RHS remains the legacy state-relation domain membership path.
          // Any other RHS is rendered as set-valued membership (`.setMember`) and
          // therefore must itself be in the verified subset.
          r match
            case _: IdentifierF => chooseWorse(classify(l), classify(r))
            case SetLiteralF(elements, _) =>
              chooseWorse(classify(l), classifySetLiteral(elements, allowEmpty = true))
            case _ => chooseWorse(classify(l), classify(r))
        case BUnion() | BIntersect() | BDiff() =>
          chooseWorse(classify(l), classify(r))
        case BSubset() =>
          // BinaryOp(Subset) over two state-relation identifiers desugars at emit
          // time to `forallRel x r1, member x r2` — pure composition over existing
          // M_L.4.f forallRel + M_L.2 member arms. No new Lean constructor.
          (l, r) match
            case (_: IdentifierF, _: IdentifierF) => SubsetStatus.InSubset
            case _ =>
              SubsetStatus.OutOfSubset(
                "BinaryOp(Subset): both operands must be state-relation identifiers " +
                  "(set-literal subset is collections-deferred)"
              )
    case QuantifierF(kind, bindings, body, _) =>
      // ∃, No, Exists are encoded as compositions of `forallEnum/forallRel + unNot` at emit time:
      //   ∃ x, P  ≡  ¬ ∀ x, ¬ P
      //   No x, P ≡  ∀ x, ¬ P
      //   Exists  alias of ∃.
      // All four kinds share the same single-binding-over-identifier restriction; the
      // identifier may resolve to either an enum (forallEnum) or a state-relation
      // (forallRel) — Emit.scala disambiguates via ServiceIRFull.d.
      kind match
        case QAll() | QSome() | QNo() | QExists() =>
          bindings match
            case List(QuantifierBindingFull(_, IdentifierF(_, _), _, _)) =>
              val bodyStatus = classify(body)
              val bindStatus = bindings.foldLeft[SubsetStatus](SubsetStatus.InSubset) {
                case (acc, QuantifierBindingFull(_, dom, _, _)) =>
                  chooseWorse(acc, classify(dom))
              }
              chooseWorse(bindStatus, bodyStatus)
            case _ =>
              SubsetStatus.OutOfSubset(
                s"Quantifier($kind): only single-binding over an enum or relation identifier is supported"
              )
    case LetF(_, value, body, _) =>
      chooseWorse(classify(value), classify(body))
    case EnumAccessF(IdentifierF(_, _), _, _) => SubsetStatus.InSubset
    case _: EnumAccessF =>
      SubsetStatus.OutOfSubset(
        "EnumAccess: only `EnumName.b` (Identifier base) is supported"
      )
    case PrimeF(inner, _)        => classify(inner)
    case PreF(inner, _)          => classify(inner)
    case WithF(base, updates, _) =>
      // M_L.4.b-ext Phase 5.a: With (record-update) lowers to a chain of `withRec`
      // Lean ctors at emit time. Phase 4b ships the parallel Skolem semantics on
      // the Lean side (zero `sorry`); the Scala-side EvalIR mirrors via
      // `Value.VEntityWith`. Multi-field updates fold left into a chain.
      val baseStatus = classify(base)
      updates.foldLeft(baseStatus) { case (acc, FieldAssignFull(_, v, _)) =>
        chooseWorse(acc, classify(v))
      }
    case FieldAccessF(base, _, _) =>
      // M_L.4.k generalised the FieldAccess base from a bare Identifier to any
      // expression that evaluates to a `vEntity`. Bare Identifier remains the
      // backward-compatible M_L.4.h case (state-scalar.field). Nested forms
      // — Index-result `users[uid].email`, chained `current_user.profile.email`,
      // and quantifier-bound `forall t in tasks, t.priority` — are all admitted
      // here and dispatched by the eval/translate arms via the entity-id-keyed
      // entityFields/predFields tables.
      classify(base)
    case IndexF(IdentifierF(_, _), keyExpr, _) =>
      classify(keyExpr)
    case _: IndexF =>
      SubsetStatus.OutOfSubset(
        "Index: only state-relation identifier base is supported (e.g. `users[uid]`)"
      )
    case _: CallF        => SubsetStatus.OutOfSubset("Call (builtins): later expansion")
    case _: IfF          => SubsetStatus.OutOfSubset("If: deferred")
    case _: LambdaF      => SubsetStatus.OutOfSubset("Lambda: outside FOL")
    case _: ConstructorF => SubsetStatus.OutOfSubset("Constructor: deferred")
    case SetLiteralF(elements, _) =>
      classifySetLiteral(elements, allowEmpty = false)
    case _: MapLiteralF => SubsetStatus.OutOfSubset("MapLiteral: collections deferred")
    case _: SetComprehensionF =>
      SubsetStatus.OutOfSubset("SetComprehension: collections deferred")
    case _: SeqLiteralF => SubsetStatus.OutOfSubset("SeqLiteral: collections deferred")
    case _: MatchesF    => SubsetStatus.OutOfSubset("Matches (regex): deferred")
    case _: SomeWrapF   => SubsetStatus.OutOfSubset("SomeWrap: option semantics deferred")
    case _: TheF        => SubsetStatus.OutOfSubset("The: choice operator deferred")
    case _: NoneLitF    => SubsetStatus.OutOfSubset("NoneLit: option semantics deferred")
    case _: FloatLitF   => SubsetStatus.OutOfSubset("FloatLit: no committed solver semantics")
    case _: StringLitF =>
      SubsetStatus.OutOfSubset("StringLit: deferred (no regex/string theory)")

  private def chooseWorse(a: SubsetStatus, b: SubsetStatus): SubsetStatus = (a, b) match
    case (SubsetStatus.InSubset, x)             => x
    case (x, SubsetStatus.InSubset)             => x
    case (out @ SubsetStatus.OutOfSubset(_), _) => out

  private def classifySetLiteral(elements: List[expr_full], allowEmpty: Boolean): SubsetStatus =
    if elements.isEmpty && !allowEmpty then
      SubsetStatus.OutOfSubset(
        "SetLiteral: empty standalone literal needs type context"
      )
    else
      elements.foldLeft[SubsetStatus](SubsetStatus.InSubset): (acc, elem) =>
        chooseWorse(acc, classify(elem))

  def isInSubset(expr: expr_full): Boolean = classify(expr) match
    case SubsetStatus.InSubset       => true
    case _: SubsetStatus.OutOfSubset => false
