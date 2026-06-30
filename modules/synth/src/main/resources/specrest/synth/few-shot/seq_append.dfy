// Appending an element to the end of a sequence
method Append<T>(s: seq<T>, x: T) returns (s': seq<T>)
  ensures |s'| == |s| + 1
  ensures s'[..|s|] == s
  ensures s'[|s|] == x
{
  s' := s + [x];
}
