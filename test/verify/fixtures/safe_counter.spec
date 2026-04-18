service SafeCounter {
  state {
    count: Int
  }

  operation Increment {
    requires:
      true

    ensures:
      count' = count + 1
  }

  operation Decrement {
    requires:
      count > 0

    ensures:
      count' = count - 1
  }

  invariant countNonNegative:
    count >= 0
}
