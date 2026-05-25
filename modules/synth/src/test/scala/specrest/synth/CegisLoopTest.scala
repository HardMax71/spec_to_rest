package specrest.synth

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.classification_operation_name

class CegisLoopTest extends CatsEffectSuite:

  private val validBody =
    """```dafny
      |method Increment(st: ServiceState)
      |  requires ServiceStateInv(st)
      |  ensures st.count == old(st.count) + 1
      |  ensures ServiceStateInv(st)
      |  modifies st
      |{
      |  st.count := st.count + 1;
      |}
      |```""".stripMargin

  private val brokenBody =
    """```dafny
      |method Increment(st: ServiceState)
      |  requires ServiceStateInv(st)
      |  ensures st.count == old(st.count) + 1
      |  ensures ServiceStateInv(st)
      |  modifies st
      |{
      |  st.count := st.count;
      |}
      |```""".stripMargin

  private def llmResp(text: String, in: Int = 100, out: Int = 200): LlmResponse =
    LlmResponse(text, TokenUsage(in, out), "claude-sonnet-4-6")

  private val postcondError =
    VerifierError(
      category = "postcondition_violation",
      message = "a postcondition could not be proved",
      line = Some(7),
      column = Some(10)
    )

  private def failingRun(opName: String, errs: List[VerifierError]): VerifierRun =
    MockDafnyVerifier.run(List(MockDafnyVerifier.errors(opName, errs)))

  private val correctRun: String => VerifierRun =
    name => MockDafnyVerifier.run(List(MockDafnyVerifier.correct(name)))

  test("verifies on first try"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.succeeding(validBody, model = "claude-sonnet-4-6")
          verifier <-
            MockDafnyVerifier.of(List(Right(correctRun(classification_operation_name(c)))))
          tracker   <- Tracker.empty
          loop       = new CegisLoop(provider, verifier, None, tracker, CegisBudget.Default)
          out       <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
          provCalls <- provider.calls
          verCalls  <- verifier.calls
        yield out match
          case CegisOutcome.Verified(body, _, iter, _) =>
            assertEquals(iter, 1)
            assert(body.contains("st.count := st.count + 1"))
            assertEquals(provCalls.length, 1)
            assertEquals(verCalls.length, 1)
          case other => fail(s"expected Verified, got $other")

  test("verifies after 2 repair iterations (3-iteration AC for URL shortener Shorten)"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(
                        List(
                          Right(llmResp(brokenBody)),
                          Right(llmResp(brokenBody)),
                          Right(llmResp(validBody))
                        )
                      )
          verifier <- MockDafnyVerifier.of(
                        List(
                          Right(failingRun(classification_operation_name(c), List(postcondError))),
                          Right(failingRun(
                            classification_operation_name(c),
                            List(postcondError.copy(line = Some(8)))
                          )),
                          Right(correctRun(classification_operation_name(c)))
                        )
                      )
          tracker   <- Tracker.empty
          loop       = new CegisLoop(provider, verifier, None, tracker, CegisBudget.Default)
          out       <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
          provCalls <- provider.calls
        yield out match
          case CegisOutcome.Verified(_, _, iter, history) =>
            assertEquals(iter, 3)
            assertEquals(provCalls.length, 3)
            assertEquals(history.records.length, 3)
            val secondPrompt = provCalls(1).userMessage
            assert(secondPrompt.contains("FAILED"), "repair prompt marks previous as FAILED")
            assert(
              secondPrompt.contains("postcondition_violation"),
              "repair prompt embeds error category"
            )
            assert(
              secondPrompt.contains("st.count := st.count"),
              "repair prompt embeds previous body verbatim"
            )
          case other => fail(s"expected Verified, got $other")

  test("aborts when iteration budget is exhausted"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(
                        List(Right(llmResp(brokenBody)), Right(llmResp(brokenBody)))
                      )
          verifier <-
            MockDafnyVerifier.of(
              List(
                Right(failingRun(classification_operation_name(c), List(postcondError))),
                Right(failingRun(
                  classification_operation_name(c),
                  List(postcondError.copy(line = Some(99)))
                ))
              )
            )
          tracker <- Tracker.empty
          loop = new CegisLoop(
                   provider,
                   verifier,
                   None,
                   tracker,
                   CegisBudget.Default.copy(maxIterations = 2, repeatedErrorThreshold = 99)
                 )
          out <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
        yield out match
          case CegisOutcome.Aborted(AbortReason.BudgetExhausted(BudgetKind.MaxIterations), _, h2) =>
            assertEquals(h2.records.length, 2)
          case other => fail(s"expected BudgetExhausted, got $other")

  test("aborts on repeated identical errors (stuck detector)"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(List.fill(8)(Right(llmResp(brokenBody))))
          verifier <-
            MockDafnyVerifier.of(
              List.fill(8)(Right(failingRun(classification_operation_name(c), List(postcondError))))
            )
          tracker <- Tracker.empty
          loop = new CegisLoop(
                   provider,
                   verifier,
                   None,
                   tracker,
                   CegisBudget.Default.copy(repeatedErrorThreshold = 3)
                 )
          out <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
        yield out match
          case CegisOutcome.Aborted(AbortReason.StuckOnSameError(err, n), _, _) =>
            assertEquals(err.category, "postcondition_violation")
            assertEquals(n, 3)
          case other => fail(s"expected StuckOnSameError, got $other")

  test("aborts when provider fails mid-loop"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(
                        List(
                          Right(llmResp(brokenBody)),
                          Left(ProviderError("connection reset"))
                        )
                      )
          verifier <-
            MockDafnyVerifier.of(
              List(Right(failingRun(classification_operation_name(c), List(postcondError))))
            )
          tracker <- Tracker.empty
          loop     = new CegisLoop(provider, verifier, None, tracker, CegisBudget.Default)
          out     <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
        yield out match
          case CegisOutcome.Aborted(AbortReason.ProviderFailed(perr, atIter), _, _) =>
            assertEquals(atIter, 2)
            assert(perr.message.contains("connection reset"))
          case other => fail(s"expected ProviderFailed, got $other")

  test("aborts when LLM body weakens contracts (DiffViolation)"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        val weakened =
          """```dafny
            |method Increment(st: ServiceState)
            |  requires ServiceStateInv(st)
            |  ensures st.count >= old(st.count)
            |  ensures ServiceStateInv(st)
            |  modifies st
            |{
            |  st.count := st.count + 1;
            |}
            |```""".stripMargin
        for
          provider <- MockProvider.succeeding(weakened, model = "claude-sonnet-4-6")
          verifier <-
            MockDafnyVerifier.of(List(Right(correctRun(classification_operation_name(c)))))
          tracker <- Tracker.empty
          loop     = new CegisLoop(provider, verifier, None, tracker, CegisBudget.Default)
          out     <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
        yield out match
          case CegisOutcome.Aborted(AbortReason.DiffViolation(_, atIter), _, _) =>
            assertEquals(atIter, 1)
          case other => fail(s"expected DiffViolation, got $other")

  test("verifier backend failure (e.g. dafny crash) propagates as VerifierBackendFailure"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.succeeding(validBody, model = "claude-sonnet-4-6")
          verifier <- MockDafnyVerifier.of(List(Left("dafny: segfault")))
          tracker  <- Tracker.empty
          loop      = new CegisLoop(provider, verifier, None, tracker, CegisBudget.Default)
          out      <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
        yield out match
          case CegisOutcome.Aborted(AbortReason.VerifierBackendFailure(msg, atIter), _, _) =>
            assertEquals(atIter, 1)
            assert(msg.contains("segfault"))
          case other => fail(s"expected VerifierBackendFailure, got $other")

  test("cache hit short-circuits the loop"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        val tmp = java.nio.file.Files.createTempDirectory("cegis-cache-hit")
        for
          provider <- MockProvider.succeeding(validBody, model = "claude-sonnet-4-6")
          verifier <-
            MockDafnyVerifier.of(List(Right(correctRun(classification_operation_name(c)))))
          tracker   <- Tracker.empty
          cache     <- Cache.make(tmp)
          loop       = new CegisLoop(provider, verifier, Some(cache), tracker, CegisBudget.Default)
          first     <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
          second    <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
          provCalls <- provider.calls
          verCalls  <- verifier.calls
        yield
          first match
            case _: CegisOutcome.Verified => ()
            case other                    => fail(s"first run expected Verified, got $other")
          second match
            case CegisOutcome.Verified(_, _, iter, _) =>
              assertEquals(iter, 0, "cache hit reports 0 iterations")
              assertEquals(provCalls.length, 1, "second run did not call provider")
              assertEquals(verCalls.length, 1, "second run did not call verifier")
            case other => fail(s"second run expected Verified, got $other")
        end for
    .void
      *> IO.unit

  test("withHints=true threads category-matched snippets into the repair prompt"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(
                        List(Right(llmResp(brokenBody)), Right(llmResp(validBody)))
                      )
          verifier <- MockDafnyVerifier.of(
                        List(
                          Right(failingRun(classification_operation_name(c), List(postcondError))),
                          Right(correctRun(classification_operation_name(c)))
                        )
                      )
          tracker <- Tracker.empty
          loop = new CegisLoop(
                   provider,
                   verifier,
                   None,
                   tracker,
                   CegisBudget.Default,
                   dafnyTimeoutSec = 60,
                   withHints = true
                 )
          _         <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
          provCalls <- provider.calls
        yield
          assert(
            provCalls.length >= 2,
            s"expected at least one repair call, got ${provCalls.length}"
          )
          val repair = provCalls(1).userMessage
          assert(repair.contains("Suggested Patterns"), "repair prompt has hints section")
          assert(repair.contains("postcondition_capture_old"), "first hint is injected")

  test("withHints=false (default) means no hints in the repair prompt"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(
                        List(Right(llmResp(brokenBody)), Right(llmResp(validBody)))
                      )
          verifier <- MockDafnyVerifier.of(
                        List(
                          Right(failingRun(classification_operation_name(c), List(postcondError))),
                          Right(correctRun(classification_operation_name(c)))
                        )
                      )
          tracker   <- Tracker.empty
          loop       = new CegisLoop(provider, verifier, None, tracker, CegisBudget.Default)
          _         <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
          provCalls <- provider.calls
        yield
          val repair = provCalls(1).userMessage
          assert(!repair.contains("Suggested Patterns"), "no hints injected when flag off")

  test("aborts on cost cap"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(
                        List.fill(5)(Right(llmResp(brokenBody, in = 1_000_000, out = 1_000_000)))
                      )
          verifier <-
            MockDafnyVerifier.of(
              List.fill(5)(Right(failingRun(classification_operation_name(c), List(postcondError))))
            )
          tracker <- Tracker.empty
          loop = new CegisLoop(
                   provider,
                   verifier,
                   None,
                   tracker,
                   CegisBudget.Default.copy(maxCostUsd = 0.001, repeatedErrorThreshold = 99)
                 )
          out <- loop.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
        yield out match
          case CegisOutcome.Aborted(AbortReason.BudgetExhausted(kind), _, _) =>
            assert(
              kind == BudgetKind.Cost
                || kind == BudgetKind.InputTokens
                || kind == BudgetKind.OutputTokens,
              s"expected a token/cost cap, got $kind"
            )
          case other => fail(s"expected BudgetExhausted, got $other")
