service UndefinedRefBad {
  state {
    count: Int
  }

  operation Inc {
    input: amount: Int
    requires:
      amount > 0
    ensures:
      count' = count + ammount
  }

  invariant nonNegative:
    cnt >= 0
}
