service ConstructorDemo {
  entity Point {
    x: Int
    y: Int
  }

  invariant ctorFieldRoundtrips:
    (Point { x = 5, y = 7 }).x = 5
}
