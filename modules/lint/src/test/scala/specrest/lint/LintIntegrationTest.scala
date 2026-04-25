package specrest.lint

import munit.CatsEffectSuite
import specrest.lint.testutil.SpecFixtures

class LintIntegrationTest extends CatsEffectSuite:

  // Specs expected to be lint-clean. ecommerce/edge_cases excluded:
  //   ecommerce — `let X in clauseA \n clauseB` parses as let-body=clauseA; clauseB
  //               loses the binding, surfacing as L02 'undefined removed'. Real
  //               authoring bug latent in the spec; the verifier is silent because
  //               unbound names get an uninterpreted Z3 constant.
  //   edge_cases — declares `entity Child extends Base` but never references Child
  //                anywhere downstream; correctly caught by L05.
  private val fixtures = List(
    "auth_service",
    "broken_decrement",
    "powerset_demo",
    "safe_counter",
    "set_comp_demo",
    "set_ops",
    "todo_list",
    "url_shortener"
  )

  fixtures.foreach: name =>
    test(s"Lint.run is silent on fixtures/spec/$name.spec"):
      SpecFixtures.loadIR(name).map: ir =>
        val diags = Lint.run(ir)
        val detail = diags
          .map(d => s"[${d.code} L${d.span.fold("?")(_.startLine.toString)}] ${d.message}")
          .mkString("\n")
        assert(diags.isEmpty, s"expected no diagnostics on $name; got:\n$detail")
