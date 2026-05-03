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
      Expr.SetLiteral(List(Expr.IntLit(1), Expr.IntLit(2))),
      Expr.BinaryOp(
        BinOp.In,
        Expr.IntLit(1),
        Expr.SetLiteral(List(Expr.IntLit(1), Expr.IntLit(2)))
      ),
      Expr.BinaryOp(
        BinOp.NotIn,
        Expr.IntLit(3),
        Expr.SetLiteral(List(Expr.IntLit(1), Expr.IntLit(2)))
      ),
      Expr.BinaryOp(
        BinOp.In,
        Expr.IntLit(1),
        Expr.SetLiteral(Nil)
      ),
      Expr.BinaryOp(
        BinOp.Union,
        Expr.SetLiteral(List(Expr.IntLit(1))),
        Expr.SetLiteral(List(Expr.IntLit(2)))
      ),
      Expr.BinaryOp(
        BinOp.Intersect,
        Expr.SetLiteral(List(Expr.IntLit(1))),
        Expr.SetLiteral(List(Expr.IntLit(1)))
      ),
      Expr.BinaryOp(
        BinOp.Diff,
        Expr.SetLiteral(List(Expr.IntLit(1))),
        Expr.SetLiteral(List(Expr.IntLit(2)))
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
      Expr.SetLiteral(Nil)
        -> "SetLiteral: empty standalone literal needs type context",
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

  test("EvalIR treats finite set equality and membership extensionally"):
    import EvalIR.*
    val left  = Expr.SetLiteral(List(Expr.IntLit(1), Expr.IntLit(2)))
    val right = Expr.SetLiteral(List(Expr.IntLit(2), Expr.IntLit(1), Expr.IntLit(1)))
    assertEquals(
      EvalIR.eval(Schema.empty, State.empty, Nil, Expr.BinaryOp(BinOp.Eq, left, right)),
      Some(Value.VBool(true))
    )
    assertEquals(
      EvalIR.eval(Schema.empty, State.empty, Nil, Expr.BinaryOp(BinOp.In, Expr.IntLit(1), right)),
      Some(Value.VBool(true))
    )

  test("EvalIR prioritizes set-valued identifiers over relation carriers for membership"):
    import EvalIR.*
    val st = State(
      scalars = List("items" -> Value.VSet(List(Value.VInt(BigInt(1))))),
      relations = List("items" -> Nil),
      lookups = Nil,
      entityFields = Nil
    )
    assertEquals(
      EvalIR.eval(
        Schema.empty,
        st,
        Nil,
        Expr.BinaryOp(BinOp.In, Expr.IntLit(1), Expr.Identifier("items"))
      ),
      Some(Value.VBool(true))
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

  test("M_L.4.k: FieldAccess on Index is in subset and renderer emits nested term"):
    // Exercise the renderer end-to-end. Wrap the nested FieldAccess in
    // `forall u in users, … = …` so the empty-domain forallRel short-circuits
    // to `Some(VBool true)` and the cert_decide path runs renderExpr on the
    // body (cubic-style "test what the test name claims" check).
    val nested = Expr.FieldAccess(
      Expr.Index(Expr.Identifier("users"), Expr.Identifier("u")),
      "email"
    )
    val q = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("u", Expr.Identifier("users"), BindingKind.In)),
      Expr.BinaryOp(BinOp.Eq, nested, nested)
    )
    assert(VerifiedSubset.isInSubset(q), "FieldAccess(Index, _) must classify as InSubset")
    val ir = ServiceIR(
      name = "IndexedField",
      entities = List(
        EntityDecl(
          name = "User",
          fields = List(FieldDecl(name = "email", typeExpr = TypeExpr.NamedType("Int")))
        )
      ),
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(
              name = "users",
              typeExpr = TypeExpr.MapType(TypeExpr.NamedType("Int"), TypeExpr.NamedType("User"))
            )
          )
        )
      ),
      invariants = List(InvariantDecl(name = Some("indexedField"), expr = q))
    )
    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule
    assertEquals(bundle.summary.certifiedChecks, 1)
    assert(
      !rendered.contains("UNRENDERABLE"),
      s"renderer must not produce UNRENDERABLE on nested FieldAccess(Index, _):\n$rendered"
    )
    assert(
      rendered.contains(".fieldAccess (.indexRel \"users\" "),
      s"renderer must emit nested .fieldAccess (.indexRel …) form:\n$rendered"
    )

  test("M_L.4.k: chained FieldAccess flips cert_decide via recursive entity seeding"):
    // currentUser.profile.email — the renderer emits chained .fieldAccess and
    // recursive demo-state seeding allocates a child id for `profile` so the
    // chain closes. Without recursive seeding, eval would bottom out at
    // `vEntity Profile ""` with no entityFields entry → None → stub.
    val chained = Expr.FieldAccess(
      Expr.FieldAccess(Expr.Identifier("currentUser"), "profile"),
      "email"
    )
    val invariant = InvariantDecl(
      name = Some("chainClosed"),
      expr = Expr.BinaryOp(BinOp.Eq, chained, chained)
    )
    val ir = ServiceIR(
      name = "ChainedField",
      entities = List(
        EntityDecl(
          name = "User",
          fields = List(FieldDecl(name = "profile", typeExpr = TypeExpr.NamedType("Profile")))
        ),
        EntityDecl(
          name = "Profile",
          fields = List(FieldDecl(name = "email", typeExpr = TypeExpr.NamedType("Int")))
        )
      ),
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "currentUser", typeExpr = TypeExpr.NamedType("User")))
        )
      ),
      invariants = List(invariant)
    )
    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule
    assertEquals(bundle.summary.certifiedChecks, 1)
    assertEquals(bundle.summary.stubbedChecks, 0)
    assert(
      rendered.contains(".fieldAccess (.fieldAccess (.ident \"currentUser\")"),
      s"chained FieldAccess must render as nested .fieldAccess:\n$rendered"
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

  // ---------------------------------------------------------------------------
  // M_L.4.b-ext Phase 5.a — `Expr.With` joins the cert-emission subset.
  // The Lean side ships full Skolem semantics for `withRec` (Phase 4b); the
  // Scala-side now mirrors via `Value.VEntityWith`, `fieldLookup` walks the
  // chain, `VerifiedSubset.classify` accepts With, and Emit renders multi-field
  // updates as a left-fold of `.withRec` ctors.
  // ---------------------------------------------------------------------------

  test("Phase 5.a: VerifiedSubset accepts With over an in-subset base + values"):
    val expr = Expr.With(
      Expr.Identifier("u"),
      List(FieldAssign("name", Expr.IntLit(0)))
    )
    assert(
      VerifiedSubset.isInSubset(expr),
      s"Expr.With over in-subset base + values must classify as InSubset, got ${VerifiedSubset.classify(expr)}"
    )

  test("Phase 5.a: VerifiedSubset rejects With with an out-of-subset update value"):
    val expr = Expr.With(
      Expr.Identifier("u"),
      List(FieldAssign("name", Expr.FloatLit(1.0)))
    )
    VerifiedSubset.classify(expr) match
      case VerifiedSubset.SubsetStatus.OutOfSubset(reason) =>
        assert(
          reason.contains("FloatLit"),
          s"With with FloatLit update must surface the inner reason, got `$reason`"
        )
      case VerifiedSubset.SubsetStatus.InSubset =>
        fail("With over FloatLit update must be OutOfSubset")

  test("Phase 5.a: EvalIR builds a VEntityWith chain and fieldLookup walks it"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "WithDemo",
      entities = List(
        EntityDecl(
          name = "User",
          fields = List(
            FieldDecl(name = "name", typeExpr = TypeExpr.NamedType("Int")),
            FieldDecl(name = "age", typeExpr = TypeExpr.NamedType("Int"))
          )
        )
      ),
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "u", typeExpr = TypeExpr.NamedType("User")))
        )
      )
    )
    val st = State.demo(ir)
    // `u with { name := 7 }` extends the seeded vEntity with a name override.
    val withExpr = Expr.With(
      Expr.Identifier("u"),
      List(FieldAssign("name", Expr.IntLit(7)))
    )
    val v = EvalIR.eval(Schema.of(ir), st, Nil, withExpr)
    assert(
      v.exists {
        case Value.VEntityWith(Value.VEntity("User", _), "name", Value.VInt(n)) =>
          n == BigInt(7)
        case _ => false
      },
      s"With must build a VEntityWith chain over the seeded vEntity, got: $v"
    )
    // FieldAccess(With(u, [name := 7]), "name") → 7 (override path).
    val accessOverride = Expr.FieldAccess(withExpr, "name")
    assertEquals(
      EvalIR.eval(Schema.of(ir), st, Nil, accessOverride),
      Some(Value.VInt(BigInt(7)))
    )
    // FieldAccess(With(u, [name := 7]), "age") → seeded default (fallback to base).
    val accessFallback = Expr.FieldAccess(withExpr, "age")
    assertEquals(
      EvalIR.eval(Schema.of(ir), st, Nil, accessFallback),
      Some(Value.VInt(BigInt(0)))
    )

  test("Phase 5.a: multi-field With folds left into chained .withRec Lean output"):
    // With(u, [a := 1, b := 2]) lowers to .withRec (.withRec (.ident "u") "a" 1) "b" 2
    val withExpr = Expr.With(
      Expr.Identifier("u"),
      List(
        FieldAssign("a", Expr.IntLit(1)),
        FieldAssign("b", Expr.IntLit(2))
      )
    )
    val invariant = InvariantDecl(
      name = Some("withChained"),
      expr = Expr.BinaryOp(BinOp.Eq, withExpr, withExpr)
    )
    val ir = ServiceIR(
      name = "WithChained",
      entities = List(
        EntityDecl(
          name = "User",
          fields = List(
            FieldDecl(name = "a", typeExpr = TypeExpr.NamedType("Int")),
            FieldDecl(name = "b", typeExpr = TypeExpr.NamedType("Int"))
          )
        )
      ),
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "u", typeExpr = TypeExpr.NamedType("User")))
        )
      ),
      invariants = List(invariant)
    )
    val rendered = Emit.emit(ir, proofsPath).renderModule
    assert(
      rendered.contains(
        "(.withRec (.withRec (.ident \"u\") \"a\" (.intLit (1 : Int))) \"b\" (.intLit (2 : Int)))"
      ),
      s"multi-field With must render as left-folded .withRec chain:\n$rendered"
    )

  test("Phase 5.a: With on entity-typed scalar flips cert_decide via demo seeding"):
    // Equality on `u with { name := 0 }` against itself must reduce to `some true`
    // because both sides build the same VEntityWith chain over the same seeded
    // vEntity. End-to-end check: classifier accepts, evaluator produces the
    // chain, renderer emits `.withRec`, and `cert_decide` discharges.
    val withExpr = Expr.With(
      Expr.Identifier("u"),
      List(FieldAssign("name", Expr.IntLit(0)))
    )
    val invariant = InvariantDecl(
      name = Some("withEqRefl"),
      expr = Expr.BinaryOp(BinOp.Eq, withExpr, withExpr)
    )
    val ir = ServiceIR(
      name = "WithRefl",
      entities = List(
        EntityDecl(
          name = "User",
          fields = List(FieldDecl(name = "name", typeExpr = TypeExpr.NamedType("Int")))
        )
      ),
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "u", typeExpr = TypeExpr.NamedType("User")))
        )
      ),
      invariants = List(invariant)
    )
    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule
    assertEquals(bundle.summary.certifiedChecks, 1)
    assertEquals(bundle.summary.stubbedChecks, 0)
    assert(
      rendered.contains("= some true"),
      s"With(u, [name := 0]) ≡ With(u, [name := 0]) must close as some true:\n$rendered"
    )
    assert(
      !rendered.contains("UNRENDERABLE"),
      s"With must render cleanly without UNRENDERABLE markers:\n$rendered"
    )

  // ---------------------------------------------------------------------------
  // M_L.4.b-ext Phase 5.b — `StatePair` + `StateMode` + mode-aware `evalAt`.
  // Single-state `eval` is now a thin wrapper over `evalAt(Pre, diag(st), …)`.
  // The diagonal-collapse property (Lean: `evalAt_diagonal_eq_eval`) holds by
  // definition. New tests pin the two-state behavior and Prime/Pre mode flips.
  // ---------------------------------------------------------------------------

  test("Phase 5.b: StatePair.diag(st) returns the same state in both modes"):
    import EvalIR.*
    val st = State(
      scalars = List("count" -> Value.VInt(BigInt(7))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val sp = StatePair.diag(st)
    assertEquals(sp.pre, st)
    assertEquals(sp.post, st)
    assertEquals(sp.at(StateMode.Pre), st)
    assertEquals(sp.at(StateMode.Post), st)

  test("Phase 5.b: evalAt on diagonal StatePair agrees with single-state eval"):
    // Spot-check a representative cross-section of expression shapes; the
    // diagonal-collapse property should hold for every closed evaluation.
    import EvalIR.*
    val st = State(
      scalars = List("count" -> Value.VInt(BigInt(3))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val sp     = StatePair.diag(st)
    val schema = Schema.empty
    val probes: List[Expr] = List(
      Expr.IntLit(1),
      Expr.BoolLit(true),
      Expr.Identifier("count"),
      Expr.UnaryOp(UnOp.Negate, Expr.Identifier("count")),
      Expr.BinaryOp(BinOp.Add, Expr.Identifier("count"), Expr.IntLit(1)),
      Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0)),
      Expr.Prime(Expr.Identifier("count")),
      Expr.Pre(Expr.Identifier("count")),
      Expr.Let("x", Expr.IntLit(5), Expr.BinaryOp(BinOp.Eq, Expr.Identifier("x"), Expr.IntLit(5)))
    )
    probes.foreach: probe =>
      assertEquals(
        EvalIR.evalAt(StateMode.Pre, schema, sp, Nil, probe),
        EvalIR.eval(schema, st, Nil, probe),
        s"evalAt(Pre, diag(st)) must agree with eval for probe: $probe"
      )
      assertEquals(
        EvalIR.evalAt(StateMode.Post, schema, sp, Nil, probe),
        EvalIR.eval(schema, st, Nil, probe),
        s"evalAt(Post, diag(st)) must agree with eval for probe: $probe"
      )

  test("Phase 5.b: evalAt with non-diagonal pair routes Identifier to active mode"):
    import EvalIR.*
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(0))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val post = State(
      scalars = List("count" -> Value.VInt(BigInt(5))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val sp = StatePair(pre, post)
    assertEquals(
      EvalIR.evalAt(StateMode.Pre, Schema.empty, sp, Nil, Expr.Identifier("count")),
      Some(Value.VInt(BigInt(0)))
    )
    assertEquals(
      EvalIR.evalAt(StateMode.Post, Schema.empty, sp, Nil, Expr.Identifier("count")),
      Some(Value.VInt(BigInt(5)))
    )

  test("Phase 5.b: Expr.Prime flips mode to Post; Expr.Pre flips mode to Pre"):
    import EvalIR.*
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(0))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val post = State(
      scalars = List("count" -> Value.VInt(BigInt(5))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val sp = StatePair(pre, post)
    // Starting in Pre, `count'` reaches the post-state.
    assertEquals(
      EvalIR.evalAt(StateMode.Pre, Schema.empty, sp, Nil, Expr.Prime(Expr.Identifier("count"))),
      Some(Value.VInt(BigInt(5)))
    )
    // Starting in Post, `pre(count)` reaches the pre-state.
    assertEquals(
      EvalIR.evalAt(StateMode.Post, Schema.empty, sp, Nil, Expr.Pre(Expr.Identifier("count"))),
      Some(Value.VInt(BigInt(0)))
    )

  test("Phase 5.b: nested temporal operators apply innermost-wins"):
    // Prime(Pre(x)) reads x from Pre; Pre(Prime(x)) reads x from Post.
    import EvalIR.*
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(0))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val post = State(
      scalars = List("count" -> Value.VInt(BigInt(5))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val sp = StatePair(pre, post)
    val primeOfPre =
      Expr.Prime(Expr.Pre(Expr.Identifier("count")))
    val preOfPrime =
      Expr.Pre(Expr.Prime(Expr.Identifier("count")))
    assertEquals(
      EvalIR.evalAt(StateMode.Pre, Schema.empty, sp, Nil, primeOfPre),
      Some(Value.VInt(BigInt(0)))
    )
    assertEquals(
      EvalIR.evalAt(StateMode.Pre, Schema.empty, sp, Nil, preOfPrime),
      Some(Value.VInt(BigInt(5)))
    )

  test("Phase 5.b: mode propagates through sub-expressions until the next Prime/Pre"):
    // `count' = count + 1` mixed temporal eval: LHS Prime reads post, RHS reads pre.
    import EvalIR.*
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(3))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val post = State(
      scalars = List("count" -> Value.VInt(BigInt(4))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val sp = StatePair(pre, post)
    val ensures = Expr.BinaryOp(
      BinOp.Eq,
      Expr.Prime(Expr.Identifier("count")),
      Expr.BinaryOp(BinOp.Add, Expr.Identifier("count"), Expr.IntLit(1))
    )
    assertEquals(
      EvalIR.evalAt(StateMode.Pre, Schema.empty, sp, Nil, ensures),
      Some(Value.VBool(true))
    )

  test("Phase 5.b: evalInvariantBodyAt threads mode through invariant-style evaluation"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "Counter",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(-1))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val post = State(
      scalars = List("count" -> Value.VInt(BigInt(0))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val sp         = StatePair(pre, post)
    val nonNegBody = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
    // Pre-state violates `count >= 0`; post-state satisfies it.
    assertEquals(
      EvalIR.evalInvariantBodyAt(StateMode.Pre, ir, sp, Nil, nonNegBody),
      Some(false)
    )
    assertEquals(
      EvalIR.evalInvariantBodyAt(StateMode.Post, ir, sp, Nil, nonNegBody),
      Some(true)
    )

  test("Phase 5.b: evalRequiresAt short-circuits on first false in active mode"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "Counter",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val sp = StatePair(
      pre = State(
        scalars = List("count" -> Value.VInt(BigInt(0))),
        relations = Nil,
        lookups = Nil,
        entityFields = Nil
      ),
      post = State(
        scalars = List("count" -> Value.VInt(BigInt(5))),
        relations = Nil,
        lookups = Nil,
        entityFields = Nil
      )
    )
    val requires = List(
      Expr.BinaryOp(BinOp.Gt, Expr.Identifier("count"), Expr.IntLit(0))
    )
    // `count > 0` is false in pre-state (count=0) and true in post-state (count=5).
    assertEquals(
      EvalIR.evalRequiresAt(StateMode.Pre, ir, sp, Nil, requires),
      Some(false)
    )
    assertEquals(
      EvalIR.evalRequiresAt(StateMode.Post, ir, sp, Nil, requires),
      Some(true)
    )

  // ---------------------------------------------------------------------------
  // M_L.4.b-ext Phase 5.c — Operation invariant-preservation certs.
  // `synthesizePostState` extracts primed-equality assignments from `ensures`
  // and applies them to the pre-state. `Emit` produces one preservation
  // theorem per (operation × invariant) pair, claiming
  // `evalPreservation = some <bool>` against the synthesised StatePair.
  // ---------------------------------------------------------------------------

  test("Phase 5.c: synthesizePostState applies `count' = count + 1` to pre"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "Counter",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val op = OperationDecl(
      name = "Increment",
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("count")),
          Expr.BinaryOp(BinOp.Add, Expr.Identifier("count"), Expr.IntLit(1))
        )
      )
    )
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(7))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val (post, unknown) = EvalIR.synthesizePostState(ir, op, pre, Nil)
    assertEquals(
      post,
      State(
        scalars = List("count" -> Value.VInt(BigInt(8))),
        relations = Nil,
        lookups = Nil,
        entityFields = Nil
      )
    )
    assertEquals(unknown, Set.empty[String])

  test("Phase 5.c: synthesizePostState handles And-joined multi-field assignments"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "MultiField",
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(name = "a", typeExpr = TypeExpr.NamedType("Int")),
            StateFieldDecl(name = "b", typeExpr = TypeExpr.NamedType("Int"))
          )
        )
      )
    )
    val op = OperationDecl(
      name = "Swap",
      ensures = List(
        Expr.BinaryOp(
          BinOp.And,
          Expr.BinaryOp(BinOp.Eq, Expr.Prime(Expr.Identifier("a")), Expr.Identifier("b")),
          Expr.BinaryOp(BinOp.Eq, Expr.Prime(Expr.Identifier("b")), Expr.Identifier("a"))
        )
      )
    )
    val pre = State(
      scalars = List("a" -> Value.VInt(BigInt(1)), "b" -> Value.VInt(BigInt(2))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val (post, unknown) = EvalIR.synthesizePostState(ir, op, pre, Nil)
    assertEquals(
      post.scalars,
      List("a" -> Value.VInt(BigInt(2)), "b" -> Value.VInt(BigInt(1)))
    )
    assertEquals(unknown, Set.empty[String])

  test("Phase 5.e: synthesizePostState marks unrecognised-clause fields as unknown"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "Constrained",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val op = OperationDecl(
      name = "Mystery",
      ensures = List(
        // `count' >= 0` is a constraint, not an assignment — synthesizer can't
        // resolve a unique post-state, so it declines.
        Expr.BinaryOp(BinOp.Ge, Expr.Prime(Expr.Identifier("count")), Expr.IntLit(0))
      )
    )
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(0))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    // Phase 5.e: per-clause partial recognition. The constraint `count' >= 0`
    // is unrecognised but we capture `count` as a touched-but-unknown field.
    // The cert renderer's gate then refuses to emit a cert when the invariant
    // reads `count`.
    val (post, unknown) = EvalIR.synthesizePostState(ir, op, pre, Nil)
    assertEquals(post, pre)
    assertEquals(unknown, Set("count"))

  test("Phase 5.e: synthesizePostState marks Prime-in-RHS fields as unknown"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "SelfRef",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val op = OperationDecl(
      name = "Recursive",
      ensures = List(
        // `count' = count' - 1` references its own primed value — the synthesizer
        // would silently treat `count'` as `pre.count` due to diagonal collapse,
        // so we reject it explicitly to avoid misleading certs.
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("count")),
          Expr.BinaryOp(BinOp.Sub, Expr.Prime(Expr.Identifier("count")), Expr.IntLit(1))
        )
      )
    )
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(0))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    // Phase 5.e: clause referencing `Prime(Identifier("count"))` on the RHS
    // is unrecognised — the synthesizer can't reach a fixed point. The
    // renderer's gate refuses if invariant reads `count`.
    val (post, unknown) = EvalIR.synthesizePostState(ir, op, pre, Nil)
    assertEquals(post, pre)
    assertEquals(unknown, Set("count"))

  test("Phase 5.c: safe_counter emits 1 invariant + 2 requires + 2 preservation = 5 certs"):
    val invariant = InvariantDecl(
      name = Some("countNonNegative"),
      expr = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
    )
    val incr = OperationDecl(
      name = "Increment",
      requires = List(Expr.BoolLit(true)),
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("count")),
          Expr.BinaryOp(BinOp.Add, Expr.Identifier("count"), Expr.IntLit(1))
        )
      )
    )
    val decr = OperationDecl(
      name = "Decrement",
      requires = List(Expr.BinaryOp(BinOp.Gt, Expr.Identifier("count"), Expr.IntLit(0))),
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("count")),
          Expr.BinaryOp(BinOp.Sub, Expr.Identifier("count"), Expr.IntLit(1))
        )
      )
    )
    val ir = ServiceIR(
      name = "SafeCounter",
      state = Some(
        stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
      ),
      operations = List(incr, decr),
      invariants = List(invariant)
    )
    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule
    assertEquals(bundle.summary.totalChecks, 5)
    assertEquals(bundle.summary.certifiedChecks, 5)
    assertEquals(bundle.summary.stubbedChecks, 0)
    assert(
      rendered.contains("cert_op_0_Increment_preserves_0_countNonNegative"),
      s"Increment preservation theorem missing:\n$rendered"
    )
    assert(
      rendered.contains("cert_op_1_Decrement_preserves_0_countNonNegative"),
      s"Decrement preservation theorem missing:\n$rendered"
    )
    assert(
      rendered.contains("evalPreservation"),
      s"preservation theorems must call `evalPreservation`:\n$rendered"
    )
    // Increment: pre.count=0 (demo satisfies requires=true and inv), post.count=1.
    assert(
      rendered.contains(
        "pre := ({ scalars := [(\"count\", .vInt (0 : Int))]"
      ),
      s"Increment preservation cert must seed pre.count = 0 (demo state):\n$rendered"
    )
    // Decrement: Phase 5.d seeding picks pre.count=1 (smallest candidate that
    // satisfies `count > 0` AND `count >= 0`). Synthesised post.count = 0.
    // Invariant at post = `0 >= 0` = true. Non-vacuous cert.
    assert(
      rendered.contains(
        "pre := ({ scalars := [(\"count\", .vInt (1 : Int))]"
      ),
      s"Decrement preservation cert must seed pre.count = 1:\n$rendered"
    )

  test("Phase 5.c: operation with no ensures emits no preservation theorems"):
    val invariant = InvariantDecl(
      name = Some("trivial"),
      expr = Expr.BoolLit(true)
    )
    val opNoEnsures = OperationDecl(
      name = "Pure",
      requires = List(Expr.BoolLit(true)),
      ensures = Nil
    )
    val ir = ServiceIR(
      name = "PureOp",
      operations = List(opNoEnsures),
      invariants = List(invariant)
    )
    val bundle   = Emit.emit(ir, proofsPath)
    val rendered = bundle.renderModule
    // 1 invariant + 1 requires + 0 preservation = 2 obligations.
    assertEquals(bundle.summary.totalChecks, 2)
    assert(
      !rendered.contains("preserves"),
      s"no preservation theorem expected for ensures-less op:\n$rendered"
    )

  test(
    "Phase 5.e: unrecognised-ensures preservation stubs only when invariant reads the touched field"
  ):
    // Phase 5.e gate: the unrecognised ensures clause `count' >= 0` taints
    // `count`. If the invariant reads `count`, we stub. If the invariant
    // doesn't read it (e.g., `BoolLit(true)`), the cert is honest.
    val constrainedOp = OperationDecl(
      name = "Constrained",
      requires = List(Expr.BoolLit(true)),
      ensures = List(
        Expr.BinaryOp(BinOp.Ge, Expr.Prime(Expr.Identifier("count")), Expr.IntLit(0))
      )
    )
    val state =
      Some(stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int"))))

    // Case A: invariant reads `count` — preservation cert stubs out.
    val readingInv = InvariantDecl(
      name = Some("nonNeg"),
      expr = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
    )
    val readingIR = ServiceIR(
      name = "ReadingInv",
      state = state,
      operations = List(constrainedOp),
      invariants = List(readingInv)
    )
    val readingBundle   = Emit.emit(readingIR, proofsPath)
    val readingRendered = readingBundle.renderModule
    assertEquals(readingBundle.summary.stubbedChecks, 1)
    assert(
      readingRendered.contains("ensures synthesis incomplete and invariant reads unknown field"),
      s"taint gate must fire when invariant reads `count`:\n$readingRendered"
    )

    // Case B: invariant doesn't read `count` — preservation cert is honest.
    val blindInv = InvariantDecl(
      name = Some("trivial"),
      expr = Expr.BoolLit(true)
    )
    val blindIR = ServiceIR(
      name = "BlindInv",
      state = state,
      operations = List(constrainedOp),
      invariants = List(blindInv)
    )
    val blindBundle = Emit.emit(blindIR, proofsPath)
    assertEquals(blindBundle.summary.stubbedChecks, 0)
    assertEquals(blindBundle.summary.certifiedChecks, 3)

  test("Phase 5.c: containsPrime detects Prime in nested arithmetic but ignores Pre"):
    val withPrime = Expr.BinaryOp(
      BinOp.Add,
      Expr.Prime(Expr.Identifier("count")),
      Expr.IntLit(1)
    )
    val withoutPrime = Expr.BinaryOp(
      BinOp.Add,
      Expr.Pre(Expr.Identifier("count")),
      Expr.IntLit(1)
    )
    assert(EvalIR.containsPrime(withPrime), "containsPrime must detect Prime in nested arith")
    assert(
      !EvalIR.containsPrime(withoutPrime),
      "containsPrime must ignore Pre (always reads pre-state, no fixed-point issue)"
    )

  // ---------------------------------------------------------------------------
  // M_L.4.b-ext Phase 5.d — Identity ensures + per-op pre-state seeding.
  // (A) `state' = state` and `state' = pre(state)` are recognised without
  //     evaluating the RHS, so non-scalar fields (Map/relation) where eval
  //     returns None aren't mis-classified as Unrecognized.
  // (C) `seedPreStateForOp` picks a pre-state where requires + invariant both
  //     hold; preservation cert is non-vacuous when possible.
  // ---------------------------------------------------------------------------

  test("Phase 5.d: identity ensures `state' = state` is recognised as no-op"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "Identity",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val op = OperationDecl(
      name = "NoOp",
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("count")),
          Expr.Identifier("count")
        )
      )
    )
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(42))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    // Identity → recognised as no-op → post == pre.
    assertEquals(EvalIR.synthesizePostState(ir, op, pre, Nil), (pre, Set.empty[String]))

  test("Phase 5.d: identity ensures `state' = pre(state)` is recognised as no-op"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "Identity",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val op = OperationDecl(
      name = "NoOpPre",
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("count")),
          Expr.Pre(Expr.Identifier("count"))
        )
      )
    )
    val pre = State(
      scalars = List("count" -> Value.VInt(BigInt(7))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    assertEquals(EvalIR.synthesizePostState(ir, op, pre, Nil), (pre, Set.empty[String]))

  test("Phase 5.d: identity recognition works for non-scalar fields (Map/relation)"):
    // For Map-typed fields, the field isn't in `scalars` — eval-based
    // synthesis would return None and reject. Identity recognition succeeds
    // structurally without eval.
    import EvalIR.*
    val ir = ServiceIR(
      name = "MapIdentity",
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(
              name = "store",
              typeExpr = TypeExpr.MapType(TypeExpr.NamedType("Int"), TypeExpr.NamedType("Int"))
            )
          )
        )
      )
    )
    val op = OperationDecl(
      name = "Touch",
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("store")),
          Expr.Identifier("store")
        )
      )
    )
    val pre = State.demo(ir)
    // Map field is in `relations` and `lookups`, NOT in `scalars`. Identity
    // recognition declines to call eval, so this succeeds as a no-op rather
    // than failing on `eval(Identifier("store"))` returning None.
    assertEquals(EvalIR.synthesizePostState(ir, op, pre, Nil), (pre, Set.empty[String]))

  test("Phase 5.d: seedPreStateForOp picks count=1 for `count > 0` requires"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "Counter",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val op = OperationDecl(
      name = "Decrement",
      requires = List(Expr.BinaryOp(BinOp.Gt, Expr.Identifier("count"), Expr.IntLit(0)))
    )
    val inv = InvariantDecl(
      name = Some("nonNeg"),
      expr = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
    )
    val demo   = State.demo(ir)
    val seeded = EvalIR.seedPreStateForOp(ir, op, inv, demo)
    // Seeded pre-state must satisfy both requires (count > 0) and invariant
    // (count >= 0). The first candidate that satisfies both wins.
    val seededCount = seeded.scalars.collectFirst { case ("count", v) => v }
    assert(
      seededCount.exists {
        case Value.VInt(n) => n > BigInt(0)
        case _             => false
      },
      s"seeded pre.count must satisfy `count > 0`, got: $seededCount"
    )

  test("Phase 5.d: seedPreStateForOp falls back to demo on unsatisfiable requires"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "Impossible",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val op = OperationDecl(
      name = "Bad",
      // count > 0 AND count < 0 is unsatisfiable. Seeder falls back to demo.
      requires = List(
        Expr.BinaryOp(BinOp.Gt, Expr.Identifier("count"), Expr.IntLit(0)),
        Expr.BinaryOp(BinOp.Lt, Expr.Identifier("count"), Expr.IntLit(0))
      )
    )
    val inv = InvariantDecl(
      name = Some("trivial"),
      expr = Expr.BoolLit(true)
    )
    val demo   = State.demo(ir)
    val seeded = EvalIR.seedPreStateForOp(ir, op, inv, demo)
    assertEquals(seeded, demo)

  test("Phase 5.d: safe_counter Decrement preservation cert is now non-vacuous"):
    val invariant = InvariantDecl(
      name = Some("countNonNegative"),
      expr = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
    )
    val decr = OperationDecl(
      name = "Decrement",
      requires = List(Expr.BinaryOp(BinOp.Gt, Expr.Identifier("count"), Expr.IntLit(0))),
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("count")),
          Expr.BinaryOp(BinOp.Sub, Expr.Identifier("count"), Expr.IntLit(1))
        )
      )
    )
    val ir = ServiceIR(
      name = "SafeCounter",
      state = Some(
        stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
      ),
      operations = List(decr),
      invariants = List(invariant)
    )
    val rendered = Emit.emit(ir, proofsPath).renderModule
    // Per-op seeding picks pre.count = 1 (smallest candidate satisfying
    // count > 0 AND count >= 0). Synthesised post.count = 0. Invariant at
    // post = 0 >= 0 = true. Non-vacuous cert.
    assert(
      rendered.contains("pre := ({ scalars := [(\"count\", .vInt (1 : Int))]"),
      s"Decrement preservation cert must seed pre.count = 1:\n$rendered"
    )
    assert(
      rendered.contains("post := ({ scalars := [(\"count\", .vInt (0 : Int))]"),
      s"Decrement preservation cert must synthesise post.count = 0:\n$rendered"
    )

  // ---------------------------------------------------------------------------
  // M_L.4.b-ext Phase 5.e — Per-clause partial recognition + set/map updates +
  // op-input parameter seeding. The synthesizer no longer declines on the
  // first unrecognised clause; instead it carries forward `unknownFields` and
  // the cert renderer's gate refuses only when the invariant reads them.
  // Set-add (`users' = users + {newUser}`) and map-add (`store' = pre(store) +
  // {k -> v}`) shapes are recognised structurally. Operation inputs are seeded
  // alongside scalars, and the seeded env is rendered into the cert so closed
  // Lean evaluation sees the same bindings.
  // ---------------------------------------------------------------------------

  test("Phase 5.e: set-add ensures `users' = users + {someElem}` updates relations + scalars"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "SetAdd",
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(name = "users", typeExpr = TypeExpr.SetType(TypeExpr.NamedType("Int")))
          )
        )
      )
    )
    val op = OperationDecl(
      name = "AddUser",
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("users")),
          Expr.BinaryOp(
            BinOp.Add,
            Expr.Identifier("users"),
            Expr.SetLiteral(List(Expr.IntLit(42)))
          )
        )
      )
    )
    val pre             = State.demo(ir)
    val (post, unknown) = EvalIR.synthesizePostState(ir, op, pre, Nil)
    assertEquals(unknown, Set.empty[String])
    // Set-add updates BOTH the scalar (VSet) AND the relation domain.
    assertEquals(
      post.scalars.collectFirst { case ("users", v) => v },
      Some(Value.VSet(List(Value.VInt(BigInt(42)))))
    )
    assertEquals(
      post.relations.collectFirst { case ("users", vs) => vs },
      Some(List(Value.VInt(BigInt(42))))
    )

  test("Phase 5.e: map-add ensures `store' = pre(store) + {k -> v}` updates lookups + relations"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "MapAdd",
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(
              name = "store",
              typeExpr = TypeExpr.MapType(TypeExpr.NamedType("Int"), TypeExpr.NamedType("Int"))
            )
          )
        )
      )
    )
    val op = OperationDecl(
      name = "Insert",
      // ensures: store' = pre(store) + {7 -> 100}
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("store")),
          Expr.BinaryOp(
            BinOp.Add,
            Expr.Pre(Expr.Identifier("store")),
            Expr.MapLiteral(List(MapEntry(Expr.IntLit(7), Expr.IntLit(100))))
          )
        )
      )
    )
    val pre             = State.demo(ir)
    val (post, unknown) = EvalIR.synthesizePostState(ir, op, pre, Nil)
    assertEquals(unknown, Set.empty[String])
    // Map-add updates BOTH lookups (key -> value pairs) AND relations
    // (the value list, since `forallRel` over a map iterates over values).
    assertEquals(
      post.lookups.collectFirst { case ("store", ps) => ps },
      Some(List((Value.VInt(BigInt(7)), Value.VInt(BigInt(100)))))
    )
    assertEquals(
      post.relations.collectFirst { case ("store", vs) => vs },
      Some(List(Value.VInt(BigInt(100))))
    )

  test("Phase 5.e: per-clause partial — recognized + unrecognized clauses coexist"):
    // Op has two ensures: one identity (recognized as no-op), one constraint
    // (unrecognized, taints `count`). If the invariant reads only the
    // identity-preserved field (`flag`), the cert is honest. If it reads
    // `count`, the gate fires.
    val ir = ServiceIR(
      name = "Mixed",
      state = Some(
        StateDecl(fields =
          List(
            StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")),
            StateFieldDecl(name = "flag", typeExpr = TypeExpr.NamedType("Bool"))
          )
        )
      )
    )
    val op = OperationDecl(
      name = "MixedOp",
      requires = List(Expr.BoolLit(true)),
      ensures = List(
        Expr.BinaryOp(
          BinOp.And,
          Expr.BinaryOp(
            BinOp.Eq,
            Expr.Prime(Expr.Identifier("flag")),
            Expr.Identifier("flag")
          ),
          Expr.BinaryOp(BinOp.Ge, Expr.Prime(Expr.Identifier("count")), Expr.IntLit(0))
        )
      )
    )
    val invFlagOnly = InvariantDecl(
      name = Some("flagSomething"),
      expr = Expr.BinaryOp(BinOp.Eq, Expr.Identifier("flag"), Expr.Identifier("flag"))
    )
    val invCount = InvariantDecl(
      name = Some("nonNeg"),
      expr = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
    )

    val flagIR =
      ServiceIR(
        name = "FlagInv",
        state = ir.state,
        operations = List(op),
        invariants = List(invFlagOnly)
      )
    val countIR =
      ServiceIR(
        name = "CountInv",
        state = ir.state,
        operations = List(op),
        invariants = List(invCount)
      )

    val flagBundle  = Emit.emit(flagIR, proofsPath)
    val countBundle = Emit.emit(countIR, proofsPath)

    // Flag invariant doesn't read `count` → cert is honest.
    assertEquals(flagBundle.summary.stubbedChecks, 0)
    assert(
      flagBundle.renderModule.contains("evalPreservation"),
      s"flag-only invariant should produce real preservation cert:\n${flagBundle.renderModule}"
    )

    // Count invariant reads `count` (which the unrecognized constraint
    // tainted) → cert stubs out.
    assertEquals(countBundle.summary.stubbedChecks, 1)
    assert(
      countBundle.renderModule
        .contains("ensures synthesis incomplete and invariant reads unknown field"),
      s"count invariant should taint and stub:\n${countBundle.renderModule}"
    )

  test("Phase 5.e: op-input parameter seeding satisfies a param-referencing requires"):
    import EvalIR.*
    val ir = ServiceIR(
      name = "ParamRef",
      state = Some(
        StateDecl(fields =
          List(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
        )
      )
    )
    val op = OperationDecl(
      name = "Adjust",
      inputs = List(ParamDecl(name = "delta", typeExpr = TypeExpr.NamedType("Int"))),
      // requires: delta > 0 — needs param-seeding to satisfy.
      requires = List(Expr.BinaryOp(BinOp.Gt, Expr.Identifier("delta"), Expr.IntLit(0))),
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Prime(Expr.Identifier("count")),
          Expr.BinaryOp(BinOp.Add, Expr.Identifier("count"), Expr.Identifier("delta"))
        )
      )
    )
    val inv = InvariantDecl(
      name = Some("nonNeg"),
      expr = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
    )
    val ctx = EvalIR.seedContextForOp(ir, op, inv, State.demo(ir))
    // Seeded env must bind `delta` to a positive integer to satisfy
    // `delta > 0` AND let the synthesizer evaluate `count + delta`.
    assert(
      ctx.env.exists {
        case ("delta", Value.VInt(n)) => n > BigInt(0)
        case _                        => false
      },
      s"seeded env must bind delta to a positive Int; env=${ctx.env}"
    )

  test("Phase 5.e: cert renders seeded env as a Lean literal beside the StatePair"):
    val ir = ServiceIR(
      name = "WithEnv",
      state = Some(
        stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
      ),
      invariants = List(
        InvariantDecl(
          name = Some("nonNeg"),
          expr = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0))
        )
      ),
      operations = List(
        OperationDecl(
          name = "Adjust",
          inputs = List(ParamDecl(name = "delta", typeExpr = TypeExpr.NamedType("Int"))),
          requires = List(Expr.BinaryOp(BinOp.Gt, Expr.Identifier("delta"), Expr.IntLit(0))),
          ensures = List(
            Expr.BinaryOp(
              BinOp.Eq,
              Expr.Prime(Expr.Identifier("count")),
              Expr.BinaryOp(BinOp.Add, Expr.Identifier("count"), Expr.Identifier("delta"))
            )
          )
        )
      )
    )
    val rendered = Emit.emit(ir, proofsPath).renderModule
    // The preservation cert's env slot must contain the seeded `delta`
    // binding (chosen so requires + invariant both hold in pre).
    assert(
      rendered.contains("(\"delta\", .vInt"),
      s"preservation cert must render seeded env binding for `delta`:\n$rendered"
    )

  test("Phase 5.e: referencedStateNames over-approximates state reads, not bound vars"):
    // `forall t in tasks, t.priority = t.priority` references `tasks` (the
    // state relation) but NOT `t` (which is bound by the quantifier). The
    // walker must respect the binder's scope.
    val expr = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("t", Expr.Identifier("tasks"), BindingKind.In)),
      Expr.BinaryOp(
        BinOp.Eq,
        Expr.FieldAccess(Expr.Identifier("t"), "priority"),
        Expr.FieldAccess(Expr.Identifier("t"), "priority")
      )
    )
    val refs = EvalIR.referencedStateNames(expr, Set.empty)
    assert(refs.contains("tasks"), s"must include the relation `tasks`: $refs")
    assert(!refs.contains("t"), s"must exclude the bound var `t`: $refs")
    assert(!refs.contains("priority"), s"must exclude field names: $refs")

  test("PR review #12: synthesizePostState rejects conflicting `x' = 1 ∧ x' = 2`"):
    // Two recognised assignments to the same scalar with different values
    // would otherwise resolve via "last write wins", certifying a post-state
    // the ensures clauses don't jointly admit. The conflict detector lifts
    // the field into `unknownFields` and drops the conflicting updates.
    import EvalIR.*
    val ir = ServiceIR(
      name = "Conflict",
      state = Some(
        StateDecl(fields = List(StateFieldDecl(name = "x", typeExpr = TypeExpr.NamedType("Int"))))
      )
    )
    val op = OperationDecl(
      name = "Inconsistent",
      ensures = List(
        Expr.BinaryOp(
          BinOp.And,
          Expr.BinaryOp(BinOp.Eq, Expr.Prime(Expr.Identifier("x")), Expr.IntLit(1)),
          Expr.BinaryOp(BinOp.Eq, Expr.Prime(Expr.Identifier("x")), Expr.IntLit(2))
        )
      )
    )
    val pre = State(
      scalars = List("x" -> Value.VInt(BigInt(0))),
      relations = Nil,
      lookups = Nil,
      entityFields = Nil
    )
    val (post, unknown) = EvalIR.synthesizePostState(ir, op, pre, Nil)
    // Conflicting field is lifted to unknownFields; post.x stays at pre's value.
    assertEquals(unknown, Set("x"))
    assertEquals(post.scalars.collectFirst { case ("x", v) => v }, Some(Value.VInt(BigInt(0))))

  test("PR review #15: containsPrime detects Prime nested in MapLiteral entries"):
    val withPrimeKey = Expr.MapLiteral(
      List(MapEntry(Expr.Prime(Expr.Identifier("k")), Expr.IntLit(1)))
    )
    val withPrimeValue = Expr.MapLiteral(
      List(MapEntry(Expr.IntLit(1), Expr.Prime(Expr.Identifier("v"))))
    )
    val cleanMap = Expr.MapLiteral(
      List(MapEntry(Expr.IntLit(1), Expr.IntLit(2)))
    )
    assert(EvalIR.containsPrime(withPrimeKey), "MapLiteral with primed key must be detected")
    assert(EvalIR.containsPrime(withPrimeValue), "MapLiteral with primed value must be detected")
    assert(!EvalIR.containsPrime(cleanMap), "MapLiteral with no primes must pass")

  test("PR review #13: VEntityWith equality uses extensional set semantics inside chain"):
    import EvalIR.Value.*
    val baseEntity = VEntity("User", "u_1")
    val a          = VEntityWith(baseEntity, "members", VSet(List(VInt(1), VInt(2))))
    val b          = VEntityWith(baseEntity, "members", VSet(List(VInt(2), VInt(1))))
    // Old structural equality compared `[1, 2]` and `[2, 1]` as unequal.
    // The recursive valueEq mirror of Lean's beqValue restores extensional
    // set equality inside record-update chains.
    val expr   = Expr.BinaryOp(BinOp.Eq, Expr.Identifier("a"), Expr.Identifier("b"))
    val env    = List("a" -> a, "b" -> b)
    val result = EvalIR.eval(EvalIR.Schema.empty, EvalIR.State.empty, env, expr)
    assertEquals(result, Some(EvalIR.Value.VBool(true)))
