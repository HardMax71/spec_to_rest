service AllLintsPass {
  entity Item {
    id: Int where value > 0
    name: String
  }

  state {
    items: Int -> lone Item
  }

  predicate hasItem(k: Int) = k in items

  operation Add {
    input: id: Int, name: String

    requires:
      id > 0
      not hasItem(id)

    ensures:
      items'[id].id = id
      items'[id].name = name
  }

  operation Remove {
    input: id: Int

    requires:
      hasItem(id)

    ensures:
      id not in items'
  }

  invariant idsPositive:
    all k in items | items[k].id > 0
}
