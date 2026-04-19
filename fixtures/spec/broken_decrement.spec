service BrokenDecrement {
  state {
    clicks: Int
  }

  operation Decrement {
    requires:
      true

    ensures:
      clicks' = clicks - 1
  }

  invariant clicksNonNegative:
    clicks >= 0
}
