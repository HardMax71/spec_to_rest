service PowersetDemo {

  entity User {
    id: Int
  }

  state {
    users: Set[User]
  }

  invariant someEmptySubsetExists:
    some t in ^users | #t = 0
}
