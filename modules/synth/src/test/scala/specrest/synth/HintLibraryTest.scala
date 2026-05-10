package specrest.synth

import munit.CatsEffectSuite

class HintLibraryTest extends CatsEffectSuite:

  test("library covers every category that PromptBuilder.repair classifies"):
    val expected = Set(
      "postcondition_violation",
      "precondition_violation",
      "loop_invariant_failure",
      "loop_invariant_not_established",
      "decreases_failure",
      "assertion_failure",
      "timeout"
    )
    val missing = expected -- HintLibrary.categories
    assert(missing.isEmpty, s"hint library missing categories: $missing")

  test("each hint resource loads and is non-empty Dafny-shaped text"):
    HintLibrary.all.foreach: hint =>
      val text = hint.snippet
      assert(text.nonEmpty, s"hint ${hint.name} loaded as empty")
      assert(
        text.startsWith("//"),
        s"hint ${hint.name} should open with a `//` rationale comment, got: ${text.take(40)}"
      )

  test("forCategory returns only matches and respects the limit"):
    val postconds = HintLibrary.forCategory("postcondition_violation", limit = 2)
    assertEquals(postconds.length, 2)
    assert(postconds.forall(_.category == "postcondition_violation"))

  test("forCategory caps at the available count when fewer hints exist"):
    val timeoutHints = HintLibrary.forCategory("timeout", limit = 5)
    assertEquals(timeoutHints.length, 1)
    assertEquals(timeoutHints.head.name, "timeout_split_proof")

  test("forCategory returns Nil for unknown category"):
    val unknown = HintLibrary.forCategory("does_not_exist")
    assertEquals(unknown, List.empty[Hint])

  test("hint names are unique across the library"):
    val names = HintLibrary.all.map(_.name)
    assertEquals(names.length, names.distinct.length, s"duplicate hint names: $names")

  test("descriptions are short and informative"):
    HintLibrary.all.foreach: hint =>
      assert(hint.description.length >= 20, s"description too short: ${hint.name}")
      assert(hint.description.length <= 200, s"description too long: ${hint.name}")
