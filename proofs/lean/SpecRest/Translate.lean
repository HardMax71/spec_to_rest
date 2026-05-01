import SpecRest.IR
import SpecRest.Smt

namespace SpecRest

/-! # Lean mirror of `modules/verify/src/main/scala/specrest/verify/z3/Translator.scala`.

Restricted to the §6.1 verified subset (research doc §6). For each `Expr`
constructor in scope, we emit a single `SmtTerm`; the M_L.2 soundness
theorem is `valueToSmt? (eval s st env e) = smtEval (correlateModel s st)
(correlateEnv env) (translate e)` for every `Expr e` in the subset.

Audit appendix mapping each Lean case to the Scala translator's line
range lives in `proofs/lean/README.md`. -/

def translate : Expr → SmtTerm
  | .boolLit b => .bLit b
  | .intLit n  => .iLit n
  | .ident x   => .var x
  | .unNot e   => .not (translate e)
  | .unNeg e   => .neg (translate e)
  | .boolBin .and     l r => .and (translate l) (translate r)
  | .boolBin .or      l r => .or (translate l) (translate r)
  | .boolBin .implies l r => .implies (translate l) (translate r)
  | .boolBin .iff     l r =>
      .and (.implies (translate l) (translate r))
           (.implies (translate r) (translate l))
  | .arith .add l r => .add (translate l) (translate r)
  | .arith .sub l r => .sub (translate l) (translate r)
  | .arith .mul l r => .mul (translate l) (translate r)
  | .arith .div l r => .div (translate l) (translate r)
  | .cmp .eq  l r => .eq (translate l) (translate r)
  | .cmp .neq l r => .not (.eq (translate l) (translate r))
  | .cmp .lt  l r => .lt (translate l) (translate r)
  | .cmp .le  l r =>
      .or (.lt (translate l) (translate r)) (.eq (translate l) (translate r))
  | .cmp .gt  l r => .lt (translate r) (translate l)
  | .cmp .ge  l r =>
      .or (.lt (translate r) (translate l)) (.eq (translate l) (translate r))
  | .letIn x v body          => .letIn x (translate v) (translate body)
  | .enumAccess en memberName => .enumElemConst en memberName
  | .member elem relName     => .inDom relName (translate elem)
  | .forallEnum var en body  => .forallEnum var en (translate body)
  | .prime e                  => translate e
  | .pre   e                  => translate e
  | .cardRel relName          => .cardRel relName

end SpecRest
