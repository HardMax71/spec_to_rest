module PowersetDemo

sig User {
  id: one Int
}

one sig State {
  users: set User
}

fact someEmptySubsetExists {
  (some t: set User | (t in State.users) and ((#(t) = 0)))
}

run global {  } for 5
