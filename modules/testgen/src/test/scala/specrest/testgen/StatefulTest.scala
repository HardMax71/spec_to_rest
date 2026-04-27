package specrest.testgen

import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate

class StatefulTest extends CatsEffectSuite:

  private def loadProfiled(path: String) =
    val src = scala.util.Using.resource(scala.io.Source.fromFile(path)): source =>
      source.getLines.mkString("\n")
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => Annotate.buildProfiledService(ir, "python-fastapi-postgres")
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("url_shortener: bundle declared and per-entity"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("url_mapping_ids = Bundle(\"url_mapping_ids\")"), out.file)

  test("url_shortener: Shorten emits target=, returns id projection"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("@rule(target=url_mapping_ids"), out.file)
      assert(out.file.contains("def shorten(self, url):"))
      assert(out.file.contains("return response_data[\"code\"]"))

  test("url_shortener: Resolve uses non-consuming bundle draw"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("@rule(code=url_mapping_ids)"))
      assert(out.file.contains("def resolve(self, code):"))

  test("url_shortener: Delete uses consumes()"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("@rule(code=consumes(url_mapping_ids))"))
      assert(out.file.contains("def delete(self, code):"))

  test("url_shortener: ListAll has @rule() decorator immediately preceding def"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out          = Stateful.emitFor(profiled)
      val listAllDef   = "def list_all(self):"
      val listAllIndex = out.file.indexOf(listAllDef)
      assert(listAllIndex >= 0, s"missing list_all def:\n${out.file}")
      val precedingLine = out.file
        .substring(0, listAllIndex)
        .linesIterator
        .toList
        .reverse
        .find(_.trim.nonEmpty)
        .map(_.trim)
      assertEquals(
        precedingLine,
        Some("@rule()"),
        s"expected bare @rule() before list_all:\n${out.file}"
      )

  test("url_shortener: invariants emitted with @invariant() decorator"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("@invariant()"))
      assert(out.file.contains("def invariant_all_ur_ls_valid(self):"))
      assert(out.file.contains("def invariant_metadata_consistent(self):"))
      assert(out.file.contains("post_state = client.get(\"/__test_admin__/state\").json()"))

  test("file emits TestCase alias and settings"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("UrlShortenerStateMachine.TestCase.settings = settings("))
      assert(out.file.contains("max_examples=25"))
      assert(out.file.contains("stateful_step_count=20"))
      assert(out.file.contains("deadline=None"))
      assert(out.file.contains("TestStatefulUrlShortener = UrlShortenerStateMachine.TestCase"))

  test("imports include Bundle, rule, initialize, invariant, consumes when used"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("    RuleBasedStateMachine,"))
      assert(out.file.contains("    Bundle,"))
      assert(out.file.contains("    rule,"))
      assert(out.file.contains("    initialize,"))
      assert(out.file.contains("    invariant,"))
      assert(out.file.contains("    consumes,"))

  test("safe_counter: no entities → no bundles, parameter-less rules"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(
        !out.file.contains("Bundle("),
        s"safe_counter has no entities, expected no Bundle:\n${out.file}"
      )
      assert(out.file.contains("def increment(self):"))
      assert(out.file.contains("def decrement(self):"))
      assert(!out.file.contains("consumes("), "no bundles → no consumes import or use")

  test("todo_list: CreateTodo emits target=todo_ids, all sub-strategies"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("todo_ids = Bundle(\"todo_ids\")"))
      assert(out.file.contains("@rule(target=todo_ids"))
      assert(out.file.contains("def create_todo(self,"))
      assert(out.file.contains("strategy_priority()"))
      assert(out.file.contains("return response_data[\"id\"]"))

  test("todo_list: state-dep transition rules use loose 4xx-tolerant assertion"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("def start_work(self, id):"))
      assert(
        out.file.contains("400 <= response.status_code < 500"),
        s"transition rules should accept 4xx for unsatisfied non-key requires:\n${out.file}"
      )

  test("rule whose only requires is `<input> in <state>` uses strict assertion"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      val resolveBlock = out.file
        .linesIterator
        .dropWhile(!_.contains("def resolve(self, code):"))
        .takeWhile(!_.startsWith("    @"))
        .mkString("\n")
      assert(
        resolveBlock.contains("assert response.status_code == 302"),
        s"Resolve has only `code in store` require → bundle-satisfied → strict;\nblock=$resolveBlock"
      )

  test("@initialize() reset is emitted exactly once"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out        = Stateful.emitFor(profiled)
      val needle     = "client.post(\"/__test_admin__/reset\")"
      val resetCount = out.file.sliding(needle.length).count(_ == needle)
      assertEquals(resetCount, 1, "single @initialize reset call expected")

  test("output contains TestCase pytest-discovery alias"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("TestStatefulSafeCounter = SafeCounterStateMachine.TestCase"))

  test("non-Create rules do NOT call response.json() before status assertion"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      val resolveBlock = out.file
        .linesIterator
        .dropWhile(!_.contains("def resolve(self, code):"))
        .takeWhile(!_.startsWith("    @"))
        .mkString("\n")
      assert(
        !resolveBlock.contains("response_data = response.json()"),
        s"Resolve (302 redirect) must not call response.json(); block:\n$resolveBlock"
      )
      val deleteBlock = out.file
        .linesIterator
        .dropWhile(!_.contains("def delete(self, code):"))
        .takeWhile(!_.startsWith("    @"))
        .mkString("\n")
      assert(
        !deleteBlock.contains("response_data = response.json()"),
        s"Delete (204 no body) must not call response.json(); block:\n$deleteBlock"
      )

  test("Create rule parses response.json() AFTER strict status assertion"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      val shortenBlock = out.file
        .linesIterator
        .dropWhile(!_.contains("def shorten(self, url):"))
        .takeWhile(!_.startsWith("    @"))
        .toList
      val assertIdx = shortenBlock.indexWhere(_.contains("assert response.status_code == 201"))
      val jsonIdx   = shortenBlock.indexWhere(_.contains("response_data = response.json()"))
      assert(
        assertIdx >= 0,
        s"missing strict status assert in shorten:\n${shortenBlock.mkString("\n")}"
      )
      assert(jsonIdx >= 0, s"missing response.json() in shorten:\n${shortenBlock.mkString("\n")}")
      assert(
        jsonIdx > assertIdx,
        s"json() must come AFTER status assert; got assert@$assertIdx, json@$jsonIdx"
      )

  test("invariant skips use <invariants> sentinel, not <service>"):
    val ir = specrest.ir.ServiceIR(
      name = "X",
      invariants = List(
        specrest.ir.InvariantDecl(
          name = Some("badInv"),
          expr = specrest.ir.Expr.SetComprehension(
            "x",
            specrest.ir.Expr.Identifier("nonsense"),
            specrest.ir.Expr.BoolLit(true)
          )
        )
      )
    )
    val profile  = specrest.profile.Annotate.buildProfiledService(ir, "python-fastapi-postgres")
    val out      = Stateful.emitFor(profile)
    val invSkips = out.skips.filter(_.kind.startsWith("stateful_invariant"))
    assert(invSkips.nonEmpty, s"expected at least one invariant skip; got ${out.skips}")
    assert(
      invSkips.forall(_.operation == "<invariants>"),
      s"all invariant skips should use <invariants> sentinel; got ${invSkips}"
    )
