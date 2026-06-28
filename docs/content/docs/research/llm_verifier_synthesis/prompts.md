---
title: "Prompts"
description: "How the initial and repair prompts are built, the three strategies, and few-shot selection"
---

The loop talks to the model through two prompts, an initial one and, after a failure, a repair one.
Both are assembled by `PromptBuilder` from text in resource files under `/specrest/synth/prompts`, so
the wording is versioned with the code rather than buried in the controller.

## The initial prompt

The system message states the rules: return only the method body, leave the signature and its
`requires`, `ensures`, and `modifies` clauses alone, add the assertions and invariants the verifier
needs, prove existence before an assign-such-that, give a `decreases` for any loop, and prefer the
plain implementation over the clever one. The user message is built from four sections, the method
signature, a prose description of the operation, the type definitions the body will touch, and a few
verified examples:

```text
SYSTEM: an expert Dafny programmer; body-only output, contracts untouched.

USER:
## Method signature      <the method, with its requires/ensures/modifies>
## Domain                <what the operation does, in prose>
## Type definitions      <the types and predicates the body references>
## Similar examples      <one to three few-shot methods>
## Your task             <implement the body; helper lemmas first; answer in a dafny block>
```

## Three strategies

The system message is not fixed; it comes in three flavors, one per `PromptStrategy`, each its own
resource file. `ZeroShot` is the default and asks directly. `ChainOfThought` asks the model to reason
about the obligations first. `PlanThenImplement` asks for a plan, then the body. The fallback walks
them in order, ZeroShot, then ChainOfThought, then PlanThenImplement, so a retry is not the same
request again; it is the same request asked a different way. That escalation order lives on the
[fallback](/research/llm_verifier_synthesis/triangulation-and-fallback) page.

## Few-shot examples

The examples are not retrieved by similarity. They are selected by the operation's kind,
`selectFor(operation_kind)`, from a small library of verified methods kept as resource files under
`/specrest/synth/few-shot`. A create-shaped operation draws map-insert examples, a delete draws
map-remove, and so on. Two of the library's entries:

```csharp
method Insert<K, V>(m: map<K, V>, k: K, v: V) returns (m': map<K, V>)
  requires k !in m
  ensures m' == m[k := v]
  ensures |m'| == |m| + 1
{ m' := m[k := v]; }
```

```csharp
method UpdateField(st: ServiceState, id: UserId, newName: string)
  modifies st
  requires id in st.users
  ensures st.users[id].name == newName
  ensures forall uid :: uid in st.users && uid != id ==> st.users[uid] == old(st.users[uid])
  ensures |st.users| == |old(st.users)|
{ st.users := st.users[id := User(newName, st.users[id].email)]; }
```

## The repair prompt

After a failed verification the prompt switches to the repair system message and adds the failure
context: the previous body, the verifier's error (its category, the clause that failed, and a
counterexample when there is one), and a hint chosen for that category from `HintLibrary`, which is
resource-backed too. The signature is restated, marked not to touch, and the model is asked for a
corrected body:

```text
SYSTEM: the repair system prompt.

USER:
## Previous attempt              <the rejected body>
## Verifier error                <category, line, message, the failed clause>
## Counterexample                <concrete values, or "not available">
## Hint                          <the category's repair hint>
## Method signature (do not modify)
## Your task                     <fix the body; return it in full>
```

One guard runs before the verifier ever sees a repaired candidate: the diff-checker compares the
candidate's contract against the original and rejects it outright if the model changed it. Models
reliably try to weaken a stubborn postcondition, turning `|st.store| == |old(st.store)| + 1` into a
`>=`, and the diff-check catches that without spending a verification on it.

## Not built yet

Two ideas from the literature shape the design but are not implemented. DafnyPro's proactive hint
augmentation, injecting a proof lemma before the first attempt rather than after a failure, is
roadmap work (issue #229), and Laurel's assertion localization, dropping `assert` placeholders at the
failing line for the model to fill, is not built. Today the hints are reactive, attached to the
repair prompt by error category.
