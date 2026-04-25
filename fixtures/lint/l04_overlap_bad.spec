service OverlapBad {
  state {
    count: Int
  }

  operation Increment {
    input: amount: Int

    requires:
      amount > 0

    ensures:
      count' = count + amount
  }

  operation Add {
    input: amount: Int

    requires:
      amount > 0

    ensures:
      count' = count + amount
  }

  invariant nonNegative:
    count >= 0
}
