package specrest.synth

import cats.effect.IO
import cats.effect.kernel.Resource
import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.classification_operation_name

import java.nio.file.Files
import java.nio.file.Path

class FallbackOrchestratorTest extends CatsEffectSuite:

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

  private def llmResp(text: String, in: Int = 100, out: Int = 200, model: String): LlmResponse =
    LlmResponse(text, TokenUsage(in, out), model)

  private val postcondError =
    VerifierError("postcondition_violation", "ensures clause not established", Some(7), Some(0))

  private def correctRun(name: String): VerifierRun =
    MockDafnyVerifier.run(List(MockDafnyVerifier.correct(name)))

  private def failingRun(name: String): VerifierRun =
    MockDafnyVerifier.run(List(MockDafnyVerifier.errors(name, List(postcondError))))

  private def tempDir(prefix: String): Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory(prefix))): dir =>
      IO.blocking {
        import scala.jdk.StreamConverters.*
        scala.util.Using.resource(Files.walk(dir)): stream =>
          stream.toScala(List).reverse.foreach: p =>
            val _ = Files.deleteIfExists(p)
      }

  private val perAttempt = CegisBudget.Default.copy(maxIterations = 2, maxCostUsd = 100.0)

  test("verifies on first attempt → no escalation, single attempt record"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(List(Right(llmResp(validBody, model = "claude-sonnet-4-6"))))
          verifier <-
            MockDafnyVerifier.of(List(Right(correctRun(classification_operation_name(c)))))
          tracker <- Tracker.empty
          orch = new FallbackOrchestrator(
                   provider,
                   verifier,
                   None,
                   None,
                   tracker,
                   perAttempt,
                   FallbackBudget.Default.copy(modelLadder = List("claude-sonnet-4-6"))
                 )
          out <- orch.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
        yield out match
          case v: FallbackOutcome.Verified =>
            assertEquals(v.attempts.length, 1)
            assertEquals(v.finalStrategy, PromptStrategy.ZeroShot)
            assertEquals(v.finalModel, "claude-sonnet-4-6")
            assert(v.body.contains("st.count := st.count + 1"))
          case other => fail(s"expected Verified, got $other")

  test("strategy switch: ZeroShot fails, ChainOfThought verifies"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          // Two LLM calls per attempt at most (perAttempt.maxIterations=2).
          // Attempt 1 (ZeroShot): broken → failingRun → broken → failingRun (aborts on repeated error or budget).
          // Attempt 2 (ChainOfThought): valid → correctRun.
          provider <- MockProvider.of(
                        List(
                          Right(llmResp(brokenBody, model = "m")),
                          Right(llmResp(brokenBody, model = "m")),
                          Right(llmResp(validBody, model = "m"))
                        )
                      )
          verifier <- MockDafnyVerifier.of(
                        List(
                          Right(failingRun(classification_operation_name(c))),
                          Right(failingRun(classification_operation_name(c))),
                          Right(correctRun(classification_operation_name(c)))
                        )
                      )
          tracker <- Tracker.empty
          orch = new FallbackOrchestrator(
                   provider,
                   verifier,
                   None,
                   None,
                   tracker,
                   perAttempt,
                   FallbackBudget.Default.copy(
                     promptStrategies = List(
                       PromptStrategy.ZeroShot,
                       PromptStrategy.ChainOfThought
                     ),
                     modelLadder = List("m")
                   )
                 )
          out <- orch.run(SynthRequest(c, h, skel, "m"))
        yield out match
          case v: FallbackOutcome.Verified =>
            assertEquals(v.finalStrategy, PromptStrategy.ChainOfThought)
            assertEquals(v.attempts.length, 2)
          case other => fail(s"expected Verified after escalation, got $other")

  test("model escalation: m1 fails on both strategies, m2 verifies"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(
                        List(
                          // m1 ZeroShot: 1 broken iter → abort (budget=1)
                          Right(llmResp(brokenBody, model = "m1")),
                          // m1 ChainOfThought: 1 broken
                          Right(llmResp(brokenBody, model = "m1")),
                          // m2 ZeroShot: valid
                          Right(llmResp(validBody, model = "m2"))
                        )
                      )
          verifier <- MockDafnyVerifier.of(
                        List(
                          Right(failingRun(classification_operation_name(c))),
                          Right(failingRun(classification_operation_name(c))),
                          Right(correctRun(classification_operation_name(c)))
                        )
                      )
          tracker <- Tracker.empty
          orch = new FallbackOrchestrator(
                   provider,
                   verifier,
                   None,
                   None,
                   tracker,
                   perAttempt.copy(maxIterations = 1),
                   FallbackBudget.Default.copy(
                     promptStrategies = List(
                       PromptStrategy.ZeroShot,
                       PromptStrategy.ChainOfThought
                     ),
                     modelLadder = List("m1", "m2")
                   )
                 )
          out <- orch.run(SynthRequest(c, h, skel, "m1"))
        yield out match
          case v: FallbackOutcome.Verified =>
            assertEquals(v.finalModel, "m2")
            assertEquals(v.finalStrategy, PromptStrategy.ZeroShot)
            assertEquals(v.attempts.length, 3)
          case other => fail(s"expected Verified after model escalation, got $other")

  test("all attempts abort → SkeletonOnly with provenance and persisted to skeletonCache"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        tempDir("orch-skel").use: dir =>
          for
            provider <- MockProvider.of(
                          List(
                            Right(llmResp(brokenBody, model = "m1")),
                            Right(llmResp(brokenBody, model = "m1"))
                          )
                        )
            verifier <- MockDafnyVerifier.of(
                          List(
                            Right(failingRun(classification_operation_name(c))),
                            Right(failingRun(classification_operation_name(c)))
                          )
                        )
            tracker   <- Tracker.empty
            skelCache <- Cache.make(Cache.skeletonsRoot(dir))
            orch = new FallbackOrchestrator(
                     provider,
                     verifier,
                     None,
                     Some(skelCache),
                     tracker,
                     perAttempt.copy(maxIterations = 1),
                     FallbackBudget.Default.copy(
                       promptStrategies = List(
                         PromptStrategy.ZeroShot,
                         PromptStrategy.ChainOfThought
                       ),
                       modelLadder = List("m1")
                     )
                   )
            req        = SynthRequest(c, h, skel, "m1", temperature = 1.0)
            out       <- orch.run(req)
            persisted <- skelCache.lookup(Cache.keyFor(h, "m1", 1.0))
          yield
            out match
              case s: FallbackOutcome.SkeletonOnly =>
                assertEquals(s.attempts.length, 2)
                assert(s.body.contains("expect false"), s"body: ${s.body}")
                assert(s.body.contains("FALLBACK SKELETON"))
              case other => fail(s"expected SkeletonOnly, got $other")
            persisted match
              case Some(entry) =>
                assertEquals(entry.outcome, CacheOutcome.Skeleton)
                assert(entry.body.contains("expect false"))
              case None => fail("skeleton entry not persisted")

  test("shared cost cap stops escalation mid-plan"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          // Each call costs ~$0.001 with 1000/2000 tokens at sonnet pricing ($3/$15 per 1M)
          // = 1000*3/1e6 + 2000*15/1e6 = 0.003 + 0.030 = 0.033 per call
          provider <- MockProvider.of(
                        List(
                          Right(llmResp(
                            brokenBody,
                            in = 1000,
                            out = 2000,
                            model = "claude-sonnet-4-6"
                          )),
                          Right(llmResp(
                            brokenBody,
                            in = 1000,
                            out = 2000,
                            model = "claude-sonnet-4-6"
                          )),
                          Right(llmResp(
                            brokenBody,
                            in = 1000,
                            out = 2000,
                            model = "claude-sonnet-4-6"
                          ))
                        )
                      )
          verifier <- MockDafnyVerifier.of(
                        List(
                          Right(failingRun(classification_operation_name(c))),
                          Right(failingRun(classification_operation_name(c))),
                          Right(failingRun(classification_operation_name(c)))
                        )
                      )
          tracker <- Tracker.empty
          orch = new FallbackOrchestrator(
                   provider,
                   verifier,
                   None,
                   None,
                   tracker,
                   perAttempt.copy(maxIterations = 1),
                   FallbackBudget.Default.copy(
                     // After 2 attempts at $0.033 each = $0.066, cap of $0.05 should stop us.
                     sharedCostCapUsd = 0.05,
                     promptStrategies = List(
                       PromptStrategy.ZeroShot,
                       PromptStrategy.ChainOfThought,
                       PromptStrategy.PlanThenImplement
                     ),
                     modelLadder = List("claude-sonnet-4-6")
                   )
                 )
          out           <- orch.run(SynthRequest(c, h, skel, "claude-sonnet-4-6"))
          providerCalls <- provider.calls
        yield out match
          case s: FallbackOutcome.SkeletonOnly =>
            assert(
              s.attempts.length < 3,
              s"should stop before exhausting plan, attempts=${s.attempts.length}"
            )
            assertEquals(providerCalls.length, s.attempts.length)
          case other => fail(s"expected SkeletonOnly after budget cap, got $other")

  test("when no modelLadder provided, falls back to req.model"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, h, skel) =>
        for
          provider <- MockProvider.of(List(Right(llmResp(validBody, model = "from-req"))))
          verifier <-
            MockDafnyVerifier.of(List(Right(correctRun(classification_operation_name(c)))))
          tracker <- Tracker.empty
          orch = new FallbackOrchestrator(
                   provider,
                   verifier,
                   None,
                   None,
                   tracker,
                   perAttempt,
                   FallbackBudget.Default.copy(
                     modelLadder = Nil,
                     promptStrategies = List(PromptStrategy.ZeroShot)
                   )
                 )
          out <- orch.run(SynthRequest(c, h, skel, "from-req"))
        yield out match
          case v: FallbackOutcome.Verified =>
            assertEquals(v.finalModel, "from-req")
          case other => fail(s"expected Verified, got $other")
