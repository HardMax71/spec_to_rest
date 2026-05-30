package specrest.synth

import munit.CatsEffectSuite
import specrest.dafny.DafnyMethodHeader

class SkeletonGeneratorTest extends CatsEffectSuite:

  private def header(sig: String): DafnyMethodHeader =
    DafnyMethodHeader(
      name = "Op",
      signature = sig,
      requiresClauses = Nil,
      ensuresClauses = Nil,
      modifiesClauses = Nil
    )

  test("emits expect false with op-name and reason payload"):
    val body = SkeletonGenerator.fallbackBody(
      header("method Foo(st: ServiceState)"),
      attempts = 3,
      finalStrategy = "ChainOfThought",
      finalModel = "claude-opus-4-7",
      reason = "budget exhausted: Cost"
    )
    assert(body.startsWith("""expect false, """"))
    assert(body.contains("FALLBACK SKELETON"))
    assert(body.contains("[op=Op]"))
    assert(body.contains("attempts=3"))
    assert(body.contains("strategy=ChainOfThought"))
    assert(body.contains("model=claude-opus-4-7"))
    assert(body.contains("reason=budget exhausted: Cost"))

  test("no out-params → just the expect line"):
    val body = SkeletonGenerator.fallbackBody(
      header("method Delete(st: ServiceState, code: ShortCode)"),
      attempts = 1,
      finalStrategy = "ZeroShot",
      finalModel = "claude-sonnet-4-6",
      reason = "stuck"
    )
    val lines = body.linesIterator.toList
    assertEquals(lines.length, 1, s"expected one line, got: $body")
    assert(lines.head.startsWith("expect false"))

  test("single out-param havoced"):
    val body = SkeletonGenerator.fallbackBody(
      header("method ListAll(st: ServiceState) returns (entries: set<UrlMapping>)"),
      attempts = 2,
      finalStrategy = "ZeroShot",
      finalModel = "claude-sonnet-4-6",
      reason = "stuck"
    )
    assert(body.contains("entries := *;"))

  test("multiple out-params with nested generic types"):
    val body = SkeletonGenerator.fallbackBody(
      header(
        "method Shorten(st: ServiceState, url: LongURL) returns (code: ShortCode, short_url: string)"
      ),
      attempts = 4,
      finalStrategy = "PlanThenImplement",
      finalModel = "claude-opus-4-7",
      reason = "stuck on postcondition_violation"
    )
    assert(body.contains("code := *;"))
    assert(body.contains("short_url := *;"))

  test("nested generics in out-params do not confuse the comma splitter"):
    val body = SkeletonGenerator.fallbackBody(
      header(
        "method Q(st: ServiceState) returns (a: map<int, seq<string>>, b: set<(int, int)>)"
      ),
      attempts = 1,
      finalStrategy = "ZeroShot",
      finalModel = "x",
      reason = "y"
    )
    assert(body.contains("a := *;"), body)
    assert(body.contains("b := *;"), body)

  test("arrow types (->, ~>, ==>) are not treated as generic closers"):
    val arrowSig =
      "method H(st: ServiceState) returns (f: int -> int, g: int ~> bool, h: int ==> int)"
    val body = SkeletonGenerator.fallbackBody(
      header(arrowSig),
      attempts = 1,
      finalStrategy = "ZeroShot",
      finalModel = "x",
      reason = "y"
    )
    assert(body.contains("f := *;"), s"missing 'f := *;': $body")
    assert(body.contains("g := *;"), s"missing 'g := *;': $body")
    assert(body.contains("h := *;"), s"missing 'h := *;': $body")

  test("backslashes in reason are escaped so the Dafny string stays valid"):
    val body = SkeletonGenerator.fallbackBody(
      header("method Foo(st: ServiceState)"),
      attempts = 1,
      finalStrategy = "ZeroShot",
      finalModel = "x",
      reason = "C:\\Users\\test\\path"
    )
    val firstLine = body.linesIterator.toList.head
    assert(firstLine.contains("\\\\"), s"backslashes not escaped: $firstLine")
    assertEquals(
      firstLine.count(_ == '"'),
      2,
      s"escaped backslashes preserved exact-2 quote count: $firstLine"
    )

  test("reason gets newline/quote-sanitized and truncated"):
    val verbose = "line1\n\"quoted\"\n" + ("x" * 500)
    val body    = SkeletonGenerator.fallbackBody(
      header("method Foo(st: ServiceState)"),
      attempts = 1,
      finalStrategy = "ZeroShot",
      finalModel = "x",
      reason = verbose
    )
    val firstLine = body.linesIterator.toList.head
    assert(!body.contains("\n\"quoted\""), "newlines should be collapsed in payload")
    assertEquals(
      firstLine.count(_ == '"'),
      2,
      s"first line must have exactly two quote chars (the wrapper); got: $firstLine"
    )
    assert(firstLine.endsWith("\";"), s"line should end with quote+semicolon: $firstLine")
    assert(body.length < 400, s"reason should be truncated, got len=${body.length}")
