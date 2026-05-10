// Pattern: when verification times out, the SMT solver is exploring too
// large a search space. Split the proof: introduce intermediate assertions
// that act as proof checkpoints, and use {:split_here} to partition
// verification conditions. Often a single {:split_here} cuts solver time
// by 10x without changing the algorithm.
method ComplexUpdate(st: ServiceState, k: K, v: V)
  modifies st
  ensures Inv1(st) && Inv2(st) && Inv3(st)
{
  // ... mutate state ...
  assert Inv1(st);
  assert {:split_here} Inv2(st);   // verifier proves Inv1 in one batch,
  assert Inv3(st);                  // Inv2 and Inv3 in another
}
