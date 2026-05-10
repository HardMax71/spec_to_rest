// Pattern: when an invariant fails ON ENTRY (before the first iteration),
// the fix is to either (a) initialize the relevant variables to a value
// that satisfies the invariant before the loop, or (b) weaken the
// invariant so the trivial pre-loop state satisfies it.
var seen: set<K> := {};   // initialise so 'seen' subset of st.store starts trivially
var i := 0;
while i < |xs|
  invariant seen <= st.store.Keys   // satisfied at i=0 by seen=={}
  invariant 0 <= i <= |xs|
{
  seen := seen + {xs[i].key};
  i := i + 1;
}
