---
title: "Worked examples"
description: "Four complete specs: URL shortener, todo list, authentication, and e-commerce"
---

### 4.1 Example 1, URL shortener

This is the running example used throughout the document.

```text
service UrlShortener {

  // --- Type Definitions ---

  type ShortCode = String where len(value) >= 6 and len(value) <= 10
                              and value matches /^[a-zA-Z0-9]+$/

  type LongURL = String where len(value) > 0 and isValidURI(value)

  type BaseURL = String where isValidURI(value)

  // --- Entities ---

  entity UrlMapping {
    code: ShortCode
    url: LongURL
    created_at: DateTime
    click_count: Int where value >= 0

    invariant: isValidURI(url)
  }

  // --- State ---

  state {
    store: ShortCode -> lone LongURL       // partial function
    metadata: ShortCode -> lone UrlMapping  // full entity lookup
    base_url: BaseURL                       // configuration
  }

  // --- Operations ---

  operation Shorten {
    input:  url: LongURL
    output: code: ShortCode, short_url: String

    requires:
      isValidURI(url)

    ensures:
      code not in pre(store)                  // code was fresh
      store' = pre(store) + {code -> url}     // mapping added
      short_url = base_url + "/" + code       // URL constructed
      #store' = #pre(store) + 1               // exactly one added
      metadata'[code].url = url               // entity synced
      metadata'[code].click_count = 0         // initialized
  }

  operation Resolve {
    input:  code: ShortCode
    output: url: LongURL

    requires:
      code in store

    ensures:
      url = store[code]                       // correct lookup
      store' = store                          // state unchanged
      metadata'[code].click_count =
        pre(metadata)[code].click_count + 1   // increment clicks
  }

  operation Delete {
    input: code: ShortCode

    requires:
      code in store

    ensures:
      code not in store'                      // removed
      code not in metadata'                   // entity removed
      #store' = #pre(store) - 1               // exactly one removed
  }

  operation ListAll {
    output: entries: Set[UrlMapping]

    requires:
      true                                    // no precondition

    ensures:
      entries = { m in metadata | true }      // all mappings
      store' = store                          // no mutation
  }

  // --- Global Invariants ---

  invariant allURLsValid:
    all c in store | isValidURI(store[c])

  invariant metadataConsistent:
    dom(store) = dom(metadata)

  invariant clickCountNonNegative:
    all c in metadata | metadata[c].click_count >= 0

  // --- Convention Overrides ---

  conventions {
    Shorten.http_method = "POST"
    Shorten.http_path = "/shorten"
    Shorten.http_status_success = 201

    Resolve.http_method = "GET"
    Resolve.http_path = "/{code}"
    Resolve.http_status_success = 302
    Resolve.http_header "Location" = output.url

    Delete.http_method = "DELETE"
    Delete.http_path = "/{code}"
    Delete.http_status_success = 204

    ListAll.http_method = "GET"
    ListAll.http_path = "/urls"
    ListAll.http_status_success = 200
  }
}
```

Without the conventions block, the engine would infer:

| Operation | Inferred Method                     | Inferred Path          | Inferred Status |
| --------- | ----------------------------------- | ---------------------- | --------------- |
| Shorten   | POST (mutates state, has input)     | `/url-mappings`        | 201             |
| Resolve   | GET (reads state, no mutation)      | `/url-mappings/{code}` | 200             |
| Delete    | DELETE (removes from state)         | `/url-mappings/{code}` | 204             |
| ListAll   | GET (reads collection, no mutation) | `/url-mappings`        | 200             |

The conventions block overrides these defaults to give a cleaner API.

### 4.2 Example 2, todo list API

```text
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
    todos: Int -> lone Todo         // id to todo mapping
    next_id: Int                    // auto-increment counter
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
```

### 4.3 Example 3, user authentication service

```text
service AuthService {

  // --- Types ---

  type Email = String where value matches /^[^@]+@[^@]+\.[^@]+$/
  type PasswordHash = String where len(value) = 64   // SHA-256 hex
  type Token = String where len(value) = 128
  type UserId = Int where value > 0

  // --- Enums ---

  enum TokenType {
    ACCESS,
    REFRESH,
    RESET
  }

  // --- Entities ---

  entity User {
    id: UserId
    email: Email
    password_hash: PasswordHash
    display_name: String where len(value) >= 1 and len(value) <= 100
    created_at: DateTime
    last_login: Option[DateTime]
    is_active: Bool

    invariant: len(email) > 0
  }

  entity Session {
    id: Int where value > 0
    user_id: UserId
    access_token: Token
    refresh_token: Token
    access_expires_at: DateTime
    refresh_expires_at: DateTime
    created_at: DateTime
    is_revoked: Bool

    invariant: access_expires_at > created_at
    invariant: refresh_expires_at > access_expires_at
    invariant: access_token != refresh_token
  }

  entity LoginAttempt {
    email: Email
    timestamp: DateTime
    success: Bool
  }

  // --- State ---

  state {
    users: UserId -> lone User
    sessions: Int -> lone Session
    login_attempts: Seq[LoginAttempt]
    user_by_email: Email -> lone User          // index
    next_user_id: UserId
    next_session_id: Int
  }

  // --- Operations ---

  operation Register {
    input:  email: Email, password: String,
            display_name: String
    output: user: User

    requires:
      email not in user_by_email               // email unique
      len(password) >= 8                       // password policy
      len(display_name) >= 1

    ensures:
      user.id = pre(next_user_id)
      user.email = email
      user.password_hash = hash(password)
      user.display_name = display_name
      user.is_active = true
      user.last_login = none
      next_user_id' = pre(next_user_id) + 1
      users' = pre(users) + {user.id -> user}
      user_by_email' = pre(user_by_email) + {email -> user}
      #users' = #pre(users) + 1
  }

  operation Login {
    input:  email: Email, password: String
    output: session: Session

    requires:
      email in user_by_email
      user_by_email[email].is_active = true
      user_by_email[email].password_hash = hash(password)
      recentFailedAttempts(email) < 5          // rate limiting

    ensures:
      let user = user_by_email[email] in
        session.user_id = user.id
        and session.is_revoked = false
        and session.access_expires_at > now()
        and session.refresh_expires_at > session.access_expires_at
        and sessions' = pre(sessions) + {session.id -> session}
        and users'[user.id].last_login = some(now())
        and login_attempts' = pre(login_attempts)
             + [LoginAttempt { email = email,
                               timestamp = now(),
                               success = true }]
  }

  operation LoginFailed {
    input: email: Email

    requires:
      email in user_by_email
      user_by_email[email].password_hash != hash(input_password)

    ensures:
      sessions' = sessions
      users' = users
      login_attempts' = pre(login_attempts)
        + [LoginAttempt { email = email,
                          timestamp = now(),
                          success = false }]
  }

  operation RefreshToken {
    input:  refresh_token: Token
    output: new_session: Session

    requires:
      some s in sessions |
        sessions[s].refresh_token = refresh_token
        and sessions[s].is_revoked = false
        and sessions[s].refresh_expires_at > now()

    ensures:
      let old_session = (the s in sessions |
        sessions[s].refresh_token = refresh_token) in
        // Old session revoked
        sessions'[old_session].is_revoked = true
        // New session created
        and new_session.user_id = pre(sessions)[old_session].user_id
        and new_session.is_revoked = false
        and new_session.access_expires_at > now()
        and sessions' = pre(sessions)
             + {old_session -> pre(sessions)[old_session]
                  with { is_revoked = true }}
             + {new_session.id -> new_session}
  }

  operation RequestPasswordReset {
    input:  email: Email
    output: reset_token: Token

    requires:
      email in user_by_email
      user_by_email[email].is_active = true

    ensures:
      // A new session of type RESET is created
      // (modeled as a session with special properties)
      some s in sessions' |
        s not in pre(sessions)
        and sessions'[s].user_id = user_by_email[email].id
        and sessions'[s].access_expires_at > now()
      users' = users
  }

  operation ResetPassword {
    input:  reset_token: Token, new_password: String

    requires:
      some s in sessions |
        sessions[s].access_token = reset_token
        and sessions[s].is_revoked = false
        and sessions[s].access_expires_at > now()
      len(new_password) >= 8

    ensures:
      let s = (the s in sessions |
        sessions[s].access_token = reset_token) in
        let user_id = pre(sessions)[s].user_id in
          users'[user_id].password_hash = hash(new_password)
          and sessions'[s].is_revoked = true
  }

  operation Logout {
    input: access_token: Token

    requires:
      some s in sessions |
        sessions[s].access_token = access_token
        and sessions[s].is_revoked = false

    ensures:
      let s = (the s in sessions |
        sessions[s].access_token = access_token) in
        sessions'[s].is_revoked = true
        and users' = users
  }

  // --- Helper Functions ---

  fact recentFailedAttemptsDef:
    all email in dom(user_by_email) |
      recentFailedAttempts(email) =
        #{ a in login_attempts |
           a.email = email
           and a.success = false
           and a.timestamp > now() - minutes(15) }

  // --- Global Invariants ---

  invariant uniqueEmails:
    all u1 in users, u2 in users |
      u1 != u2 implies users[u1].email != users[u2].email

  invariant emailIndexConsistent:
    all email in user_by_email |
      user_by_email[email] in ran(users)
      and user_by_email[email].email = email

  invariant sessionBelongsToUser:
    all s in sessions |
      sessions[s].user_id in users

  invariant revokedSessionsStayRevoked:
    all s in sessions |
      pre(sessions)[s].is_revoked = true implies sessions'[s].is_revoked = true

  invariant accessTokensUnique:
    all s1 in sessions, s2 in sessions |
      s1 != s2 implies
        sessions[s1].access_token != sessions[s2].access_token

  invariant rateLimitEnforced:
    all email in user_by_email |
      recentFailedAttempts(email) < 5 or
        (no op: Login | op.input.email = email)

  // --- Conventions ---

  conventions {
    Register.http_path = "/auth/register"
    Register.http_status_success = 201

    Login.http_path = "/auth/login"
    Login.http_method = "POST"
    Login.http_status_success = 200

    RefreshToken.http_path = "/auth/refresh"
    RefreshToken.http_method = "POST"

    RequestPasswordReset.http_path = "/auth/password-reset"
    RequestPasswordReset.http_method = "POST"

    ResetPassword.http_path = "/auth/password-reset/confirm"
    ResetPassword.http_method = "POST"

    Logout.http_path = "/auth/logout"
    Logout.http_method = "POST"
    Logout.http_status_success = 204
  }
}
```

### 4.4 Example 4, e-commerce order service

```text
service OrderService {

  // --- Types ---

  type Money = Int where value >= 0           // cents
  type Quantity = Int where value > 0
  type SKU = String where len(value) >= 3 and len(value) <= 20
  type OrderId = Int where value > 0
  type CustomerId = Int where value > 0

  // --- Enums ---

  enum OrderStatus {
    DRAFT,
    PLACED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURNED
  }

  enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    REFUNDED,
    FAILED
  }

  // --- Entities ---

  entity Product {
    sku: SKU
    name: String where len(value) >= 1
    price: Money
    description: Option[String]

    invariant: price > 0
  }

  entity LineItem {
    id: Int where value > 0
    product_sku: SKU
    quantity: Quantity
    unit_price: Money
    line_total: Money

    invariant: line_total = unit_price * quantity
    invariant: unit_price > 0
  }

  entity Order {
    id: OrderId
    customer_id: CustomerId
    status: OrderStatus
    items: Set[LineItem]
    subtotal: Money
    tax: Money
    total: Money
    created_at: DateTime
    updated_at: DateTime
    shipped_at: Option[DateTime]
    delivered_at: Option[DateTime]

    invariant: subtotal = sum(items, i => i.line_total)
    invariant: total = subtotal + tax
    invariant: #items > 0 implies total > 0
    invariant: status = SHIPPED implies shipped_at != none
    invariant: status = DELIVERED implies delivered_at != none
    invariant: delivered_at != none implies shipped_at != none
  }

  entity Payment {
    id: Int where value > 0
    order_id: OrderId
    amount: Money
    status: PaymentStatus
    created_at: DateTime

    invariant: amount > 0
  }

  entity InventoryEntry {
    sku: SKU
    available: Int where value >= 0
    reserved: Int where value >= 0

    invariant: available >= 0
    invariant: reserved >= 0
  }

  // --- State ---

  state {
    orders: OrderId -> lone Order
    products: SKU -> lone Product
    inventory: SKU -> lone InventoryEntry
    payments: Int -> lone Payment
    next_order_id: OrderId
    next_payment_id: Int
  }

  // --- State Machine ---

  transition OrderLifecycle {
    entity: Order
    field: status

    DRAFT      -> PLACED     via PlaceOrder
    DRAFT      -> CANCELLED  via CancelOrder
    PLACED     -> PAID       via RecordPayment     when paymentCaptured(order_id)
    PLACED     -> CANCELLED  via CancelOrder
    PAID       -> SHIPPED    via ShipOrder
    PAID       -> CANCELLED  via CancelOrder       when refundIssued(order_id)
    SHIPPED    -> DELIVERED  via ConfirmDelivery
    DELIVERED  -> RETURNED   via ProcessReturn      when withinReturnWindow(order_id)
  }

  // --- Operations ---

  operation CreateDraftOrder {
    input:  customer_id: CustomerId
    output: order: Order

    requires:
      true

    ensures:
      order.id = pre(next_order_id)
      order.customer_id = customer_id
      order.status = DRAFT
      order.items = {}
      order.subtotal = 0
      order.tax = 0
      order.total = 0
      next_order_id' = pre(next_order_id) + 1
      orders' = pre(orders) + {order.id -> order}
  }

  operation AddLineItem {
    input:  order_id: OrderId, sku: SKU, quantity: Quantity
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = DRAFT
      sku in products
      sku in inventory
      inventory[sku].available >= quantity

    ensures:
      let product = products[sku] in
      let item = LineItem {
        product_sku = sku,
        quantity = quantity,
        unit_price = product.price,
        line_total = product.price * quantity
      } in
        order = pre(orders)[order_id] with {
          items = pre(orders)[order_id].items + {item},
          subtotal = pre(orders)[order_id].subtotal + item.line_total,
          total = pre(orders)[order_id].subtotal + item.line_total
                  + pre(orders)[order_id].tax
        }
        orders' = pre(orders) + {order_id -> order}
        // Reserve inventory
        inventory'[sku].reserved =
          pre(inventory)[sku].reserved + quantity
        inventory'[sku].available =
          pre(inventory)[sku].available - quantity
  }

  operation RemoveLineItem {
    input:  order_id: OrderId, item_id: Int
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = DRAFT
      some i in orders[order_id].items | i.id = item_id

    ensures:
      let removed = (the i in pre(orders)[order_id].items |
                      i.id = item_id) in
        order = pre(orders)[order_id] with {
          items = pre(orders)[order_id].items - {removed},
          subtotal = pre(orders)[order_id].subtotal - removed.line_total,
          total = pre(orders)[order_id].subtotal - removed.line_total
                  + pre(orders)[order_id].tax
        }
        orders' = pre(orders) + {order_id -> order}
        // Release reserved inventory
        inventory'[removed.product_sku].reserved =
          pre(inventory)[removed.product_sku].reserved - removed.quantity
        inventory'[removed.product_sku].available =
          pre(inventory)[removed.product_sku].available + removed.quantity
  }

  operation PlaceOrder {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = DRAFT
      #orders[order_id].items > 0

    ensures:
      order = pre(orders)[order_id] with { status = PLACED }
      orders' = pre(orders) + {order_id -> order}
      inventory' = inventory    // reservations stay
  }

  operation RecordPayment {
    input:  order_id: OrderId, amount: Money
    output: payment: Payment

    requires:
      order_id in orders
      orders[order_id].status = PLACED
      amount = orders[order_id].total

    ensures:
      payment.order_id = order_id
      payment.amount = amount
      payment.status = CAPTURED
      orders'[order_id].status = PAID
      payments' = pre(payments) + {payment.id -> payment}
      next_payment_id' = pre(next_payment_id) + 1
  }

  operation ShipOrder {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = PAID

    ensures:
      order = pre(orders)[order_id] with {
        status = SHIPPED,
        shipped_at = some(now())
      }
      orders' = pre(orders) + {order_id -> order}
      // Committed inventory: reduce reserved count
      all item in order.items |
        inventory'[item.product_sku].reserved =
          pre(inventory)[item.product_sku].reserved - item.quantity
  }

  operation ConfirmDelivery {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = SHIPPED

    ensures:
      order = pre(orders)[order_id] with {
        status = DELIVERED,
        delivered_at = some(now())
      }
      orders' = pre(orders) + {order_id -> order}
  }

  operation CancelOrder {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status in {DRAFT, PLACED, PAID}

    ensures:
      order = pre(orders)[order_id] with { status = CANCELLED }
      orders' = pre(orders) + {order_id -> order}
      // Release all reserved inventory
      all item in pre(orders)[order_id].items |
        inventory'[item.product_sku].available =
          pre(inventory)[item.product_sku].available + item.quantity
        and inventory'[item.product_sku].reserved =
          pre(inventory)[item.product_sku].reserved - item.quantity
      // Refund if paid
      orders[order_id].status = PAID implies
        some p in payments' |
          p not in pre(payments)
          and payments'[p].order_id = order_id
          and payments'[p].status = REFUNDED
  }

  operation ProcessReturn {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = DELIVERED
      // Within 30-day return window
      orders[order_id].delivered_at != none
      now() - orders[order_id].delivered_at <= days(30)

    ensures:
      order = pre(orders)[order_id] with { status = RETURNED }
      orders' = pre(orders) + {order_id -> order}
      // Restore inventory
      all item in pre(orders)[order_id].items |
        inventory'[item.product_sku].available =
          pre(inventory)[item.product_sku].available + item.quantity
      // Issue refund
      some p in payments' |
        p not in pre(payments)
        and payments'[p].order_id = order_id
        and payments'[p].status = REFUNDED
        and payments'[p].amount = pre(orders)[order_id].total
  }

  operation GetOrder {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders

    ensures:
      order = orders[order_id]
      orders' = orders
  }

  operation ListOrders {
    input:  customer_id: Option[CustomerId],
            status_filter: Option[OrderStatus]
    output: results: Seq[Order]

    requires:
      true

    ensures:
      all o in results |
        o in ran(orders)
        and (customer_id = none or o.customer_id = customer_id)
        and (status_filter = none or o.status = status_filter)
      orders' = orders
  }

  // --- Global Invariants ---

  invariant orderTotalConsistency:
    all oid in orders |
      orders[oid].total =
        sum(orders[oid].items, i => i.line_total) + orders[oid].tax

  invariant lineItemTotalConsistency:
    all oid in orders |
      all item in orders[oid].items |
        item.line_total = item.unit_price * item.quantity

  invariant inventoryNonNegative:
    all sku in inventory |
      inventory[sku].available >= 0
      and inventory[sku].reserved >= 0

  invariant placedOrdersHaveItems:
    all oid in orders |
      orders[oid].status != DRAFT implies #orders[oid].items > 0

  invariant paidOrdersHavePayments:
    all oid in orders |
      orders[oid].status in {PAID, SHIPPED, DELIVERED} implies
        some pid in payments |
          payments[pid].order_id = oid
          and payments[pid].status = CAPTURED

  invariant cancelledPaidOrdersHaveRefunds:
    all oid in orders |
      orders[oid].status = CANCELLED implies
        (all pid in payments |
          payments[pid].order_id = oid and payments[pid].status = CAPTURED
          implies
          some rid in payments |
            payments[rid].order_id = oid and payments[rid].status = REFUNDED)

  invariant inventoryReservationsMatchOrders:
    all sku in inventory |
      inventory[sku].reserved =
        sum({ item in { oid in orders | orders[oid].status in {DRAFT, PLACED, PAID} } |
              some i in orders[item].items | i.product_sku = sku },
            order_id => sum(
              { i in orders[order_id].items | i.product_sku = sku },
              i => i.quantity))

  // --- Conventions ---

  conventions {
    CreateDraftOrder.http_path = "/orders"
    CreateDraftOrder.http_status_success = 201

    AddLineItem.http_method = "POST"
    AddLineItem.http_path = "/orders/{order_id}/items"
    AddLineItem.http_status_success = 201

    RemoveLineItem.http_method = "DELETE"
    RemoveLineItem.http_path = "/orders/{order_id}/items/{item_id}"

    PlaceOrder.http_method = "POST"
    PlaceOrder.http_path = "/orders/{order_id}/place"

    RecordPayment.http_method = "POST"
    RecordPayment.http_path = "/orders/{order_id}/payments"
    RecordPayment.http_status_success = 201

    ShipOrder.http_method = "POST"
    ShipOrder.http_path = "/orders/{order_id}/ship"

    ConfirmDelivery.http_method = "POST"
    ConfirmDelivery.http_path = "/orders/{order_id}/deliver"

    CancelOrder.http_method = "POST"
    CancelOrder.http_path = "/orders/{order_id}/cancel"

    ProcessReturn.http_method = "POST"
    ProcessReturn.http_path = "/orders/{order_id}/return"

    GetOrder.http_path = "/orders/{order_id}"
    ListOrders.http_path = "/orders"
  }
}
```
