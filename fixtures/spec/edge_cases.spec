service EdgeCases {

  // --- Type with regex ---
  type Email = String where value matches /^[^@]+@[^@]+\.[^@]+$/

  // --- Entity with extends ---
  entity Base {
    id: Int where value > 0
    created_at: DateTime
  }

  entity Child extends Base {
    name: String
    score: Float where value >= 0.0 and value <= 100.0
  }

  // --- Enum with trailing comma ---
  enum Color {
    RED,
    GREEN,
    BLUE,
  }

  // --- State with all multiplicity types ---
  state {
    one_rel: Int -> one String
    lone_rel: Int -> lone String
    some_rel: Int -> some String
    set_rel: Int -> set String
    plain: Int
    map_state: Map[String, Int]
    seq_state: Seq[String]
    opt_state: Option[Bool]
  }

  // --- Operation with no input ---
  operation NoInput {
    output: count: Int

    requires:
      true

    ensures:
      count = #one_rel
  }

  // --- Operation with no output ---
  operation NoOutput {
    input: key: Int

    requires:
      key > 0

    ensures:
      plain' = key
  }

  // --- Primed variable edge cases ---
  operation PrimedVars {
    input: x: Int
    output: y: Int

    requires:
      x > 0

    ensures:
      plain' = x
      one_rel' = pre(one_rel) + {x -> "hello"}
      y = pre(plain) + 1
  }

  // --- Lambda expressions ---
  operation Lambdas {
    input: items: Set[Int]
    output: total: Int

    requires:
      #items > 0

    ensures:
      total = sum(items, i => i * 2)
  }

  // --- Nested quantifiers ---
  invariant nestedQuantifiers:
    all x in one_rel |
      all y in one_rel |
        x != y implies one_rel[x] != one_rel[y]

  // --- If-then-else expression ---
  function clamp(val: Int, lo: Int, hi: Int): Int =
    if val < lo then lo else if val > hi then hi else val

  // --- Predicate ---
  predicate isPositive(n: Int) = n > 0

  // --- Let expression in invariant ---
  invariant letExample:
    let total = #one_rel in
      total >= 0 and total <= 1000

  // --- Constructor in ensures ---
  operation WithConstructor {
    input: name: String
    output: item: Base

    requires:
      len(name) > 0

    ensures:
      item = Base { id = 1, created_at = now() }
  }

  // --- Set comprehension ---
  operation Comprehension {
    output: positives: Set[Int]

    requires:
      true

    ensures:
      positives = { x in one_rel | x > 0 }
  }

  // --- Map literal ---
  operation MapLit {
    output: mapping: Map[String, Int]

    requires:
      true

    ensures:
      mapping = { "a" -> 1, "b" -> 2 }
  }

  // --- Sequence literal ---
  operation SeqLit {
    output: items: Seq[Int]

    requires:
      true

    ensures:
      items = [1, 2, 3]
  }

  // --- Empty collection literals ---
  operation EmptyLiterals {
    output: s: Set[Int], sq: Seq[Int]

    requires:
      true

    ensures:
      s = {}
      sq = []
  }

  // --- The expression ---
  operation TheExpr {
    input: id: Int
    output: found: String

    requires:
      id in one_rel

    ensures:
      found = the x in one_rel | one_rel[x] = "target"
  }

  // --- Some as Option wrapper ---
  operation SomeWrap {
    input: val: Int
    output: result: Option[Int]

    requires:
      true

    ensures:
      result = some(val + 1)
  }

  // --- Complex chained postfix ---
  invariant chainedPostfix:
    all x in one_rel |
      pre(one_rel)[x].length >= 0

  // --- Iff operator ---
  invariant iffTest:
    all x in one_rel |
      x > 0 iff one_rel[x] != "negative"

  // --- Convention block ---
  conventions {
    NoInput.http_method = "GET"
    NoInput.http_path = "/count"
    NoOutput.http_method = "PUT"
    NoOutput.http_path = "/plain/{key}"
  }

  // --- Import (would go at top normally, testing grammar accepts it) ---

  // --- Fact ---
  fact someFact:
    #one_rel >= 0

  // --- Transition ---
  transition ColorCycle {
    entity: Child
    field: name

    RED -> GREEN via NoInput
    GREEN -> BLUE via NoOutput when plain > 0
  }
}
