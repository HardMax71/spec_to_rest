# SpecRest — Isabelle/HOL proof track

This is the Isabelle/HOL port of the SpecRest soundness machinery, replacing the original Lean 4
track. Pivot decided 2026-05-04; tracking issue
[#193](https://github.com/HardMax71/spec_to_rest/issues/193).

The Lean track under `proofs/lean/` remains buildable for one full release cycle while the Isabelle
port catches up to feature parity (M_L.0 through M_L.4.k + issue #195). Once parity is reached and
the `Code_Target_Scala` extractor replaces the hand-written `cert/EvalIR.scala` mirror, the Lean
track retires (PR #193b).

## Layout

```text
proofs/isabelle/
├── README.md              this file
├── STATUS.md              proof-state ledger (mirrors proofs/lean/STATUS.md)
└── SpecRest/
    ├── ROOT                 Isabelle session definition
    ├── SpecRest.thy         top-level theory; imports IR (and, eventually, Smt/Semantics/...)
    └── IR.thy               deep-embedded IR: type_expr, expr, structures
```

## Build (requires Isabelle2025-2)

```bash
isabelle build -d proofs/isabelle/SpecRest -b SpecRest
```

The first build downloads/compiles `HOL` and `HOL-Library` heaps (~5-10 minutes); subsequent builds
reuse them. Heap files cache under `~/.isabelle/Isabelle2025-2/heaps/`.

## Why the pivot

See issue #193 for the full rationale. Headlines:

- **`Code_Target_Scala` is production-ready** (used in Stainless/Leon at EPFL since 2010). Replaces
  the custom Lean → Scala extractor that the original #193 scoped at 2-3 person-months.
- **Toolchain stability**: Isabelle releases yearly with AFP push-through migration; Lean 4 ran 4.27
  → 4.30 in 14 weeks Feb–Apr 2026.
- **Cleaner Path 3 TCB**: Isabelle kernel + Z3 driver versus Lean's `Lean.ofReduceBool` + custom
  extractor (1-2 kLoC, in our TCB) + Z3 driver.
- **Net effort to Path-3-shipped**: 4-7 PM vs 5-9 PM on the Lean path (with multi-contributor
  parallelism on the re-port).

## Phased delivery

Per issue #193, 10 phases sized for single-contributor PRs:

| Phase | Days (1×) | Days (2×) | Deliverable                                                         |
| ----- | --------- | --------- | ------------------------------------------------------------------- |
| 0     | 2         | 2         | Branch + Isabelle install + skeleton ROOT/`SpecRest.thy` + `IR.thy` |
| 1     | 7         | 5         | Port `IR.lean` (done in Phase 0); port `Smt.lean` → `Smt.thy`       |
| 2     | 10        | 6         | Port `Semantics.lean`; port `Translate.lean`                        |
| 3     | 14        | 9         | Port per-case lemmas in `Lemmas.lean`                               |
| 4     | 21        | 13        | Port `Soundness.lean` (5374 LoC); close universal `soundness`       |
| 5     | 5         | 5         | `Code_Target_Scala` setup + `export_code` for `eval`, `translate`   |
| 6     | 4         | 4         | `cert/EmitIsabelle.scala` replacing `cert/Emit.scala`               |
| 7     | 3         | 3         | A8a/A8b oracles + CI workflow migration                             |
| 8     | 3         | 3         | Drift audit migration; side-by-side gating                          |
| 9     | 3         | 3         | Docs: §7 of `10_translator_soundness.md` + `STATUS.md` migration    |
| 10    | follow-up | follow-up | Retire Lean track (`rm -rf proofs/lean/`)                           |

Total: 53-72 person-days depending on contributor count.

## Conventions

- **Naming**: snake_case for types (`type_expr`, `bool_bin_op`); CamelCase for constructors
  (`BoolLit`, `IntT`). Differs from Lean's lowerCamelCase constructors (`boolLit`); the extractor's
  `code_printing` directives handle the rename to Scala-style `VBool` etc. when emitting
  `EvalGenerated.scala`.
- **Strings**: `String.literal` (extracts to Scala `String`) everywhere — never `string` (=
  `char list`, extracts as `List[Char]`).
- **Integers**: `int` (mathematical, unbounded; extracts to Scala `BigInt`). Never `nat` (loses
  negative range; would force code-gen casts at every Z3 boundary).
- **Records vs datatypes**: `record` for product types (single constructor, named fields);
  `datatype` for sum types. Mirrors the Lean split between `structure` and `inductive`.
- **No `mathlib` analog**: keep dependencies to `Main` + `HOL-Library` only. AFP entries are
  imported only when load-bearing (none currently).
