service PowersetOps {

  entity User {
  }

  state {
    users: Set[User]
  }

  invariant allValid:
    all u in users | u = u

  operation AddUser {
    input: u: User

    requires:
      some t in ^users | t = users and u not in t

    ensures:
      users' = users union {u}
  }
}
