package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.HydrationScope.KeySource
import specrest.codegen.HydrationScope.Scope
import specrest.codegen.testutil.SpecFixtures
import specrest.ir.generated.SpecRestGenerated.operName
import specrest.ir.generated.SpecRestGenerated.svcOperations

class HydrationScopeTest extends CatsEffectSuite:

  private def keys(names: String*): Scope      = Scope.Keys(names.toList.map(KeySource.Input(_)))
  private def srcs(sources: KeySource*): Scope = Scope.Keys(sources.toList)

  private val orderPayments = KeySource.ValueColumn("orders", "order_id")
  private val orderItemSkus = KeySource.DependentField("orders", "items", "product_sku")
  private val usersFromUbe  = KeySource.FieldOfRows("user_by_email", "id")
  private val usersFromSess = KeySource.FieldOfRows("sessions", "user_id")
  private val ubeFromUsers  = KeySource.FieldOfRows("users", "email")

  // The census over the four fixture specs, condensed to the load-bearing
  // shapes: keyed reads, persist-only inserts, forced-full searches and
  // scans, identity-frame skips, and the invariant closure's support loads.
  // The ecommerce orders/payments reference cycle is cut structurally (the
  // payments ValueColumn filter confines every loaded row's reference to
  // the hydrated order keys), and the auth users/user_by_email cycle is
  // certified by the bilateral index-equality conjuncts, so both sides stay
  // keyed with derived sources instead of collapsing to Full.
  private val expectations: List[(String, String, String, Scope)] = List(
    ("ecommerce", "GetOrder", "orders", keys("order_id")),
    ("ecommerce", "GetOrder", "inventory", srcs(orderItemSkus)),
    ("ecommerce", "GetOrder", "payments", srcs(orderPayments)),
    ("ecommerce", "ListOrders", "orders", Scope.Full),
    // A ValueColumn filter from a fully hydrated source, combined with the
    // covering back-membership (every payment references an order), selects
    // the whole relation; the closure loads it whole outright.
    ("ecommerce", "ListOrders", "payments", Scope.Full),
    ("ecommerce", "ListOrders", "inventory", srcs(orderItemSkus)),
    ("ecommerce", "CreateDraftOrder", "orders", keys()),
    // A persist-only orders scope is empty at guard time, so the closure
    // stays quiet and the draft insert touches nothing else.
    ("ecommerce", "CreateDraftOrder", "inventory", Scope.Skip),
    ("ecommerce", "CreateDraftOrder", "payments", Scope.Skip),
    ("ecommerce", "AddLineItem", "orders", keys("order_id")),
    ("ecommerce", "AddLineItem", "products", keys("sku")),
    ("ecommerce", "AddLineItem", "inventory", srcs(KeySource.Input("sku"), orderItemSkus)),
    ("ecommerce", "AddLineItem", "payments", srcs(orderPayments)),
    ("ecommerce", "RecordPayment", "orders", keys("order_id")),
    ("ecommerce", "RecordPayment", "payments", srcs(orderPayments)),
    ("ecommerce", "PlaceOrder", "orders", keys("order_id")),
    ("ecommerce", "PlaceOrder", "inventory", srcs(orderItemSkus)),
    ("ecommerce", "PlaceOrder", "payments", srcs(orderPayments)),
    // The definite-description binding (`the i in ...items`) is dependent-
    // transparent, so inventory keys off the removed item's sku through the
    // same nested hop the closure uses.
    ("ecommerce", "RemoveLineItem", "inventory", srcs(orderItemSkus)),
    ("ecommerce", "RemoveLineItem", "orders", keys("order_id")),
    ("ecommerce", "RemoveLineItem", "payments", srcs(orderPayments)),
    ("todo_list", "CreateTodo", "todos", keys()),
    ("todo_list", "UpdateTodo", "todos", keys("id")),
    ("todo_list", "ListTodos", "todos", Scope.Full),
    ("todo_list", "DeleteTodo", "todos", keys("id")),
    ("auth_service", "Login", "sessions", Scope.Full),
    ("auth_service", "Login", "user_by_email", srcs(KeySource.Input("email"), ubeFromUsers)),
    ("auth_service", "Login", "users", srcs(usersFromUbe, usersFromSess)),
    ("auth_service", "Login", "failed_logins", keys("email")),
    ("auth_service", "Login", "login_attempts", Scope.Full),
    ("auth_service", "Login", "reset_tokens", Scope.Skip),
    ("auth_service", "Register", "user_by_email", srcs(KeySource.Input("email"), ubeFromUsers)),
    ("auth_service", "Register", "users", srcs(usersFromUbe)),
    ("auth_service", "Register", "sessions", Scope.Skip),
    ("auth_service", "RefreshToken", "sessions", Scope.Full),
    ("auth_service", "RefreshToken", "users", srcs(usersFromSess, usersFromUbe)),
    ("auth_service", "RefreshToken", "user_by_email", srcs(ubeFromUsers)),
    (
      "auth_service",
      "RequestPasswordReset",
      "user_by_email",
      srcs(KeySource.Input("email"), ubeFromUsers)
    ),
    ("auth_service", "RequestPasswordReset", "users", srcs(usersFromUbe)),
    ("auth_service", "RequestPasswordReset", "reset_tokens", keys()),
    ("auth_service", "ResetPassword", "reset_tokens", keys("reset_token")),
    // The let-bound token row field keys the users read, and the bilateral
    // cycle certificate keeps the derived chain instead of Full.
    (
      "auth_service",
      "ResetPassword",
      "users",
      srcs(KeySource.FieldOfRows("reset_tokens", "user_id"), usersFromUbe)
    ),
    ("auth_service", "ResetPassword", "user_by_email", srcs(ubeFromUsers)),
    ("url_shortener", "Resolve", "store", keys("code")),
    // dom(store) = dom(metadata) stays aligned: both keyed by the same
    // input, so the closure leaves the keyed scopes alone.
    ("url_shortener", "Resolve", "metadata", keys("code")),
    // Candidate-freshness not-in and output-keyed writes: full.
    ("url_shortener", "Shorten", "store", Scope.Full),
    ("url_shortener", "Shorten", "metadata", Scope.Full),
    ("url_shortener", "ListAll", "metadata", Scope.Full),
    // Alignment raises the skipped side to match the full one.
    ("url_shortener", "ListAll", "store", Scope.Full)
  )

  private def normalize(sc: Scope): Scope = sc match
    case Scope.Keys(sources) => Scope.Keys(sources.sortBy(_.toString))
    case other               => other

  expectations.groupBy(_._1).toList.sortBy(_._1).foreach: (spec, rows) =>
    test(s"$spec hydration scopes match the contract census"):
      SpecFixtures.loadIR(spec).map: ir =>
        rows.foreach { case (_, opName, field, expected) =>
          val op = svcOperations(ir)
            .find(o => operName(o) == opName)
            .getOrElse(fail(s"$spec has no operation '$opName'"))
          val got = HydrationScope.analyze(op, ir)(field)
          assertEquals(normalize(got), normalize(expected), s"$opName/$field")
        }
