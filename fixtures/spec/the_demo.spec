service TheDemo {
  state {
    scores: Map[Int, Int]
  }

  invariant pivotNonNegative:
    (the k in scores | scores[k] = 100) >= 0
}
