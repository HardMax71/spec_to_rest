// AUTO-GENERATED Dafny skeleton for service SafeCounter.
// Spec-derived signatures and contracts are immutable; only method bodies are synthesized.

datatype Option<T> = None | Some(value: T)

class ServiceState
{
  var count: int
}

predicate ServiceStateInv(st: ServiceState)
  reads st
{
  (st.count >= 0)
}

method Increment(st: ServiceState)
  modifies st
  requires ServiceStateInv(st)
  ensures st.count == old(st.count) + 1
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method Decrement(st: ServiceState)
  modifies st
  requires ServiceStateInv(st)
  requires st.count > 0
  ensures st.count == old(st.count) - 1
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}