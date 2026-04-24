module ContradictoryPowerset

sig User {}

one sig State {
  users: set User
}

fact trivialPowersetAnchor {
  (some t: set User | (t in State.users) and ((t = State.users)))
}

fact userNotEqualSelf {
  (some u: User | (u in State.users) and ((u != u)))
}

run global {  } for 5
