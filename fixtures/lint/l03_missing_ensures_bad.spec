service MissingEnsuresBad {
  state {
    count: Int
  }

  operation Read {
    output: result: Int

    requires:
      true
  }

  invariant nonNegative:
    count >= 0
}
