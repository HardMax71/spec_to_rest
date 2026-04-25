service UnusedEntityBad {
  entity Used {
    id: Int
    name: String
  }

  entity Orphan {
    id: Int
    label: String
  }

  state {
    items: Int -> lone Used
  }

  operation Add {
    input: id: Int, name: String
    requires:
      id > 0
    ensures:
      items'[id].id = id
      items'[id].name = name
  }

  invariant idsPositive:
    all i in items | items[i].id > 0
}
