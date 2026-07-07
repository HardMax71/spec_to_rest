package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.HydrationScope.KeySource
import specrest.codegen.HydrationScope.Scope
import specrest.codegen.testutil.SpecFixtures
import specrest.ir.generated.SpecRestGenerated.operName
import specrest.ir.generated.SpecRestGenerated.svcOperations

class HydrationScopeTest extends CatsEffectSuite:

  private def keys(names: String*): Scope = Scope.Keys(names.toList.map(KeySource.Input(_)))

  // The census over the four fixture specs, condensed to the load-bearing
  // shapes: keyed reads, persist-only inserts, forced-full searches and
  // scans, identity-frame skips, and the invariant closure (cross-relation
  // invariant references load whole once their domain relation hydrates).
  private val expectations: List[(String, String, String, Scope)] = List(
    // paidOrdersHavePayments and paymentsReferenceExistingOrders form a
    // cycle (orders reference payments, payments reference orders), so any
    // op hydrating a nonempty orders slice fixpoints to whole orders and
    // payments, and itemSkusStocked drags inventory along. The way out is
    // reverse-reference keyed loads (payments filtered by the hydrated
    // order ids), deferred with KeySource.DependentField.
    ("ecommerce", "GetOrder", "orders", Scope.Full),
    ("ecommerce", "GetOrder", "inventory", Scope.Full),
    ("ecommerce", "GetOrder", "payments", Scope.Full),
    ("ecommerce", "ListOrders", "orders", Scope.Full),
    ("ecommerce", "CreateDraftOrder", "orders", keys()),
    // A persist-only orders scope is empty at guard time, so the closure
    // stays quiet and the draft insert touches nothing else.
    ("ecommerce", "CreateDraftOrder", "inventory", Scope.Skip),
    ("ecommerce", "AddLineItem", "orders", Scope.Full),
    ("ecommerce", "AddLineItem", "products", keys("sku")),
    ("ecommerce", "AddLineItem", "inventory", Scope.Full),
    ("ecommerce", "RecordPayment", "orders", Scope.Full),
    ("ecommerce", "RecordPayment", "payments", Scope.Full),
    ("ecommerce", "PlaceOrder", "orders", Scope.Full),
    ("ecommerce", "PlaceOrder", "inventory", Scope.Full),
    ("ecommerce", "RemoveLineItem", "inventory", Scope.Full),
    ("ecommerce", "RemoveLineItem", "orders", Scope.Full),
    ("todo_list", "CreateTodo", "todos", keys()),
    ("todo_list", "UpdateTodo", "todos", keys("id")),
    ("todo_list", "ListTodos", "todos", Scope.Full),
    ("todo_list", "DeleteTodo", "todos", keys("id")),
    ("auth_service", "Login", "sessions", Scope.Full),
    // emailIndexConsistent and userKeyMatchesId tie the user relations
    // together, so hydrating either pulls both whole.
    ("auth_service", "Login", "user_by_email", Scope.Full),
    ("auth_service", "Login", "users", Scope.Full),
    ("auth_service", "Login", "failed_logins", keys("email")),
    ("auth_service", "Login", "login_attempts", Scope.Full),
    ("auth_service", "Login", "reset_tokens", Scope.Skip),
    ("auth_service", "RefreshToken", "sessions", Scope.Full),
    ("auth_service", "RequestPasswordReset", "user_by_email", Scope.Full),
    ("auth_service", "RequestPasswordReset", "users", Scope.Full),
    ("auth_service", "RequestPasswordReset", "reset_tokens", keys()),
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

  expectations.groupBy(_._1).toList.sortBy(_._1).foreach: (spec, rows) =>
    test(s"$spec hydration scopes match the contract census"):
      SpecFixtures.loadIR(spec).map: ir =>
        rows.foreach { case (_, opName, field, expected) =>
          val op = svcOperations(ir)
            .find(o => operName(o) == opName)
            .getOrElse(fail(s"$spec has no operation '$opName'"))
          val got = HydrationScope.analyze(op, ir)(field)
          val normalized = got match
            case Scope.Keys(srcs) =>
              Scope.Keys(srcs.sortBy {
                case KeySource.Input(n)                   => n
                case KeySource.DependentField(o, _, _, _) => o
              })
            case other => other
          assertEquals(normalized, expected, s"$opName/$field")
        }
