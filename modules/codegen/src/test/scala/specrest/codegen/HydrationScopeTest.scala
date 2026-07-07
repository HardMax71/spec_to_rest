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
  // scans, and identity-frame skips.
  private val expectations: List[(String, String, String, Scope)] = List(
    ("ecommerce", "GetOrder", "orders", keys("order_id")),
    ("ecommerce", "ListOrders", "orders", Scope.Full),
    ("ecommerce", "CreateDraftOrder", "orders", keys()),
    ("ecommerce", "AddLineItem", "orders", keys("order_id")),
    ("ecommerce", "AddLineItem", "products", keys("sku")),
    ("ecommerce", "AddLineItem", "inventory", keys("sku")),
    ("ecommerce", "RecordPayment", "orders", keys("order_id")),
    ("ecommerce", "RecordPayment", "payments", keys()),
    ("ecommerce", "PlaceOrder", "orders", keys("order_id")),
    // inventory' = inventory: the body leaves it alone, nothing hydrates.
    ("ecommerce", "PlaceOrder", "inventory", Scope.Skip),
    // inventory keyed by a field of a definite-description row: fail-open.
    ("ecommerce", "RemoveLineItem", "inventory", Scope.Full),
    ("ecommerce", "RemoveLineItem", "orders", keys("order_id")),
    ("todo_list", "CreateTodo", "todos", keys()),
    ("todo_list", "UpdateTodo", "todos", keys("id")),
    ("todo_list", "ListTodos", "todos", Scope.Full),
    ("todo_list", "DeleteTodo", "todos", keys("id")),
    ("auth_service", "Login", "sessions", Scope.Full),
    ("auth_service", "Login", "user_by_email", keys("email")),
    ("auth_service", "Login", "failed_logins", keys("email")),
    ("auth_service", "Login", "users", keys()),
    ("auth_service", "Login", "login_attempts", Scope.Full),
    ("auth_service", "RefreshToken", "sessions", Scope.Full),
    ("auth_service", "RequestPasswordReset", "user_by_email", keys("email")),
    ("auth_service", "RequestPasswordReset", "reset_tokens", keys()),
    ("url_shortener", "Resolve", "store", keys("code")),
    ("url_shortener", "Resolve", "metadata", keys("code")),
    // Candidate-freshness not-in and output-keyed writes: full.
    ("url_shortener", "Shorten", "store", Scope.Full),
    ("url_shortener", "Shorten", "metadata", Scope.Full),
    ("url_shortener", "ListAll", "metadata", Scope.Full),
    ("url_shortener", "ListAll", "store", Scope.Skip)
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
