package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class ExprToPythonTest extends CatsEffectSuite:

  private def i(n: Int): expr_full        = IntLitF(int_of_integer(BigInt(n)), None)
  private def s(t: String): expr_full     = StringLitF(t, None)
  private def b(v: Boolean): expr_full    = BoolLitF(v, None)
  private def id(name: String): expr_full = IdentifierF(name, None)

  private val uriParam = ParamDeclFull("s", NamedTypeF("String", None), None)
  private val uriBody  = MatchesF(id("s"), "^https?:..[^\\s]+", None)
  private val emailParam =
    ParamDeclFull("s", NamedTypeF("String", None), None)
  private val emailBody = MatchesF(id("s"), "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", None)

  private val preamblePredicates: Map[String, PredicateDeclFull] = Map(
    "isValidURI"   -> PredicateDeclFull("isValidURI", List(uriParam), uriBody, None),
    "isValidEmail" -> PredicateDeclFull("isValidEmail", List(emailParam), emailBody, None)
  )

  private val ctx = TestCtx(
    inputs = Set("x", "url"),
    outputs = Set("code", "short_url"),
    stateFields = Set("count", "store", "metadata", "base_url"),
    mapStateFields = Set.empty,
    enumValues = Map("Status" -> Set("todo", "done")),
    userFunctions = Map.empty,
    userPredicates = preamblePredicates,
    boundVars = Set.empty,
    capture = CaptureMode.PostState
  )

  private def py(e: Translated): String = e match
    case Translated.Emit(t)    => t
    case Translated.Skip(r, _) => fail(s"expected Py, got Skip($r)")

  private def reason(e: Translated): String = e match
    case Translated.Skip(r, _) => r
    case Translated.Emit(t)    => fail(s"expected Skip, got Py($t)")

  test("literals translate directly"):
    assertEquals(py(ExprToPython.translate(i(42), ctx)), "42")
    assertEquals(py(ExprToPython.translate(b(true), ctx)), "True")
    assertEquals(py(ExprToPython.translate(b(false), ctx)), "False")
    assertEquals(py(ExprToPython.translate(s("hi"), ctx)), "\"hi\"")
    assertEquals(py(ExprToPython.translate(NoneLitF(None), ctx)), "None")

  test("identifier resolution: bound > output > input > state"):
    val withBound = ctx.withBound(List("c"))
    assertEquals(py(ExprToPython.translate(id("c"), withBound)), "c")
    assertEquals(
      py(ExprToPython.translate(id("code"), ctx)),
      "response_data[\"code\"]"
    )
    assertEquals(py(ExprToPython.translate(id("x"), ctx)), "x")
    assertEquals(
      py(ExprToPython.translate(id("count"), ctx)),
      "post_state[\"count\"]"
    )
    assertEquals(
      py(ExprToPython.translate(id("count"), ctx.withCapture(CaptureMode.PreState))),
      "pre_state[\"count\"]"
    )

  test("unknown identifier skips with reason"):
    val r = reason(ExprToPython.translate(id("nope"), ctx))
    assert(r.contains("nope"), s"expected reason to mention 'nope', got: $r")

  test("Pre/Prime flip capture mode for state reads"):
    val pre = ExprToPython.translate(PreF(id("count"), None), ctx)
    assertEquals(py(pre), "pre_state[\"count\"]")
    val post = ExprToPython.translate(PrimeF(id("count"), None), ctx)
    assertEquals(py(post), "post_state[\"count\"]")

  test("Pre does not affect output identifiers"):
    val e = PreF(id("code"), None)
    assertEquals(py(ExprToPython.translate(e, ctx)), "response_data[\"code\"]")

  test("BinaryOp: arithmetic + comparison + logical + membership"):
    val cases: List[(bin_op_full, String)] = List(
      BAdd()       -> "((1) + (2))",
      BSub()       -> "((1) - (2))",
      BMul()       -> "((1) * (2))",
      BDiv()       -> "((1) / (2))",
      BEq()        -> "((1) == (2))",
      BNeq()       -> "((1) != (2))",
      BLt()        -> "((1) < (2))",
      BLe()        -> "((1) <= (2))",
      BGt()        -> "((1) > (2))",
      BGe()        -> "((1) >= (2))",
      BAnd()       -> "((1) and (2))",
      BOr()        -> "((1) or (2))",
      BImplies()   -> "((not (1)) or (2))",
      BIff()       -> "((1) == (2))",
      BIn()        -> "((1) in (2))",
      BNotIn()     -> "((1) not in (2))",
      BUnion()     -> "((1) | (2))",
      BIntersect() -> "((1) & (2))",
      BDiff()      -> "((1) - (2))",
      BSubset()    -> "((1) <= (2))"
    )
    cases.foreach: (op, expected) =>
      val e = BinaryOpF(op, i(1), i(2), None)
      assertEquals(py(ExprToPython.translate(e, ctx)), expected, s"op=$op")

  test("UnaryOp: not / negate / cardinality / power"):
    val notE = UnaryOpF(UNot(), b(true), None)
    assertEquals(py(ExprToPython.translate(notE, ctx)), "(not (True))")
    val negE = UnaryOpF(UNegate(), i(5), None)
    assertEquals(py(ExprToPython.translate(negE, ctx)), "(-(5))")
    val cardE = UnaryOpF(UCardinality(), id("store"), None)
    assertEquals(py(ExprToPython.translate(cardE, ctx)), "len(post_state[\"store\"])")
    val powE = UnaryOpF(UPower(), id("store"), None)
    assertEquals(py(ExprToPython.translate(powE, ctx)), "_powerset(post_state[\"store\"])")

  test("FieldAccess on output → response_data['outer']['field']"):
    val e = FieldAccessF(id("code"), "value", None)
    assertEquals(py(ExprToPython.translate(e, ctx)), "response_data[\"code\"][\"value\"]")

  test("Index: store[code] uses output for key, state for base"):
    val e = IndexF(id("store"), id("code"), None)
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "post_state[\"store\"][response_data[\"code\"]]"
    )

  test("EnumAccess emits the member as a string literal"):
    val e = EnumAccessF(id("Status"), "todo", None)
    assertEquals(py(ExprToPython.translate(e, ctx)), "\"todo\"")

  test("Recognized calls: max, min, now, days, sum"):
    val maxE = CallF(id("max"), List(id("store")), None)
    assertEquals(py(ExprToPython.translate(maxE, ctx)), "max(post_state[\"store\"])")
    val minE = CallF(id("min"), List(id("store")), None)
    assertEquals(py(ExprToPython.translate(minE, ctx)), "min(post_state[\"store\"])")
    val nowE = CallF(id("now"), Nil, None)
    assertEquals(
      py(ExprToPython.translate(nowE, ctx)),
      "datetime.datetime.now(datetime.timezone.utc).timestamp()"
    )
    val daysE = CallF(id("days"), List(i(30)), None)
    assertEquals(
      py(ExprToPython.translate(daysE, ctx)),
      "datetime.timedelta(days=30).total_seconds()"
    )
    val sumE = CallF(
      id("sum"),
      List(
        id("store"),
        LambdaF("i", FieldAccessF(id("i"), "line_total", None), None)
      ),
      None
    )
    // After the Builtins-registry refactor, `sum` lowers as an opaque
    // function-call composition: the lambda renders as `(lambda i: ...)` via
    // the standard LambdaF translator, and the registry's `sum` emit wraps
    // it as `sum((lambda)(_x) for _x in coll)`. Same semantics, slightly less
    // tight than the prior inline-body form — but per-backend special-casing
    // is gone and adding new builtins is now a single registry entry.
    assertEquals(
      py(ExprToPython.translate(sumE, ctx)),
      "sum(((lambda i: (i[\"line_total\"])))(_x) for _x in (post_state[\"store\"]))"
    )

  test("User-defined function call resolves via TestCtx.userFunctions"):
    val fn = FunctionDeclFull(
      "isPositive",
      List(ParamDeclFull("n", NamedTypeF("Int", None), None)),
      NamedTypeF("Bool", None),
      BinaryOpF(BGt(), id("n"), i(0), None),
      None
    )
    val ctxWithFn = ctx.copy(userFunctions = Map("isPositive" -> fn))
    val callE     = CallF(id("isPositive"), List(i(5)), None)
    assertEquals(py(ExprToPython.translate(callE, ctxWithFn)), "is_positive(5)")

  test("Unknown function (not built-in, not user-declared) skips"):
    val callE = CallF(id("noSuchBuiltin"), List(id("x")), None)
    assert(reason(ExprToPython.translate(callE, ctx)).contains("noSuchBuiltin/1"))

  test("hash/1 is a builtin: emits hashlib.sha256(str(...).encode()).hexdigest()"):
    // `url` is an `inputs` field in this test ctx, so the inner translation succeeds.
    // str(...) coercion makes the call robust to non-string inputs (numbers, None).
    val callE = CallF(id("hash"), List(id("url")), None)
    assertEquals(
      py(ExprToPython.translate(callE, ctx)),
      "hashlib.sha256(str(url).encode()).hexdigest()"
    )

  test("minutes/hours/seconds/days are builtins: emit timedelta total_seconds"):
    List(
      ("minutes", "datetime.timedelta(minutes=5).total_seconds()"),
      ("hours", "datetime.timedelta(hours=5).total_seconds()"),
      ("seconds", "datetime.timedelta(seconds=5).total_seconds()"),
      ("days", "datetime.timedelta(days=5).total_seconds()")
    ).foreach: (fname, expected) =>
      val callE = CallF(id(fname), List(i(5)), None)
      assertEquals(py(ExprToPython.translate(callE, ctx)), expected, s"$fname/1")

  test("User-defined call with wrong arity skips with arity-mismatch reason"):
    val fn = FunctionDeclFull(
      "isPositive",
      List(ParamDeclFull("n", NamedTypeF("Int", None), None)),
      NamedTypeF("Bool", None),
      BinaryOpF(BGt(), id("n"), i(0), None),
      None
    )
    val ctxWithFn = ctx.copy(userFunctions = Map("isPositive" -> fn))
    val callE     = CallF(id("isPositive"), List(i(5), i(6)), None)
    val r         = reason(ExprToPython.translate(callE, ctxWithFn))
    assert(r.contains("wrong arity"), s"expected arity-mismatch reason, got: $r")
    assert(r.contains("expected 1, got 2"))

  test("Indirect call (non-identifier callee) translates by applying callee to args"):
    val callE = CallF(
      LambdaF("y", BinaryOpF(BAdd(), id("y"), i(1), None), None),
      List(i(7)),
      None
    )
    assertEquals(
      py(ExprToPython.translate(callE, ctx)),
      "((lambda y: (((y) + (1)))))(7)"
    )

  test("Recognized calls: len, dom, ran, isValidURI, isValidEmail"):
    val lenE = CallF(id("len"), List(id("store")), None)
    assertEquals(py(ExprToPython.translate(lenE, ctx)), "len(post_state[\"store\"])")
    val domE = CallF(id("dom"), List(id("store")), None)
    assertEquals(py(ExprToPython.translate(domE, ctx)), "set(post_state[\"store\"].keys())")
    val ranE = CallF(id("ran"), List(id("store")), None)
    assertEquals(py(ExprToPython.translate(ranE, ctx)), "set(post_state[\"store\"].values())")
    val uriE = CallF(id("isValidURI"), List(id("url")), None)
    assertEquals(py(ExprToPython.translate(uriE, ctx)), "is_valid_uri(url)")
    val emailE = CallF(id("isValidEmail"), List(id("x")), None)
    assertEquals(py(ExprToPython.translate(emailE, ctx)), "is_valid_email(x)")

  test("Bare enum-member identifier resolves to its string literal"):
    val ctxWithEnum = ctx.copy(enumValues = Map("Status" -> Set("TODO", "DONE")))
    assertEquals(
      py(ExprToPython.translate(id("TODO"), ctxWithEnum)),
      "\"TODO\""
    )
    assertEquals(
      py(ExprToPython.translate(id("DONE"), ctxWithEnum)),
      "\"DONE\""
    )

  test("Unknown function call skips"):
    val e = CallF(id("custom_predicate"), List(i(1)), None)
    assert(reason(ExprToPython.translate(e, ctx)).contains("custom_predicate"))

  test("Skip propagates through binary ops (unbound identifier)"):
    val e = BinaryOpF(BEq(), id("nope"), i(0), None)
    assert(reason(ExprToPython.translate(e, ctx)).contains("nope"))

  test("If / Let translate to Python ternary / lambda"):
    val ifE = IfF(b(true), i(1), i(2), None)
    assertEquals(py(ExprToPython.translate(ifE, ctx)), "((1) if (True) else (2))")
    val letE = LetF("v", i(7), id("v"), None)
    assertEquals(py(ExprToPython.translate(letE, ctx)), "((lambda v=(7): (v))())")

  test("Empty SetLiteral → set(); non-empty → {…}"):
    val empty = SetLiteralF(Nil, None)
    assertEquals(py(ExprToPython.translate(empty, ctx)), "set()")
    val nonEmpty = SetLiteralF(List(i(1), i(2)), None)
    assertEquals(py(ExprToPython.translate(nonEmpty, ctx)), "{1, 2}")

  test("Quantifier forall over state"):
    val body = CallF(
      id("isValidURI"),
      List(IndexF(id("store"), id("c"), None)),
      None
    )
    val q = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", id("store"), BkIn(), None)),
      body,
      None
    )
    assertEquals(
      py(ExprToPython.translate(q, ctx)),
      "all(is_valid_uri(post_state[\"store\"][c]) for c in (post_state[\"store\"]))"
    )

  test("Quantifier exists / no map to any / not any"):
    val body = BinaryOpF(BGt(), id("c"), i(0), None)
    val exists = QuantifierF(
      QExists(),
      List(QuantifierBindingFull("c", id("store"), BkIn(), None)),
      body,
      None
    )
    assert(py(ExprToPython.translate(exists, ctx)).startsWith("any("))
    val no = QuantifierF(
      QNo(),
      List(QuantifierBindingFull("c", id("store"), BkIn(), None)),
      body,
      None
    )
    assert(py(ExprToPython.translate(no, ctx)).startsWith("(not any("))

  test("Quantifier with Colon binding skips"):
    val q = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("op", id("Login"), BkColon(), None)),
      b(true),
      None
    )
    assert(reason(ExprToPython.translate(q, ctx)).contains("non-`in`"))

  test("MapLiteral: empty → {}, non-empty → {k: v}"):
    assertEquals(py(ExprToPython.translate(MapLiteralF(Nil, None), ctx)), "{}")
    val nonEmpty = MapLiteralF(
      List(
        MapEntryFull(id("code"), id("url"), None),
        MapEntryFull(i(1), s("a"), None)
      ),
      None
    )
    assertEquals(
      py(ExprToPython.translate(nonEmpty, ctx)),
      "{response_data[\"code\"]: url, 1: \"a\"}"
    )

  test("BinaryOp.Add over MapLiteral merges as dict-spread"):
    val e = BinaryOpF(
      BAdd(),
      PreF(id("store"), None),
      MapLiteralF(List(MapEntryFull(id("code"), id("url"), None)), None),
      None
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "{**(pre_state[\"store\"]), **({response_data[\"code\"]: url})}"
    )

  test("Constructor → Python dict literal with snake-case-preserving field keys"):
    val e = ConstructorF(
      "LineItem",
      List(
        FieldAssignFull("product_sku", id("x"), None),
        FieldAssignFull("quantity", i(3), None)
      ),
      None
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "{\"product_sku\": x, \"quantity\": 3}"
    )

  test("Constructor with no fields → empty dict"):
    assertEquals(py(ExprToPython.translate(ConstructorF("Foo", Nil, None), ctx)), "{}")

  test("Constructor with Python-reserved field names is fine — keys are quoted strings"):
    val e = ConstructorF("Foo", List(FieldAssignFull("class", i(1), None)), None)
    assertEquals(py(ExprToPython.translate(e, ctx)), "{\"class\": 1}")

  test("With → spread base then update keys"):
    val e = WithF(
      PreF(id("store"), None),
      List(
        FieldAssignFull("status", s("PLACED"), None),
        FieldAssignFull("count", i(0), None)
      ),
      None
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "{**(pre_state[\"store\"]), \"status\": \"PLACED\", \"count\": 0}"
    )

  test("With with Python-reserved field names is fine — keys are quoted strings"):
    val e = WithF(i(0), List(FieldAssignFull("class", i(1), None)), None)
    assertEquals(py(ExprToPython.translate(e, ctx)), "{**(0), \"class\": 1}")

  test("SetComprehension over a Map[K, V] state field iterates values"):
    val ctxWithMap = ctx.copy(
      stateFields = ctx.stateFields + "lookup",
      mapStateFields = Set("lookup")
    )
    val e = SetComprehensionF("m", id("lookup"), b(true), None)
    assertEquals(
      py(ExprToPython.translate(e, ctxWithMap)),
      "{m for m in (post_state[\"lookup\"]).values() if (True)}"
    )

  test(
    "SetComprehension over a relation-typed state field iterates keys (Quantifier convention)"
  ):
    val ctxWithRel = ctx.copy(
      stateFields = ctx.stateFields + "one_rel",
      mapStateFields = Set.empty
    )
    val e = SetComprehensionF(
      "x",
      id("one_rel"),
      BinaryOpF(BGt(), id("x"), i(0), None),
      None
    )
    assertEquals(
      py(ExprToPython.translate(e, ctxWithRel)),
      "{x for x in (post_state[\"one_rel\"]) if (((x) > (0)))}"
    )

  test("SetComprehension over a non-map iterates directly"):
    val ctxWithSet = ctx.copy(stateFields = ctx.stateFields + "items", mapStateFields = Set.empty)
    val e = SetComprehensionF(
      "x",
      id("items"),
      BinaryOpF(BGt(), id("x"), i(0), None),
      None
    )
    assertEquals(
      py(ExprToPython.translate(e, ctxWithSet)),
      "{x for x in (post_state[\"items\"]) if (((x) > (0)))}"
    )

  test("SeqLiteral → Python list literal"):
    assertEquals(py(ExprToPython.translate(SeqLiteralF(Nil, None), ctx)), "[]")
    val nonEmpty = SeqLiteralF(List(i(1), i(2), i(3)), None)
    assertEquals(py(ExprToPython.translate(nonEmpty, ctx)), "[1, 2, 3]")

  test("Matches → re.fullmatch is not None"):
    val e = MatchesF(id("x"), "[a-z]+", None)
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "(re.fullmatch(\"[a-z]+\", x) is not None)"
    )

  test("The(v, dom, body) → next((v for v in dom if body), None)"):
    val e = TheF(
      "i",
      id("store"),
      BinaryOpF(BGt(), id("i"), i(0), None),
      None
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "next((i for i in (post_state[\"store\"]) if (((i) > (0)))), None)"
    )

  test("Lambda → Python lambda"):
    val e = LambdaF(
      "y",
      BinaryOpF(BAdd(), id("y"), i(1), None),
      None
    )
    assertEquals(py(ExprToPython.translate(e, ctx)), "(lambda y: (((y) + (1))))")

  test("Lambda with reserved param name skips"):
    val e = LambdaF("class", id("class"), None)
    assert(reason(ExprToPython.translate(e, ctx)).contains("Python-reserved"))

  test("SomeWrap is identity"):
    assertEquals(py(ExprToPython.translate(SomeWrapF(i(42), None), ctx)), "42")

  test("safe_counter Decrement requires: count > 0"):
    val e   = BinaryOpF(BGt(), id("count"), i(0), None)
    val pre = ctx.withCapture(CaptureMode.PreState)
    assertEquals(py(ExprToPython.translate(e, pre)), "((pre_state[\"count\"]) > (0))")

  test("safe_counter Increment ensures: count' = count + 1"):
    val e = BinaryOpF(
      BEq(),
      PrimeF(id("count"), None),
      BinaryOpF(BAdd(), id("count"), i(1), None),
      None
    )
    val pre = ctx.withCapture(CaptureMode.PreState)
    assertEquals(
      py(ExprToPython.translate(e, pre)),
      "((post_state[\"count\"]) == (((pre_state[\"count\"]) + (1))))"
    )

  test("url_shortener Shorten ensures: code not in pre(store)"):
    val e = BinaryOpF(BNotIn(), id("code"), PreF(id("store"), None), None)
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "((response_data[\"code\"]) not in (pre_state[\"store\"]))"
    )

  test("url_shortener invariant: all c in store | isValidURI(store[c])"):
    val q = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", id("store"), BkIn(), None)),
      CallF(
        id("isValidURI"),
        List(IndexF(id("store"), id("c"), None)),
        None
      ),
      None
    )
    assertEquals(
      py(ExprToPython.translate(q, ctx)),
      "all(is_valid_uri(post_state[\"store\"][c]) for c in (post_state[\"store\"]))"
    )

  test("url_shortener Shorten: store' = pre(store) + {code -> url} → dict-spread"):
    val e = BinaryOpF(
      BEq(),
      PrimeF(id("store"), None),
      BinaryOpF(
        BAdd(),
        PreF(id("store"), None),
        MapLiteralF(List(MapEntryFull(id("code"), id("url"), None)), None),
        None
      ),
      None
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "((post_state[\"store\"]) == ({**(pre_state[\"store\"]), **({response_data[\"code\"]: url})}))"
    )

  test("pyString escapes quotes, backslash, newline"):
    assertEquals(ExprToPython.pyString("a\"b\\c\n"), "\"a\\\"b\\\\c\\n\"")

  test("Python-reserved input names are skipped (would otherwise emit invalid Python)"):
    val ctxKw = ctx.copy(inputs = ctx.inputs ++ Set("class", "lambda"))
    val r1    = ExprToPython.translate(id("class"), ctxKw)
    assert(r1.isInstanceOf[Translated.Skip], s"got $r1")
    val r2 = ExprToPython.translate(id("lambda"), ctxKw)
    assert(r2.isInstanceOf[Translated.Skip], s"got $r2")

  test("Let with Python-reserved binding name is skipped"):
    val e = LetF("class", i(1), id("class"), None)
    val r = ExprToPython.translate(e, ctx)
    r match
      case Translated.Skip(reason, _) => assert(reason.contains("Python-reserved"))
      case other                      => fail(s"expected Skip, got $other")
