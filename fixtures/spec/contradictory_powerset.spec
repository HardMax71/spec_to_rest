service ContradictoryPowerset {

  entity User {
  }

  state {
    users: Set[User]
  }

  invariant trivialPowersetAnchor:
    some t in ^users | t = users

  invariant userNotEqualSelf:
    some u in users | u != u
}
