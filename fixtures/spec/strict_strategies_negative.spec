service StrictStrategiesNeg {
  type CustomCode = String where custom_pred(value)

  state {
    counter: Int
  }

  operation Increment {
    requires:
      true

    ensures:
      counter' = counter + 1
  }

  invariant counterNonNegative:
    counter >= 0
}
