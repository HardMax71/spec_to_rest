// Pattern: capture old(...) values into ghost-friendly local vars at the
// beginning of the body, then reference those locals in assertions that
// discharge the postcondition. The verifier finds a chain of equalities
// easier to follow than nested old(...) terms scattered through the proof.
ghost var oldStore := old(st.store);
ghost var oldCount := old(st.count);
// ... mutate state ...
assert st.store == oldStore[code := url];
assert st.count == oldCount + 1;
