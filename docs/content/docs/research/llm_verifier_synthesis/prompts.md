---
title: "Prompts"
description: "The shipped system prompts, the three strategies, few-shot selection, and the repair prompt"
---

The loop talks to the model through two prompts, an initial one and, after a failure, a repair one.
Both are assembled by [`PromptBuilder`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/synth/src/main/scala/specrest/synth/PromptBuilder.scala) from text in resource files under `/specrest/synth/prompts`, so
the wording is versioned with the code rather than buried in the controller. The blocks below are the
files as shipped.

## The initial prompt

The default `ZeroShot` system message:

```text
You are an expert Dafny programmer specializing in verified implementations.
Your task is to produce a method body that the Dafny verifier will accept.

Rules:
1. You MUST NOT modify the method signature, requires, ensures, or modifies clauses.
2. You MUST produce code that satisfies ALL postconditions.
3. You MAY add local variables, assertions, loop invariants, and ghost code.
4. You MAY define helper lemmas inline before the method.
5. You SHOULD add intermediate assertions to help the verifier.
6. You SHOULD use `calc` blocks for complex multi-step reasoning.
7. You MUST NOT introduce new {:extern} declarations.
8. Prefer simple, straightforward implementations over clever ones.

Return your code inside a single ```dafny fenced block. Include any helper
lemmas you need before the method declaration. Do not paraphrase or weaken
contracts to make verification easier — that is rejected by the diff checker.
```

The user message that follows it is built from five sections: the method signature, a prose
description of the operation, the type definitions the body will touch, one to two verified
examples, and the task to perform.

## Three strategies

The system message comes in three flavors, one per [`PromptStrategy`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/synth/src/main/scala/specrest/synth/PromptStrategy.scala), each its own resource file.
`ZeroShot` asks directly. `ChainOfThought` asks the model to reason about the obligations first.
`PlanThenImplement` asks for a plan, then the body. The fallback walks them in order, ZeroShot, then
ChainOfThought, then PlanThenImplement, so a retry is not the same request again; it is the same
request asked a different way. That escalation order lives on the
[fallback](/research/llm_verifier_synthesis/triangulation-and-fallback) page.

## Few-shot examples

The examples are not retrieved by similarity. They are selected by the operation's kind,
[`selectFor(operation_kind)`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/synth/src/main/scala/specrest/synth/FewShot.scala), from a five-file library kept as resources. A create draws the
fresh-insert and counter examples, a delete draws map-remove, a filtered read draws the sequence-sum,
and a transition draws the state-modify. Two of the files, verbatim:

```csharp
// few-shot/map_insert_fresh.dfy
// Inserting a fresh key into a map
method Insert<K(==),V>(m: map<K,V>, k: K, v: V) returns (m': map<K,V>)
  requires k !in m
  ensures m' == m[k := v]
  ensures |m'| == |m| + 1
{
  m' := m[k := v];
}
```

```csharp
// few-shot/state_modify.dfy
// Updating a counter field on a class while leaving other state untouched
class Counter {
  var count: int
}

method Increment(c: Counter)
  modifies c
  requires c.count >= 0
  ensures c.count == old(c.count) + 1
{
  c.count := c.count + 1;
}
```

## The repair prompt

After a failed verification the system message switches to the repair one:

```text
You are an expert Dafny programmer fixing a verified implementation.
Your previous attempt failed verification. You will be shown the candidate body
and the verifier's diagnosis. Produce a corrected method body.

Rules:
1. You MUST NOT modify the method signature, requires, ensures, or modifies clauses.
2. You MUST address the specific verifier failure described below.
3. You MAY add local variables, assertions, loop invariants, and ghost code.
4. You MAY define helper lemmas inline before the method.
5. You MUST NOT introduce new {:extern} declarations.

Return your COMPLETE corrected body inside a single ```dafny fenced block.
Do not weaken contracts — the diff checker will reject any signature change.
```

The user message adds the previous body, the verifier's error and the clause it failed, a
counterexample when there is one, and a hint chosen for that error's category from [`HintLibrary`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/synth/src/main/scala/specrest/synth/HintLibrary.scala). The
hints are resource files too, each a small worked pattern. The one for a quantified-postcondition
failure shows the helper-lemma trick and warns off the unsound shortcut:

```csharp
// hints/postcondition_helper_lemma.dfy
lemma ElementPreservedAfterInsert<K(==), V>(s: map<K, V>, k: K, v: V, k': K)
  requires k' in s && k' != k
  ensures (s[k := v])[k'] == s[k']
{
  // Proven by Dafny; the lemma block is REQUIRED so the claim is a discharged
  // obligation, not an {:axiom} the synthesizer could "verify" against falsely.
}
```

One guard runs before the verifier sees a repaired candidate: the diff-checker compares the
candidate's contract against the original and rejects it if the model changed it. Models reliably try
to weaken a stubborn postcondition, turning `|st.store| == |old(st.store)| + 1` into a `>=`, and the
diff-check catches that without spending a verification on it.

## Not built yet

The hints today are hand-curated and reactive: a person wrote each one, and they attach to the repair
prompt by error category. Two extensions from the literature are not in place. Automatic hint
discovery, mining new patterns from the codebase's own verified runs instead of writing them by hand,
is a follow-up. And Laurel's assertion localization, dropping `assert` placeholders at the failing
line for the model to fill, is not built; the hints stay at the prompt level rather than editing the
candidate.
