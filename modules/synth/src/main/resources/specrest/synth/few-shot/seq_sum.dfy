// Summing a sequence with a loop invariant
ghost function SumSpec(s: seq<int>): int
  decreases |s|
{
  if |s| == 0 then 0
  else s[0] + SumSpec(s[1..])
}

method Sum(s: seq<int>) returns (total: int)
  ensures total == SumSpec(s)
{
  total := 0;
  var i := |s|;
  while i > 0
    invariant 0 <= i <= |s|
    invariant total == SumSpec(s[i..])
    decreases i
  {
    i := i - 1;
    total := s[i] + total;
  }
}
