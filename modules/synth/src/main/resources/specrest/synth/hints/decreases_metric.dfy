// Pattern: a 'decreases' clause must produce a value that is strictly
// less in the well-founded order on each iteration / recursive call.
// For collections, '|s| - i' or 's[i..]' work; for natural recursion,
// the parameter itself decreases. Avoid 'decreases *' (unbounded).
while i < |xs|
  invariant 0 <= i <= |xs|
  decreases |xs| - i        // strictly decreases as i grows toward |xs|
{
  i := i + 1;
}

method Sum(xs: seq<int>) returns (r: int)
  decreases |xs|              // structural recursion on sequence length
{
  if |xs| == 0 { r := 0; }
  else {
    var rest := Sum(xs[1..]);
    r := xs[0] + rest;
  }
}
