package specrest.synth

import scala.io.Source
import scala.util.Using

final case class Hint(
    name: String,
    category: String,
    description: String
) derives CanEqual:
  lazy val snippet: String = HintLibrary.loadSnippet(name)

object HintLibrary:

  private val resourceRoot = "/specrest/synth/hints"

  val all: List[Hint] = List(
    Hint(
      "postcondition_capture_old",
      "postcondition_violation",
      "Save old(...) values into ghost-friendly local vars at method entry; reference these in postcondition assertions."
    ),
    Hint(
      "postcondition_branch_assert",
      "postcondition_violation",
      "Push a disjunctive postcondition into per-branch asserts so the verifier handles each side independently."
    ),
    Hint(
      "postcondition_helper_lemma",
      "postcondition_violation",
      "Extract a forall-over-collection postcondition into a helper lemma the body invokes element-wise."
    ),
    Hint(
      "precondition_guard",
      "precondition_violation",
      "Guard a method call with an `if` block that proves the callee's precondition holds in that branch."
    ),
    Hint(
      "precondition_strengthen_local",
      "precondition_violation",
      "Restate a hard-to-establish callee precondition as a local assertion proven from prior facts."
    ),
    Hint(
      "loop_invariant_strengthen",
      "loop_invariant_failure",
      "Strengthen the loop invariant with counter bounds, accumulator-vs-prefix relations, and prefix-freeze clauses."
    ),
    Hint(
      "loop_invariant_initially",
      "loop_invariant_not_established",
      "Initialise locals before the loop so the invariant trivially holds at entry, or weaken the invariant."
    ),
    Hint(
      "loop_init_before_loop",
      "loop_invariant_not_established",
      "Declare and assign loop-state locals BEFORE the while; entry-state must satisfy the invariant unaided."
    ),
    Hint(
      "decreases_metric",
      "decreases_failure",
      "Pick a metric (e.g. `|s| - i` or sequence length) that strictly decreases on each iteration / recursive call."
    ),
    Hint(
      "decreases_lexicographic",
      "decreases_failure",
      "Use a tuple `decreases a, b` when a single metric doesn't shrink monotonically but a lexicographic pair does."
    ),
    Hint(
      "assertion_intermediate_lemma",
      "assertion_failure",
      "Factor a stuck inline assertion into an intermediate lemma whose post the call site can directly cite."
    ),
    Hint(
      "timeout_split_proof",
      "timeout",
      "Insert `{:split_here}` markers between groups of postconditions so the SMT solver verifies them in batches."
    )
  )

  def forCategory(category: String, limit: Int = 2): List[Hint] =
    all.filter(_.category == category).take(limit)

  def categories: Set[String] = all.map(_.category).toSet

  def loadSnippet(name: String): String =
    val path = s"$resourceRoot/$name.dfy"
    val stream = Option(getClass.getResourceAsStream(path))
      .getOrElse(sys.error(s"Hint resource not found: $path"))
    Using.resource(Source.fromInputStream(stream, "UTF-8"))(_.mkString)
