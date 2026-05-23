package specrest.convention

object Builtins:

  // One spec per spec-language builtin function. Each backend's expression
  // translator dispatches via `Builtins.byName` instead of carrying its own
  // case-list, so adding a builtin is a single edit here (was 4+ files: the
  // three ExprBackends plus the lint allowlist plus runtime-template imports).
  //
  // The emit fields are intentionally per-backend lambdas: emitting
  // `hashlib.sha256(...)` vs `_sha256Hex(...)` vs `crypto/sha256.Sum256(...)`
  // is irreducible per-language work, but bundling those three together makes
  // the symmetry visible and makes it impossible to forget one.
  //
  // Higher-order builtins (currently just `sum`, which takes a lambda) are NOT
  // here — they keep their per-backend special-case because the lambda
  // unpacking pattern needs to access ctx.boundVars, which would require
  // threading a ctx-aware closure type through the registry.
  final case class BuiltinSpec(
      name: String,
      arity: Int,
      py: List[String] => String,
      ts: List[String] => String,
      go: List[String] => String,
      pyImports: List[String] = Nil,
      tsImports: List[BuiltinSpec.TsImport] = Nil,
      goImports: List[String] = Nil,
      description: String
  )

  object BuiltinSpec:
    // ("node:crypto", "createHash") → `import { createHash } from "node:crypto"`.
    final case class TsImport(module: String, symbol: String) derives CanEqual

  // --- set/map operations ---

  val Len: BuiltinSpec = BuiltinSpec(
    name = "len",
    arity = 1,
    py = a => s"len(${a(0)})",
    ts = a => s"_len(${a(0)})",
    go = a => s"_len(${a(0)})",
    description = "cardinality of a collection"
  )

  val Dom: BuiltinSpec = BuiltinSpec(
    name = "dom",
    arity = 1,
    py = a => s"set(${a(0)}.keys())",
    ts = a => s"new Set(Object.keys(${a(0)}))",
    go = a => s"_keys(${a(0)})",
    description = "key-set of a map"
  )

  val Ran: BuiltinSpec = BuiltinSpec(
    name = "ran",
    arity = 1,
    py = a => s"set(${a(0)}.values())",
    ts = a => s"new Set(Object.values(${a(0)}))",
    go = a => s"_values(${a(0)})",
    description = "value-set of a map"
  )

  val Max: BuiltinSpec = BuiltinSpec(
    name = "max",
    arity = 1,
    py = a => s"max(${a(0)})",
    ts = a => s"Math.max(...Array.from(${a(0)}))",
    go = a => s"_max(${a(0)})",
    description = "maximum of a non-empty collection"
  )

  val Min: BuiltinSpec = BuiltinSpec(
    name = "min",
    arity = 1,
    py = a => s"min(${a(0)})",
    ts = a => s"Math.min(...Array.from(${a(0)}))",
    go = a => s"_min(${a(0)})",
    description = "minimum of a non-empty collection"
  )

  // --- time ---

  // `now()` returns epoch seconds (float/number) so `now() - minutes(15)` is
  // well-typed numeric arithmetic across all backends. Comparing the result
  // against an ISO-string DateTime field is a separate concern (type-aware
  // field-access lowering — not solved here, deferred).
  val Now: BuiltinSpec = BuiltinSpec(
    name = "now",
    arity = 0,
    py = _ => "datetime.datetime.now(datetime.timezone.utc).timestamp()",
    ts = _ => "(Date.now() / 1000)",
    go = _ => "_now()",
    pyImports = List("datetime"),
    description = "current UTC time as epoch seconds (numeric, not ISO string)"
  )

  val Days: BuiltinSpec = BuiltinSpec(
    name = "days",
    arity = 1,
    py = a => s"datetime.timedelta(days=${a(0)}).total_seconds()",
    ts = a => s"((${a(0)}) * 86400)",
    go = a => s"_mul(${a(0)}, int64(86400))",
    pyImports = List("datetime"),
    description = "n days expressed as epoch seconds"
  )

  val Hours: BuiltinSpec = BuiltinSpec(
    name = "hours",
    arity = 1,
    py = a => s"datetime.timedelta(hours=${a(0)}).total_seconds()",
    ts = a => s"((${a(0)}) * 3600)",
    go = a => s"_mul(${a(0)}, int64(3600))",
    pyImports = List("datetime"),
    description = "n hours expressed as epoch seconds"
  )

  val Minutes: BuiltinSpec = BuiltinSpec(
    name = "minutes",
    arity = 1,
    py = a => s"datetime.timedelta(minutes=${a(0)}).total_seconds()",
    ts = a => s"((${a(0)}) * 60)",
    go = a => s"_mul(${a(0)}, int64(60))",
    pyImports = List("datetime"),
    description = "n minutes expressed as epoch seconds"
  )

  val Seconds: BuiltinSpec = BuiltinSpec(
    name = "seconds",
    arity = 1,
    py = a => s"datetime.timedelta(seconds=${a(0)}).total_seconds()",
    ts = a => s"(${a(0)})",
    go = a => s"_mul(${a(0)}, int64(1))",
    pyImports = List("datetime"),
    description = "n seconds expressed as epoch seconds"
  )

  // --- hashing ---

  // SHA-256 hex digest. Argument is coerced to string in every backend (Python
  // `str(...)`, Go `fmt.Sprintf("%v", ...)`, TS `String(...)`) so non-string
  // inputs (numbers from JSON, None/null) don't blow up at runtime.
  val Hash: BuiltinSpec = BuiltinSpec(
    name = "hash",
    arity = 1,
    py = a => s"hashlib.sha256(str(${a(0)}).encode()).hexdigest()",
    ts = a => s"_sha256Hex(${a(0)})",
    go = a => s"_sha256Hex(${a(0)})",
    pyImports = List("hashlib"),
    tsImports = List(BuiltinSpec.TsImport("node:crypto", "createHash")),
    goImports = List("crypto/sha256", "encoding/hex"),
    description = "SHA-256 hex digest of the (coerced-to-string) argument"
  )

  // Absolute value. All three backends accept the arg pre-rendered; Go uses the
  // `_abs` runtime helper to handle the `any` payload uniformly.
  val Abs: BuiltinSpec = BuiltinSpec(
    name = "abs",
    arity = 1,
    py = a => s"abs(${a(0)})",
    ts = a => s"Math.abs(Number(${a(0)}))",
    go = a => s"_abs(${a(0)})",
    description = "absolute value of a numeric expression"
  )

  // Higher-order: second arg is a function/lambda applied per element. Both args
  // are pre-rendered; we never introduce a new binding name so a spec like
  // `let _x = V in sum(coll, i => i + _x)` doesn't see its free `_x` shadowed
  // by an iteration variable. The lambda is passed AS A VALUE to a function
  // that consumes a sequence:
  //   Python: `sum(map(lambda, coll))` — map iterates, no inner binding
  //   TS: `coll.map(lambda).reduce(...)` — lambda runs before reduce's callback
  //                                         introduces its own `v`
  //   Go: `_sum(coll, lambda)` — runtime helper invokes per element
  val Sum: BuiltinSpec = BuiltinSpec(
    name = "sum",
    arity = 2,
    py = a => s"sum(map(${a(1)}, ${a(0)}))",
    ts = a => s"Array.from(${a(0)}).map(${a(1)}).reduce((acc, v) => acc + Number(v), 0)",
    go = a => s"_sum(${a(0)}, ${a(1)})",
    description = "sum of `fn(elem)` over a collection"
  )

  // The canonical list. Order is documentational; lookups go through `byName`.
  val all: List[BuiltinSpec] =
    List(Len, Dom, Ran, Max, Min, Abs, Now, Days, Hours, Minutes, Seconds, Hash, Sum)

  val byName: Map[String, BuiltinSpec] = all.map(b => b.name -> b).toMap

  val names: Set[String] = byName.keySet
