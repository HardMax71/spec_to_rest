---
title: "Prompts"
description: "Prompt structure, few-shot selection, failure recovery, and the template library"
---

## 6. Prompt engineering for verified code generation

### 6.1 Research foundation

This section draws on techniques from four major projects:

1. **DafnyPro (2026).** Three inference-time techniques, diff-checker, pruner, hint augmentation
  , achieving 86% on DafnyBench.
2. **Laurel (UCSD, 2024).** Assertion localization, identifying WHERE in the code annotations are
   needed, then using the LLM to fill them.
3. **AlphaVerus (CMU, 2024).** Self-improving via iterative translation from known-good programs in
   other languages.
4. **Clover (Stanford, 2023).** Triangulated generation of code + annotations + docstrings.

### 6.2 Initial prompt structure

The initial prompt (iteration 1) follows this template:

````
SYSTEM MESSAGE:
You are an expert Dafny programmer specializing in verified implementations.
Your task is to produce a method body that the Dafny verifier will accept.

Rules:
1. You MUST NOT modify the method signature, requires, ensures, or modifies clauses.
2. You MUST produce code that satisfies ALL postconditions.
3. You MAY add local variables, assertions, loop invariants, and ghost code.
4. You MAY call helper lemmas if you define them.
5. You SHOULD add intermediate assertions to help the verifier.
6. You SHOULD use `calc` blocks for complex multi-step reasoning.
7. Prefer simple, straightforward implementations over clever ones.

USER MESSAGE:
## Method Signature
```csharp
{complete_dafny_skeleton}
````

## Domain Description

{natural_language_description_of_the_operation}

## Type Definitions

```csharp
{relevant_type_definitions}
```text

## Similar Verified Examples

{few_shot_examples}

## Your Task

Produce the complete method body for `{method_name}`. Return your code inside a `dafny` code block.
Include any helper lemmas you need BEFORE the method.

````

### 6.3 Few-shot example selection

The prompt includes 1-3 examples of verified Dafny methods similar to the target.
Examples are selected from a template library based on:

1. **Operation pattern:** map insert, map lookup, map delete, sequence processing,
   stateful update, nondeterministic choice, etc.
2. **Postcondition structure:** cardinality preservation, state transition, value
   computation, etc.
3. **Complexity level:** simple (one-liner), medium (conditional + update), complex
   (loop with invariant).

#### Template library (core patterns)

```python
EXAMPLE_LIBRARY = {
    "map_insert_fresh": {
        "pattern": ["map_update", "cardinality_increase", "fresh_key"],
        "code": """
method Insert<K,V>(m: map<K,V>, k: K, v: V) returns (m': map<K,V>)
  requires k !in m
  ensures m' == m[k := v]
  ensures |m'| == |m| + 1
{
  m' := m[k := v];
  // Dafny's built-in map reasoning handles cardinality
}"""
    },
    "map_update_existing": {
        "pattern": ["map_update", "cardinality_same", "existing_key"],
        "code": """
method Update<K,V>(m: map<K,V>, k: K, v: V) returns (m': map<K,V>)
  requires k in m
  ensures m' == m[k := v]
  ensures |m'| == |m|
{
  m' := m[k := v];
}"""
    },
    "map_delete": {
        "pattern": ["map_remove", "cardinality_decrease"],
        "code": """
method Remove<K,V>(m: map<K,V>, k: K) returns (m': map<K,V>)
  requires k in m
  ensures k !in m'
  ensures |m'| == |m| - 1
  ensures forall j :: j in m && j != k ==> j in m' && m'[j] == m[j]
{
  m' := map j | j in m && j != k :: m[j];
}"""
    },
    "sequence_sum": {
        "pattern": ["loop", "accumulator", "sequence_processing"],
        "code": """
method Sum(s: seq<int>) returns (total: int)
  ensures total == SumSpec(s)
{
  total := 0;
  var i := 0;
  while i < |s|
    invariant 0 <= i <= |s|
    invariant total == SumSpec(s[..i])
    decreases |s| - i
  {
    total := total + s[i];
    i := i + 1;
  }
  assert s[..|s|] == s;  // trigger for the final postcondition
}"""
    },
    "nondeterministic_fresh": {
        "pattern": ["assign_such_that", "fresh_value", "existence_proof"],
        "code": """
lemma FreshKeyExists<K,V>(m: map<K,V>)
  ensures exists k: K :: k !in m
{
  // For types with infinite inhabitants, this is always true.
  // For finite types, need a cardinality argument.
}

method GetFreshKey<K,V>(m: map<K,V>) returns (k: K)
  ensures k !in m
{
  FreshKeyExists(m);
  k :| k !in m;
}"""
    },
}
````

### 6.4 Failure recovery prompt structure

On iterations 2+, the prompt includes the failure context:

````
SYSTEM MESSAGE:
{same as initial}

USER MESSAGE:
## Previous Attempt (FAILED)
```csharp
{previous_candidate_body}
````

## Verifier Error

**{error.category}** at line {error.line}: {error.message}

## Failed Specification Clause

```csharp
{error.related_clause}
```text

## Counterexample

{formatted_counterexample_or_none}

## Diagnosis

{repair_hint}

## What to Fix

{specific_guidance_based_on_error_category}

## Method Signature (UNCHANGED -- do NOT modify)

```csharp
{complete_dafny_skeleton}
```text

## Your Task

Fix the method body to pass verification. Return the COMPLETE corrected body.

````

### 6.5 Dafnypro's three inference-time techniques (adapted)

#### Technique 1: Diff-checker

After the LLM returns its candidate, we verify that the immutable parts of the Dafny
file are unchanged:

```python
def diff_check(original_skeleton: str, candidate: str) -> tuple[bool, str]:
    """Ensure LLM did not modify requires/ensures/modifies."""
    original_sig = extract_method_signature(original_skeleton)
    candidate_sig = extract_method_signature(candidate)

    if original_sig != candidate_sig:
        diff = compute_diff(original_sig, candidate_sig)
        return False, f"You modified the method signature. Changes:\n{diff}\nRevert these changes."
    return True, ""
````

If the diff-check fails, we do not invoke the Dafny verifier. Instead, we immediately re-prompt the
LLM with a warning that it modified the signature.

**Why this matters.** LLMs frequently weaken postconditions to make verification easier. For
example, changing `ensures |st.store| == |old(st.store)| + 1` to
`ensures |st.store| >= |old(st.store)|`. The diff-checker catches this immediately.

#### Technique 2: Pruner

After the LLM returns its candidate and the diff-check passes, we identify potentially unnecessary
annotations:

```python
def prune_unnecessary_annotations(candidate: str) -> str:
    """Remove annotations that are redundant or slow down verification."""
    # Step 1: Identify all assert statements
    assertions = find_all_assertions(candidate)

    # Step 2: For each assertion, try verification without it
    for assertion in assertions:
        pruned = remove_assertion(candidate, assertion)
        if verify_fast(pruned):  # quick check with short timeout
            candidate = pruned  # assertion was unnecessary

    return candidate
```

The pruner prevents a common failure mode: the LLM adds many intermediate assertions "to be safe,"
but some of these create new verification obligations that Z3 struggles with. Removing them can make
verification faster and more likely to succeed.

**Practical consideration.** The pruner is optional and runs only if the candidate fails
verification with a timeout. It is a last resort, rather than a default step, because each pruning trial
requires a Dafny invocation.

#### Technique 3: Hint augmentation

Before prompting the LLM, we check if the operation matches any known patterns that benefit from
specific proof strategies:

```python
PROOF_HINTS = {
    "map_cardinality_after_insert": """
    // PROOF HINT: When inserting a fresh key into a map, Dafny may need help
    // proving |m[k := v]| == |m| + 1. Use this lemma:
    lemma MapInsertFreshIncreases<K,V>(m: map<K,V>, k: K, v: V)
      requires k !in m
      ensures |m[k := v]| == |m| + 1
    """,

    "map_cardinality_after_remove": """
    // PROOF HINT: When removing a key from a map, use:
    lemma MapRemoveDecreases<K,V>(m: map<K,V>, k: K)
      requires k in m
      ensures |map j | j in m && j != k :: m[j]| == |m| - 1
    """,

    "sequence_slice_equality": """
    // PROOF HINT: To prove s[..|s|] == s, assert it directly.
    // Dafny sometimes needs this trigger for sequence operations.
    assert s[..|s|] == s;
    """,

    "forall_in_updated_map": """
    // PROOF HINT: To prove a property holds for all elements in m[k := v]:
    // 1. Prove it for k -> v specifically
    // 2. Prove it for all j in m where j != k (from old(m))
    // 3. Dafny can then combine
    """,
}

def select_hints(ensures_clauses: list[str]) -> list[str]:
    """Select relevant proof hints based on the postconditions."""
    hints = []
    for clause in ensures_clauses:
        if "||" in clause and "map" in clause.lower():
            hints.append(PROOF_HINTS["map_cardinality_after_insert"])
        # ... more pattern matching
    return hints
```

### 6.6 Laurel's assertion localization (adapted)

Laurel (2024, UCSD) tackles a specific sub-problem: WHERE in the code should assertions be placed to
help the verifier? Instead of asking the LLM to generate the entire proof, Laurel:

1. Identifies verification failure locations.
2. Inserts `assert ???;` placeholders at those locations.
3. Asks the LLM to fill in the placeholders with concrete assertions.
4. Uses similar verified lemmas as few-shot context.

#### Adaptation for our pipeline

After a verification failure, if the error is a postcondition violation or a loop invariant failure,
we:

1. Parse the error location to identify which code path fails.
2. Insert a placeholder: `assert /* FILL: help the verifier prove {clause} */ true;`
3. Include the placeholder in the next prompt, asking the LLM to replace `true` with a meaningful
   intermediate assertion.

```python
def localize_and_insert_placeholders(
    candidate: str,
    errors: list[VerifierError]
) -> str:
    """Insert assertion placeholders at error locations."""
    for error in errors:
        if error.category in ("postcondition_violation", "assertion_failure"):
            # Insert before the return statement on the failing path
            line = find_return_statement_before(candidate, error.line)
            placeholder = (
                f"assert /* FILL: establish {error.related_clause} */ true;"
            )
            candidate = insert_line(candidate, line, placeholder)
    return candidate
```

## Appendix B: Complete prompt templates

### B.1 Initial synthesis prompt (full)

````
SYSTEM:
You are an expert Dafny programmer. Your task is to implement the body of a Dafny
method such that the Dafny auto-active verifier accepts it.

Rules you MUST follow:
1. Return ONLY the method body (code between { and }).
2. Do NOT modify the method signature, requires, ensures, or modifies clauses.
3. You MAY add local variables, assertions, ghost variables, and loop invariants.
4. You MAY define helper lemmas BEFORE the method (prefix with "// HELPER LEMMA").
5. You SHOULD add `assert` statements to help the verifier when postconditions
   involve complex reasoning (map cardinality, quantifiers, etc.).
6. If you use a while loop, you MUST provide a `decreases` clause and loop invariants.
7. If you use `:|` (assign-such-that), you MUST prove existence (via a lemma or assertion).
8. Prefer simple, direct implementations. Avoid unnecessary complexity.

Common Dafny patterns:
- Map update: `m' := m[k := v];` creates a new map with key k mapped to v.
- Map remove: `m' := map j | j in m && j != k :: m[j];`
- Map cardinality after insert: `|m[k := v]| == |m| + 1` when `k !in m`.
- Sequence sum: use a while loop with `invariant total == Sum(s[..i])`.
- Nondeterministic choice: `x :| P(x);` requires `exists x :: P(x)`.

USER:
## Operation: {operation_name}
## Description: {natural_language_description}

## Complete Dafny File
```csharp
{complete_dafny_skeleton_with_types_predicates_and_method}
````

## Similar Verified Examples

{few_shot_examples}

## Proof Hints (if applicable)

{selected_proof_hints}

## Your Task

Implement the body of method `{method_name}`. If you need helper lemmas, define them before the
method body, prefixed with "// HELPER LEMMA". Return your answer in a `dafny` code block.

```

### B.2 Failure recovery prompt (full)

```text

SYSTEM: {same as initial}

USER:

## VERIFICATION FAILED -- Iteration {n} of {max}

Your previous implementation of `{method_name}` was rejected by the Dafny verifier.

## Your Previous Code

```csharp
{previous_candidate_body}
```text

## Verifier Errors ({num_errors} total)

### Error 1

- Type: {error_1.category}
- Location: line {error_1.line}, column {error_1.column}
- Message: {error_1.message}
- Related clause: `{error_1.related_clause}`
- Counterexample: {error_1.counterexample or "not available"}

### Error 2 (if any)

{...}

## Diagnosis

{repair_hint_for_primary_error}

## What Changed From Your Previous Attempt

{diff_summary_if_iteration_3_plus}

## Complete Dafny File (for reference -- do NOT modify the signature)

```csharp
{complete_dafny_skeleton}
```text

## Your Task

Fix the method body so that the Dafny verifier accepts it. Address the verifier errors above. Focus
on error 1 first. Return the COMPLETE corrected method body in a `dafny` code block.

````

## Appendix C: Example template library entries

### C.1 Map insert with freshness proof

```csharp
// Pattern: Insert a new entry with a generated key into a map.
// Postconditions: key is fresh, map grows by 1, value is stored.

lemma FreshKeyExists<V>(m: map<int, V>, bound: int)
  requires forall k :: k in m ==> 0 <= k < bound
  requires bound >= 0
  ensures bound !in m
{
  // bound is not in m because all keys in m are < bound.
}

method InsertWithFreshKey<V>(m: map<int, V>, v: V, nextId: int)
  returns (m': map<int, V>, key: int)
  requires forall k :: k in m ==> 0 <= k < nextId
  requires nextId >= 0
  ensures key == nextId
  ensures key !in m
  ensures m' == m[key := v]
  ensures |m'| == |m| + 1
{
  FreshKeyExists(m, nextId);
  key := nextId;
  m' := m[key := v];
}
````

### C.2 Stateful update with frame condition

```csharp
// Pattern: Update one field of one entry, prove everything else is unchanged.

method UpdateField(st: ServiceState, id: UserId, newName: string)
  modifies st
  requires id in st.users
  ensures st.users[id].name == newName
  ensures st.users[id].email == old(st.users[id].email)
  ensures forall uid :: uid in st.users && uid != id ==>
    st.users[uid] == old(st.users[uid])
  ensures |st.users| == |old(st.users)|
{
  var oldUser := st.users[id];
  var newUser := User(newName, oldUser.email);
  st.users := st.users[id := newUser];
}
```

### C.3 Sequence processing with loop

```csharp
// Pattern: Process a sequence with a loop, maintaining a running invariant.

ghost function SumPrices(items: seq<LineItem>): int
  decreases |items|
{
  if |items| == 0 then 0
  else items[0].price * items[0].quantity + SumPrices(items[1..])
}

lemma SumPricesAppend(items: seq<LineItem>, idx: int)
  requires 0 <= idx < |items|
  ensures SumPrices(items[..idx+1]) ==
    SumPrices(items[..idx]) + items[idx].price * items[idx].quantity
  decreases idx
{
  if idx == 0 {
    assert items[..1] == [items[0]];
  } else {
    SumPricesAppend(items, idx - 1);
    assert items[..idx+1] == items[..idx] + [items[idx]];
  }
}

method ComputeTotal(items: seq<LineItem>) returns (total: int)
  requires forall i :: 0 <= i < |items| ==> items[i].price >= 0
  requires forall i :: 0 <= i < |items| ==> items[i].quantity >= 0
  ensures total == SumPrices(items)
{
  total := 0;
  var i := 0;
  while i < |items|
    invariant 0 <= i <= |items|
    invariant total == SumPrices(items[..i])
    decreases |items| - i
  {
    SumPricesAppend(items, i);
    total := total + items[i].price * items[i].quantity;
    i := i + 1;
  }
  assert items[..|items|] == items;
}
```

### C.4 Nondeterministic choice with existence proof

```csharp
// Pattern: Choose a value satisfying a predicate, with an existence proof.
// Used when the spec says "output satisfies P" but doesn't say how to compute it.

predicate IsValidCode(s: string)
{
  |s| == 8 && forall i :: 0 <= i < |s| ==>
    ('a' <= s[i] <= 'z') || ('0' <= s[i] <= '9')
}

lemma ValidCodeExists()
  ensures exists s :: IsValidCode(s)
{
  var witness := "abcdefgh";
  assert |witness| == 8;
  assert forall i :: 0 <= i < 8 ==> ('a' <= witness[i] <= 'z');
  assert IsValidCode(witness);
}

method GenerateCode() returns (code: string)
  ensures IsValidCode(code)
{
  ValidCodeExists();
  code :| IsValidCode(code);
}
```

_End of document. This design is based on research surveyed in `00_comprehensive_analysis.md` and
represents the detailed architecture for Phase 4 (LLM Synthesis Loop) of the spec-to-REST compiler
build plan._
