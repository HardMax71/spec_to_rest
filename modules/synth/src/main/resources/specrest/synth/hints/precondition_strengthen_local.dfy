// Pattern: a precondition that the verifier cannot establish at a call
// site often becomes provable when restated as an assertion immediately
// before the call, with the assertion proven by a small local argument
// (e.g. a chain of equalities or a previously-established invariant).
assert ServiceStateInv(st);     // recover the invariant we just preserved
assert k in st.store by {
  assert old(k in st.store);    // prior fact
  assert st.store == old(st.store);  // not mutated in this branch
}
ChildOperation(st, k);
