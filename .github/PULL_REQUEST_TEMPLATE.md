## Summary

<!-- 1-3 bullet points describing the change. -->

## Test plan

<!-- Bulleted markdown checklist of TODOs for testing the pull request. -->

## Proof program checklist

If this PR touches `modules/ir/src/main/scala/specrest/ir/Types.scala` — specifically `Expr`,
`BinOp`, `UnOp`, `TypeExpr`, or any declaration ADT — also update:

- [ ] `proofs/lean/SpecRest/IR.lean.todo` — record the drift in the log section.
- [ ] `proofs/lean/STATUS.md` — re-sync the per-case ledger.
- [ ] `docs/content/docs/research/13_global_proof_profile.md` — only if the change moves a feature
      between `bootstrap` / `first ship` / `defer` / `exclude`.
- [ ] `docs/content/docs/research/12_global_proof_status.md` — note the governed-surface move and
      verify the `M_G.0` theorem statement still reads honestly.

If this PR does **not** touch the IR ADT, delete this section.
