// Pattern: inserting a record keyed by a freshness counter. The state
// invariants already say every existing key is below the counter and every
// cross-reference points at an existing key, so the fresh id is disjoint
// from everything; two targeted asserts surface that chain. Keep the rest
// of the body minimal: a direct construction plus the state updates. Most
// postconditions follow from the invariants without restating them.
var fresh_id := st.next_id;
record := Record(fresh_id, /* remaining fields from inputs/defaults */);
assert fresh_id !in st.records;
assert forall r :: r in st.refs ==> st.refs[r].record_id != fresh_id;
st.records := st.records[fresh_id := record];
st.next_id := fresh_id + 1;
