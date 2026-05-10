// Pattern: when the invariant uses a fresh local variable, that variable
// must be declared and initialised BEFORE the while-loop, not first
// touched inside the body. The invariant is checked at loop entry —
// before any iteration has run.
var acc: int := 0;             // declare + init BEFORE the while
var i := 0;
while i < n
  invariant acc == seq_sum(s, 0, i)   // valid at i=0 because acc == 0 == seq_sum(s, 0, 0)
  invariant 0 <= i <= n
  decreases n - i
{
  acc := acc + s[i];
  i := i + 1;
}
