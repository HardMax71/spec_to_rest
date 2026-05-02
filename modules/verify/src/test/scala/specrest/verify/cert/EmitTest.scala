package specrest.verify.cert

import munit.FunSuite
import specrest.ir.*

class EmitTest extends FunSuite:

  private val proofsPath = "/tmp/spec-rest-proofs-stub"

  private def stubState(field: StateFieldDecl): StateDecl =
    StateDecl(fields = List(field))

  test("safe_counter-shaped IR yields a verified-subset certificate"):
    val invariant = InvariantDecl(
      name = Some("countNonNegative"),
      expr = Expr.BinaryOp(
        BinOp.Ge,
        Expr.Identifier("count"),
        Expr.IntLit(0)
      )
    )
    val ir = ServiceIR(
      name = "SafeCounter",
      state = Some(
        stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
      ),
      invariants = List(invariant)
    )

    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule

    assertEquals(bundle.summary.totalChecks, 1)
    assertEquals(bundle.summary.certifiedChecks, 1)
    assertEquals(bundle.summary.stubbedChecks, 0)

    assert(
      rendered.contains("import SpecRest.Cert"),
      s"expected `import SpecRest.Cert` in module:\n$rendered"
    )
    assert(
      rendered.contains("theorem cert_invariant_0_countNonNegative"),
      s"expected named cert theorem:\n$rendered"
    )
    assert(
      rendered.contains("cert_decide"),
      s"expected `cert_decide` tactic:\n$rendered"
    )
    assert(
      rendered.contains("= some true"),
      s"expected EvalIR-computed `some true`:\n$rendered"
    )

  test("unsatisfied placeholder state yields cert claiming `some false`"):
    // Invariant `count > 5`. Default placeholder state has count = 0.
    // The honest cert states `eval ... = some false`, not `some true`.
    val invariant = InvariantDecl(
      name = Some("strictlyPositive"),
      expr = Expr.BinaryOp(
        BinOp.Gt,
        Expr.Identifier("count"),
        Expr.IntLit(5)
      )
    )
    val ir = ServiceIR(
      name = "Demo",
      state = Some(
        stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
      ),
      invariants = List(invariant)
    )

    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule
    assertEquals(bundle.summary.totalChecks, 1)
    assertEquals(bundle.summary.certifiedChecks, 1)
    assert(
      rendered.contains("= some false"),
      s"emitter must record actual EvalIR result, not blindly assert true:\n$rendered"
    )

  test("operation requires clauses get their own per-clause certs"):
    val op = OperationDecl(
      name = "Decrement",
      requires = List(
        Expr.BinaryOp(BinOp.Gt, Expr.Identifier("count"), Expr.IntLit(0))
      )
    )
    val ir = ServiceIR(
      name = "SafeCounter",
      state = Some(
        stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
      ),
      operations = List(op)
    )

    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule
    assertEquals(bundle.summary.totalChecks, 1)
    assert(
      rendered.contains("cert_op_0_Decrement_requires_0"),
      s"expected per-requires cert theorem:\n$rendered"
    )
    assert(
      rendered.contains("evalRequiresAll"),
      s"expected `evalRequiresAll` shape for op-requires cert:\n$rendered"
    )

  test("out-of-subset invariant emits a `sorry` stub with TODO marker"):
    val invariant = InvariantDecl(
      name = Some("hasField"),
      expr = Expr.FieldAccess(Expr.Identifier("user"), "id")
    )
    val ir = ServiceIR(
      name = "Demo",
      invariants = List(invariant)
    )

    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule

    assertEquals(bundle.summary.totalChecks, 1)
    assertEquals(bundle.summary.certifiedChecks, 0)
    assertEquals(bundle.summary.stubbedChecks, 1)

    assert(
      !rendered.split("\n").exists(line => line.matches(".*:= (by )?sorry.*")),
      s"emitted bundle must contain no `sorry` proof bodies (sorry-free closure):\n$rendered"
    )
    assert(
      rendered.contains(":= trivial"),
      s"out-of-subset stub must close via `trivial`:\n$rendered"
    )
    assert(
      rendered.contains("TODO[M_L.4]"),
      s"expected TODO marker:\n$rendered"
    )
    assert(
      bundle.theorems.forall(t => !t.contains("cert_decide")),
      s"out-of-subset stub must NOT call `cert_decide`:\n${bundle.theorems.mkString("\n---\n")}"
    )

  test("anonymous invariant gets a synthetic name"):
    val invariant = InvariantDecl(
      name = None,
      expr = Expr.BoolLit(true)
    )
    val ir = ServiceIR(
      name = "Anon",
      invariants = List(invariant)
    )

    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule
    assert(
      rendered.contains("cert_invariant_0_anon_0"),
      s"expected synthetic name `anon_0`:\n$rendered"
    )

  test("rendered lakefile points at the project's proofs/lean workspace"):
    val ir = ServiceIR(
      name = "Trivial",
      invariants = List(
        InvariantDecl(name = Some("alwaysTrue"), expr = Expr.BoolLit(true))
      )
    )
    val bundle   = Emit.emit(ir, "/abs/path/to/proofs/lean")
    val lakefile = bundle.renderLakefile
    assert(lakefile.contains("name = \"trivial-cert\""), s"kebab module name:\n$lakefile")
    assert(lakefile.contains("[[require]]"), s"path require clause:\n$lakefile")
    assert(
      lakefile.contains("path = \"/abs/path/to/proofs/lean\""),
      s"absolute path to proofs/lean:\n$lakefile"
    )
    assert(lakefile.contains("[[lean_lib]]"), s"lean_lib block:\n$lakefile")

  test("VerifiedSubset.classify accepts the §6.1 minimum"):
    val verifiedSamples = List(
      Expr.BoolLit(true),
      Expr.IntLit(42),
      Expr.Identifier("x"),
      Expr.UnaryOp(UnOp.Not, Expr.BoolLit(false)),
      Expr.UnaryOp(UnOp.Negate, Expr.IntLit(3)),
      Expr.BinaryOp(BinOp.And, Expr.BoolLit(true), Expr.BoolLit(true)),
      Expr.BinaryOp(BinOp.Or, Expr.BoolLit(true), Expr.BoolLit(false)),
      Expr.BinaryOp(BinOp.Implies, Expr.BoolLit(true), Expr.BoolLit(true)),
      Expr.BinaryOp(BinOp.Eq, Expr.IntLit(1), Expr.IntLit(1)),
      Expr.BinaryOp(BinOp.Lt, Expr.IntLit(1), Expr.IntLit(2)),
      Expr.BinaryOp(
        BinOp.In,
        Expr.Identifier("u"),
        Expr.Identifier("active")
      ),
      Expr.BinaryOp(
        BinOp.NotIn,
        Expr.Identifier("u"),
        Expr.Identifier("active")
      ),
      Expr.Let("x", Expr.IntLit(1), Expr.Identifier("x")),
      Expr.EnumAccess(Expr.Identifier("Color"), "Red"),
      Expr.Quantifier(
        QuantKind.All,
        List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
        Expr.BoolLit(true)
      ),
      Expr.BinaryOp(BinOp.Add, Expr.IntLit(1), Expr.IntLit(2)),
      Expr.BinaryOp(BinOp.Sub, Expr.IntLit(3), Expr.IntLit(1)),
      Expr.BinaryOp(BinOp.Mul, Expr.IntLit(2), Expr.IntLit(2)),
      Expr.BinaryOp(BinOp.Div, Expr.IntLit(4), Expr.IntLit(2)),
      Expr.Prime(Expr.Identifier("count")),
      Expr.Pre(Expr.Identifier("count")),
      Expr.UnaryOp(UnOp.Cardinality, Expr.Identifier("rel")),
      Expr.Index(Expr.Identifier("users"), Expr.Identifier("uid")),
      Expr.FieldAccess(Expr.Identifier("currentUser"), "email"),
      // M_L.4.k: nested FieldAccess shapes — Index-result, chained, quantifier-bound.
      Expr.FieldAccess(
        Expr.Index(Expr.Identifier("users"), Expr.Identifier("uid")),
        "email"
      ),
      Expr.FieldAccess(
        Expr.FieldAccess(Expr.Identifier("currentUser"), "profile"),
        "email"
      ),
      Expr.BinaryOp(BinOp.Subset, Expr.Identifier("active"), Expr.Identifier("members")),
      Expr.Quantifier(
        QuantKind.Some,
        List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
        Expr.BoolLit(true)
      ),
      Expr.Quantifier(
        QuantKind.No,
        List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
        Expr.BoolLit(false)
      ),
      Expr.Quantifier(
        QuantKind.Exists,
        List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
        Expr.BoolLit(true)
      )
    )
    verifiedSamples.foreach: sample =>
      assert(
        VerifiedSubset.isInSubset(sample),
        s"expected $sample to be classified `InSubset`"
      )

  test("VerifiedSubset.classify rejects out-of-subset cases with a reason"):
    val rejected = List(
      Expr.UnaryOp(UnOp.Power, Expr.Identifier("x")) -> "UnaryOp.Power",
      Expr.BinaryOp(BinOp.Subset, Expr.IntLit(0), Expr.Identifier("b"))
        -> "BinaryOp(Subset): both operands must be state-relation identifiers",
      Expr.Index(Expr.IntLit(0), Expr.IntLit(1))
        -> "Index: only state-relation identifier base is supported",
      // Shape constraints — classifier rejects what renderExpr can't render:
      Expr.BinaryOp(BinOp.In, Expr.Identifier("u"), Expr.BoolLit(true))
        -> "BinaryOp(In): rhs must be a state-relation identifier",
      Expr.BinaryOp(BinOp.NotIn, Expr.Identifier("u"), Expr.BoolLit(true))
        -> "BinaryOp(NotIn): rhs must be a state-relation identifier",
      Expr.Quantifier(
        QuantKind.All,
        List(
          QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In),
          QuantifierBinding("d", Expr.Identifier("Color"), BindingKind.In)
        ),
        Expr.BoolLit(true)
      ) -> "Quantifier(All): only single-binding over an enum or relation identifier is supported",
      Expr.Quantifier(
        QuantKind.Some,
        List(
          QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In),
          QuantifierBinding("d", Expr.Identifier("Color"), BindingKind.In)
        ),
        Expr.BoolLit(true)
      ) -> "Quantifier(Some): only single-binding over an enum or relation identifier is supported",
      Expr.Quantifier(
        QuantKind.All,
        List(QuantifierBinding("c", Expr.BoolLit(true), BindingKind.In)),
        Expr.BoolLit(true)
      ) -> "Quantifier(All): only single-binding over an enum or relation identifier is supported",
      Expr.EnumAccess(Expr.BoolLit(true), "Red")
        -> "EnumAccess: only `EnumName.member` (Identifier base) is supported"
    )
    rejected.foreach:
      case (sample, expectedFragment) =>
        VerifiedSubset.classify(sample) match
          case VerifiedSubset.SubsetStatus.OutOfSubset(reason) =>
            assert(
              reason.contains(expectedFragment),
              s"expected reason for $sample to mention `$expectedFragment`, got `$reason`"
            )
          case VerifiedSubset.SubsetStatus.InSubset =>
            fail(s"expected $sample to be OutOfSubset, but was InSubset")

  test("renderExpr emits valid Lean for `forallEnum` and `enumAccess`"):
    // For shapes EvalIR can compute on a stateless demo schema, the cert
    // emitter should produce well-formed Lean — no `/- ... -/` placeholders.
    // BinaryOp(In) requires state-relation seeding (deferred to richer
    // demo-state synthesis); the classifier-rejects-bad-shapes test elsewhere
    // covers gating, and the renderer's `.member` arm is exercised
    // structurally via the rendering of in-subset bodies elsewhere.
    val forallInv = InvariantDecl(
      name = Some("colorReflexive"),
      expr = Expr.Quantifier(
        QuantKind.All,
        List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
        Expr.BinaryOp(BinOp.Eq, Expr.Identifier("c"), Expr.Identifier("c"))
      )
    )
    val enumAccessInv = InvariantDecl(
      name = Some("redIsRed"),
      expr = Expr.BinaryOp(
        BinOp.Eq,
        Expr.EnumAccess(Expr.Identifier("Color"), "Red"),
        Expr.EnumAccess(Expr.Identifier("Color"), "Red")
      )
    )
    val ir = ServiceIR(
      name = "ShapeProbe",
      enums = List(EnumDecl("Color", List("Red", "Green"))),
      invariants = List(forallInv, enumAccessInv)
    )
    val rendered = Emit.emit(ir, proofsPath).renderModule
    assert(
      rendered.contains(".forallEnum") && rendered.contains("\"Color\""),
      s"forallEnum rendering missing:\n$rendered"
    )
    assert(
      rendered.contains(".enumAccess") && rendered.contains("\"Red\""),
      s"enumAccess rendering missing:\n$rendered"
    )
    assert(
      !rendered.contains("/- Quantifier(All):") && !rendered.contains("UNRENDERABLE"),
      s"renderer must never produce bare-comment or UNRENDERABLE placeholders for in-subset shapes:\n$rendered"
    )

  test("renderExpr emits `.forallRel` when binding ranges over a state-relation identifier"):
    // `forall u in users, P` where `users` is NOT an enum should render as `.forallRel`,
    // not `.forallEnum`. Disambiguation key: `Emit.scala` checks `ir.enums`.
    val forallRelInv = InvariantDecl(
      name = Some("usersInhabited"),
      expr = Expr.Quantifier(
        QuantKind.All,
        List(QuantifierBinding("u", Expr.Identifier("users"), BindingKind.In)),
        Expr.BoolLit(true)
      )
    )
    val ir = ServiceIR(
      name = "RelProbe",
      enums = Nil,
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(
              name = "users",
              typeExpr = TypeExpr.SetType(TypeExpr.NamedType("Int"))
            )
          )
        )
      ),
      invariants = List(forallRelInv)
    )
    val rendered = Emit.emit(ir, proofsPath).renderModule
    assert(
      rendered.contains(".forallRel") && rendered.contains("\"users\""),
      s"forallRel rendering missing:\n$rendered"
    )
    assert(
      !rendered.contains(".forallEnum") && !rendered.contains("UNRENDERABLE"),
      s"forallRel arm must not also emit forallEnum nor UNRENDERABLE:\n$rendered"
    )
    // Lean-side `evalForallRel` looks up `st.relationDomain rel`. If the rendered
    // state literal omits the relation entry, lookupRel returns none and the cert
    // theorem's `cert_decide` fails on `none = some true`. The rendered state must
    // mirror EvalIR.State.demo, which populates non-scalar state-fields with
    // empty domains (M_L.4.f). Cubic flagged this as P1 on the original M_L.4.f PR.
    assert(
      rendered.contains("(\"users\", [])"),
      s"renderStateLit must emit relation entries with their (possibly empty) domains:\n$rendered"
    )

  test("renderExpr emits `.forallRel + .member` composition for Subset over relations"):
    // M_L.4.i: BinaryOp(Subset, r1, r2) over two state-relation identifiers desugars
    // at emit time to `forallRel _subset_x r1 (member (.ident _subset_x) r2)`.
    // No new Lean constructor; pure composition.
    val subsetInv = InvariantDecl(
      name = Some("activeSubset"),
      expr = Expr.BinaryOp(
        BinOp.Subset,
        Expr.Identifier("active"),
        Expr.Identifier("members")
      )
    )
    val ir = ServiceIR(
      name = "SubsetProbe",
      enums = Nil,
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(
              name = "active",
              typeExpr = TypeExpr.SetType(TypeExpr.NamedType("Int"))
            ),
            StateFieldDecl(
              name = "members",
              typeExpr = TypeExpr.SetType(TypeExpr.NamedType("Int"))
            )
          )
        )
      ),
      invariants = List(subsetInv)
    )
    val rendered = Emit.emit(ir, proofsPath).renderModule
    assert(
      rendered.contains(".forallRel") &&
        rendered.contains("\"active\"") &&
        rendered.contains(".member") &&
        rendered.contains("\"_subset_x\"") &&
        rendered.contains("\"members\""),
      s"Subset composition must render forallRel+member with both relation names:\n$rendered"
    )
    assert(
      !rendered.contains("UNRENDERABLE"),
      s"Subset arm must not produce UNRENDERABLE:\n$rendered"
    )

  test("EvalIR demo state synthesizes vEntity for entity-typed scalars"):
    val ir = ServiceIR(
      name = "EntityDemo",
      entities = List(EntityDecl(name = "User")),
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "owner", typeExpr = TypeExpr.NamedType("User")))
        )
      )
    )
    val st       = EvalIR.State.demo(ir)
    val ownerVal = st.scalars.collectFirst { case ("owner", v) => v }
    // M_L.4.k: scalar's vEntity carries a freshly-minted id; entityFields is keyed
    // by that id so FieldAccess(Identifier("owner"), field) reaches the field table
    // via base eval → vEntity → id.
    assert(
      ownerVal.exists {
        case EvalIR.Value.VEntity("User", id) => id.nonEmpty
        case _                                => false
      },
      s"entity-typed scalar must default to VEntity with a fresh id, got: $ownerVal"
    )
    val mintedId     = ownerVal.collect { case EvalIR.Value.VEntity(_, id) => id }.get
    val mappedFields = st.entityFields.collectFirst { case (k, fs) if k == mintedId => fs }
    assert(
      mappedFields.isDefined,
      s"entityFields must be keyed by the minted id ($mintedId), got: ${st.entityFields}"
    )

  test("EvalIR demo state synthesizes vEnum (first member) for enum-typed scalars"):
    val ir = ServiceIR(
      name = "EnumDemo",
      enums = List(EnumDecl("Color", List("Red", "Green", "Blue"))),
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "favorite", typeExpr = TypeExpr.NamedType("Color")))
        )
      )
    )
    val st     = EvalIR.State.demo(ir)
    val favVal = st.scalars.collectFirst { case ("favorite", v) => v }
    assert(
      favVal.contains(EvalIR.Value.VEnum("Color", "Red")),
      s"enum-typed scalar must default to VEnum with first member, got: $favVal"
    )

  test("EvalIR mirrors safe_counter eval on placeholder state"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "SafeCounter",
      state = Some(
        stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
      )
    )
    val st = State.demo(ir)
    val invariantBody =
      Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
    assertEquals(
      EvalIR.evalInvariantBody(ir, st, Nil, invariantBody),
      Some(true)
    )
    val negatedBody =
      Expr.BinaryOp(BinOp.Lt, Expr.Identifier("count"), Expr.IntLit(0))
    assertEquals(
      EvalIR.evalInvariantBody(ir, st, Nil, negatedBody),
      Some(false)
    )

  test("M_L.4.k: bare FieldAccess on entity-typed scalar still reaches cert_decide"):
    val invariant = InvariantDecl(
      name = Some("emailNonEmpty"),
      expr = Expr.BinaryOp(
        BinOp.Eq,
        Expr.FieldAccess(Expr.Identifier("currentUser"), "email"),
        Expr.FieldAccess(Expr.Identifier("currentUser"), "email")
      )
    )
    val ir = ServiceIR(
      name = "AuthDemo",
      entities = List(
        EntityDecl(
          name = "User",
          fields = List(
            FieldDecl(name = "email", typeExpr = TypeExpr.NamedType("Int"))
          )
        )
      ),
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "currentUser", typeExpr = TypeExpr.NamedType("User")))
        )
      ),
      invariants = List(invariant)
    )
    val bundle = Emit.emit(ir, proofsPath)
    assertEquals(bundle.summary.certifiedChecks, 1)
    assertEquals(bundle.summary.stubbedChecks, 0)

  test("M_L.4.k: FieldAccess on Index is in subset and never UNRENDERABLE"):
    val nested = Expr.FieldAccess(
      Expr.Index(Expr.Identifier("users"), Expr.Identifier("uid")),
      "email"
    )
    assert(
      VerifiedSubset.isInSubset(nested),
      "FieldAccess(Index, _) must classify as InSubset"
    )
    assert(
      VerifiedSubset.isInSubset(
        Expr.FieldAccess(Expr.FieldAccess(Expr.Identifier("u"), "profile"), "email")
      ),
      "chained FieldAccess must classify as InSubset"
    )

  test("M_L.4.k: forall t in tasks, t.field passes classifier and flips cert_decide on empty rel"):
    val body = Expr.BinaryOp(
      BinOp.Eq,
      Expr.FieldAccess(Expr.Identifier("t"), "priority"),
      Expr.FieldAccess(Expr.Identifier("t"), "priority")
    )
    val q = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("t", Expr.Identifier("tasks"), BindingKind.In)),
      body
    )
    assert(
      VerifiedSubset.isInSubset(q),
      "quantifier-bound FieldAccess must be in subset post-M_L.4.k"
    )
    val ir = ServiceIR(
      name = "QuantTask",
      entities = List(
        EntityDecl(
          name = "Task",
          fields = List(FieldDecl(name = "priority", typeExpr = TypeExpr.NamedType("Int")))
        )
      ),
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(
              name = "tasks",
              typeExpr = TypeExpr.MapType(TypeExpr.NamedType("Int"), TypeExpr.NamedType("Task"))
            )
          )
        )
      ),
      invariants = List(InvariantDecl(name = Some("allPriEq"), expr = q))
    )
    // Demo state seeds `tasks` with empty domain. forallRel over empty short-circuits
    // to `Some(VBool true)` without evaluating the body, so the cert flips to
    // cert_decide even though FieldAccess on `t` would otherwise stub. This is
    // the empty-domain win path documented in the M_L.4.k closure record.
    val bundle = Emit.emit(ir, proofsPath)
    assertEquals(bundle.summary.certifiedChecks, 1)
    assertEquals(bundle.summary.stubbedChecks, 0)
    val rendered = bundle.renderModule
    assert(
      rendered.contains(".fieldAccess (.ident \"t\")"),
      s"quantifier-bound FieldAccess must render with .ident base:\n$rendered"
    )
