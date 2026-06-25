# Isabelle SpecRest — Speedup Log

This file documents an end-to-end pass to speed up the build of `proofs/isabelle/SpecRest`. It is a
working journal: every attempted change is logged with its measured impact and a verdict
(**Completed / Failed / Dropped / Deferred**). Keep adding to it.

## Baseline (clean rebuild, local, 4 vCPU, Isabelle2025-2)

| Theory          | Cum. time |
| --------------- | --------- |
| `IR.thy`        | 57.5 s    |
| `Smt.thy`       | 40.7 s    |
| `Semantics.thy` | 40.1 s    |
| `Soundness.thy` | 10.5 s    |
| `Codegen.thy`   | 3.7 s     |
| `Translate.thy` | 1.1 s     |
| `SpecRest.thy`  | 0.2 s     |

**Total: 2 m 48 s wall, 5 m 05 s CPU, 20.0 s GC, parallelism factor 1.93.**

Empirical sweep (clean rebuilds, same box):

| Config                                                     | Wall                  | GC     |
| ---------------------------------------------------------- | --------------------- | ------ |
| Baseline (`threads=4`, default heap)                       | **2:48**              | 20.0 s |
| `threads=2 parallel_proofs=2`                              | 2:59 (worse)          | 24.3 s |
| `threads=8 parallel_proofs=2`                              | aborted (sqlite race) | —      |
| `ML_OPTIONS="--minheap 2000 --maxheap 6000 --gcthreads 4"` | 2:46                  | 19.8 s |

**Conclusion (overturns the obvious guesses):** more cores and bigger heap each buy ~0 s. The
dominant time is **single-threaded work inside the `fun` and `datatype` packages** — see the build
log line `command "fun" running for 28.843s (line 144 of theory "SpecRest.Semantics")`. ~94 of the
168 elapsed seconds (56 %) are inside three sequential package commands. Knobs that don't attack
that work don't move the needle.

## Hot spots (with line refs)

- `IR.thy:41` — `datatype expr` with 24 constructors. BNF derives ~24 injectivity + ~276
  distinctness + recursor + induction + case + `set_/map_/rel_` BNF combinators per default plugin
  set.
- `Semantics.thy:144` — `fun eval` mutually recursive with two `forall_*` helpers, 24+ clauses.
  Triggers `lexicographic_order` which is **NP-complete with space exponential in #mutual
  functions**
  ([Bulwahn-Krauss-Nipkow, FroCoS 2007](https://www21.in.tum.de/~krauss/papers/lexicographic-orders.pdf)).
- `Smt.thy:163` — `fun smt_eval`, structurally isomorphic to `eval`.
- `Soundness.thy` — 1271 lines, 107 lemmas, but only 10.5 s. Repetition is real but is a
  **maintenance** problem, not a build-time one.

## Plan

Tiers are ordered by safety. Each tier is independently revertible.

### Tier 1 — Pure CI/build config (no proof risk)

1. ML heap bump in workflow (`--minheap 2000 --maxheap 6000 --gcthreads 4`).
2. `process_policy="taskset --cpu-list 0-3"` to pin the ML process.
3. `isabelle build -S` pre-flight gate (skip work when no `.thy` changed).
4. Two-tier heap cache (HOL base + project parent), seL4 pattern.
5. Session split into 4 sessions so `-j` overlaps `Sem` and `Smt`.

### Tier 2 — Datatype/`fun` config (low proof risk, must verify)

6. `datatype (plugins only: code size)` on every datatype in `IR.thy` / `Semantics.thy` / `Smt.thy`.
7. `primrec` audit: convert recursive list helpers (`contains_*`, `dedupe_*`) from `fun` to
   `primrec`.
8. Hand-written termination on `fun eval` and `fun smt_eval`.

### Tier 3 — Code surgery (mechanical, build verifies)

9. Delete dead lemmas (`Soundness.thy:235-378` and `615-661`).

### Tier 4 — Deferred (need human judgment + AFP deps)

These are documented in §"Dropped/Deferred" below — they are the right end-state design but not safe
to auto-apply.

## In progress

(_none_ — see Dropped/Deferred for items not yet pursued)

## End-state metrics (this PR)

| Metric                | Baseline     | This PR         | Δ                                |
| --------------------- | ------------ | --------------- | -------------------------------- |
| Wall time             | 168 s (2:48) | **66 s (1:06)** | **−102 s, −61 %**                |
| CPU time              | 305 s        | 184 s           | −121 s                           |
| GC time               | 20.0 s       | 18.3 s          | −1.7 s                           |
| Parallelism factor    | 1.93         | 2.79            | +0.86                            |
| Soundness.thy LOC     | 1271         | 1127            | −144                             |
| Generated Scala drift | n/a          | OK              | regenerated for `primrec` rename |

## Completed

### Tier 1.1-1.4 — Workflow tuning (2026-05-13)

`.github/workflows/isabelle-build.yml`:

- Added `ML_OPTIONS="--minheap 2000 --maxheap 6000 --gcthreads 4"` env var. Sources:
  [Poly/ML Heap Parameters](http://www.lfcs.inf.ed.ac.uk/software/polyml/docs/HeapParms9.html),
  [Isabelle System Manual §1.1.1](https://isabelle.in.tum.de/doc/system.pdf). Local impact: -2 s
  (within noise); harmless under heavier loads.
- Added `-o process_policy="taskset --cpu-list 0-3"` to the build invocation to pin the ML process
  and reduce GH-Actions scheduler thrash.
- Added `isabelle build -S` pre-flight gate that exits 0 when sources are unchanged. Source:
  `isabelle build -h` lists `-S` as soft build.
- Refined heap cache to two-tier: primary key on `hashFiles(ROOT, *.thy)`, `restore-keys` falls back
  to `IR.thy`-only hash, then version-only. Save split out via `actions/cache/save` and gated to
  `push` on `main` only, following [seL4 ci-actions pattern](https://github.com/seL4/ci-actions).

### Tier 2.1 — BNF plugin pruning (2026-05-13) — **additional −22 s**

Added `(plugins only: code size)` to every `datatype` declaration in `IR.thy`, `Semantics.thy`,
`Smt.thy` (33 declarations total). Skips `quickcheck`, `nitpick`, `transfer`, `lifting` plugin
derivations — none of which are used by the proofs (verified via grep for `lift_definition`,
`transfer_rule`, `quickcheck`, `nitpick`, `set_/map_/rel_<datatype>`).

Source: [Datatypes manual §"Selecting Plugins"](https://isabelle.in.tum.de/doc/datatypes.pdf).

| Metric             | Before (post-dep-break) | After plugin prune | Δ                                 |
| ------------------ | ----------------------- | ------------------ | --------------------------------- |
| Wall time          | 126 s                   | **104 s (1:44)**   | −22 s                             |
| CPU time           | 319 s                   | 239 s              | −80 s                             |
| GC time            | 21.5 s                  | 17.7 s             | −3.8 s                            |
| Parallelism factor | 2.66                    | 2.31               | −0.35 (because total CPU dropped) |

**Verified Scala extraction is byte-identical** by running the CI drift check locally:

```text
isabelle export -d proofs/isabelle/SpecRest -O ... -x 'SpecRest_Codegen.Codegen:code/*' SpecRest_Codegen
diff -u modules/ir/.../SpecRestGenerated.scala $exported  →  DRIFT OK
```

### Tier 2.2 — `primrec` audit (2026-05-13) — **negligible (in noise)**

Converted six list-recursive helpers from `fun` to `primrec`:

- `Semantics.thy:contains_value`, `dedupe_values`
- `Smt.thy:contains_smt_val`, `dedupe_smt_vals`
- `IR.thy:string_in_list`

`primrec` skips `lexicographic_order` and `pat_completeness` and goes straight through the BNF
recursor, generating fewer auxiliary facts. Source:
[Krauss, _functions.pdf_ §"primrec"](https://isabelle.in.tum.de/doc/functions.pdf);
[isabelle-users mailing list, "primrec or fun"](https://lists.cam.ac.uk/pipermail/cl-isabelle-users/2016-May/msg00038.html).
Folklore says ~2-5× faster per definition; for these tiny helpers the absolute savings are
sub-second — kept anyway because the simpler form is easier to reason about and improves intent
clarity.

### Tier 3.1 (partial) — Delete unused soundness lemmas (2026-05-13) — **−7 lines/sec**

Deleted `Soundness.thy:235-378` — twelve `soundness_*_bin` / `soundness_*_int` lemmas that **were
not referenced anywhere** (verified by grep across the entire `proofs/` and `modules/` trees). The
top-level `soundness` theorem dispatches via the `*_step` lemma family (`Soundness.thy:848-1196`),
not these. Saves ~144 source lines and a few seconds of `apply (cases …) simp_all` work.

### Tier 2.3 — Hand-written termination on `eval` / `smt_eval` (2026-05-13) — **additional −28 s**

Replaced `fun` with
`function (sequential) … by pat_completeness auto termination by (relation "measures […]") auto` for
the two mutually-recursive 24-clause definitions. The auto `lexicographic_order` is **NP-complete
with space exponential in the number of mutually recursive functions**
([Bulwahn-Krauss-Nipkow, FroCoS 2007](https://www21.in.tum.de/~krauss/papers/lexicographic-orders.pdf));
for a 3-fn / 24-clause cluster it spent ~25 s per cluster. The hand-written measure packs the
sum-type-encoded argument and decreases lexicographically:

```isabelle
termination
  by (relation "measures [
        (λp. case p of
               Inl (_, _, _, e) ⇒ size e
             | Inr (Inl (_, _, _, _, _, _, body)) ⇒ size body
             | Inr (Inr (_, _, _, _, _, body)) ⇒ size body),
        (λp. case p of
               Inl _ ⇒ 0
             | Inr (Inl (_, _, _, _, _, members, _)) ⇒ Suc (length members)
             | Inr (Inr (_, _, _, _, rel_dom, _)) ⇒ Suc (length rel_dom))
       ]")
     auto
```

Per-theory cumulative-time deltas:

| Theory          | Before (auto termination) | After (manual termination) | Δ     |
| --------------- | ------------------------- | -------------------------- | ----- |
| `Semantics.thy` | 44.7 s                    | **15.2 s**                 | −29 s |
| `Smt.thy`       | 38.6 s                    | **19.4 s**                 | −19 s |

(They run in parallel after the dep break, so wall savings = −10 s on Semantics' part of the
parallel max + −18 s on Smt's part = ~−28 s on the combined critical path.)

| Wall  | Before | After           | Δ     |
| ----- | ------ | --------------- | ----- |
| Total | 105 s  | **77 s (1:17)** | −28 s |

Drift-check still byte-identical. Source:
[Krauss, _functions.pdf_ §"Termination"](https://isabelle.in.tum.de/doc/functions.pdf).

### Tier 2.6 — Shallow `fun` patterns on wide datatypes (2026-05-25) — **additional −145 s on a single `fun`**

After the middle-end lift series landed `Classify.thy` (PR #319), cold rebuild jumped from 2:09 to
4:23. The per-command timing trace (extracted from the session DB by the `isabelle-build.yml`
workflow — see "Profiling tool" below) fingered a single command:

```text
152.53s  fun             Classify.thy:131  isCardinalityRhs
```

55 % of the entire build, sitting on one 5-equation `fun` over `expr_full` (~50 constructors). The
equations used deeply-nested constructor patterns:

```isabelle
| "isCardinalityRhs (UnaryOpF UCardinality (PreF (IdentifierF m _) _) _) n = (m = n)"
| "isCardinalityRhs (BinaryOpF BAdd inner (IntLitF _ _) _) n = isCardinalityRhs inner n"
```

The `fun` package's pattern-completeness proof must verify the catch-all `_` covers every other
constructor combination at every nesting level. For a 50-ctor datatype with 4-deep nesting, that's a
combinatorial explosion in the proof obligation.

**Fix:** split into three `fun`s, each pattern-matching **only on the top-level constructor** of the
wide datatype:

```isabelle
fun innerIsTargetCard :: "expr_full ⇒ String.literal ⇒ bool" where
  "innerIsTargetCard (PreF (IdentifierF m _) _) n = (m = n)"
| "innerIsTargetCard (IdentifierF m _) n = (m = n)"
| "innerIsTargetCard _ _ = False"

fun isIntLit :: "expr_full ⇒ bool" where
  "isIntLit (IntLitF _ _) = True"
| "isIntLit _ = False"

fun isCardinalityRhs :: "expr_full ⇒ String.literal ⇒ bool" where
  "isCardinalityRhs (UnaryOpF op inner _) n =
     ((case op of UCardinality ⇒ True | _ ⇒ False) ∧ innerIsTargetCard inner n)"
| "isCardinalityRhs (BinaryOpF op inner rhs _) n =
     ((case op of BAdd ⇒ True | BSub ⇒ True | _ ⇒ False)
      ∧ isIntLit rhs ∧ isCardinalityRhs inner n)"
| "isCardinalityRhs _ _ = False"
```

Each helper's completeness check is O(constructors), not O(constructors^depth). The behavior is
identical; semantically it's the same recogniser.

| Metric             | Before (nested) | After (shallow)  | Δ                 |
| ------------------ | --------------- | ---------------- | ----------------- |
| `isCardinalityRhs` | 152.53 s        | ~2.5 s (sum)     | **−150 s**        |
| Total wall (cold)  | 263 s (4:23)    | **129 s (2:09)** | **−134 s, −51 %** |
| Total CPU          | 591 s           | 413 s            | −178 s            |

Drift-check still byte-identical.

**Generalises to:** any `fun` whose patterns nest constructors of a wide datatype more than 1-2
levels deep. When you see a slow `fun` on `expr_full` / `smt_term` / similar, flatten the patterns
first. Profiling beats guessing — always extract the trace.

**Profiling tool (reusable for any new bottleneck):**

```bash
# After a clean build, pull per-command timings from the session DB:
DB="$HOME/.isabelle/Isabelle2025-2/heaps/polyml-*/log/SpecRest.db"
sqlite3 "$DB" "SELECT writefile('/tmp/cmd.blob', command_timings) FROM isabelle_session_info LIMIT 1"
zstd -dq /tmp/cmd.blob -o /tmp/cmd.yxml
# YXML format: \x05 separates entries, \x06 separates k=v fields.
# Look for entries with both 'elapsed' and 'file' to get per-command timing.
# Full parser script in .github/workflows/isabelle-build.yml "Extract per-command timing trace".
```

The CI job uploads the parsed report as an `isabelle-timing-trace` artifact on every run; download
it from a failed perf-regression PR to see which command spiked without rebuilding locally.

### Tier 2.4 — Hand-written termination on `lower` (2026-05-13) — **additional −11 s**

After Tier 2.3, IR.thy became the new bottleneck at 52.7 s. The build log fingered
`command "fun" running for 21.687s (line 319 of theory "SpecRest.IR")`: the `lower` /
`lower_set_list` / `lower_with_assigns` mutually-recursive cluster that lowers `expr_full` to the
verified-subset `expr`.

Same pattern as Tier 2.3 — `function (sequential)` + explicit `measures`. Measure on the sum-typed
argument:

```isabelle
termination
  by (relation "measures [
        (λp. case p of
               Inl (_, e) ⇒ size e
             | Inr (Inl (_, elems, _)) ⇒ size_list size elems
             | Inr (Inr (_, updates, _, _)) ⇒ size_list size updates),
        (λp. case p of
               Inl _ ⇒ 0
             | Inr (Inl (_, elems, _)) ⇒ Suc (length elems)
             | Inr (Inr (_, updates, _, _)) ⇒ Suc (length updates))
       ]")
     auto
```

`size_list size` is the BNF-derived combinator for summing per-element sizes; available because
`(plugins only: code size)` keeps the size plugin.

| Theory   | Before | After      | Δ       |
| -------- | ------ | ---------- | ------- |
| `IR.thy` | 52.7 s | **40.2 s** | −12.5 s |

| Wall  | Before | After           | Δ     |
| ----- | ------ | --------------- | ----- |
| Total | 77 s   | **66 s (1:06)** | −11 s |

Drift-check still byte-identical.

### Tier 1.5 — Break Smt→Sem dependency (2026-05-13) — **−42 s**

The original `Smt.thy` imported `Semantics` only because it referenced the `state_mode` datatype.
With `state_mode` moved to `IR.thy`, `Smt` and `Semantics` become **independent siblings** in the
theory DAG. With the existing `threads = 4` session option, Isabelle's intra-session theory
parallelism overlaps the two ~40 s `fun` definitions.

| Metric             | Baseline     | After dep break  | Δ                         |
| ------------------ | ------------ | ---------------- | ------------------------- |
| Wall time          | 168 s (2:48) | **126 s (2:06)** | **−42 s, −25 %**          |
| CPU time           | 305 s        | 319 s            | +14 s (more cores active) |
| GC time            | 20.0 s       | 21.5 s           | +1.5 s                    |
| Parallelism factor | 1.93         | **2.66**         | +0.73                     |

The build log now interleaves Sem and Smt `fun`-elaboration messages, confirming overlap.

Files touched:

- `proofs/isabelle/SpecRest/IR.thy` — added `datatype state_mode = SmPre | SmPost`.
- `proofs/isabelle/SpecRest/Semantics.thy` — removed the duplicate `state_mode` declaration.
- `proofs/isabelle/SpecRest/Smt.thy` — `imports Semantics` → `imports IR`.
- `proofs/isabelle/SpecRest/Soundness.thy` — `imports Translate` → `imports Translate Semantics`
  (Soundness uses `eval` directly).
- `proofs/isabelle/SpecRest/Codegen.thy` — added `Semantics` to imports (it exports `eval` to
  Scala).

### Tier 2.7 - Shallow `case` bodies for widening-triggered `fun` blowups (2026-06-02)

The Z3-subset lift series (`If`/`Option`/`String`/`Seq`) widened `ir_value` from 7 to 11
constructors. That re-detonated Tier 2.6 in two _pre-existing_ helper `fun`s that were cheap at 7
constructors: `eval_arith` and `eval_cmp` (`Semantics.thy`). Both match a two-operand cross-product
of nested patterns (`eval_arith AddOp (Some (VInt a)) (Some (VInt b)) = ...`); the `fun` package
compiles that into a decision tree whose `pat_completeness` obligation is combinatorial in the
datatype width. The session-DB trace fingered them:

```text
439.82s  fun  Semantics.thy  eval_arith
206.35s  fun  Semantics.thy  eval_cmp
```

That is 646 s, 82 % of the 783 s Core build, on two non-recursive funs. Profiling note: the
session-DB `command_timings` offsets are in Isabelle _symbols_ (`\<Rightarrow>` is one symbol but 11
bytes), so a byte-slicing line mapper under-reports the line number by ~15. Count symbols (`\<...>`
as one) to land on the right command.

**Fix:** single-equation `fun` with a nested single-scrutinee `case` body, and the operator dispatch
pulled into four tiny shallow helpers (`int_arith`/`real_arith`/`int_cmp`/`real_cmp`). The fun's own
`pat_completeness` becomes trivial (one variable pattern); the `case` translation does the value
dispatch cheaply, the same mechanism as the `fieldNameIfStateIndex` style already used elsewhere.
The seven dependent lemmas move off the now-trivial `eval_arith.induct`/`eval_cmp.induct` to
`split: option.splits ir_value.splits ...` (plus `cases op` where a helper-result shape is needed).
Two pre-existing funs with the identical disease got the same flattening: `keyExistencePair`
(`IR_Analysis.thy`) and `resolveWithBase` (`IR_Helpers.thy`).

| Command                 | Before  | After  |
| ----------------------- | ------- | ------ |
| `eval_arith`            | 439.8 s | <0.1 s |
| `eval_cmp`              | 206.4 s | <0.1 s |
| `keyExistencePair`      | 37.6 s  | <0.1 s |
| `resolveWithBase`       | 27.0 s  | <0.1 s |
| Core (wall)             | 13:03   | 1:31   |
| Full build (cached HOL) | ~14:40  | 2:45   |

The four helpers are new top-level functions and the four rewritten bodies are restructured, so
`SpecRestGenerated.scala` was regenerated. Verified behaviour-preserving: zero `sorry`,
`sbt ir/compile` clean, 106 testgen behavioural / stateful / structural tests green.

**Generalises Tier 2.6:** widening a datatype can silently re-detonate any _existing_ `fun` that
matches its constructors more than one or two levels deep, especially a two-operand cross-product.
After adding constructors, re-profile and move the match into a `case` body.

### Tier 2.8 - Flatten the relation-reference recognisers (post-lift re-detonation) (2026-06-03)

The Z3-subset lift series (#378-#381: `If`/`Option`/`String`/`Seq`+`Map`) widened `smt_term` (33 to
41 ctors) and `expr`/`expr_full`. That re-detonated **four sibling relation-reference recognisers**
built on `Pre (Ident ...)` / `Prime (Ident ...)`-style **nested constructor patterns** (the Tier 2.6
disease, in functions that pre-date the lift). A local clean rebuild plus a symbol-aware session-DB
trace fingered them:

| `fun`                 | theory             | before |
| --------------------- | ------------------ | ------ |
| `peelSmtRelationRef`  | `Smt.thy`          | 27.0 s |
| `rel_ref_shape`       | `Preservation.thy` | 11.2 s |
| `peel_relation_ref`   | `IR.thy`           | 11.1 s |
| `peelRelationRefFull` | `Semantics.thy`    | 8.8 s  |

~58 s across four non-recursive recognisers, all from `<Ctor> (<InnerCtor> ...)` nesting whose
`pat_completeness` obligation is combinatorial in datatype width (41-ctor `smt_term`, ~50-ctor
`expr_full`).

**Fix:** the Tier 2.6 flatten. Match only the top-level constructor; delegate the inner
`TVar`/`Ident` check to a flat one-level helper (`relRefVarName` / `identName` / `identNameFull`).
Each completeness check becomes O(ctors), not O(ctors^2).

Downstream proof updates (the reason Tier 2.5 deferred the SMT one when it was only a 10 s saving;
the calculus changed once the widenings pushed the cluster to 58 s):

- `peelSmtRelationRef`'s eight `TPre_*`/`TPrime_*` `[simp]` lemmas were re-keyed to the helper as
  four `relRefVarName_translate_*` lemmas. The new `peelSmtRelationRef (TPre t) = relRefVarName t`
  rule fires first, rewriting the goal past the old `peelSmtRelationRef (TPre (translate ...))`
  shape before the old lemmas could match.
- `peelSmtRelationRef_translate` (inducts on `peel_relation_ref.induct`) needed the bridge
  `relRefVarName (translate b) = identName b`, since after flattening the induct case is `Pre b`
  with `b` abstract, not `Pre (Ident ...)`.
- The `.cases`-based proofs (`rel_ref_lower`, `peelRelationRefFull_some_imp_rel_ref_shape`,
  `peelRelationRefFull_lower`) take `(auto dest!: identNameFull_SomeD)` with the inversion
  `identNameFull e = Some r ==> (EX sp. e = IdentifierF r sp)`. It must be forced (`dest!`): `auto`
  rewrites `~= None` into `= Some y` before a `~= None`-keyed elim could fire.

| Metric                | Before | After     | Δ                |
| --------------------- | ------ | --------- | ---------------- |
| `peelSmtRelationRef`  | 27.0 s | <0.2 s    | -27 s            |
| `rel_ref_shape`       | 11.2 s | <0.1 s    | -11 s            |
| `peel_relation_ref`   | 11.1 s | <0.1 s    | -11 s            |
| `peelRelationRefFull` | 8.8 s  | <0.1 s    | -9 s             |
| Core (wall)           | 124 s  | **101 s** | -23 s            |
| Soundness (wall)      | 28 s   | **21 s**  | -7 s             |
| Full build (cold)     | 212 s  | **182 s** | **-30 s, -14 %** |

`peel_relation_ref` and its new `identName` helper are reachable from the `export_code` roots, so
`SpecRestGenerated.scala` was regenerated (the old extracted Scala literally enumerated the
`TPre(BLit) ... TPre(TMapCons)` cross-product, which was the cost). `rel_ref_shape`/`wf_z3` live in
the Soundness session and are not extracted. Verified: zero `sorry`, drift-clean, `sbt ir/compile`
clean.

**Profiling-visibility gap (fixed).** `isabelle-build.yml` previously extracted only
`SpecRest_Codegen.db`, so the critical-path `SpecRest_Core` and `SpecRest_Soundness` sessions never
appeared in the CI `isabelle-timing-trace` artifact - all four bottlenecks above were invisible
there - and its byte-slicing line mapper under-reported lines by ~15 (the `command_timings` offset
counts Isabelle _symbols_, where `\<dots>` is one symbol, not its byte length). The timing-extract
step now calls `proofs/isabelle/timing_trace.py`, which auto-discovers every session declared in
`ROOT` (no hardcoded session names), maps offsets symbol-accurately, and emits an OVERALL top-N
merged across all sessions plus per-session detail. Run it locally with:

```bash
python3 proofs/isabelle/timing_trace.py \
  "$(isabelle getenv -b ISABELLE_HOME_USER)/heaps" proofs/isabelle/SpecRest /tmp/trace.txt
```

### CI `-j 2` + the 89s preservation proof + theory splits (2026-06-06)

Post-`eval_full` (#392) pass: the `isabelle-build` workflow had crept to ~15 min wall and several
proof files exceeded 500 lines.

**1. `-j 2` + collapse the redundant preflight/build double-step.** The `isabelle build -S -b`
preflight step was doing the _entire_ build, while the separately-tuned "Build SpecRest session"
step (carrying `-o process_policy=...`) then ran as a 7 s no-op. Merged into one source-change-gated
step that carries the tuned flags AND `-j 2`. `SpecRest_Soundness` and `SpecRest_Codegen` are
independent siblings over `SpecRest_Core` (see "Multi-session ROOT split"); at the default `-j 1`
they built sequentially. Also fixed a latent bug: the "Save Isabelle heaps" gate still referenced
the deleted `steps.preflight` id, which would have disabled heap caching on `main` (every run a cold
rebuild).

| Phase (local, Core cached) | `-j 1` (sequential) | `-j 2` |
| -------------------------- | ------------------- | ------ |
| Soundness + Codegen        | ~5:34               | 3:13   |

Source: [system.pdf](https://isabelle.in.tum.de/doc/system.pdf), `-j`.

**2. The 89 s preservation proof** (`lower_binop_some`, `Preservation.thy`) - the single biggest
command in the whole build. `by (cases r) (auto split: option.splits)` over `r :: expr_full` (27
ctors) generated 27 subgoals, each re-deriving `lower`'s 20-way `case op`. Fix: split the BIn/BNotIn
disjunction on `op` _before_ `cases r`, so `op` is concrete and `auto` stops re-deriving the op
dispatch per constructor. Same proof, semantically.

| Command                    | Before  | After  |
| -------------------------- | ------- | ------ |
| `lower_binop_some` `by`    | 88.91 s | <0.7 s |
| Soundness command-time sum | 165 s   | 85 s   |

**3. Theory splits (maintainability, build-neutral).** `eval_full` (the proof-only reference
semantics, not extracted) moved out of `Semantics.thy` (2031 -> 1385) into a new
`core/Semantics_Reference.thy` leaf (651) imported by nothing - so reference-semantics edits no
longer invalidate the `Codegen`/`Soundness` heaps. `Preservation.thy` (1657) split 4 ways along its
phase comments: `Preservation_Wf` (120), `Preservation_Lower` (557), `Preservation_WellTyped` (153),
`Preservation` (844, the irreducible `h3_preservation` umbrella - one theorem). Linear import chain,
so no build-parallelism gain; pure maintainability. Drift-clean (no extracted function changed).

### Soundness session DAG split + binop step-lemma extraction (2026-06-11)

Post-#398 pass on the Soundness session shape. `DirectSound.thy` (1,699 lines) was one theory, and
the session graph chained everything behind `Preservation_Wf`'s 29 s `wf_z3` definition - even
though the 1,273-line soundness induction never references `wf_z3` (first use is the totality
section), and `DirectPreservation`'s only cross-need is `wf_z3_imp_tfd_some`.

**1. Theory split into three independent branches:**

- `DirectSound_Smt` (339) -> `DirectSound_Desugar` (181) -> `DirectSound` (792): the soundness
  chain, no `Preservation_Wf` import at all.
- `Preservation_Wf` -> `DirectTotality` (435): the progress half, parallel to the soundness chain.
- `Preservation_Wf` -> `Preservation_WellTyped` -> `DirectPreservation`: the typing branch, now
  importing `DirectTotality` instead of the soundness monolith.

`identName_SomeD` (nee `identNameFull_SomeD`) moved from `Preservation_Wf` to `core/Semantics.thy`
next to `identName` so both branches reach it.

| Metric             | Before | After A | After A+B |
| ------------------ | ------ | ------- | --------- |
| Soundness wall     | 1:35   | 1:24    | 1:31      |
| Parallelism factor | 2.38   | 2.67    | 2.36      |

Wall delta is within run noise; the structural wins are (a) the three branches elaborate
concurrently in batch builds, (b) in PIDE an induction edit re-checks only `DirectSound` itself -
the ~30 s of plumbing/desugar/totality stays green, (c) per-theory readability. Note batch
`isabelle build` still re-elaborates the whole session on any theory edit - the split does NOT make
the heap incremental.

**2. `binop_noncomp_step` extraction.** The 198-line `proof (cases bop)` block inside
`direct_soundness` case 6 (all 14 non-comprehension binops) is now a standalone toplevel lemma
taking the two IHs as \<And>-premises; the induction case dispatches with
`binop_noncomp_step[OF deN None IHl[OF deN None _ _ ...] ...]` - the `OF`-with-`_` partial
application converts the guarded induction IH to the lemma's 2-premise shape. The comprehension
branch (~50 lines) stays inline: its IHs need mid-proof-derived guard facts (`enr'`, `srd`, `etm`),
so a step-lemma signature would be larger than the body. Same verdict for the remaining cases (each
<= 56 readable lines): extraction boilerplate would exceed the win.

### Hotspot pass: flattens + RHS factoring of `translate` / `wf_z3` (2026-06-11)

Post-#391-collapse profiling pass (CI trace, run #280 baseline). Three classes of fix, one PR:

**1. Tier 2.6/2.7 re-detonations flattened** (the collapse made `expr` THE wide datatype):
`containsPreInPlusChain`, `referencesPrimedRelation`, `referencesPreRelation` (IR_Helpers),
`isLenOfValue`, `extractFieldName`, `matchesIdentityShape` (IR_Analysis), `innerIsTargetCard`
(Classify - the Tier 2.6 fix itself had re-detonated), `callSelfAllNames`, `callSelfFilteredNames`
(LintAnalysis), `translate_forall_step` (Translate), `eval_set_bin` (Semantics, two-operand
cross-product). Plus `TArith_sound`'s 4x12x12 case proof split into four per-op lemmas (parallel
toplevel commands) + trivial dispatcher.

**2. `translate` RHS factoring - the lever the earlier "Failed" entry left untried.** The diagnosis
there ("complex recursive RHS") resolves to a concrete mechanism: nested-pattern case bodies
(`case r of SetComprehensionF _ (IdentifierF _ _) p _ => ... | _ => <recursive calls>`) compile to
case trees whose DEFAULT branch is duplicated per constructor - ~58 copies of recursive-call-bearing
branches per comp dispatch, and the 20 binop branches each carried a 3x3 option-pair case. Fix:
hoist the dispatches into non-recursive helpers (`comp_parts`, `identName` - now in IR.thy next to
`expr` - and a `map2_opt` combinator), leaving the recursive equation with plain applications.
Termination via `comp_parts_size [termination_simp]` (`size p < size r`). Same factoring for `wf_z3`
via `comp_pred_or_self` (returns the comprehension predicate for ident-domain comps, the expression
itself otherwise - semantics identical because `wf_z3 (SetComprehensionF ...) = False`).

**3. Downstream re-keys:** `translate_BEq_noncomp`/`translate_BIn_noncomp` restated via
`map2_opt`/`identName` (consumers reduce through `map2_opt_eq_Some [simp]`); `wf_z3` consumers
bridge with `comp_pred_or_self_noncomp`. Three fix sites in DirectTotality.

| Command                  | Before         | After      |
| ------------------------ | -------------- | ---------- |
| `translate` (def + term) | 28.4 s + 8.3 s | 2.3 s      |
| `wf_z3`                  | 29.3 s         | < 2 s      |
| `export_code`            | 26.9 s         | 12.4 s     |
| Bucket-A recognisers     | ~48 s          | < 2 s each |

| Session command total | Baseline (run #280) | After       |
| --------------------- | ------------------- | ----------- |
| Core                  | 215.6 s             | 125.2 s     |
| Codegen               | 145.5 s             | 103.2 s     |
| Soundness             | 87.7 s              | 35.2 s      |
| **Grand total**       | **448.8 s**         | **263.6 s** |

Wall: Core 4:37 -> 1:31, Soundness ~1:30 -> 0:12, Codegen ~1:05 -> 0:38. The extracted `translate`
lost ~700 lines of enumerated cross-product patterns (regen +246/-945). Remaining top items are
irreducible: datatype BNF derivations, `export_code`, the mutual walkers.

## Failed

### wf_z3 / lower definition cost (mutual `fun`/`function` over `expr_full`) - the levers don't apply

`wf_z3` (`Preservation.thy`, 4-way mutual `fun`, ~20 s) and `lower` (`IR_Lower.thy`, 5-way mutual
`function`, ~37 s) appear in the per-command trace as the _definition_ command (not termination, not
completeness). Two levers tried on `wf_z3`, both failed:

- `fun` -> `function (sequential)` + hand termination: 20.6 s -> 17.8 s def + 2.0 s term = no
  change. Unlike `walkUndefinedExpr` (Tier 2.x), `lexicographic_order` was never the cost here - the
  recursion is trivially structural.
- Flatten the nested `SetComprehensionF _ (IdentifierF _ _) p _` case into single-level cases (Tier
  2.6 style): **worse**, 20.6 s -> 22.0 s (more case expressions to compile).

Diagnosis: the cost is the package compiling the _complex recursive RHS_ (the 15-branch `case op`
with many recursive call-sites), NOT mutual arity (`stripSpans` is **5-way** mutual over `expr_full`
and is <2.2 s) nor nested patterns. `lower` shares the disease and is _extracted_ (any change forces
a Scala regen). Left as-is: the gain is partly hidden by `-j 2` (Codegen overlaps Soundness's tail)
and the standard Tier 2.x levers do not apply. The one untried idea - factor the repeated
`(case (lower l, lower r) of (Some, Some) => ...)` into a helper - changes extracted Scala and is
speculative.

### Tier 3.1 (partial) — Delete `peel_smt_translate_*` `[simp]` lemmas

Tried deleting the 12 `peel_smt_translate_{BoolBin,Arith,Cmp,SetBin, TPrime_*,TPre_*}` `[simp]`
lemmas at `Soundness.thy:615-661` (originally flagged as "superseded by the inductive
`peel_smt_relation_ref_translate` at line 663"). Build broke with **12 unsolved subgoals** in the
proof of `peel_smt_relation_ref_translate` itself: its terminal `qed simp_all` was implicitly
relying on those `[simp]` lemmas to dispatch the BoolBin / Arith / Cmp / SetBin / TPrime*\* /
TPre*\* cases of the structural induction.

**Restored.** The lemmas are not literally unreferenced — they are load-bearing for the simpset
rewrite that closes the induction's non-trivial cases. The explorer-agent analysis that flagged them
as "superseded" missed the implicit `[simp]` reliance. Kept the safer half of the cleanup
(Soundness.thy:235-378 — the non-`[simp]` `soundness_*_bin` lemmas, see Completed §"Tier 3.1
(partial)").

### Tier 2.5 — Convert `peel_smt_relation_ref` from `fun` to `definition`

**Tried (twice) and reverted.** Diagnostic build (`-o build_progress_threshold=2`) revealed
`peel_smt_relation_ref` (Smt.thy:122) is consuming **~10 s** during compile — an outsized cost for a
4-clause non-recursive `fun` with wildcard match over the 30-constructor `smt_term`. The `fun`
package's pattern-completeness check on a wildcard-over-large-datatype is the bottleneck.

Tried two conversions:

1. `definition peel_smt_relation_ref ≡ case … of … | _ ⇒ None` plus three explicit `[simp]` lemmas
   for the named cases.
2. Same definition with `[simp]` declared on the def itself (so `case` evaluates implicitly).

Both broke 12 downstream `[simp]` lemmas (`peel_smt_translate_BoolBin`, …) and the
`peel_smt_relation_ref_translate` proof: those proofs apply `peel_smt_relation_ref` to **opaque**
terms like `translate (BoolBin op l r sp)`. With the original `fun`, the auto-generated
`peel_smt_relation_ref _ = None` simp rule fires for any non-`TVar`/`TPre TVar`/`TPrime TVar` shape
regardless of whether the scrutinee is a known constructor. With `definition + case`, simp only
reduces the case when the scrutinee is a known constructor — so `translate (SetBin vb vc vd ve)`
(where `vb` is symbolic) never reduces.

Fixing this would require rewriting all 12 `peel_smt_translate_*` lemmas and the induction proof to
first do `cases op` (forcing the constructor to be concrete) and then
`simp add: peel_smt_relation_ref_def`. ~30 lines of mechanical proof changes — worth doing if the 10
s win matters more than reviewer-friction. **Deferred** for now.

**Update — third attempt, dropped permanently.** Even after rewriting all 12 `peel_smt_translate_*`
lemmas to thread `cases op` and updating the induction proof to
`qed (simp_all add: peel_smt_relation_ref_def)`, the build still failed: the
`peel_relation_ref.induct` wildcard case generates a goal of the form
`(case translate base of TVar x ⇒ Some x | _ ⇒ None) = None` for a universally-quantified `base`.
With `translate base` opaque, the `case` cannot reduce; the original `fun`'s wildcard simp rule
fired on opaque scrutinees, but `definition + case` does not.

A real fix would need either ~22 explicit per-`smt_term`-constructor `[simp]` lemmas to compensate
for the missing wildcard rule, or restructuring `peel_smt_relation_ref_translate` to do an
additional inner `cases base` rather than relying on `peel_relation_ref.induct`'s wildcard branch.
Either is more invasive than the 10 s saving warrants for a perf PR. **Dropped permanently.**

The same pattern blocks `peel_relation_ref → definition` (~3 s, IR.thy:77). Note: this refactor
becomes natural when also doing the Lifting/Transfer or locale rework — it can ride along with that.

### Multi-session ROOT split — **implemented** (#358 follow-up)

An earlier attempt split `ROOT` into `SpecRest_IR` + `SpecRest` while keeping every `.thy` in one
directory, and Isabelle rejected it:

```text
*** Duplicate use of directory ".../proofs/isabelle/SpecRest"
***   for session "SpecRest_IR" vs. session "SpecRest"
```

The fix it identified — one session per directory — is in place. The base was later split again
along the IR/meaning seam (the datatype + analysis layer never imports the meaning layer), giving
four sessions, each `in` its own subdirectory: `SpecRest_IR` → `SpecRest_Semantics` → independent
siblings `SpecRest_Soundness` and `SpecRest_Codegen`.

- `core/` → `SpecRest_IR` (IR datatypes + IR_Helpers, IR_Recognizers, IR_Lint, IR_FreeVars,
  IR_Analysis)
- `semantics/` → `SpecRest_Semantics` (Smt(\_Fresh), Semantics(\_Eval/\_Typing),
  Semantics_Reference, Semantics_Inlining, Translate)
- `soundness/` → `SpecRest_Soundness`
- `codegen/` → `SpecRest_Codegen` (schema/OpenAPI/Alloy/classify helpers + `Codegen.thy` export)

Cross-session imports are session-qualified (`imports SpecRest_IR.IR_Helpers` from `semantics/`,
`SpecRest_Semantics.Translate` from `codegen/`); same-session imports stay bare. Measured cold
times: IR ≈ 60 s, Semantics ≈ 110 s, Codegen ≈ 50 s, Soundness ≈ 25 s. A `codegen/` edit (the common
codegen-lift case) rebuilds only `SpecRest_Codegen` (~50 s), reusing the IR + Semantics + Soundness
heaps; a `semantics/` edit (translate / eval lifts) reuses the ~60 s IR heap instead of
re-elaborating it. `restore-keys` partial-hit reuse still applies on top. See `README.md` → Session
structure.

## Dropped / Deferred

### `partial_function (mode = option)` for `eval` / `smt_eval`

Would skip `lexicographic_order` entirely
([Krauss, _Recursive Definitions of Monadic Functions_](https://arxiv.org/pdf/1012.4895)). Predicted
save: 20-40 s. **Deferred** because:

- Plain `partial_function` does not support mutual recursion. Requires
  [`Partial_Function_MR`](https://www.isa-afp.org/entries/Partial_Function_MR.html) from the AFP,
  which is not installed.
- Replaces `f.induct` with `f.fixp_induct`. The 15 `*_step` lemmas in `Soundness.thy:848-1196` use
  `eval.induct` directly, so the proof script needs a corresponding rewrite.
- Real-world precedent: CakeML's Isabelle semantics uses this exact pattern
  ([Hupel & Nipkow, ITP 2018](https://lars.hupel.info/pub/isabelle-cakeml.pdf)).

### Lifting + Transfer for `value_to_smt` correspondence

Would collapse the `value_to_smt_eq_*` family in `Soundness.thy:43-114` and the 13-lemma
`contains_value_map_value_to_smt` / `dedupe_*` block in `Soundness.thy:430-484`
([Huffman & Kunčar, CPP 2013](https://www21.in.tum.de/~kuncar/documents/huffman-kuncar-cpp2013.pdf)).
Predicted save: ~150 lines from `Soundness.thy`; ~0 s build-time impact. **Deferred** because the
refactor needs careful registration of per-constructor `[transfer_rule]` annotations and human
review of the resulting `by transfer simp` rewrites.

### Locale parameterising the value algebra

Would let one definition of `eval` interpret to both `Semantics.eval` and `Smt.smt_eval`. Saves ~300
lines combined ([Ballarin, _Tutorial to Locales_](https://isabelle.in.tum.de/doc/locales.pdf)).
**Deferred** — locales for exactly two interpretations are heavyweight; revisit when a third value
algebra (e.g. concolic) arrives.

### Eisbach `method` for the `*_step` lemmas

Would collapse the 15 `*_step` lemmas in `Soundness.thy:848-1196` into ~50 lines
([Matichuk-Murray-Wenzel, JAR 2016](https://link.springer.com/article/10.1007/s10817-015-9360-2)).
**Deferred** — requires careful per-lemma rewriting; Eisbach errors point at the combinator, not the
failing inner tactic, and review burden is real.

### Bigger CI runners (16-core), `build_cluster`, self-hosted runners

Skipped: Amdahl bound puts theoretical asymptotic at ~2.85× our current 1.93× parallelism factor
([Huch & Wenzel, _Distributed Parallel Build for AFP_, ITP 2024](https://drops.dagstuhl.de/entities/document/10.4230/LIPIcs.ITP.2024.22)).
At 2:48 baseline, additional cores cost 4× for ≤50 s. `build_cluster` is designed for AFP scale (4-8
h serial baseline); two orders of magnitude over threshold.

### Move parts to Dafny / Lean / Coq

Skipped: the project's Dafny presence is a codegen target, not a verification target.
Re-implementing `eval` / `smt_eval` / `expr` / `ir_value` / `smt_val` elsewhere costs more than it
saves.

### `inductive` (relational) `eval`

Skipped: `Codegen.thy` extracts `eval`/`smt_eval` to Scala. Going relational forces `code_pred` to
recover an executable; net wash.

## Sources

Primary references (with links). Cite from this list when annotating new entries.

- [Isabelle System Manual (`system.pdf`)](https://isabelle.in.tum.de/doc/system.pdf) — sessions,
  threads, `-j`, ROOT syntax, heap location, soft build (`-S`).
- [Krauss, _Defining Recursive Functions in Isabelle/HOL_ (`functions.pdf`)](https://isabelle.in.tum.de/doc/functions.pdf)
  — `fun` vs `function` vs `primrec`, termination methods, congruence rules.
- [Datatypes manual (`datatypes.pdf`)](https://isabelle.in.tum.de/doc/datatypes.pdf) — BNF plugin
  selection, `(plugins only: …)` and `(plugins del: …)` syntax.
- [Bulwahn-Krauss-Nipkow, _Finding Lexicographic Orders for Termination Proofs in Isabelle/HOL_, FroCoS 2007](https://www21.in.tum.de/~krauss/papers/lexicographic-orders.pdf)
  — proves `lexicographic_order` is NP-complete and exponential-space in #mutual functions.
- [Krauss, _Recursive Definitions of Monadic Functions_, PAR 2010](https://arxiv.org/pdf/1012.4895)
  — `partial_function (mode = option)` mechanics.
- [AFP `Partial_Function_MR` (Thiemann)](https://www.isa-afp.org/entries/Partial_Function_MR.html) —
  mutual-recursion wrapper around `partial_function`.
- [Hupel & Nipkow, _A Verified Compiler from Isabelle/HOL to CakeML_, ITP 2018](https://lars.hupel.info/pub/isabelle-cakeml.pdf)
  — real-world `partial_function (option)` precedent for a 24-clause evaluator.
- [Huch & Wenzel, _Distributed Parallel Build for the AFP_, ITP 2024](https://drops.dagstuhl.de/entities/document/10.4230/LIPIcs.ITP.2024.22)
  — multi-session topology, heap save/load costs, parallelism Amdahl curves.
- [Huffman & Kunčar, _Lifting and Transfer_, CPP 2013](https://www21.in.tum.de/~kuncar/documents/huffman-kuncar-cpp2013.pdf)
  — `lift_definition` / `transfer` / `transfer_prover`.
- [Matichuk, Murray, Wenzel, _Eisbach: A Proof Method Language_, JAR 2016](https://link.springer.com/article/10.1007/s10817-015-9360-2)
  — `method` declarations.
- [Ballarin, _Tutorial to Locales and Locale Interpretation_ (`locales.pdf`)](https://isabelle.in.tum.de/doc/locales.pdf).
- [Schirmer & Wenzel, _State Spaces — The Locale Way_, ENTCS 2009](https://www.sciencedirect.com/science/article/pii/S1571066109004198)
  — record overhead is O(n²) field-update commutation lemmas; below ~10 fields it does not bite.
- [Klein, _Style Guide for Isabelle/HOL_](https://proofcraft.org/blog/isabelle-style.html) and
  [Part 2](https://proofcraft.org/blog/isabelle-style-part2.html) — `auto` as terminal-only; bundles
  over global `[simp]`.
- [Paulson, _Type classes versus locales_, 2022](https://lawrencecpaulson.github.io/2022/03/23/Locales.html)
  — when a locale beats a type class.
- [seL4 `ci-actions`](https://github.com/seL4/ci-actions) — canonical pattern of redirecting
  `ISABELLE_HEAPS` into `${GITHUB_WORKSPACE}/cache/` for `actions/cache`, plus eviction of
  always-rebuilt leaf images.
- [Poly/ML Heap Parameters (LFCS Edinburgh)](http://www.lfcs.inf.ed.ac.uk/software/polyml/docs/HeapParms9.html)
  — `--minheap`, `--maxheap`, `--gcthreads`, `--gcpercent` semantics.
- [GitHub Actions — Dependency caching](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching)
  — `restore-keys`, separate `restore`/`save` actions.
- [Blanchette et al., _MaSh: Machine Learning for Sledgehammer_, ITP 2013](https://www.tcs.ifi.lmu.de/staff/jasmin-blanchette/mash.pdf)
  — Sledgehammer + MaSh for free proof-pattern injection (already in distribution).
