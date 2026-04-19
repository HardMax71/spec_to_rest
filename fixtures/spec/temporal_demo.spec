service TemporalDemo {

  entity User {
  }

  state {
    users: Set[User]
  }

  invariant usersAreValid:
    all u in users | u = u

  temporal nonDeletedEventuallyExists:
    eventually(some u: User | u in users)

  temporal allUsersAlwaysValid:
    always(all u in users | u = u)
}
