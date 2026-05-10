// Pattern: when a single metric does not strictly decrease but a tuple
// does, use a lexicographic decreases clause. Useful for nested loops
// or recursion that shrinks one dimension while another temporarily grows.
while !done
  invariant 0 <= outer && 0 <= inner
  decreases outer, |remaining|     // outer decreases primarily;
                                    // |remaining| decreases when outer is fixed
{
  if needs_reset {
    outer := outer - 1;
    remaining := initial_pool;     // OK: outer dropped, lex still <
  } else {
    remaining := remaining[1..];   // outer fixed, |remaining| dropped
  }
}
