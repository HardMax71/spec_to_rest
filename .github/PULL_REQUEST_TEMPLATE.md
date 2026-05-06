## Summary

<!-- 1-3 bullet points describing the change. -->

## Test plan

<!-- Bulleted markdown checklist of TODOs for testing the pull request. -->

## Proof program checklist

The IR ADT lives in `proofs/isabelle/SpecRest/IR.thy` and is auto-extracted to
`modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala` by `Code_Target_Scala`.
**Never hand-edit `SpecRestGenerated.scala`** — it is regenerated on every `isabelle build SpecRest`
and your changes will be lost.

If this PR touches any `*.thy` under `proofs/isabelle/SpecRest/`:

- [ ] `proofs/isabelle/SpecRest/IR.thy` — verified-subset `expr` and full-input `expr_full` ADTs;
      the `lower :: expr_full ⇒ expr option` projection.
- [ ] `proofs/isabelle/STATUS.md` — update the per-case ledger if the verified subset moves.
- [ ] `proofs/isabelle/SpecRest/Soundness.thy` — extend the universal `soundness` theorem with a
      per-case `*_step` lemma + dispatch arm if a new `expr` constructor was added; update
      `lower_soundness` if `lower` moved.
- [ ] Re-run `isabelle build -d proofs/isabelle/SpecRest -j 4 SpecRest` and confirm zero `sorry`.
- [ ] Regenerate `modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala` from the
      new extraction (run the `isabelle build` above; commit the resulting diff).
- [ ] Run `sbt compile` + `sbt test` — every consumer module reads the extracted positional case
      classes directly, so an ADT change ripples through parser/lint/convention/profile/
      codegen/testgen/verify/cli at compile time.

If this PR does **not** touch any `*.thy` file, delete this section.
