---
title: "Walkthrough"
description: "Synthesizing the URL shortener's Shorten operation, from skeleton to verified body"
---

This page follows one operation, `Shorten` from the URL shortener, through the whole loop: the
generated skeleton, a first attempt the verifier rejects, two repairs, and the verified result.

## The spec

```spec
operation Shorten {
  input:  url: LongURL
  output: code: ShortCode, short_url: String

  requires:
    isValidURI(url)

  ensures:
    code not in pre(store)
    store' = pre(store) + {code -> url}
    short_url = base_url + "/" + code
    #store' = #pre(store) + 1
    metadata'[code].url = url
    metadata'[code].click_count = 0
}
```

## The skeleton

`inspect --format dafny` turns the operation into a complete Dafny file. The spec-derived parts, the
type predicates, the state class, and the method contract, are fixed; only the body is the model's to
write. The pieces that matter here, with the full file living in `fixtures/golden/dafny/`:

```csharp
type ShortCode = string
predicate ShortCodeWhere(value: string)
{ |value| >= 6 && |value| <= 10 && matches___a_zA_Z0_9___(value) }

type LongURL = string
predicate LongURLWhere(value: string) { |value| > 0 && isValidURI(value) }

class ServiceState {
  var store: map<ShortCode, LongURL>
  var metadata: map<ShortCode, UrlMapping>
  var base_url: BaseURL
}

predicate ServiceStateInv(st: ServiceState)
  reads st
{
  (forall c :: c in st.store ==> isValidURI(st.store[c]))
  && st.store.Keys == st.metadata.Keys
  && (forall c :: c in st.metadata ==> st.metadata[c].click_count >= 0)
}

predicate isValidURI(x: string) { true }   // axiomatized placeholder

method Shorten(st: ServiceState, url: LongURL) returns (code: ShortCode, short_url: string)
  modifies st
  requires ServiceStateInv(st)
  requires LongURLWhere(url)
  ensures code !in old(st.store)
  ensures st.store == old(st.store)[code := url]
  ensures short_url == old(st.base_url) + "/" + code
  ensures |st.store| == |old(st.store)| + 1
  ensures code in st.metadata && st.metadata[code].url == url
  ensures code in st.metadata && st.metadata[code].click_count == 0
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}
```

A few of the spec-to-Dafny moves show up here: `code not in pre(store)` is `code !in old(st.store)`,
`store' = pre(store) + {code -> url}` is the map update `st.store == old(st.store)[code := url]`, and
`#store'` is `|st.store|`. The refinement on `ShortCode` rides along as the `ShortCodeWhere`
predicate, and `isValidURI` is axiomatized to `true`, a placeholder the verifier treats as opaque.

## First attempt, and why it fails

Asked for a body, the model reaches for the obvious and hardcodes a code:

```csharp
{
  code := "abcdef";
  st.store := st.store[code := url];
  short_url := st.base_url + "/" + code;
}
```

The verifier rejects it:

```text
candidate.dfy(...): Error: a postcondition could not be proved on this return path
  Related location: ensures code !in old(st.store)
```

A fixed string is not provably fresh: nothing rules out `"abcdef"` already being a key in
`old(st.store)`, so the freshness postcondition fails. The body has not set the metadata entry
either, but freshness is the first wall.

## Second attempt

The feedback says the code must be provably fresh, so the model switches to Dafny's assign-such-that:

```csharp
{
  code :| code !in st.store;
  st.store := st.store[code := url];
  short_url := st.base_url + "/" + code;
}
```

`code :| P` picks some value satisfying `P`, but Dafny will not take it on faith; it must first prove
such a value exists, and it cannot:

```text
candidate.dfy(...): Error: cannot establish the existence of LHS values that satisfy the such-that predicate
```

Nothing in scope tells the verifier that the space of valid codes outruns any finite `store`.

## Third attempt, verified

The fix is a lemma supplying exactly that fact, called before the assign-such-that:

```csharp
{
  FreshCodeExists(st.store);
  code :| code !in st.store;
  st.store := st.store[code := url];
  st.metadata := st.metadata[code := UrlMapping(code, url, 0, 0)];
  short_url := st.base_url + "/" + code;
}

lemma FreshCodeExists(m: map<string, string>)
  ensures exists c :: c !in m
{ /* the code space is unbounded; a finite map cannot hold all of it */ }
```

With the lemma in scope the existence obligation discharges. Inserting a fresh key gives
`|st.store| == |old(st.store)| + 1` for free, the metadata assignment settles the two metadata
clauses, and `ServiceStateInv` still holds because the new URL is valid and the key sets line up.
Dafny reports no errors:

```text
Dafny program verifier finished with 2 verified, 0 errors
```

## Compiling the result

Once a body verifies, the ghost parts, the lemma and any assertions, are erased, and the rest
compiles to the target language. How a verified Dafny body becomes Python, Go, or TypeScript, and
what the runtime needs, is on the [Dafny](/research/llm_verifier_synthesis/dafny) page. The
convention engine then wraps that body in the HTTP handler, the validation, and the database calls.
