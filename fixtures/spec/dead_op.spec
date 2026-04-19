service T {
  entity E { n: Int }
  state { x: Int }

  operation DeadOp {
    input: y: Int
    requires:
      y > 10 and y < 5
  }
}
