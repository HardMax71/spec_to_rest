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

  test("todo_list: CreateTodo emits target=todo_todo_ids (initial status from ensures)"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("todo_todo_ids = Bundle(\"todo_todo_ids\")"), out.file)
      assert(out.file.contains("@rule(target=todo_todo_ids"), out.file)
      assert(out.file.contains("def create_todo(self,"))
      assert(out.file.contains("strategy_priority()"))
      assert(out.file.contains("return response_data[\"id\"]"))

  // ---------- #153: per-status bundles ----------

  test("#153: todo_list declares one Bundle per Status enum value"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("todo_todo_ids = Bundle(\"todo_todo_ids\")"), out.file)
      assert(
        out.file.contains("todo_in_progress_ids = Bundle(\"todo_in_progress_ids\")"),
        out.file
      )
      assert(out.file.contains("todo_done_ids = Bundle(\"todo_done_ids\")"), out.file)
      assert(out.file.contains("todo_archived_ids = Bundle(\"todo_archived_ids\")"), out.file)
      assert(
        !out.file.contains("    todo_ids = Bundle"),
        s"legacy single bundle should be gone:\n${out.file}"
      )

  test("#153: unguarded transition consumes from + targets to + strict success"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(
        out.file.contains("@rule(target=todo_in_progress_ids, id=consumes(todo_todo_ids))"),
        out.file
      )
      assert(out.file.contains("def start_work_from_todo_to_in_progress(self, id):"), out.file)
      assert(
        out.file.contains("assert response.status_code == 200, response.text"),
        out.file
      )

  test("#153: Archive emits archive_from_todo + archive_from_done (one per from)"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(
        out.file.contains("@rule(target=todo_archived_ids, id=consumes(todo_todo_ids))"),
        out.file
      )
      assert(out.file.contains("def archive_from_todo_to_archived(self, id):"), out.file)
      assert(
        out.file.contains("@rule(target=todo_archived_ids, id=consumes(todo_done_ids))"),
        out.file
      )
      assert(out.file.contains("def archive_from_done_to_archived(self, id):"), out.file)

  test("#153: guarded Reopen returns id on 2xx, multiple() on 4xx"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(
        out.file.contains("@rule(target=todo_in_progress_ids, id=consumes(todo_done_ids))"),
        out.file
      )
      assert(out.file.contains("def reopen_from_done_to_in_progress(self, id):"), out.file)
      val reopenBlock = out.file
        .linesIterator
        .dropWhile(!_.contains("def reopen_from_done_to_in_progress"))
        .takeWhile(!_.startsWith("    @"))
        .mkString("\n")
      assert(reopenBlock.contains("if response.status_code == 200:"), reopenBlock)
      assert(reopenBlock.contains("return id"), reopenBlock)
      assert(reopenBlock.contains("return multiple()"), reopenBlock)

  test(
    "#153: GetTodo (recognized `id in todos`) draws from union of all 4 bundles, strict"
  ):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(
        out.file.contains(
          "@rule(id=st.one_of(todo_todo_ids, todo_in_progress_ids, todo_done_ids, todo_archived_ids))"
        ),
        out.file
      )
      val getBlock = out.file
        .linesIterator
        .dropWhile(!_.contains("def get_todo"))
        .takeWhile(!_.startsWith("    @"))
        .mkString("\n")
      assert(
        getBlock.contains("assert response.status_code == 200, response.text"),
        s"GetTodo's `id in todos` is fully satisfied by union membership → strict;\nblock=$getBlock"
      )
      assert(
        !getBlock.contains("400 <= response.status_code < 500"),
        s"strict path — no loose 4xx fallback;\nblock=$getBlock"
      )

  test("#153: DeleteTodo draws from union, non-consuming, loose"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out    = Stateful.emitFor(profiled)
      val lines  = out.file.linesIterator.toList
      val defIdx = lines.indexWhere(_.contains("def delete_todo"))
      assert(defIdx > 0, s"missing delete_todo def:\n${out.file}")
      val ruleLine = lines(defIdx - 1)
      assertEquals(
        ruleLine.trim,
        "@rule(id=st.one_of(todo_todo_ids, todo_in_progress_ids, todo_done_ids, todo_archived_ids))",
        s"DeleteTodo across multi-bundle union should be non-consuming;\nrule=$ruleLine"
      )
      assert(!ruleLine.contains("consumes("), s"DeleteTodo over union must NOT consume:\n$ruleLine")

  test("#153: imports include consumes and multiple when used"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("    consumes,"), out.file)
      assert(out.file.contains("    multiple,"), out.file)

  test("#153: url_shortener (no enum transition) keeps single legacy bundle"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(out.file.contains("url_mapping_ids = Bundle(\"url_mapping_ids\")"), out.file)
      assert(
        !out.file.contains("multiple()"),
        s"no guarded transitions → no multiple():\n${out.file}"
      )
      assert(!out.file.contains("    multiple,"), s"no multiple() use → no import:\n${out.file}")
      assert(
        !out.file.contains("st.one_of(url_mapping_ids"),
        s"single bundle → no union:\n${out.file}"
      )

  test("#153: safe_counter (no entities) unchanged"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out = Stateful.emitFor(profiled)
      assert(!out.file.contains("Bundle("), s"no entities → no bundles:\n${out.file}")
      assert(!out.file.contains("multiple()"), s"no transitions → no multiple():\n${out.file}")

  // ---------- PR #157 review round 1 fixes ----------

  test("#157 fix A: Create without deterministic status falls back to legacy single bundle"):
    val spec =
      """|service Demo {
         |  enum Status { ACTIVE, ARCHIVED }
         |  entity Foo {
         |    id: Int
         |    status: Status
         |  }
         |  state {
         |    foos: Int -> lone Foo
         |  }
         |  transition FooLifecycle {
         |    entity: Foo
         |    field: status
         |    ACTIVE -> ARCHIVED via Archive
         |  }
         |  operation CreateFoo {
         |    input: status: Status
         |    output: foo: Foo
         |    ensures:
         |      foo.id = 1
         |      foo.status = status
         |      foos' = pre(foos) + {foo.id -> foo}
         |  }
         |  operation Archive {
         |    input: id: Int
         |    requires: id in foos
         |    ensures: foos'[id].status = ARCHIVED
         |  }
         |  conventions {
         |    CreateFoo.http_path = "/foos"
         |    CreateFoo.http_status_success = 201
         |    Archive.http_method = "POST"
         |    Archive.http_path = "/foos/{id}/archive"
         |  }
         |}
         |""".stripMargin
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) =>
            val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
            val out      = Stateful.emitFor(profiled)
            assert(out.file.contains("foo_ids = Bundle(\"foo_ids\")"), out.file)
            assert(
              !out.file.contains("foo_active_ids = Bundle"),
              s"non-deterministic Create should disable per-status:\n${out.file}"
            )
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("#157 fix B: status conjunct on non-transition field doesn't shrink bundles to empty"):
    val spec =
      """|service Demo {
         |  enum Status { ACTIVE, ARCHIVED }
         |  enum Priority { LOW, HIGH }
         |  entity Foo {
         |    id: Int
         |    status: Status
         |    priority: Priority
         |  }
         |  state {
         |    foos: Int -> lone Foo
         |  }
         |  transition FooLifecycle {
         |    entity: Foo
         |    field: status
         |    ACTIVE -> ARCHIVED via Archive
         |  }
         |  operation CreateFoo {
         |    input: priority: Priority
         |    output: foo: Foo
         |    ensures:
         |      foo.id = 1
         |      foo.status = ACTIVE
         |      foo.priority = priority
         |      foos' = pre(foos) + {foo.id -> foo}
         |  }
         |  operation HighPriorityOnly {
         |    input: id: Int
         |    requires:
         |      id in foos
         |      foos[id].priority = HIGH
         |    ensures: foos' = pre(foos)
         |  }
         |  operation Archive {
         |    input: id: Int
         |    requires: id in foos
         |    ensures: foos'[id].status = ARCHIVED
         |  }
         |  conventions {
         |    CreateFoo.http_path = "/foos"
         |    CreateFoo.http_status_success = 201
         |    HighPriorityOnly.http_method = "POST"
         |    HighPriorityOnly.http_path = "/foos/{id}/high"
         |    Archive.http_method = "POST"
         |    Archive.http_path = "/foos/{id}/archive"
         |  }
         |}
         |""".stripMargin
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) =>
            val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
            val out      = Stateful.emitFor(profiled)
            // bundles for both status enum values exist
            assert(out.file.contains("foo_active_ids = Bundle(\"foo_active_ids\")"), out.file)
            assert(out.file.contains("foo_archived_ids = Bundle(\"foo_archived_ids\")"), out.file)
            // priority restriction (non-transition field) does NOT zero the bundle list
            // → HighPriorityOnly draws from union (not Skipped), and is loose
            val highBlock = out.file
              .linesIterator
              .dropWhile(!_.contains("def high_priority_only"))
              .takeWhile(!_.startsWith("    @"))
              .mkString("\n")
            assert(
              highBlock.contains("400 <= response.status_code < 500"),
              s"priority restriction unsatisfied by status bundles → loose:\n$highBlock"
            )
            assert(
              !out.file.contains("st.nothing()"),
              s"priority restriction must not skip the rule:\n${out.file}"
            )
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("#157 fix D: distinct from→to pairs get distinct function names"):
    val spec =
      """|service Demo {
         |  enum Status { A, B, C }
         |  entity Foo {
         |    id: Int
         |    status: Status
         |  }
         |  state {
         |    foos: Int -> lone Foo
         |  }
         |  transition FooLifecycle {
         |    entity: Foo
         |    field: status
         |    A -> B via Move
         |    A -> C via Move
         |  }
         |  operation CreateFoo {
         |    input: x: Int
         |    output: foo: Foo
         |    ensures:
         |      foo.id = x
         |      foo.status = A
         |      foos' = pre(foos) + {foo.id -> foo}
         |  }
         |  operation Move {
         |    input: id: Int
         |    requires: id in foos
         |    ensures: foos'[id].status = B
         |  }
         |  conventions {
         |    CreateFoo.http_path = "/foos"
         |    CreateFoo.http_status_success = 201
         |    Move.http_method = "POST"
         |    Move.http_path = "/foos/{id}/move"
         |  }
         |}
         |""".stripMargin
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) =>
            val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
            val out      = Stateful.emitFor(profiled)
            assert(out.file.contains("def move_from_a_to_b(self, id):"), out.file)
            assert(out.file.contains("def move_from_a_to_c(self, id):"), out.file)
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test(
    "#157 fix I: conjunctive status restrictions intersect (AND semantics, not union)"
  ):
    val spec =
      """|service Demo {
         |  enum Status { ACTIVE, PAUSED, ARCHIVED }
         |  entity Foo {
         |    id: Int
         |    status: Status
         |  }
         |  state {
         |    foos: Int -> lone Foo
         |  }
         |  transition FooLifecycle {
         |    entity: Foo
         |    field: status
         |    ACTIVE -> ARCHIVED via Archive
         |  }
         |  operation CreateFoo {
         |    input: x: Int
         |    output: foo: Foo
         |    ensures:
         |      foo.id = x
         |      foo.status = ACTIVE
         |      foos' = pre(foos) + {foo.id -> foo}
         |  }
         |  operation Narrow {
         |    input: id: Int
         |    requires:
         |      id in foos
         |      foos[id].status in {ACTIVE, ARCHIVED}
         |      foos[id].status = ACTIVE
         |    ensures: foos' = pre(foos)
         |  }
         |  operation Archive {
         |    input: id: Int
         |    requires: id in foos
         |    ensures: foos'[id].status = ARCHIVED
         |  }
         |  conventions {
         |    CreateFoo.http_path = "/foos"
         |    CreateFoo.http_status_success = 201
         |    Narrow.http_method = "POST"
         |    Narrow.http_path = "/foos/{id}/narrow"
         |    Archive.http_method = "POST"
         |    Archive.http_path = "/foos/{id}/archive"
         |  }
         |}
         |""".stripMargin
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) =>
            val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
            val out      = Stateful.emitFor(profiled)
            // intersection of {ACTIVE, ARCHIVED} ∩ {ACTIVE} = {ACTIVE} → single bundle
            assert(
              out.file.contains("@rule(id=foo_active_ids)"),
              s"intersection of {ACTIVE,ARCHIVED} and {ACTIVE} should be just ACTIVE;\n${out.file}"
            )
            assert(out.file.contains("def narrow(self, id):"), out.file)
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test(
    "#157 fix J: single-bundle Delete with unrecognized requires uses non-consuming + loose"
  ):
    val spec =
      """|service Demo {
         |  entity Foo {
         |    id: Int
         |    locked: Bool
         |  }
         |  state {
         |    foos: Int -> lone Foo
         |  }
         |  operation CreateFoo {
         |    input: x: Int
         |    output: foo: Foo
         |    ensures:
         |      foo.id = x
         |      foo.locked = false
         |      foos' = pre(foos) + {foo.id -> foo}
         |  }
         |  operation DeleteFoo {
         |    input: id: Int
         |    requires:
         |      id in foos
         |      foos[id].locked = false
         |    ensures: foos' = pre(foos) - {id}
         |  }
         |  conventions {
         |    CreateFoo.http_path = "/foos"
         |    CreateFoo.http_status_success = 201
         |    DeleteFoo.http_method = "DELETE"
         |    DeleteFoo.http_path = "/foos/{id}"
         |  }
         |}
         |""".stripMargin
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) =>
            val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
            val out      = Stateful.emitFor(profiled)
            // No transition → legacy single bundle. requires `foos[id].locked = false`
            // uses bool literal — not enum, so recognizer marks it unrecognized.
            // strictByConstruction = false → must NOT consume.
            assert(out.file.contains("foo_ids = Bundle"), out.file)
            assert(
              out.file.contains("@rule(id=foo_ids)"),
              s"non-strict Delete must draw non-consuming;\n${out.file}"
            )
            assert(
              !out.file.contains("@rule(id=consumes(foo_ids))"),
              s"non-strict Delete must NOT consume:\n${out.file}"
            )
            // body must be loose
            val delBlock = out.file
              .linesIterator
              .dropWhile(!_.contains("def delete_foo"))
              .takeWhile(!_.startsWith("    @"))
              .mkString("\n")
            assert(
              delBlock.contains("400 <= response.status_code < 500"),
              s"non-strict Delete must be loose;\nblock=$delBlock"
            )
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

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
