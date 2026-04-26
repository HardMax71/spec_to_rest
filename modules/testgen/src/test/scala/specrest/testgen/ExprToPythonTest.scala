package specrest.testgen

import munit.FunSuite
import specrest.ir.BinOp
import specrest.ir.BindingKind
import specrest.ir.Expr
import specrest.ir.QuantKind
import specrest.ir.QuantifierBinding
import specrest.ir.UnOp

class ExprToPythonTest extends FunSuite:

  private val ctx = TestCtx(
    inputs = Set("x", "url"),
    outputs = Set("code", "short_url"),
    stateFields = Set("count", "store", "metadata", "base_url"),
    enumValues = Map("Status" -> Set("todo", "done")),
    knownPredicates = TestCtx.DefaultPredicates,
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

  test("UnaryOp: not / negate / cardinality; power skips"):
    val notE = Expr.UnaryOp(UnOp.Not, Expr.BoolLit(true))
    assertEquals(py(ExprToPython.translate(notE, ctx)), "(not (True))")
    val negE = Expr.UnaryOp(UnOp.Negate, Expr.IntLit(5))
    assertEquals(py(ExprToPython.translate(negE, ctx)), "(-(5))")
    val cardE = Expr.UnaryOp(UnOp.Cardinality, Expr.Identifier("store"))
    assertEquals(py(ExprToPython.translate(cardE, ctx)), "len(post_state[\"store\"])")
    val powE = Expr.UnaryOp(UnOp.Power, Expr.IntLit(2))
    assert(reason(ExprToPython.translate(powE, ctx)).contains("Power"))

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

  test("Recognized calls: len, dom, ran, isValidURI, valid_email"):
    val lenE = Expr.Call(Expr.Identifier("len"), List(Expr.Identifier("store")))
    assertEquals(py(ExprToPython.translate(lenE, ctx)), "len(post_state[\"store\"])")
    val domE = Expr.Call(Expr.Identifier("dom"), List(Expr.Identifier("store")))
    assertEquals(py(ExprToPython.translate(domE, ctx)), "set(post_state[\"store\"].keys())")
    val ranE = Expr.Call(Expr.Identifier("ran"), List(Expr.Identifier("store")))
    assertEquals(py(ExprToPython.translate(ranE, ctx)), "set(post_state[\"store\"].values())")
    val uriE = Expr.Call(Expr.Identifier("isValidURI"), List(Expr.Identifier("url")))
    assertEquals(py(ExprToPython.translate(uriE, ctx)), "is_valid_uri(url)")
    val emailE = Expr.Call(Expr.Identifier("valid_email"), List(Expr.Identifier("x")))
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

  test("Skip propagates through binary ops"):
    val e = Expr.BinaryOp(BinOp.Eq, Expr.MapLiteral(Nil), Expr.IntLit(0))
    assertEquals(reason(ExprToPython.translate(e, ctx)), "MapLiteral")

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

  test("Skip cases: MapLiteral, Constructor, With, SetComprehension, SeqLiteral, Matches"):
    val cases: List[(Expr, String)] = List(
      Expr.MapLiteral(Nil)           -> "MapLiteral",
      Expr.Constructor("Foo", Nil)   -> "Constructor",
      Expr.With(Expr.IntLit(0), Nil) -> "With",
      Expr.SetComprehension(
        "x",
        Expr.Identifier("store"),
        Expr.BoolLit(true)
      )                                            -> "SetComprehension",
      Expr.SeqLiteral(Nil)                         -> "SeqLiteral",
      Expr.Matches(Expr.Identifier("x"), "[a-z]+") -> "Matches",
      Expr.Lambda("y", Expr.Identifier("y"))       -> "Lambda",
      Expr.SomeWrap(Expr.IntLit(1))                -> "SomeWrap"
    )
    cases.foreach: (e, sub) =>
      val r = reason(ExprToPython.translate(e, ctx))
      assert(r.contains(sub), s"expected '$sub' in skip reason, got: $r")

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

  test("url_shortener Shorten: store' = pre(store) + {code -> url} skips on MapLiteral"):
    val e = Expr.BinaryOp(
      BinOp.Eq,
      Expr.Prime(Expr.Identifier("store")),
      Expr.BinaryOp(
        BinOp.Add,
        Expr.Pre(Expr.Identifier("store")),
        Expr.MapLiteral(Nil)
      )
    )
    assertEquals(reason(ExprToPython.translate(e, ctx)), "MapLiteral")

  test("pyString escapes quotes, backslash, newline"):
    assertEquals(ExprToPython.pyString("a\"b\\c\n"), "\"a\\\"b\\\\c\\n\"")
