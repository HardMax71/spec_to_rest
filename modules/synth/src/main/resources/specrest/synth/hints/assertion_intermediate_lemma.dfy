// Pattern: when an inline assertion fails, factor the proof into an
// intermediate lemma. The lemma's pre/post forces the verifier to follow
// the explicit reasoning chain rather than rediscover it from context.
lemma SubsetTransitive(a: set<int>, b: set<int>, c: set<int>)
  requires a <= b && b <= c
  ensures a <= c
{ /* trivial — Dafny derives this from the requires */ }

method UseIt(x: set<int>, y: set<int>, z: set<int>)
  requires x <= y && y <= z
{
  SubsetTransitive(x, y, z);
  assert x <= z;     // discharged by the lemma's postcondition
}
