package specrest.synth

import munit.CatsEffectSuite

class SynthesisReportTest extends CatsEffectSuite:

  private def verifiedAttempt(model: String, cost: Double): AttemptRecord =
    AttemptRecord(
      strategy = PromptStrategy.ZeroShot,
      model = model,
      cegisOutcome = CegisOutcome.Verified("body", "full", iterations = 1, CegisHistory.empty),
      costUsd = cost,
      inputTokens = 100,
      outputTokens = 200
    )

  private def abortedAttempt(model: String, cost: Double, reason: AbortReason): AttemptRecord =
    AttemptRecord(
      strategy = PromptStrategy.ChainOfThought,
      model = model,
      cegisOutcome = CegisOutcome.Aborted(reason, None, CegisHistory.empty),
      costUsd = cost,
      inputTokens = 50,
      outputTokens = 100
    )

  test("Verdict.fromOutcome maps single-attempt Verified to Verified"):
    val v = FallbackOutcome.Verified(
      "body",
      "full",
      PromptStrategy.ZeroShot,
      "m",
      cegisIterations = 1,
      attempts = List(verifiedAttempt("m", 0.001))
    )
    assertEquals(Verdict.fromOutcome(v), Verdict.Verified)

  test("Verdict.fromOutcome maps multi-attempt Verified to VerifiedEscalated"):
    val v = FallbackOutcome.Verified(
      "body",
      "full",
      PromptStrategy.ChainOfThought,
      "m2",
      cegisIterations = 1,
      attempts = List(
        abortedAttempt("m1", 0.01, AbortReason.BudgetExhausted(BudgetKind.MaxIterations)),
        verifiedAttempt("m2", 0.005)
      )
    )
    assertEquals(Verdict.fromOutcome(v), Verdict.VerifiedEscalated)

  test("Verdict.fromOutcome maps SkeletonOnly to Skeleton"):
    val s = FallbackOutcome.SkeletonOnly(
      "expect false",
      "full",
      reason = "budget exhausted",
      attempts = List(
        abortedAttempt("m", 0.01, AbortReason.BudgetExhausted(BudgetKind.Cost))
      )
    )
    assertEquals(Verdict.fromOutcome(s), Verdict.Skeleton)

  test("OpOutcome.fromFallback aggregates token + cost across attempts"):
    val out = FallbackOutcome.Verified(
      "body",
      "full",
      PromptStrategy.ChainOfThought,
      "m2",
      cegisIterations = 2,
      attempts = List(
        abortedAttempt("m1", 0.01, AbortReason.BudgetExhausted(BudgetKind.MaxIterations)),
        verifiedAttempt("m2", 0.005)
      )
    )
    val op = OpOutcome.fromFallback("Shorten", out)
    assertEquals(op.attempts, 2)
    assertEqualsDouble(op.totalCostUsd, 0.015, 0.0001)
    assertEquals(op.inputTokens, 150L)
    assertEquals(op.outputTokens, 300L)
    assertEquals(op.finalStrategy, PromptStrategy.ChainOfThought)
    assertEquals(op.finalModel, "m2")

  test("Reporter.render plain-text emits one row per op + a totals line"):
    val ops = List(
      OpOutcome(
        "Shorten",
        Verdict.Verified,
        PromptStrategy.ZeroShot,
        "claude-sonnet-4-6",
        1,
        1,
        0.001,
        100,
        200,
        None
      ),
      OpOutcome(
        "Resolve",
        Verdict.VerifiedEscalated,
        PromptStrategy.ChainOfThought,
        "claude-opus-4-7",
        2,
        2,
        0.05,
        500,
        1000,
        None
      ),
      OpOutcome(
        "Analytics",
        Verdict.Skeleton,
        PromptStrategy.PlanThenImplement,
        "claude-opus-4-7",
        0,
        4,
        0.10,
        1500,
        3000,
        Some("stuck on postcondition_violation")
      )
    )
    val rendered = Reporter.render(SynthesisReport(ops), useColor = false)
    assert(rendered.contains("Shorten"))
    assert(rendered.contains("VERIFIED"))
    assert(rendered.contains("VERIFIED-ESCALATED"))
    assert(rendered.contains("SKELETON"))
    assert(rendered.contains("verified=1"))
    assert(rendered.contains("escalated=1"))
    assert(rendered.contains("skeleton=1"))
    assert(rendered.contains("cost=$0.1510"), s"missing cost line in:\n$rendered")
    // No ANSI codes when useColor=false
    assert(!rendered.contains("[3"), s"plain mode should not emit ANSI: $rendered")

  test("Reporter.render with color wraps verdict in ANSI escapes"):
    val ops = List(
      OpOutcome(
        "Foo",
        Verdict.Skeleton,
        PromptStrategy.ZeroShot,
        "m",
        0,
        1,
        0.01,
        50,
        100,
        Some("x")
      )
    )
    val rendered = Reporter.render(SynthesisReport(ops), useColor = true)
    assert(rendered.contains("[31m"), "red ANSI for SKELETON missing")
    assert(rendered.contains("[0m"), "reset code missing")
