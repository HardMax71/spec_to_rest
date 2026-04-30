package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.BinOp
import specrest.ir.BindingKind
import specrest.ir.Expr
import specrest.ir.QuantKind
import specrest.ir.QuantifierBinding
import specrest.ir.UnOp

class ExprToPythonTest extends CatsEffectSuite:

  private val uriParam = specrest.ir.ParamDecl("s", specrest.ir.TypeExpr.NamedType("String"), None)
  private val uriBody  = Expr.Matches(Expr.Identifier("s"), "^https?:..[^\\s]+", None)
  private val emailParam =
    specrest.ir.ParamDecl("s", specrest.ir.TypeExpr.NamedType("String"), None)
  private val emailBody = Expr.Matches(Expr.Identifier("s"), "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", None)

  private val preamblePredicates = Map(
    "isValidURI"   -> specrest.ir.PredicateDecl("isValidURI", List(uriParam), uriBody, None),
    "isValidEmail" -> specrest.ir.PredicateDecl("isValidEmail", List(emailParam), emailBody, None)
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

  private def py(e: ExprPy): String = e match
    case ExprPy.Py(t)      => t
    case ExprPy.Skip(r, _) => fail(s"expected Py, got Skip($r)")

  private def reason(e: ExprPy): String = e match
    case ExprPy.Skip(r, _) => r
    case ExprPy.Py(t)      => fail(s"expected Skip, got Py($t)")

  test("literals translate directly"):
    assertEquals(py(ExprToPython.translate(Expr.IntLit(42), ctx)), "42")
    assertEquals(py(ExprToPython.translate(Expr.BoolLit(true), ctx)), "True")
    assertEquals(py(ExprToPython.translate(Expr.BoolLit(false), ctx)), "False")
    assertEquals(py(ExprToPython.translate(Expr.StringLit("hi"), ctx)), "\"hi\"")
    assertEquals(py(ExprToPython.translate(Expr.NoneLit(None), ctx)), "None")

  test("identifier resolution: bound > output > input > state"):
    val withBound = ctx.withBound(List("c"))
    assertEquals(py(ExprToPython.translate(Expr.Identifier("c"), withBound)), "c")
    assertEquals(
      py(ExprToPython.translate(Expr.Identifier("code"), ctx)),
      "response_data[\"code\"]"
    )
    assertEquals(py(ExprToPython.translate(Expr.Identifier("x"), ctx)), "x")
    assertEquals(
      py(ExprToPython.translate(Expr.Identifier("count"), ctx)),
      "post_state[\"count\"]"
    )
    assertEquals(
      py(ExprToPython.translate(Expr.Identifier("count"), ctx.withCapture(CaptureMode.PreState))),
      "pre_state[\"count\"]"
    )

  test("unknown identifier skips with reason"):
    val r = reason(ExprToPython.translate(Expr.Identifier("nope"), ctx))
    assert(r.contains("nope"), s"expected reason to mention 'nope', got: $r")

  test("Pre/Prime flip capture mode for state reads"):
    val pre = ExprToPython.translate(Expr.Pre(Expr.Identifier("count")), ctx)
    assertEquals(py(pre), "pre_state[\"count\"]")
    val post = ExprToPython.translate(Expr.Prime(Expr.Identifier("count")), ctx)
    assertEquals(py(post), "post_state[\"count\"]")

  test("Pre does not affect output identifiers"):
    val e = Expr.Pre(Expr.Identifier("code"))
    assertEquals(py(ExprToPython.translate(e, ctx)), "response_data[\"code\"]")

  test("BinaryOp: arithmetic + comparison + logical + membership"):
    val cases = List(
      BinOp.Add       -> "((1) + (2))",
      BinOp.Sub       -> "((1) - (2))",
      BinOp.Mul       -> "((1) * (2))",
      BinOp.Div       -> "((1) / (2))",
      BinOp.Eq        -> "((1) == (2))",
      BinOp.Neq       -> "((1) != (2))",
      BinOp.Lt        -> "((1) < (2))",
      BinOp.Le        -> "((1) <= (2))",
      BinOp.Gt        -> "((1) > (2))",
      BinOp.Ge        -> "((1) >= (2))",
      BinOp.And       -> "((1) and (2))",
      BinOp.Or        -> "((1) or (2))",
      BinOp.Implies   -> "((not (1)) or (2))",
      BinOp.Iff       -> "((1) == (2))",
      BinOp.In        -> "((1) in (2))",
      BinOp.NotIn     -> "((1) not in (2))",
      BinOp.Union     -> "((1) | (2))",
      BinOp.Intersect -> "((1) & (2))",
      BinOp.Diff      -> "((1) - (2))",
      BinOp.Subset    -> "((1) <= (2))"
    )
    cases.foreach: (op, expected) =>
      val e = Expr.BinaryOp(op, Expr.IntLit(1), Expr.IntLit(2))
      assertEquals(py(ExprToPython.translate(e, ctx)), expected, s"op=$op")

  test("UnaryOp: not / negate / cardinality / power"):
    val notE = Expr.UnaryOp(UnOp.Not, Expr.BoolLit(true))
    assertEquals(py(ExprToPython.translate(notE, ctx)), "(not (True))")
    val negE = Expr.UnaryOp(UnOp.Negate, Expr.IntLit(5))
    assertEquals(py(ExprToPython.translate(negE, ctx)), "(-(5))")
    val cardE = Expr.UnaryOp(UnOp.Cardinality, Expr.Identifier("store"))
    assertEquals(py(ExprToPython.translate(cardE, ctx)), "len(post_state[\"store\"])")
    val powE = Expr.UnaryOp(UnOp.Power, Expr.Identifier("store"))
    assertEquals(py(ExprToPython.translate(powE, ctx)), "_powerset(post_state[\"store\"])")

  test("FieldAccess on output → response_data['outer']['field']"):
    val e = Expr.FieldAccess(Expr.Identifier("code"), "value")
    assertEquals(py(ExprToPython.translate(e, ctx)), "response_data[\"code\"][\"value\"]")

  test("Index: store[code] uses output for key, state for base"):
    val e = Expr.Index(Expr.Identifier("store"), Expr.Identifier("code"))
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "post_state[\"store\"][response_data[\"code\"]]"
    )

  test("EnumAccess emits the member as a string literal"):
    val e = Expr.EnumAccess(Expr.Identifier("Status"), "todo")
    assertEquals(py(ExprToPython.translate(e, ctx)), "\"todo\"")

  test("Recognized calls: max, min, now, days, sum"):
    val maxE = Expr.Call(Expr.Identifier("max"), List(Expr.Identifier("store")))
    assertEquals(py(ExprToPython.translate(maxE, ctx)), "max(post_state[\"store\"])")
    val minE = Expr.Call(Expr.Identifier("min"), List(Expr.Identifier("store")))
    assertEquals(py(ExprToPython.translate(minE, ctx)), "min(post_state[\"store\"])")
    val nowE = Expr.Call(Expr.Identifier("now"), Nil)
    assertEquals(
      py(ExprToPython.translate(nowE, ctx)),
      "datetime.datetime.now(datetime.timezone.utc).isoformat()"
    )
    val daysE = Expr.Call(Expr.Identifier("days"), List(Expr.IntLit(30)))
    assertEquals(
      py(ExprToPython.translate(daysE, ctx)),
      "datetime.timedelta(days=30).total_seconds()"
    )
    val sumE = Expr.Call(
      Expr.Identifier("sum"),
      List(
        Expr.Identifier("store"),
        Expr.Lambda("i", Expr.FieldAccess(Expr.Identifier("i"), "line_total"))
      )
    )
    assertEquals(
      py(ExprToPython.translate(sumE, ctx)),
      "sum((i[\"line_total\"]) for i in (post_state[\"store\"]))"
    )

  test("User-defined function call resolves via TestCtx.userFunctions"):
    val fn = specrest.ir.FunctionDecl(
      name = "isPositive",
      params = List(specrest.ir.ParamDecl("n", specrest.ir.TypeExpr.NamedType("Int"))),
      returnType = specrest.ir.TypeExpr.NamedType("Bool"),
      body = Expr.BinaryOp(BinOp.Gt, Expr.Identifier("n"), Expr.IntLit(0))
    )
    val ctxWithFn = ctx.copy(userFunctions = Map("isPositive" -> fn))
    val callE     = Expr.Call(Expr.Identifier("isPositive"), List(Expr.IntLit(5)))
    assertEquals(py(ExprToPython.translate(callE, ctxWithFn)), "is_positive(5)")

  test("Unknown function (not built-in, not user-declared) skips"):
    val callE = Expr.Call(Expr.Identifier("hash"), List(Expr.Identifier("x")))
    assert(reason(ExprToPython.translate(callE, ctx)).contains("hash/1"))

  test("User-defined call with wrong arity skips with arity-mismatch reason"):
    val fn = specrest.ir.FunctionDecl(
      name = "isPositive",
      params = List(specrest.ir.ParamDecl("n", specrest.ir.TypeExpr.NamedType("Int"))),
      returnType = specrest.ir.TypeExpr.NamedType("Bool"),
      body = Expr.BinaryOp(BinOp.Gt, Expr.Identifier("n"), Expr.IntLit(0))
    )
    val ctxWithFn = ctx.copy(userFunctions = Map("isPositive" -> fn))
    val callE     = Expr.Call(Expr.Identifier("isPositive"), List(Expr.IntLit(5), Expr.IntLit(6)))
    val r         = reason(ExprToPython.translate(callE, ctxWithFn))
    assert(r.contains("wrong arity"), s"expected arity-mismatch reason, got: $r")
    assert(r.contains("expected 1, got 2"))

  test("Indirect call (non-identifier callee) translates by applying callee to args"):
    val callE = Expr.Call(
      Expr.Lambda("y", Expr.BinaryOp(BinOp.Add, Expr.Identifier("y"), Expr.IntLit(1))),
      List(Expr.IntLit(7))
    )
    assertEquals(
      py(ExprToPython.translate(callE, ctx)),
      "((lambda y: (((y) + (1)))))(7)"
    )

  test("Recognized calls: len, dom, ran, isValidURI, isValidEmail"):
    val lenE = Expr.Call(Expr.Identifier("len"), List(Expr.Identifier("store")))
    assertEquals(py(ExprToPython.translate(lenE, ctx)), "len(post_state[\"store\"])")
    val domE = Expr.Call(Expr.Identifier("dom"), List(Expr.Identifier("store")))
    assertEquals(py(ExprToPython.translate(domE, ctx)), "set(post_state[\"store\"].keys())")
    val ranE = Expr.Call(Expr.Identifier("ran"), List(Expr.Identifier("store")))
    assertEquals(py(ExprToPython.translate(ranE, ctx)), "set(post_state[\"store\"].values())")
    val uriE = Expr.Call(Expr.Identifier("isValidURI"), List(Expr.Identifier("url")))
    assertEquals(py(ExprToPython.translate(uriE, ctx)), "is_valid_uri(url)")
    val emailE = Expr.Call(Expr.Identifier("isValidEmail"), List(Expr.Identifier("x")))
    assertEquals(py(ExprToPython.translate(emailE, ctx)), "is_valid_email(x)")

  test("Bare enum-member identifier resolves to its string literal"):
    val ctxWithEnum = ctx.copy(enumValues = Map("Status" -> Set("TODO", "DONE")))
    assertEquals(
      py(ExprToPython.translate(Expr.Identifier("TODO"), ctxWithEnum)),
      "\"TODO\""
    )
    assertEquals(
      py(ExprToPython.translate(Expr.Identifier("DONE"), ctxWithEnum)),
      "\"DONE\""
    )

  test("Unknown function call skips"):
    val e = Expr.Call(Expr.Identifier("custom_predicate"), List(Expr.IntLit(1)))
    assert(reason(ExprToPython.translate(e, ctx)).contains("custom_predicate"))

  test("Skip propagates through binary ops (unbound identifier)"):
    val e = Expr.BinaryOp(BinOp.Eq, Expr.Identifier("nope"), Expr.IntLit(0))
    assert(reason(ExprToPython.translate(e, ctx)).contains("nope"))

  test("If / Let translate to Python ternary / lambda"):
    val ifE = Expr.If(Expr.BoolLit(true), Expr.IntLit(1), Expr.IntLit(2))
    assertEquals(py(ExprToPython.translate(ifE, ctx)), "((1) if (True) else (2))")
    val letE = Expr.Let("v", Expr.IntLit(7), Expr.Identifier("v"))
    assertEquals(py(ExprToPython.translate(letE, ctx)), "((lambda v=(7): (v))())")

  test("Empty SetLiteral → set(); non-empty → {…}"):
    val empty = Expr.SetLiteral(Nil)
    assertEquals(py(ExprToPython.translate(empty, ctx)), "set()")
    val nonEmpty = Expr.SetLiteral(List(Expr.IntLit(1), Expr.IntLit(2)))
    assertEquals(py(ExprToPython.translate(nonEmpty, ctx)), "{1, 2}")

  test("Quantifier forall over state"):
    val body = Expr.Call(
      Expr.Identifier("isValidURI"),
      List(Expr.Index(Expr.Identifier("store"), Expr.Identifier("c")))
    )
    val q = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("c", Expr.Identifier("store"), BindingKind.In)),
      body
    )
    assertEquals(
      py(ExprToPython.translate(q, ctx)),
      "all(is_valid_uri(post_state[\"store\"][c]) for c in (post_state[\"store\"]))"
    )

  test("Quantifier exists / no map to any / not any"):
    val body = Expr.BinaryOp(BinOp.Gt, Expr.Identifier("c"), Expr.IntLit(0))
    val exists = Expr.Quantifier(
      QuantKind.Exists,
      List(QuantifierBinding("c", Expr.Identifier("store"), BindingKind.In)),
      body
    )
    assert(py(ExprToPython.translate(exists, ctx)).startsWith("any("))
    val no = Expr.Quantifier(
      QuantKind.No,
      List(QuantifierBinding("c", Expr.Identifier("store"), BindingKind.In)),
      body
    )
    assert(py(ExprToPython.translate(no, ctx)).startsWith("(not any("))

  test("Quantifier with Colon binding skips"):
    val q = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("op", Expr.Identifier("Login"), BindingKind.Colon)),
      Expr.BoolLit(true)
    )
    assert(reason(ExprToPython.translate(q, ctx)).contains("non-`in`"))

  test("MapLiteral: empty → {}, non-empty → {k: v}"):
    assertEquals(py(ExprToPython.translate(Expr.MapLiteral(Nil), ctx)), "{}")
    val nonEmpty = Expr.MapLiteral(
      List(
        specrest.ir.MapEntry(Expr.Identifier("code"), Expr.Identifier("url")),
        specrest.ir.MapEntry(Expr.IntLit(1), Expr.StringLit("a"))
      )
    )
    assertEquals(
      py(ExprToPython.translate(nonEmpty, ctx)),
      "{response_data[\"code\"]: url, 1: \"a\"}"
    )

  test("BinaryOp.Add over MapLiteral merges as dict-spread"):
    val e = Expr.BinaryOp(
      BinOp.Add,
      Expr.Pre(Expr.Identifier("store")),
      Expr.MapLiteral(
        List(specrest.ir.MapEntry(Expr.Identifier("code"), Expr.Identifier("url")))
      )
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "{**(pre_state[\"store\"]), **({response_data[\"code\"]: url})}"
    )

  test("Constructor → Python dict literal with snake-case-preserving field keys"):
    val e = Expr.Constructor(
      "LineItem",
      List(
        specrest.ir.FieldAssign("product_sku", Expr.Identifier("x")),
        specrest.ir.FieldAssign("quantity", Expr.IntLit(3))
      )
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "{\"product_sku\": x, \"quantity\": 3}"
    )

  test("Constructor with no fields → empty dict"):
    assertEquals(py(ExprToPython.translate(Expr.Constructor("Foo", Nil), ctx)), "{}")

  test("Constructor with Python-reserved field names is fine — keys are quoted strings"):
    val e = Expr.Constructor("Foo", List(specrest.ir.FieldAssign("class", Expr.IntLit(1))))
    assertEquals(py(ExprToPython.translate(e, ctx)), "{\"class\": 1}")

  test("With → spread base then update keys"):
    val e = Expr.With(
      Expr.Pre(Expr.Identifier("store")),
      List(
        specrest.ir.FieldAssign("status", Expr.StringLit("PLACED")),
        specrest.ir.FieldAssign("count", Expr.IntLit(0))
      )
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "{**(pre_state[\"store\"]), \"status\": \"PLACED\", \"count\": 0}"
    )

  test("With with Python-reserved field names is fine — keys are quoted strings"):
    val e = Expr.With(
      Expr.IntLit(0),
      List(specrest.ir.FieldAssign("class", Expr.IntLit(1)))
    )
    assertEquals(py(ExprToPython.translate(e, ctx)), "{**(0), \"class\": 1}")

  test("SetComprehension over a Map[K, V] state field iterates values"):
    val ctxWithMap = ctx.copy(
      stateFields = ctx.stateFields + "lookup",
      mapStateFields = Set("lookup")
    )
    val e = Expr.SetComprehension("m", Expr.Identifier("lookup"), Expr.BoolLit(true))
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
    val e = Expr.SetComprehension(
      "x",
      Expr.Identifier("one_rel"),
      Expr.BinaryOp(BinOp.Gt, Expr.Identifier("x"), Expr.IntLit(0))
    )
    assertEquals(
      py(ExprToPython.translate(e, ctxWithRel)),
      "{x for x in (post_state[\"one_rel\"]) if (((x) > (0)))}"
    )

  test("SetComprehension over a non-map iterates directly"):
    val ctxWithSet = ctx.copy(stateFields = ctx.stateFields + "items", mapStateFields = Set.empty)
    val e = Expr.SetComprehension(
      "x",
      Expr.Identifier("items"),
      Expr.BinaryOp(BinOp.Gt, Expr.Identifier("x"), Expr.IntLit(0))
    )
    assertEquals(
      py(ExprToPython.translate(e, ctxWithSet)),
      "{x for x in (post_state[\"items\"]) if (((x) > (0)))}"
    )

  test("SeqLiteral → Python list literal"):
    assertEquals(py(ExprToPython.translate(Expr.SeqLiteral(Nil), ctx)), "[]")
    val nonEmpty = Expr.SeqLiteral(List(Expr.IntLit(1), Expr.IntLit(2), Expr.IntLit(3)))
    assertEquals(py(ExprToPython.translate(nonEmpty, ctx)), "[1, 2, 3]")

  test("Matches → re.fullmatch is not None"):
    val e = Expr.Matches(Expr.Identifier("x"), "[a-z]+")
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "(re.fullmatch(\"[a-z]+\", x) is not None)"
    )

  test("The(v, dom, body) → next((v for v in dom if body), None)"):
    val e = Expr.The(
      "i",
      Expr.Identifier("store"),
      Expr.BinaryOp(BinOp.Gt, Expr.Identifier("i"), Expr.IntLit(0))
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "next((i for i in (post_state[\"store\"]) if (((i) > (0)))), None)"
    )

  test("Lambda → Python lambda"):
    val e = Expr.Lambda(
      "y",
      Expr.BinaryOp(BinOp.Add, Expr.Identifier("y"), Expr.IntLit(1))
    )
    assertEquals(py(ExprToPython.translate(e, ctx)), "(lambda y: (((y) + (1))))")

  test("Lambda with reserved param name skips"):
    val e = Expr.Lambda("class", Expr.Identifier("class"))
    assert(reason(ExprToPython.translate(e, ctx)).contains("Python-reserved"))

  test("SomeWrap is identity"):
    assertEquals(py(ExprToPython.translate(Expr.SomeWrap(Expr.IntLit(42)), ctx)), "42")

  test("safe_counter Decrement requires: count > 0"):
    val e   = Expr.BinaryOp(BinOp.Gt, Expr.Identifier("count"), Expr.IntLit(0))
    val pre = ctx.withCapture(CaptureMode.PreState)
    assertEquals(py(ExprToPython.translate(e, pre)), "((pre_state[\"count\"]) > (0))")

  test("safe_counter Increment ensures: count' = count + 1"):
    val e = Expr.BinaryOp(
      BinOp.Eq,
      Expr.Prime(Expr.Identifier("count")),
      Expr.BinaryOp(BinOp.Add, Expr.Identifier("count"), Expr.IntLit(1))
    )
    val pre = ctx.withCapture(CaptureMode.PreState)
    assertEquals(
      py(ExprToPython.translate(e, pre)),
      "((post_state[\"count\"]) == (((pre_state[\"count\"]) + (1))))"
    )

  test("url_shortener Shorten ensures: code not in pre(store)"):
    val e = Expr.BinaryOp(
      BinOp.NotIn,
      Expr.Identifier("code"),
      Expr.Pre(Expr.Identifier("store"))
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "((response_data[\"code\"]) not in (pre_state[\"store\"]))"
    )

  test("url_shortener invariant: all c in store | isValidURI(store[c])"):
    val q = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("c", Expr.Identifier("store"), BindingKind.In)),
      Expr.Call(
        Expr.Identifier("isValidURI"),
        List(Expr.Index(Expr.Identifier("store"), Expr.Identifier("c")))
      )
    )
    assertEquals(
      py(ExprToPython.translate(q, ctx)),
      "all(is_valid_uri(post_state[\"store\"][c]) for c in (post_state[\"store\"]))"
    )

  test("url_shortener Shorten: store' = pre(store) + {code -> url} → dict-spread"):
    val e = Expr.BinaryOp(
      BinOp.Eq,
      Expr.Prime(Expr.Identifier("store")),
      Expr.BinaryOp(
        BinOp.Add,
        Expr.Pre(Expr.Identifier("store")),
        Expr.MapLiteral(
          List(specrest.ir.MapEntry(Expr.Identifier("code"), Expr.Identifier("url")))
        )
      )
    )
    assertEquals(
      py(ExprToPython.translate(e, ctx)),
      "((post_state[\"store\"]) == ({**(pre_state[\"store\"]), **({response_data[\"code\"]: url})}))"
    )

  test("pyString escapes quotes, backslash, newline"):
    assertEquals(ExprToPython.pyString("a\"b\\c\n"), "\"a\\\"b\\\\c\\n\"")

  test("Python-reserved input names are skipped (would otherwise emit invalid Python)"):
    val ctxKw = ctx.copy(inputs = ctx.inputs ++ Set("class", "lambda"))
    val r1    = ExprToPython.translate(Expr.Identifier("class"), ctxKw)
    assert(r1.isInstanceOf[ExprPy.Skip], s"got $r1")
    val r2 = ExprToPython.translate(Expr.Identifier("lambda"), ctxKw)
    assert(r2.isInstanceOf[ExprPy.Skip], s"got $r2")

  test("Let with Python-reserved binding name is skipped"):
    val e = Expr.Let("class", Expr.IntLit(1), Expr.Identifier("class"))
    val r = ExprToPython.translate(e, ctx)
    r match
      case ExprPy.Skip(reason, _) => assert(reason.contains("Python-reserved"))
      case other                  => fail(s"expected Skip, got $other")
