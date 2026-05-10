// Pattern: when calling a method whose precondition isn't obviously
// satisfied, add a guard (if-block) that checks the condition explicitly
// and either proves the precondition holds in that branch or returns early.
// This converts an unprovable global precondition into a local assertion.
if k in st.store {
  assert k in st.store;  // precondition for InternalLookup
  result := InternalLookup(st, k);
} else {
  result := DefaultValue;
}
