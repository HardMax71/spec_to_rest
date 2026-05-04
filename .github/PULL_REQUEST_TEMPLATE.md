## Summary

<!-- 1-3 bullet points describing the change. -->

## Test plan

<!-- Bulleted markdown checklist of TODOs for testing the pull request. -->

## Proof program checklist

If this PR touches `modules/ir/src/main/scala/specrest/ir/Types.scala` — specifically `Expr`,
`BinOp`, `UnOp`, `TypeExpr`, or any declaration ADT — also update:

- [ ] `proofs/isabelle/SpecRest/IR.thy` — re-sync the Isabelle-side ADT.
- [ ] `proofs/isabelle/STATUS.md` — update the per-case ledger if the verified subset moves.
- [ ] `proofs/isabelle/SpecRest/Soundness.thy` — extend the universal `soundness` theorem with a
      per-case `*_step` lemma + dispatch arm if a new `expr` constructor was added.
- [ ] Re-run `isabelle build -d proofs/isabelle/SpecRest -j 4 SpecRest` and confirm zero `sorry`.
- [ ] If `Code_Target_Scala` extraction shape changed, regenerate
      `modules/verify/.../cert/generated/SpecRestGenerated.scala`.

If this PR does **not** touch the IR ADT, delete this section.
