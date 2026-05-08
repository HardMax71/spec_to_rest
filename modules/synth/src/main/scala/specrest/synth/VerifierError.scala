package specrest.synth

final case class VerifierError(
    category: String,
    message: String,
    line: Option[Int] = None,
    relatedClause: Option[String] = None,
    counterexample: Option[String] = None
)
