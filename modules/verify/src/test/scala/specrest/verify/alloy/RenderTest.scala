package specrest.verify.alloy

import specrest.verify.CheckStatus

class RenderTest extends munit.FunSuite:

  test("empty module renders the module header"):
    val m   = AlloyModule("Empty", Nil, Nil, Nil)
    val out = Render.render(m)
    assert(out.contains("module Empty"), s"missing module header: $out")

  test("sig with set field renders correctly"):
    val sig = AlloySig(
      name = "User",
      fields = List(AlloyField("friends", AlloyFieldMultiplicity.Set, "User"))
    )
    val m   = AlloyModule("M", List(sig), Nil, Nil)
    val out = Render.render(m)
    assert(out.contains("sig User {"), s"missing sig: $out")
    assert(out.contains("friends: set User"), s"missing field: $out")

  test("fact renders with name and body"):
    val f   = AlloyFact(Some("positive"), "some User")
    val m   = AlloyModule("M", Nil, List(f), Nil)
    val out = Render.render(m)
    assert(out.contains("fact positive {"), s"missing fact: $out")
    assert(out.contains("some User"), s"missing body: $out")

  test("run command renders with scope"):
    val cmd = AlloyCommand("go", AlloyCommandKind.Run, "some User", 5)
    val m   = AlloyModule("M", Nil, Nil, List(cmd))
    val out = Render.render(m)
    assert(out.contains("run go { some User } for 5"), s"missing command: $out")

  test("rendered module round-trips through AlloyBackend (sat case)"):
    val m = AlloyModule(
      name = "RoundTrip",
      sigs = List(AlloySig("Elem")),
      facts = List(AlloyFact(Some("nonEmpty"), "some Elem")),
      commands = List(AlloyCommand("go", AlloyCommandKind.Run, "some Elem", 5))
    )
    val source  = Render.render(m)
    val backend = new AlloyBackend
    val result  = backend.checkSync(source, commandIdx = 0, timeoutMs = 30_000L).toOption.get
    assertEquals(result.status, CheckStatus.Sat, s"expected sat; source=$source")
    assert(result.solution.isDefined, "expected a solution for sat case")

  test("rendered module round-trips through AlloyBackend (unsat case)"):
    val m = AlloyModule(
      name = "Contradiction",
      sigs = List(AlloySig("Elem")),
      facts = List(AlloyFact(Some("impossible"), "some Elem and no Elem")),
      commands = List(AlloyCommand("go", AlloyCommandKind.Run, "", 5))
    )
    val source  = Render.render(m)
    val backend = new AlloyBackend
    val result  = backend.checkSync(source, commandIdx = 0, timeoutMs = 30_000L).toOption.get
    assertEquals(result.status, CheckStatus.Unsat, s"expected unsat; source=$source")

  test("singleton State sig with set field + subset powerset quantification"):
    val m = AlloyModule(
      name = "StateDemo",
      sigs = List(
        AlloySig("User"),
        AlloySig(
          "State",
          isOne = true,
          fields = List(AlloyField("users", AlloyFieldMultiplicity.Set, "User"))
        )
      ),
      facts = List(AlloyFact(
        Some("someEmptySubsetOfUsers"),
        "some t: set User | t in State.users and no t"
      )),
      commands = List(AlloyCommand("go", AlloyCommandKind.Run, "", 5))
    )
    val source  = Render.render(m)
    val backend = new AlloyBackend
    val result  = backend.checkSync(source, commandIdx = 0, timeoutMs = 30_000L).toOption.get
    assertEquals(result.status, CheckStatus.Sat, s"expected sat; source=$source")

  test("existential powerset quantification (some t: set …) works end-to-end"):
    val m = AlloyModule(
      name = "Powerset",
      sigs = List(AlloySig("Atom")),
      facts = List(AlloyFact(
        Some("someEmptySubset"),
        "some t: set Atom | no t"
      )),
      commands = List(AlloyCommand("go", AlloyCommandKind.Run, "", 5))
    )
    val source  = Render.render(m)
    val backend = new AlloyBackend
    val result  = backend.checkSync(source, commandIdx = 0, timeoutMs = 30_000L).toOption.get
    assertEquals(result.status, CheckStatus.Sat, s"expected sat; source=$source")
