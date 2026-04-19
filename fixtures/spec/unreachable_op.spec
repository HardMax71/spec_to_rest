service T {
  state { x: Int }

  operation UnreachableOp {
    requires:
      x < 5
  }

  invariant: x > 100
}
