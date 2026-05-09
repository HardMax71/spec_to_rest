package specrest.synth

import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.parser
import munit.CatsEffectSuite
import specrest.convention.dafny.DafnyMethodHeader

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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

  private def tempDir(prefix: String): Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory(prefix))): dir =>
      IO.blocking {
        import scala.jdk.StreamConverters.*
        scala.util.Using.resource(Files.walk(dir)): stream =>
          stream.toScala(List).reverse.foreach: p =>
            val _ = Files.deleteIfExists(p)
      }

  test("round-trip: store then lookup returns same entry"):
    tempDir("synth-cache-test").use: temp =>
      Cache.make(temp).flatMap: cache =>
        val key = Cache.keyFor(header, "claude-sonnet-4-6", 0.0)
        cache.store(key, entry) *>
          cache.lookup(key).map(r => assertEquals(r, Some(entry)))

  test("lookup miss returns None"):
    tempDir("synth-cache-test-miss").use: temp =>
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

  test("clause boundary shift produces a distinct key"):
    val a  = header.copy(requiresClauses = List("xE"), ensuresClauses = List("y"))
    val b  = header.copy(requiresClauses = List("x"), ensuresClauses = List("Ey"))
    val ka = Cache.keyFor(a, "m", 0.0)
    val kb = Cache.keyFor(b, "m", 0.0)
    assertNotEquals(ka, kb)

  test("legacy JSON without `outcome` field decodes as Verified"):
    val legacy =
      """{
        |  "candidate": "method ... { ... }",
        |  "body": "  st.count := st.count + 1;",
        |  "usage": {"inputTokens": 120, "outputTokens": 80},
        |  "model": "claude-sonnet-4-6",
        |  "promptVersion": "v1"
        |}""".stripMargin
    parser.decode[CacheEntry](legacy) match
      case Right(decoded) =>
        assertEquals(decoded.outcome, CacheOutcome.Verified)
        assertEquals(decoded.body, "  st.count := st.count + 1;")
      case Left(err) => fail(s"legacy decode failed: $err")

  test("Skeleton-tagged entry round-trips through cache and is sharded the same way"):
    tempDir("synth-cache-skeleton").use: temp =>
      Cache.make(Cache.skeletonsRoot(temp)).flatMap: cache =>
        val key       = Cache.keyFor(header, "claude-opus-4-7", 1.0)
        val skelEntry = entry.copy(outcome = CacheOutcome.Skeleton)
        cache.store(key, skelEntry) *> cache.lookup(key).map: r =>
          assertEquals(r, Some(skelEntry))
          val sharded = Cache.skeletonsRoot(temp).resolve(key.value.take(2))
          assert(Files.isDirectory(sharded), "entries are sharded by 2-char prefix")

  test("verifiedRoot and skeletonsRoot resolve to expected subdirectories"):
    val root = java.nio.file.Paths.get("/tmp/x")
    assertEquals(Cache.verifiedRoot(root).getFileName.toString, "verified")
    assertEquals(Cache.skeletonsRoot(root).getFileName.toString, "skeletons")

  test("on-disk JSON for a Skeleton entry contains outcome=skeleton"):
    tempDir("synth-cache-on-disk").use: temp =>
      Cache.make(temp).flatMap: cache =>
        val key       = Cache.keyFor(header, "m", 0.0)
        val skelEntry = entry.copy(outcome = CacheOutcome.Skeleton)
        cache.store(key, skelEntry) *> IO.blocking {
          val onDisk = temp.resolve(key.value.take(2)).resolve(s"${key.value}.json")
          val text   = new String(Files.readAllBytes(onDisk), StandardCharsets.UTF_8)
          assert(text.contains("\"outcome\""), s"json missing outcome: $text")
          assert(text.contains("\"skeleton\""), s"outcome value not 'skeleton': $text")
        }
