// Pattern: when a loop invariant fails preservation, often a STRONGER
// invariant works because it gives the verifier the missing information
// it needs to re-derive the original after one iteration. Common
// strengthenings: bound the loop counter, relate the accumulator to a
// prefix expression, freeze unmodified collection elements.
var i := 0;
var acc := 0;
while i < |s|
  invariant 0 <= i <= |s|                   // counter bound
  invariant acc == sum(s, 0, i)             // accumulator vs prefix
  invariant forall j :: 0 <= j < i ==> s[j] == old(s[j])  // freeze prefix
  decreases |s| - i
{
  acc := acc + s[i];
  i := i + 1;
}
