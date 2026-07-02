package specrest

package object synth:
  // v2: ServiceStateInv carries the schema-derived refinements (entity field
  // aliases, relation key/value aliases), so bodies verified under the v1
  // contract are not sound against the reconciled runtime and must re-verify.
  val SynthPromptVersion: String = "v2"
