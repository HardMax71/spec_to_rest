import SpecRest.IR
import SpecRest.Semantics
import SpecRest.Lemmas

/-! # M_L.3 — Per-run translation-validation certificates.

For every `verify` run on a spec whose `Expr`s lie in the M_L.1 verified
subset, the Scala emitter (`modules/verify/.../cert/Emit.scala`) writes a
`<spec>.cert.lean` file containing one `theorem cert_<id>` per
verification check. Each theorem's body uses `native_decide` to discharge
a closed-evaluation goal of the shape
`evalInvariant <schema> <state> <env> <inv> = some <expected_bool>` (or
the analogous `evalRequiresAll` shape for operation `requires` checks).

This file exposes the import surface those generated theorems rely on.
The actual proofs reduce to kernel + native-decide computation; the
trust-relevant axiom is `Lean.ofReduceBool`, in addition to whatever
the M_L.1 / M_L.2 surface already requires.

Out-of-subset cases emit `theorem cert_<id> ... := by sorry` with a
comment naming the offending `Expr` constructor; those certificates do
not improve trust but do compile, so a `lake build` can still succeed
on a mixed-subset bundle.

This is Path A from research doc §5.1: cheap per-run trust improvement,
no universal quantifier, reuses M_L.1's denotational semantics. -/

namespace SpecRest.Cert

open SpecRest

/-- Tactic alias used by every emitted certificate. Reduces to
    `native_decide`, which closes the closed-evaluation goal via the
    Lean compiler and the `Lean.ofReduceBool` axiom. -/
syntax "cert_decide" : tactic

macro_rules
  | `(tactic| cert_decide) => `(tactic| native_decide)

end SpecRest.Cert
