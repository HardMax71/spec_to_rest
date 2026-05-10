// Pattern: when the postcondition has a disjunctive shape (e.g.
// `result.Success? ==> P || result.Failure?`), explicitly assert
// each branch's invariant inside the corresponding control-flow path.
// This pushes the disjunction obligation down to local arithmetic
// the SMT solver handles directly.
if cond {
  // ... establish branch A ...
  assert branchA_inv;
} else {
  // ... establish branch B ...
  assert branchB_inv;
}
// post: branchA_inv || branchB_inv  -- discharged by case split
