service CircularPredicateBad {
  state {
    count: Int
  }

  predicate isA(n: Int) = isB(n)
  predicate isB(n: Int) = isA(n)

  operation Touch {
    requires:
      isA(count)
    ensures:
      count' = count
  }

  invariant nonNegative:
    count >= 0
}
