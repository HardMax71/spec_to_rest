service TodoList {

  // --- Enums ---

  enum Status {
    TODO,
    IN_PROGRESS,
    DONE,
    ARCHIVED
  }

  enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
  }

  // --- Entities ---

  entity Todo {
    id: Int where value > 0
    title: String where len(value) >= 1 and len(value) <= 200
    description: Option[String]
    status: Status
    priority: Priority
    created_at: DateTime
    updated_at: DateTime
    completed_at: Option[DateTime]
    tags: Set[String]

    invariant: status = DONE implies completed_at != none
    invariant: status != DONE implies completed_at = none
    invariant: updated_at >= created_at
  }

  // --- State ---

  state {
    todos: Int -> lone Todo
    next_id: Int
  }

  // --- State Machine ---

  transition TodoLifecycle {
    entity: Todo
    field: status

    TODO        -> IN_PROGRESS  via StartWork
    TODO        -> ARCHIVED     via Archive
    IN_PROGRESS -> DONE         via Complete
    IN_PROGRESS -> TODO         via PauseWork
    DONE        -> ARCHIVED     via Archive
    DONE        -> IN_PROGRESS  via Reopen     when updated_at > completed_at
  }

  // --- Operations ---

  operation CreateTodo {
    input:  title: String, description: Option[String],
            priority: Priority, tags: Set[String]
    output: todo: Todo

    requires:
      len(title) >= 1

    ensures:
      todo.id = pre(next_id)
      todo.title = title
      todo.description = description
      todo.status = TODO
      todo.priority = priority
      todo.tags = tags
      todo.completed_at = none
      next_id' = pre(next_id) + 1
      todos' = pre(todos) + {todo.id -> todo}
      #todos' = #pre(todos) + 1
  }

  operation GetTodo {
    input:  id: Int
    output: todo: Todo

    requires:
      id in todos

    ensures:
      todo = todos[id]
      todos' = todos
  }

  operation ListTodos {
    input:  status_filter: Option[Status],
            priority_filter: Option[Priority],
            tag_filter: Option[String]
    output: results: Seq[Todo]

    requires:
      true

    ensures:
      all t in results |
        t in ran(todos)
        and (status_filter = none or t.status = status_filter)
        and (priority_filter = none or t.priority = priority_filter)
        and (tag_filter = none or tag_filter in t.tags)
      todos' = todos
  }

  operation UpdateTodo {
    input:  id: Int, title: Option[String],
            description: Option[String], priority: Option[Priority],
            tags: Option[Set[String]]
    output: todo: Todo

    requires:
      id in todos

    ensures:
      todo.id = id
      title != none implies todo.title = title
      description != none implies todo.description = description
      priority != none implies todo.priority = priority
      tags != none implies todo.tags = tags
      title = none implies todo.title = pre(todos)[id].title
      todo.status = pre(todos)[id].status
      todo.updated_at >= pre(todos)[id].updated_at
      todos' = pre(todos) + {id -> todo}
  }

  operation StartWork {
    input: id: Int
    output: todo: Todo

    requires:
      id in todos
      todos[id].status = TODO

    ensures:
      todo = pre(todos)[id] with { status = IN_PROGRESS }
      todos' = pre(todos) + {id -> todo}
  }

  operation Complete {
    input: id: Int
    output: todo: Todo

    requires:
      id in todos
      todos[id].status = IN_PROGRESS

    ensures:
      todo = pre(todos)[id] with {
        status = DONE,
        completed_at = some(now())
      }
      todos' = pre(todos) + {id -> todo}
  }

  operation Reopen {
    input: id: Int
    output: todo: Todo

    requires:
      id in todos
      todos[id].status = DONE

    ensures:
      todo = pre(todos)[id] with {
        status = IN_PROGRESS,
        completed_at = none
      }
      todos' = pre(todos) + {id -> todo}
  }

  operation Archive {
    input: id: Int

    requires:
      id in todos
      todos[id].status in {TODO, DONE}

    ensures:
      todos'[id].status = ARCHIVED
      #todos' = #pre(todos)
  }

  operation DeleteTodo {
    input: id: Int

    requires:
      id in todos

    ensures:
      id not in todos'
      #todos' = #pre(todos) - 1
  }

  // --- Global Invariants ---

  invariant idsPositive:
    all id in todos | id > 0

  invariant nextIdMonotonic:
    next_id > 0

  invariant nextIdFresh:
    next_id not in todos

  invariant completedImpliesDone:
    all id in todos |
      todos[id].status = DONE iff todos[id].completed_at != none

  // --- Conventions ---

  conventions {
    CreateTodo.http_path = "/todos"
    CreateTodo.http_status_success = 201

    GetTodo.http_path = "/todos/{id}"
    ListTodos.http_path = "/todos"

    StartWork.http_method = "POST"
    StartWork.http_path = "/todos/{id}/start"

    Complete.http_method = "POST"
    Complete.http_path = "/todos/{id}/complete"

    Reopen.http_method = "POST"
    Reopen.http_path = "/todos/{id}/reopen"

    Archive.http_method = "POST"
    Archive.http_path = "/todos/{id}/archive"
  }
}
