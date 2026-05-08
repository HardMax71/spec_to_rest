package specrest.synth

import munit.CatsEffectSuite
import specrest.convention.dafny.DafnyMethodHeader

import java.nio.file.Files

class CacheTest extends CatsEffectSuite:

  private val header = DafnyMethodHeader(
    "Increment",
    "method Increment(st: ServiceState)",
    requiresClauses = List("ServiceStateInv(st)"),
    ensuresClauses = List("st.count == old(st.count) + 1"),
    modifiesClauses = List("st")
  )

  private val entry = CacheEntry(
    candidate = "method Increment(...) { ... }",
    body = "  st.count := st.count + 1;",
    usage = TokenUsage(120, 80),
    model = "claude-sonnet-4-6",
    promptVersion = "v1"
  )

  test("round-trip: store then lookup returns same entry"):
    val temp = Files.createTempDirectory("synth-cache-test")
    Cache.make(temp).flatMap: cache =>
      val key = Cache.keyFor(header, "claude-sonnet-4-6", 0.0)
      cache.store(key, entry) *>
        cache.lookup(key).map(r => assertEquals(r, Some(entry)))

  test("lookup miss returns None"):
    val temp = Files.createTempDirectory("synth-cache-test-miss")
    Cache.make(temp).flatMap: cache =>
      val key = Cache.keyFor(header, "claude-sonnet-4-6", 0.0)
      cache.lookup(key).map(r => assertEquals(r, None))

  test("different model produces different key"):
    val k1 = Cache.keyFor(header, "claude-sonnet-4-6", 0.0)
    val k2 = Cache.keyFor(header, "claude-haiku-4-5", 0.0)
    assertNotEquals(k1, k2)

  test("different prompt version produces different key"):
    val k1 = Cache.keyFor(header, "m", 0.0, "v1")
    val k2 = Cache.keyFor(header, "m", 0.0, "v2")
    assertNotEquals(k1, k2)

  test("identical inputs produce identical keys"):
    val k1 = Cache.keyFor(header, "m", 0.0)
    val k2 = Cache.keyFor(header, "m", 0.0)
    assertEquals(k1, k2)
