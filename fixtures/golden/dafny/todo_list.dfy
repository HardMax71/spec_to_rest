// AUTO-GENERATED Dafny skeleton for service TodoList.
// Spec-derived signatures and contracts are immutable; only method bodies are synthesized.

datatype Option<T> = None | Some(value: T)

ghost function TheBy<K, V>(m: map<K, V>, p: K -> bool): K
  requires exists k :: k in m && p(k)
  requires forall k1, k2 :: k1 in m && k2 in m && p(k1) && p(k2) ==> k1 == k2
  ensures TheBy(m, p) in m && p(TheBy(m, p))
{
  var k :| k in m && p(k); k
}

datatype Status = TODO | IN_PROGRESS | DONE | ARCHIVED
datatype Priority = LOW | MEDIUM | HIGH | URGENT

datatype Todo = Todo(id: int, title: string, description: Option<string>, status: Status, priority: Priority, created_at: int, updated_at: int, completed_at: Option<int>, tags: set<string>)

predicate TodoInv(x: Todo)
{
  (x.id > 0)
  && ((|x.title| >= 1 && |x.title| <= 200))
  && ((x.status == DONE ==> x.completed_at != None))
  && ((x.status != DONE ==> x.completed_at == None))
  && (x.updated_at >= x.created_at)
}

class ServiceState
{
  var todos: map<int, Todo>
  var next_id: int
}

predicate ServiceStateInv(st: ServiceState)
  reads st
{
  (forall id :: (id in st.todos) ==> (id > 0))
  && (st.next_id > 0)
  && (st.next_id !in st.todos)
  && (forall id :: (id in st.todos) ==> ((st.todos[id].status == DONE <==> st.todos[id].completed_at != None)))
}

function now(): int

method CreateTodo(st: ServiceState, title: string, description: Option<string>, priority: Priority, tags: set<string>) returns (todo: Todo)
  modifies st
  requires ServiceStateInv(st)
  requires |title| >= 1
  ensures todo.id == old(st.next_id)
  ensures todo.title == title
  ensures todo.description == description
  ensures todo.status == TODO
  ensures todo.priority == priority
  ensures todo.tags == tags
  ensures todo.completed_at == None
  ensures st.next_id == old(st.next_id) + 1
  ensures st.todos == old(st.todos)[todo.id := todo]
  ensures |st.todos| == |old(st.todos)| + 1
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method GetTodo(st: ServiceState, id: int) returns (todo: Todo)
  modifies st
  requires ServiceStateInv(st)
  requires id in st.todos
  ensures todo == old(st.todos)[id]
  ensures st.todos == old(st.todos)
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method ListTodos(st: ServiceState, status_filter: Option<Status>, priority_filter: Option<Priority>, tag_filter: Option<string>) returns (results: seq<Todo>)
  modifies st
  requires ServiceStateInv(st)
  ensures forall t :: (t in results) ==> ((((t in old(st.todos).Values && (status_filter == None || t.status == status_filter.value)) && (priority_filter == None || t.priority == priority_filter.value)) && (tag_filter == None || tag_filter.value in t.tags)))
  ensures st.todos == old(st.todos)
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method UpdateTodo(st: ServiceState, id: int, title: Option<string>, description: Option<string>, priority: Option<Priority>, tags: Option<set<string>>) returns (todo: Todo)
  modifies st
  requires ServiceStateInv(st)
  requires id in st.todos
  ensures todo.id == id
  ensures (title != None ==> todo.title == title.value)
  ensures (description != None ==> todo.description == description.value)
  ensures (priority != None ==> todo.priority == priority.value)
  ensures (tags != None ==> todo.tags == tags.value)
  ensures (title == None ==> todo.title == old(st.todos)[id].title)
  ensures todo.status == old(st.todos)[id].status
  ensures todo.updated_at >= old(st.todos)[id].updated_at
  ensures st.todos == old(st.todos)[id := todo]
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method StartWork(st: ServiceState, id: int) returns (todo: Todo)
  modifies st
  requires ServiceStateInv(st)
  requires id in st.todos
  requires st.todos[id].status == TODO
  ensures todo == old(st.todos)[id].(status := IN_PROGRESS)
  ensures st.todos == old(st.todos)[id := todo]
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method Complete(st: ServiceState, id: int) returns (todo: Todo)
  modifies st
  requires ServiceStateInv(st)
  requires id in st.todos
  requires st.todos[id].status == IN_PROGRESS
  ensures todo == old(st.todos)[id].(status := DONE, completed_at := Some(now()))
  ensures st.todos == old(st.todos)[id := todo]
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method Reopen(st: ServiceState, id: int) returns (todo: Todo)
  modifies st
  requires ServiceStateInv(st)
  requires id in st.todos
  requires st.todos[id].status == DONE
  ensures todo == old(st.todos)[id].(status := IN_PROGRESS, completed_at := None)
  ensures st.todos == old(st.todos)[id := todo]
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method Archive(st: ServiceState, id: int)
  modifies st
  requires ServiceStateInv(st)
  requires id in st.todos
  requires st.todos[id].status in {TODO, DONE}
  ensures st.todos[id].status == ARCHIVED
  ensures |st.todos| == |old(st.todos)|
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method DeleteTodo(st: ServiceState, id: int)
  modifies st
  requires ServiceStateInv(st)
  requires id in st.todos
  ensures id !in st.todos
  ensures |st.todos| == |old(st.todos)| - 1
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}
