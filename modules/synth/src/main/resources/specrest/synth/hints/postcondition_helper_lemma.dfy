// Pattern: when the postcondition involves quantifiers over collection
// state, extract a helper lemma that proves the property for one element.
// Calling the lemma after each mutation discharges the forall obligation
// without the verifier having to instantiate the quantifier itself. The
// lemma is proven by the verifier — DO NOT use {:axiom}, which would
// trust the property unsoundly and let the synthesizer "verify" with a
// false fact.
lemma ElementPreservedAfterInsert<K(==), V>(s: map<K, V>, k: K, v: V, k': K)
  requires k' in s && k' != k
  ensures (s[k := v])[k'] == s[k']
{
  // Proven by Dafny from the definition of map update; no body needed
  // for this trivial case, but the lemma block is REQUIRED to make the
  // claim a discharged proof obligation rather than an axiom.
}

method Insert<K(==), V>(st: ServiceState, k: K, v: V)
  modifies st
  ensures forall k' :: k' in old(st.m) && k' != k ==> st.m[k'] == old(st.m)[k']
{
  st.m := st.m[k := v];
  forall k' | k' in old(st.m) && k' != k
    ensures st.m[k'] == old(st.m)[k']
  { ElementPreservedAfterInsert(old(st.m), k, v, k'); }
}
