## Summary

<!-- 1-3 bullet points describing the change. -->

## Test plan

<!-- Bulleted markdown checklist of TODOs for testing the pull request. -->

## Proof program checklist

The IR ADT lives in `proofs/isabelle/SpecRest/IR.thy` and is extracted to
`modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala` by `Code_Target_Scala`.
**Never hand-edit `SpecRestGenerated.scala`**. The `isabelle-build` workflow re-runs the extraction
in check mode on every PR and fails on any drift.

If this PR touches any `*.thy` under `proofs/isabelle/SpecRest/`:

- [ ] `proofs/isabelle/SpecRest/IR.thy`: verified-subset `expr` and full-input `expr_full` ADTs; the
      `lower :: expr_full ⇒ expr option` projection.
- [ ] `proofs/isabelle/STATUS.md`: update the per-case ledger if the verified subset moves.
- [ ] `proofs/isabelle/SpecRest/Soundness.thy`: extend the universal `soundness` theorem with a
      per-case `*_step` lemma + dispatch arm if a new `expr` constructor was added; update
      `lower_soundness` if `lower` moved.
- [ ] Re-run `isabelle build -d proofs/isabelle/SpecRest -j 4 SpecRest` and confirm zero `sorry`.
- [ ] Regenerate `SpecRestGenerated.scala` per `proofs/isabelle/README.md` → "Regenerating
      `SpecRestGenerated.scala`" and commit the diff. CI will fail otherwise.
- [ ] Run `sbt compile` + `sbt test`: every consumer module reads the extracted positional case
      classes directly, so an ADT change ripples through parser/lint/convention/profile/
      codegen/testgen/verify/cli at compile time.

If this PR does **not** touch any `*.thy` file, delete this section.
