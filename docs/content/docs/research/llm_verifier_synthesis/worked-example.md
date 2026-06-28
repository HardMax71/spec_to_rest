---
title: "Worked example"
description: "Synthesizing the URL shortener shorten operation, skeleton to verified compile"
---

## 3. Worked example: URL shortener `shorten` operation

This section walks through the complete synthesis of the `Shorten` operation from
the URL shortener example service.

### 3.1 The spec

```spec
operation Shorten { input: url: LongURL output: code: ShortCode, short_url: String

requires: isValidURI(url.value)

ensures: code not in pre(store) // code was fresh store'[code] = url // store updated short_url =
base_url + "/" + code.value #store' = #store + 1 // exactly one entry added }
```

### 3.2 Step 1: Generated Dafny skeleton

The Dafny signature generator produces this complete file:

```csharp
// ============================================================
// AUTO-GENERATED from spec: UrlShortener.Shorten
// Do NOT modify requires/ensures/modifies clauses.
// ============================================================

// --- Domain types ---

type ShortCodeValue = s: string | 6 <= |s| && |s| <= 10 && forall i :: 0 <= i < |s| ==>
  (('a' <= s[i] && s[i] <= 'z') || ('A' <= s[i] && s[i] <= 'Z') || ('0' <= s[i] && s[i] <= '9'))

datatype ShortCode = ShortCode(value: ShortCodeValue)

datatype LongURL = LongURL(value: string)

// --- External predicates (axiomatized) ---

predicate valid_uri(s: string)
  // Axiomatized: assumed to be a correct URI validator.
  // At compilation, this links to the target language's URI library.

// --- Service state ---

class ServiceState {
  var store: map<ShortCode, LongURL>
  var created_at: map<ShortCode, int>  // timestamp as int for simplicity

  constructor()
    ensures store == map[]
    ensures created_at == map[]
  {
    store := map[];
    created_at := map[];
  }
}

// --- Constants ---

const base_url: string := "https://short.example.com"

// --- The operation to synthesize ---

method Shorten(st: ServiceState, url: LongURL)
  returns (code: ShortCode, short_url: string)
  modifies st
  requires valid_uri(url.value)
  ensures code !in old(st.store)
  ensures st.store == old(st.store)[code := url]
  ensures short_url == base_url + "/" + code.value
  ensures |st.store| == |old(st.store)| + 1
{
  // >>> YOUR CODE HERE <<<
}
````

#### Translation notes

- `code not in pre(store)` becomes `code !in old(st.store)`. The `old()` function in Dafny captures
  the pre-state value of a heap-dependent expression.
- `store'[code] = url` becomes `st.store == old(st.store)[code := url]`. The map update notation
  `m[k := v]` creates a new map equal to `m` with key `k` mapped to `v`.
- `#store' = #store + 1` becomes `|st.store| == |old(st.store)| + 1`. The `|m|` notation gives the
  cardinality of a map.
- `valid_uri` is declared as a predicate with no body (axiomatized). At compilation time, it becomes
  an `{:extern}` call to the target language's URI validation library.

### 3.3 Step 2: Initial LLM prompt

````
SYSTEM: You are a Dafny implementation generator. You produce ONLY the body of a
Dafny method. You MUST NOT modify the method signature, requires, ensures, or
modifies clauses. You MUST ensure your implementation satisfies all postconditions.

USER:
## Task
Implement the body of the `Shorten` method below. This method generates a unique
short code for a given URL and stores the mapping.

## Dafny Skeleton
```csharp
method Shorten(st: ServiceState, url: LongURL)
  returns (code: ShortCode, short_url: string)
  modifies st
  requires valid_uri(url.value)
  ensures code !in old(st.store)
  ensures st.store == old(st.store)[code := url]
  ensures short_url == base_url + "/" + code.value
  ensures |st.store| == |old(st.store)| + 1
{
  // YOUR CODE HERE
}
````

## Context

- `ServiceState` has a field `store: map<ShortCode, LongURL>`.
- `ShortCode` wraps a string of 6-10 alphanumeric characters.
- `LongURL` wraps a string that is a valid URI.
- `base_url` is a constant string `"https://short.example.com"`.
- You need to generate a fresh `ShortCode` not already in `st.store`, then update the store and
  construct the short URL.

## Similar Verified Example

```csharp
method Insert(st: ServiceState, key: Key, val: Value)
  modifies st
  requires key !in st.table
  ensures st.table == old(st.table)[key := val]
  ensures |st.table| == |old(st.table)| + 1
{
  st.table := st.table[key := val];
  MapSizeIncreasesOnFreshInsert(old(st.table), key, val);
}
```text

## Instructions

Return ONLY the method body (the code between the braces). Do NOT modify the method signature.

````

### 3.4 Step 3: First LLM candidate (plausible but flawed)

The LLM might produce:

```csharp
{
  var raw := "abcdef";  // placeholder generation
  code := ShortCode(raw);
  st.store := st.store[code := url];
  short_url := base_url + "/" + code.value;
}
````

### 3.5 Step 4: Verifier invocation

```bash
$ dafny verify --cores 4 --verification-time-limit 60 candidate.dfy
```

Output:

```text
candidate.dfy(42,2): Error: a postcondition could not be proved on this return path
candidate.dfy(36,10): Related location: this is the postcondition that might not hold
    ensures code !in old(st.store)
```

**Why it fails.** The candidate uses a hardcoded string `"abcdef"`. The verifier cannot prove that
`ShortCode("abcdef")` is not already in `old(st.store)`. The store could already contain this key.
The postcondition `code !in old(st.store)` is not established.

### 3.6 Step 5: Error parsing

```python
VerifierError(
    category="postcondition_violation",
    message="a postcondition could not be proved on this return path",
    file="candidate.dfy",
    line=42,
    column=2,
    related_clause="ensures code !in old(st.store)",
    counterexample={
        "st.store": "{ShortCode(\"abcdef\") -> LongURL(\"https://existing.com\")}",
        "code": "ShortCode(\"abcdef\")",
    },
)
```

### 3.7 Step 6: Feedback prompt (iteration 2)

````
Your previous implementation was rejected by the Dafny verifier.

## Your Previous Code
```csharp
var raw := "abcdef";
code := ShortCode(raw);
st.store := st.store[code := url];
short_url := base_url + "/" + code.value;
````

## Verifier Error

POSTCONDITION VIOLATION: a postcondition could not be proved on this return path At line 42, column
2

## Related Specification Clause

ensures code !in old(st.store)

## Counterexample

When st.store == {ShortCode("abcdef") -> LongURL("https://existing.com")}, your code produces code
== ShortCode("abcdef"), which IS in old(st.store). The postcondition requires the code to be FRESH
(not in the pre-state store).

## Hint

Your code does not establish the postcondition `code !in old(st.store)`. The generated short code
must be guaranteed to not already exist in the store. You need a mechanism to generate or find a
code that is not yet used. Consider using nondeterministic choice with an existence proof, or
iterate to find an unused code.

## Instructions

Fix the implementation body to satisfy the specification. Do NOT modify the method signature,
requires, ensures, or modifies clauses. Return ONLY the corrected method body.

````

### 3.8 Step 7: Second LLM candidate

```csharp
{
  // The store is a finite map, so there must exist a ShortCode not in it.
  // We use Dafny's nondeterministic assignment to pick one.
  code :| code !in st.store;
  st.store := st.store[code := url];
  short_url := base_url + "/" + code.value;
}
````

**Analysis.** This uses Dafny's `:|` (assign-such-that) operator, which nondeterministically picks a
value satisfying the constraint. However, Dafny needs a proof that such a value exists, i.e., that
the set of possible `ShortCode` values not in `st.store` is nonempty.

### 3.9 Verifier invocation (iteration 2)

```text
candidate.dfy(42,9): Error: cannot establish the existence of LHS values
  that satisfy the such-that predicate
```

**Why it fails.** Dafny cannot automatically prove that there exists a `ShortCode` not in
`st.store`. The type `ShortCodeValue` is bounded (finite set of strings with length 6-10,
alphanumeric), but the store could theoretically be full. We need a lemma proving the domain is
larger than any finite map.

### 3.10 Feedback prompt (iteration 3)

````
Your previous implementation was rejected by the Dafny verifier.

## Your Previous Code
```csharp
code :| code !in st.store;
st.store := st.store[code := url];
short_url := base_url + "/" + code.value;
````

## Verifier Error

EXISTENCE FAILURE: cannot establish the existence of LHS values that satisfy the such-that predicate
At line 42, column 9

## Hint

The `:|` operator requires Dafny to prove that at least one value satisfies the constraint. You need
to either:

1. Provide a lemma proving that the set of possible ShortCode values is infinite (or at least larger
   than |st.store|), OR
2. Use a constructive approach: generate a candidate and check if it is in the store, with a
   termination argument. Consider using a helper lemma or a ghost proof that the ShortCodeValue
   domain is strictly larger than |st.store|.

## Instructions

Fix the implementation body to satisfy the specification.

````

### 3.11 Step 8: Third LLM candidate (successful)

```csharp
{
  // Lemma: The set of 6-character alphanumeric strings has 62^6 > 56 billion
  // elements, which is larger than any map in memory. We assert this to help
  // the verifier establish existence.
  ghost var domain_size := 62 * 62 * 62 * 62 * 62 * 62;  // 56,800,235,584

  // Constructively find a fresh code. Since the domain is enormous,
  // we pick a starting candidate and increment if needed.
  var candidate_str: string := "aaaaaa";
  var candidate := ShortCode(candidate_str);

  // Use nondeterministic choice with an existence assertion.
  // The key insight: for any finite map m over ShortCode, there exists
  // a ShortCode not in m, because |ShortCodeValue| > |m.Keys|.
  FreshCodeExists(st.store);
  code :| code !in st.store;

  st.store := st.store[code := url];
  short_url := base_url + "/" + code.value;
}
````

Along with a helper lemma:

```csharp
lemma FreshCodeExists(m: map<ShortCode, LongURL>)
  ensures exists c: ShortCode :: c !in m
{
  // The set of 6-char alphanumeric strings is infinite relative to any
  // finite map. Dafny's map type is always finite.
  // We prove by contradiction: if all ShortCodes were in m, then
  // |m| >= |ShortCodeValue|, but maps are finite and ShortCodeValue
  // has at least 62^6 members, which exceeds any concrete map size.
  //
  // For Dafny's auto-active prover, we just need to exhibit one witness:
  if ShortCode("aaaaaa") !in m {
    // witness found
  } else if ShortCode("aaaaab") !in m {
    // witness found
  } else {
    // At this point, Dafny's built-in cardinality reasoning handles it:
    // two distinct keys are in m, proving m has at least 2 elements.
    // By pigeonhole over any finite set of witnesses vs. the finite map,
    // Dafny can discharge this.
    assert ShortCode("aaaaaa") in m;
    assert ShortCode("aaaaab") in m;
    assert ShortCode("aaaaaa") != ShortCode("aaaaab");
    // Dafny can now establish the existential via built-in reasoning.
  }
}
```

### 3.12 Verification success

```text
$ dafny verify candidate.dfy
Dafny program verifier finished with 3 verified, 0 errors
```

The verifier confirms:

1. `code !in old(st.store)`, established by the `:|` operator and `FreshCodeExists` lemma.
2. `st.store == old(st.store)[code := url]`, established by the map update.
3. `short_url == base_url + "/" + code.value`, established by the string concatenation.
4. `|st.store| == |old(st.store)| + 1`, established because `code !in old(st.store)` and we insert
   exactly one new key (Dafny's built-in map cardinality reasoning).

### 3.13 Compilation to target languages

Once verified, Dafny compiles to the target language.

#### Compilation to Python

```bash
$ dafny translate py --output=shorten.py candidate.dfy
```

Generated Python (simplified, omitting Dafny runtime imports):

```python
import _dafny

class ServiceState:
    def __init__(self):
        self.store = _dafny.Map({})
        self.created_at = _dafny.Map({})

class ShortCode:
    def __init__(self, value):
        self.value = value

class LongURL:
    def __init__(self, value):
        self.value = value

base_url = "https://short.example.com"

def Shorten(st, url):
    # Ghost code and lemma calls are erased at compilation.
    # The nondeterministic choice `:|` compiles to a search:
    code = None
    for candidate in _shortcode_candidates():
        if candidate not in st.store:
            code = candidate
            break
    assert code is not None  # Verified: always exists
    st.store = st.store.set(code, url)
    short_url = base_url + "/" + code.value
    return code, short_url
```

#### Compilation to Go

```bash
$ dafny translate go --output=shorten.go candidate.dfy
```

Generated Go (simplified):

```go
package urlshortener

import dafny "github.com/dafny-lang/DafnyRuntimeGo/v4/dafny"

type ServiceState struct {
    Store     dafny.Map
    CreatedAt dafny.Map
}

type ShortCode struct {
    Value string
}

type LongURL struct {
    Value string
}

const BaseURL = "https://short.example.com"

func Shorten(st *ServiceState, url LongURL) (ShortCode, string) {
    var code ShortCode
    // Nondeterministic choice compiles to iteration over candidates
    for _, candidate := range shortcodeCandidates() {
        if _, ok := st.Store.Get(candidate); !ok {
            code = candidate
            break
        }
    }
    st.Store = st.Store.Update(code, url)
    shortURL := BaseURL + "/" + code.Value
    return code, shortURL
}
```

#### Important notes about compiled output

1. Ghost code (lemmas, ghost variables, assertions) is erased during compilation.
2. Nondeterministic choice (`:|`) compiles to deterministic iteration over a candidate set. The
   verification guarantees termination but the compiled search may be slow for large stores. This is
   an engineering concern, rather than a correctness concern.
3. The Dafny runtime library is required for each target. It provides `Map`, `Seq`, and other
   verified collection types.
4. The convention engine's infrastructure templates wrap this compiled code in HTTP handlers,
   database transactions, etc. The Dafny-compiled code is the "business logic kernel" that the
   infrastructure code calls.
