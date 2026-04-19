service SetOpsDemo {

  state {
    counters: Int -> lone Int
  }

  invariant keysPositive:
    all id in counters | id > 0

  invariant valuesInRange:
    all id in counters | counters[id] in {0, 1, 2, 3, 5}

  operation Bump {
    input: id: Int
    requires:
      id in counters
      counters[id] in {0, 1, 2}
    ensures:
      counters' = pre(counters) + {id -> 5}
  }

  operation Touch {
    input: id: Int
    requires:
      id in counters
      counters[id] not in {5}
    ensures:
      counters' = pre(counters) + {id -> 3}
  }

  operation Merge {
    input: a: Set[Int], b: Set[Int]
    output: u: Set[Int]

    requires:
      true

    ensures:
      u = a union b
  }

  operation Common {
    input: a: Set[Int], b: Set[Int]
    output: i: Set[Int]

    requires:
      true

    ensures:
      i = a intersect b
  }

  operation OnlyA {
    input: a: Set[Int], b: Set[Int]
    output: d: Set[Int]

    requires:
      true

    ensures:
      d = a minus b
  }

  operation Included {
    input: a: Set[Int], b: Set[Int]
    output: result: Bool

    requires:
      true

    ensures:
      result = (a subset b)
  }

  operation MembershipInUnion {
    input: x: Int, a: Set[Int], b: Set[Int]
    output: inA: Bool

    requires:
      x in (a union b)

    ensures:
      inA = (x in a) or (x in b)
  }
}
