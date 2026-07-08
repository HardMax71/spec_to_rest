// AUTO-GENERATED Dafny skeleton for service SafeCounter.
// Spec-derived signatures and contracts are immutable; only method bodies are synthesized.

datatype Option<T> = None | Some(value: T)

ghost function TheBy<K, V>(m: map<K, V>, p: K -> bool): K
  requires exists k :: k in m && p(k)
  requires forall k1, k2 :: k1 in m && k2 in m && p(k1) && p(k2) ==> k1 == k2
  ensures TheBy(m, p) in m && p(TheBy(m, p))
{
  var k :| k in m && p(k); k
}

ghost function TheBySet<T>(s: set<T>, p: T -> bool): T
  requires exists x :: x in s && p(x)
  requires forall x1, x2 :: x1 in s && x2 in s && p(x1) && p(x2) ==> x1 == x2
  ensures TheBySet(s, p) in s && p(TheBySet(s, p))
{
  var x :| x in s && p(x); x
}

class ServiceState
{
  var count: int
}

predicate ServiceStateInv(st: ServiceState)
  reads st
{
  (st.count >= 0)
}

predicate RequiresIncrement(st: ServiceState)
  reads st
{
  (ServiceStateInv(st))
}
method Increment(st: ServiceState)
  modifies st
  requires ServiceStateInv(st)
  ensures st.count == old(st.count) + 1
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

predicate RequiresDecrement(st: ServiceState)
  reads st
{
  (ServiceStateInv(st))
  && (st.count > 0)
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
