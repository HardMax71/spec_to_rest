service TypeMismatchBad {
  state {
    count: Int
    flag: Bool
  }

  operation Inc {
    requires:
      flag and 5
    ensures:
      count' = count + true
  }

  invariant nonNegative:
    count > true
}
