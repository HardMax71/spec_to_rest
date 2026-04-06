service OrderService {

  // --- Types ---

  type Money = Int where value >= 0
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
      inventory' = inventory
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
      all item in pre(orders)[order_id].items |
        inventory'[item.product_sku].available =
          pre(inventory)[item.product_sku].available + item.quantity
        and inventory'[item.product_sku].reserved =
          pre(inventory)[item.product_sku].reserved - item.quantity
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
      orders[order_id].delivered_at != none
      now() - orders[order_id].delivered_at <= days(30)

    ensures:
      order = pre(orders)[order_id] with { status = RETURNED }
      orders' = pre(orders) + {order_id -> order}
      all item in pre(orders)[order_id].items |
        inventory'[item.product_sku].available =
          pre(inventory)[item.product_sku].available + item.quantity
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
