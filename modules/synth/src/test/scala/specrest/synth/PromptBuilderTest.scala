package specrest.synth

import munit.CatsEffectSuite

class PromptBuilderTest extends CatsEffectSuite:

  test("initial prompt for safe_counter Increment contains all required sections"):
    Fixtures.loadHeader("safe_counter", "Increment").map:
      case (c, header, skeleton) =>
        val p = PromptBuilder.initial(c, header, skeleton)
        assert(p.system.contains("Dafny"), "system prompt mentions Dafny")
        assert(p.system.contains("MUST NOT modify the method signature"))
        val u = p.user
        List(
          "## Method Signature",
          "## Domain Description",
          "## Type Definitions",
          "## Similar Verified Examples",
          "## Your Task"
        ).foreach: section =>
          assert(u.contains(section), s"missing section $section in:\n$u")
        assert(u.contains("Increment"), "operation name appears")
        assert(u.contains(header.signature), "signature appears verbatim")

  test("initial prompt for url_shortener Shorten includes few-shot map_insert pattern"):
    Fixtures.loadHeader("url_shortener", "Shorten").map:
      case (c, h, skel) =>
        val p = PromptBuilder.initial(c, h, skel)
        assert(p.user.contains("```dafny"), "few-shot block uses dafny fence")
        assert(p.user.contains("Insert"), "map_insert_fresh few-shot present")

  test("repair prompt embeds verifier error and previous body"):
    Fixtures.loadHeader("safe_counter", "Increment").map:
      case (c, h, skel) =>
        val err = VerifierError(
          category = "postcondition_violation",
          message = "ensures clause not established",
          line = Some(42),
          relatedClause = Some("st.count == old(st.count) + 1")
        )
        val p = PromptBuilder.repair(c, h, skel, "{ st.count := st.count; }", err)
        assert(p.user.contains("FAILED"))
        assert(p.user.contains("postcondition_violation"))
        assert(p.user.contains("line 42"))
        assert(p.user.contains("st.count := st.count"), "previous body verbatim")
        assert(p.user.contains("st.count == old(st.count) + 1"))

  test("system prompt forbids new {:extern} declarations"):
    val s = PromptBuilder.systemInitial
    assert(s.contains("{:extern}"))

  test("ChainOfThought strategy swaps the system prompt and adds task-section hint"):
    Fixtures.loadHeader("safe_counter", "Increment").map:
      case (c, header, skel) =>
        val p = PromptBuilder.initial(c, header, skel, PromptStrategy.ChainOfThought)
        assert(p.system.contains("chain-of-thought"), "system mentions strategy")
        assert(p.user.contains("step-by-step"), "task section nudges step-by-step")

  test("PlanThenImplement strategy swaps the system prompt and adds plan hint"):
    Fixtures.loadHeader("safe_counter", "Increment").map:
      case (c, header, skel) =>
        val p = PromptBuilder.initial(c, header, skel, PromptStrategy.PlanThenImplement)
        assert(p.system.contains("Phase 1"), "system describes two-phase approach")
        assert(p.system.contains("Phase 2"))
        assert(p.user.contains("numbered plan"), "task section nudges numbered plan")
