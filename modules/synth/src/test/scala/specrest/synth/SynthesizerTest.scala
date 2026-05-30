package specrest.synth

import munit.CatsEffectSuite

import java.nio.file.Files

class SynthesizerTest extends CatsEffectSuite:

  private val mockResponseValid =
    """Here is the body.
      |
      |```dafny
      |method Increment(st: ServiceState)
      |  requires ServiceStateInv(st)
      |  ensures st.count == old(st.count) + 1
      |  ensures ServiceStateInv(st)
      |  modifies st
      |{
      |  st.count := st.count + 1;
      |}
      |```
      |""".stripMargin

  test("happy path: provider returns valid Dafny → result has body and tracked cost"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, header, skel) =>
        for
          provider <- MockProvider.succeeding(
                        mockResponseValid,
                        in = 100,
                        out = 200,
                        model = "claude-sonnet-4-6"
                      )
          tracker <- Tracker.empty
          synth    = new Synthesizer(provider, None, tracker)
          req      = SynthRequest(c, header, skel, "claude-sonnet-4-6")
          result  <- synth.synthesize(req)
          summary <- tracker.summary
          calls   <- provider.calls
        yield result match
          case Left(err) => fail(s"expected Right, got $err")
          case Right(r)  =>
            assert(r.body.contains("st.count := st.count + 1"))
            assertEquals(r.usage, TokenUsage(100, 200))
            assert(r.costUsd > 0.0)
            assertEquals(r.cached, false)
            assertEquals(summary.operations, 1)
            assertEquals(summary.cachedHits, 0)
            assertEquals(calls.length, 1)

  test("cache hit avoids LLM call"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, header, skel) =>
        val temp = Files.createTempDirectory("synth-cache-hit")
        for
          provider <- MockProvider.succeeding(
                        mockResponseValid,
                        in = 100,
                        out = 200,
                        model = "claude-sonnet-4-6"
                      )
          tracker <- Tracker.empty
          cache   <- Cache.make(temp)
          synth    = new Synthesizer(provider, Some(cache), tracker)
          req      = SynthRequest(c, header, skel, "claude-sonnet-4-6")
          first   <- synth.synthesize(req)
          second  <- synth.synthesize(req)
          summary <- tracker.summary
          calls   <- provider.calls
        yield
          assert(first.isRight)
          second match
            case Left(err) => fail(s"expected cached Right, got $err")
            case Right(r2) =>
              assertEquals(r2.cached, true)
              assertEquals(calls.length, 1, "second call must hit cache")
              assertEquals(summary.operations, 2)
              assertEquals(summary.cachedHits, 1)

  test("provider error surfaces as ProviderFailure"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, header, skel) =>
        for
          provider <- MockProvider.failing("network down")
          tracker  <- Tracker.empty
          synth     = new Synthesizer(provider, None, tracker)
          result   <- synth.synthesize(SynthRequest(c, header, skel, "claude-sonnet-4-6"))
        yield result match
          case Right(_)                   => fail("expected provider failure")
          case Left(err: ProviderFailure) =>
            assert(err.message.contains("network down"))
          case Left(other) => fail(s"expected ProviderFailure, got $other")

  test("unparseable LLM output surfaces as ResponseParseFailure"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, header, skel) =>
        for
          provider <- MockProvider.succeeding("no fenced block here", model = "claude-sonnet-4-6")
          tracker  <- Tracker.empty
          synth     = new Synthesizer(provider, None, tracker)
          result   <- synth.synthesize(SynthRequest(c, header, skel, "claude-sonnet-4-6"))
        yield result match
          case Left(_: ResponseParseFailure) => ()
          case other                         => fail(s"expected ResponseParseFailure, got $other")

  test("LLM that weakens postcondition surfaces as DiffCheckFailure"):
    Fixtures.loadHeader("safe_counter", "Increment").flatMap:
      case (c, header, skel) =>
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
          tracker  <- Tracker.empty
          synth     = new Synthesizer(provider, None, tracker)
          result   <- synth.synthesize(SynthRequest(c, header, skel, "claude-sonnet-4-6"))
        yield result match
          case Left(_: DiffCheckFailure) => ()
          case other                     => fail(s"expected DiffCheckFailure, got $other")
