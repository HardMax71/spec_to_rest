service SetCompDemo {

  entity User {
    id: Int
    active: Bool
  }

  state {
    activeUsers: Set[User]
  }

  invariant activeUsersSet:
    activeUsers = { u in User | u.active }
}
