---
title: "Survey of specification languages"
description: "How Alloy, TLA+, Quint, VDM, Dafny, Event-B, Z, TypeSpec, Smithy, and P express the same concepts"
---

Ten specification languages, grouped four ways: relational modeling (Alloy), state-transition
systems (TLA+, Quint, Event-B, and P), operation modeling with contracts (VDM-SL and Dafny), formal
schemas (Z), and API description languages (TypeSpec and Smithy). To compare them on the same
footing, each one below expresses the same four things:

- a store mapping short codes to URLs,
- a shorten operation that adds a mapping,
- a resolve operation that looks one up by code,
- an invariant that every stored URL is valid.

## Alloy 6

Alloy models a system as sets of atoms, called signatures, and the relations between them, called
fields, with global facts and reusable predicates for constraints and assertions naming the
properties to check. Version 6 added temporal operators, `always`, `eventually`, `after`, and primed
variables, so a model can reason about state over time. The notation is relational and reads like
mathematics: `+` is union, `&` intersection, `.` relational join, `~` transpose, `^` transitive
closure, and the multiplicity keywords `one`, `lone`, `some`, and `set` fix how many things a field
relates.

```text
open util/ordering[Time]

sig ShortCode {
  value: one String
}

sig LongURL {
  value: one String
}

sig Store {
  mapping: ShortCode -> lone LongURL,
  time: one Time
}

-- The store at each time step
fact traces {
  all t: Time - last |
    let t' = t.next |
      some op: Operation | op.pre = t and op.post = t'
}

-- Invariant: all stored URLs are valid
fact validURLs {
  all s: Store, c: ShortCode |
    c in s.mapping.LongURL implies isValidURI[s.mapping[c]]
}

pred isValidURI[u: LongURL] {
  -- abstract predicate; in Alloy we'd constrain the string set
  u.value in ValidURIStrings
}

-- Shorten operation
pred shorten[s, s': Store, url: LongURL, code: ShortCode] {
  -- precondition: code is fresh
  code not in s.mapping.LongURL
  -- postcondition: new mapping added
  s'.mapping = s.mapping + code -> url
  -- frame: nothing else changes
  isValidURI[url]
}

-- Resolve operation
pred resolve[s: Store, code: ShortCode, url: LongURL] {
  -- precondition: code exists
  code in s.mapping.LongURL
  -- postcondition: correct lookup
  url = s.mapping[code]
}

-- Alloy 6 temporal version (alternative)
one sig ActiveStore {
  var entries: ShortCode -> lone LongURL
}

fact invariantAlways {
  always all c: ShortCode, u: LongURL |
    c -> u in ActiveStore.entries implies isValidURI[u]
}

pred shorten_temporal[url: LongURL, code: ShortCode] {
  code not in ActiveStore.entries.LongURL
  ActiveStore.entries' = ActiveStore.entries + code -> url
}
```

Bounded model checking is the real draw. Alloy searches a finite scope and surfaces counterexamples
on its own, and its relational core handles transitive closure in a way few spec languages manage.
For this project it misses on the things that matter: no notion of HTTP or services, no path to
generated code, thin string handling, and a bounded analysis that proves nothing beyond the chosen
scope. The `sig`, `fact`, and `pred` vocabulary is unfamiliar to most developers, too.

## TLA+ (Temporal Logic of Actions)

TLA+ holds the entire system state in a set of variables and describes change with actions, which
are predicates relating the current state to the next one through primed variables. Temporal
formulas state safety and liveness: `[]` for always, `<>` for eventually, `~>` for leads-to, with
`UNCHANGED` framing the variables an action leaves alone. The pretty-printed form looks like
mathematics; the ASCII form spells the operators out as `/\`, `\/`, `=>`, and `EXCEPT`.

```text
------------------------------ MODULE UrlShortener ------------------------------
EXTENDS Strings, FiniteSets, TLC

CONSTANTS ShortCodeSet, LongURLSet, ValidURIs

VARIABLES store, created_at

TypeInvariant ==
  /\ store \in [ShortCodeSet -> LongURLSet \cup {NULL}]
  /\ created_at \in [ShortCodeSet -> Nat \cup {NULL}]

Init ==
  /\ store = [c \in ShortCodeSet |-> NULL]
  /\ created_at = [c \in ShortCodeSet |-> NULL]

\* Invariant: all stored URLs are valid
AllStoredURLsValid ==
  \A c \in ShortCodeSet :
    store[c] # NULL => store[c] \in ValidURIs

\* Shorten operation
Shorten(url, code) ==
  /\ url \in ValidURIs                          \* precondition
  /\ store[code] = NULL                         \* code is fresh
  /\ store' = [store EXCEPT ![code] = url]      \* update store
  /\ created_at' = [created_at EXCEPT ![code] = 1]
  /\ AllStoredURLsValid'                         \* invariant maintained

\* Resolve operation (no state change)
Resolve(code) ==
  /\ store[code] # NULL                         \* precondition: exists
  /\ UNCHANGED <<store, created_at>>            \* no mutation
  \* The "output" is store[code]

\* Next state relation
Next ==
  \/ \E url \in LongURLSet, code \in ShortCodeSet : Shorten(url, code)
  \/ \E code \in ShortCodeSet : Resolve(code)

\* Temporal specification
Spec == Init /\ [][Next]_<<store, created_at>>

\* Safety property
Safety == []AllStoredURLsValid
================================================================================
```

Amazon has run TLA+ at scale on S3, DynamoDB, and EBS, its TLC model checker is genuinely
industrial, and liveness and fairness are both in reach. The cost for our purposes is everything
else: the syntax is verbose and heavily mathematical, there is no data modeling past functions and
sets, operations have no input or output distinct from action parameters, and nothing generates
code. Modeling state as global variables updated through `EXCEPT` reads as unintuitive to most
developers.

## Quint

Quint keeps TLA+'s semantic model, the variables, actions, and temporal properties, but wraps it in
a syntax aimed at developers: `var` for state, `action` for a transition, `val` for a definition,
`temporal` for a property, and primed state written `variable' = ...`. It reads close to TypeScript.

```typescript
module UrlShortener {
  // Types
  type ShortCode = str
  type LongURL = str

  // State
  var store: ShortCode -> LongURL
  var created_at: ShortCode -> int

  // Pure helper
  pure def isValidURI(url: LongURL): bool = {
    url.length() > 0 and url.startsWith("http")
  }

  // Initial state
  action init = all {
    store' = Map(),
    created_at' = Map(),
  }

  // Shorten operation
  action shorten(url: LongURL, code: ShortCode): bool = all {
    isValidURI(url),
    not(store.has(code)),
    store' = store.put(code, url),
    created_at' = created_at.put(code, 0),
  }

  // Resolve operation
  action resolve(code: ShortCode): bool = all {
    store.has(code),
    store' = store,
    created_at' = created_at,
  }
  // The "output" would be store.get(code)

  // Invariant
  val allStoredURLsValid: bool =
    store.keys().forall(c => isValidURI(store.get(c)))

  // Temporal property
  temporal safety = always(allStoredURLsValid)

  // Step relation
  action step = any {
    nondet url = oneOf(Set("http://example.com"))
    nondet code = oneOf(Set("abc123"))
    shorten(url, code),
    nondet code = oneOf(store.keys())
    resolve(code),
  }
}
```

The syntax is the appeal, and the verification power is unchanged, since Quint compiles to TLA+ for
checking and ships a REPL for poking at a spec interactively; Informal Systems develops it actively.
What it lacks for us is the data side. There is no way to model entities, fields, or
multiplicities, no pre/postcondition form (an action is a conjunction of booleans), and no operation
input or output separate from the action's parameters.

## VDM-SL (Vienna Development Method, Specification Language)

VDM-SL separates types, state, functions, and operations. An operation carries explicit `pre` and
`post` conditions, with `~` marking the old state inside a postcondition; functions stay pure; and
invariants attach to both types and state through `inv`. It is keyword-heavy and reads like
pseudocode.

```text
types
  ShortCode = seq of char
  inv sc == len sc >= 6 and len sc <= 10;

  LongURL = seq of char
  inv url == isValidURI(url);

state UrlShortener of
  store : map ShortCode to LongURL
  created_at : map ShortCode to nat
inv mk_UrlShortener(s, c) ==
  dom s = dom c and
  forall code in set dom s & isValidURI(s(code))
init s == s = mk_UrlShortener({|->}, {|->})
end

functions
  isValidURI: seq of char -> bool
  isValidURI(url) ==
    len url > 0 and url(1,...,4) = "http"

operations
  Shorten: LongURL ==> ShortCode
  Shorten(url) ==
    let code in set { generateCode() } in (
      store := store munion { code |-> url };
      created_at := created_at munion { code |-> 0 };
      return code
    )
  pre isValidURI(url)
  post url = store(RESULT) and
       RESULT not in set dom store~ and
       card dom store = card dom store~ + 1;

  Resolve: ShortCode ==> LongURL
  Resolve(code) ==
    return store(code)
  pre code in set dom store
  post RESULT = store(code) and
       store = store~;
```

Its pre/postcondition syntax is the cleanest of any language here. The `ext rd`/`ext wr` clause says
exactly which state an operation may read or write, type invariants sit inline, and the `~` for old
state is hard to improve on. The drawbacks are age and reach: the syntax dates to the 1970s, there
is no temporal reasoning, relational modeling stops at maps and sets with no multiplicities, and
tool support is limited to the Overture IDE.

## Dafny

Dafny is a programming language built around verification. Classes and datatypes hold the data,
methods carry `requires` and `ensures` clauses, and loop invariants with `decreases` clauses let the
verifier discharge proofs on its own; ghost variables and functions exist only for specification.
Boogie and Z3 check every contract at compile time. The style is C-like with specification keywords,
and verification is auto-active: you write the spec, the verifier fills in the proof.

```csharp
class ShortCode {
  var value: string
  ghost predicate Valid()
    reads this
  {
    6 <= |value| && |value| <= 10 &&
    forall i :: 0 <= i < |value| ==> IsAlphaNum(value[i])
  }
}

predicate IsValidURI(url: string) {
  |url| > 0 && url[..4] == "http"
}

class UrlShortener {
  var store: map<string, string>
  var created_at: map<string, int>

  ghost predicate Valid()
    reads this
  {
    (forall c :: c in store ==> IsValidURI(store[c])) &&
    store.Keys == created_at.Keys
  }

  constructor()
    ensures Valid()
    ensures store == map[]
  {
    store := map[];
    created_at := map[];
  }

  method Shorten(url: string) returns (code: string)
    requires IsValidURI(url)
    requires Valid()
    modifies this
    ensures Valid()
    ensures code !in old(store)
    ensures store == old(store)[code := url]
    ensures |store| == |old(store)| + 1
    ensures code in store && store[code] == url
  {
    code := GenerateCode();
    assume code !in store;  // would need real implementation
    store := store[code := url];
    created_at := created_at[code := 0];
  }

  method Resolve(code: string) returns (url: string)
    requires code in store
    requires Valid()
    ensures url == store[code]
    ensures store == old(store)
  {
    url := store[code];
  }
}
```

The contracts are machine-checked at compile time, `old()` names the pre-state cleanly, the
`modifies` clause controls framing, and verified code compiles to C#, Java, Go, JavaScript, and
Python. It is the most mature verification-aware language in the list. For specifying a service it
sits too low, though: relations carry no multiplicity constraints, nothing maps a contract to HTTP,
the programmer has to write the method body rather than just the contract, and verification of
string operations is weak.

## Event-B

Event-B splits a model in two. Contexts hold the static part, the sets, constants, and axioms;
machines hold the dynamic part, the variables, invariants, and events. An event has guards, which
act as preconditions, and actions, which update state. Refinement lets a model gain detail in
stages, and the Rodin platform generates and discharges the proof obligations. The notation is
set-theoretic and keyword-heavy, with the mathematical operators written in ASCII.

```text
MACHINE UrlShortener

SETS
  SHORT_CODE; LONG_URL

CONSTANTS
  ValidURIs

PROPERTIES
  ValidURIs <: LONG_URL

VARIABLES
  store, created_at

INVARIANT
  store : SHORT_CODE +-> LONG_URL &     /* partial function */
  created_at : SHORT_CODE +-> NAT &
  dom(store) = dom(created_at) &
  ran(store) <: ValidURIs                /* all stored URLs valid */

INITIALISATION
  store := {} ||
  created_at := {}

EVENTS

  Shorten =
    ANY url, code WHERE
      url : ValidURIs &                  /* guard: valid URL */
      code : SHORT_CODE &
      code /: dom(store)                 /* guard: code is fresh */
    THEN
      store := store \/ {code |-> url} ||
      created_at := created_at \/ {code |-> 0}
    END;

  Resolve =
    ANY code WHERE
      code : dom(store)                  /* guard: code exists */
    THEN
      skip                               /* no state change */
    END
    /* output modeled as: store(code) */

END
```

Rodin generates the proof obligations and discharges most of them without help, refinement supports
top-down design, and the guards line up neatly with HTTP preconditions; plugins even generate Java,
C, and Ada. For a REST service the machinery is too heavy. The syntax is verbose and unfamiliar,
events are atomic so there is no temporal reasoning, refinement is more apparatus than a CRUD service
needs, the Rodin tooling is Eclipse-based and aging, and relations have no multiplicity syntax.

## Z notation

Z builds everything out of schemas, each a bundle of declarations and predicates. A state schema
declares the variables and their types; an operation schema references a state schema and marks
inputs with `?`, outputs with `!`, and the after-state with a prime. The `Delta` convention pulls in
both before and after state, and `Xi` pulls in a state that stays fixed. The real notation uses box
diagrams; the listing below is the ASCII rendering.

```text
-- Basic type declarations
[STRING]

-- Abbreviation definitions
ShortCode == { s: STRING | #s >= 6 /\ #s <= 10 }
LongURL == { s: STRING | IsValidURI(s) }

-- State schema
|-- UrlShortenerState ----------------------------------------
| store : ShortCode -|-> LongURL        -- partial function
| created_at : ShortCode -|-> DateTime
|--------------------------------------------------------------
| dom store = dom created_at
| ran store <: { u: LongURL | IsValidURI(u) }
|--------------------------------------------------------------

-- Initial state
|-- InitUrlShortener -----------------------------------------
| UrlShortenerState
|--------------------------------------------------------------
| store = {}
| created_at = {}
|--------------------------------------------------------------

-- Shorten operation
|-- Shorten --------------------------------------------------
| Delta UrlShortenerState               -- imports store, store'
| url? : LongURL                        -- input
| code! : ShortCode                     -- output
|--------------------------------------------------------------
| IsValidURI(url?)                      -- precondition
| code! \notin dom store                -- code is fresh
| store' = store \cup { code! |-> url?} -- update
| created_at' = created_at \cup { code! |-> now }
| #store' = #store + 1
|--------------------------------------------------------------

-- Resolve operation
|-- Resolve --------------------------------------------------
| Xi UrlShortenerState                  -- Xi = no state change
| code? : ShortCode                     -- input
| url! : LongURL                        -- output
|--------------------------------------------------------------
| code? \in dom store                   -- precondition
| url! = store(code?)                   -- output definition
|--------------------------------------------------------------
```

Z is precise to a fault, its schema calculus composes operations by conjunction, disjunction, and
piping, and the `?`, `!`, and prime conventions leave no ambiguity about what is input, output, or
after-state. Against our needs the notation itself is the problem: it is heavily mathematical, the
schema boxes do not survive plain text, the tooling type-checks but does not execute (CZT, fuzz),
there is no temporal reasoning and no code generation, and the user community is small today.

## TypeSpec

TypeSpec describes an API's shapes with models and groups its operations into interfaces, then
annotates the HTTP details with decorators (`@route`, `@get`, `@post`, `@query`, `@path`, `@body`).
It compiles to OpenAPI, JSON Schema, and Protobuf. Microsoft built it as the successor to Cadl, and
the syntax is TypeScript with decorators.

```typespec
import "@typespec/http";
import "@typespec/rest";

using TypeSpec.Http;
using TypeSpec.Rest;

@doc("A short code identifier")
scalar ShortCode extends string;

@doc("A long URL")
scalar LongURL extends string;

model UrlMapping {
  @key code: ShortCode;
  url: LongURL;
  createdAt?: utcDateTime;
}

model ShortenRequest {
  url: LongURL;
}

model ShortenResponse {
  code: ShortCode;
  shortUrl: string;
}

@error
model ErrorResponse {
  code: int32;
  message: string;
}

@route("/")
namespace UrlShortener {

  @post
  @route("/shorten")
  op shorten(@body request: ShortenRequest):
    ShortenResponse | ErrorResponse;

  @get
  @route("/{code}")
  op resolve(@path code: ShortCode):
    { @statusCode statusCode: 302; @header location: string; }
    | ErrorResponse;

  @delete
  @route("/{code}")
  op remove(@path code: ShortCode):
    { @statusCode statusCode: 204; }
    | ErrorResponse;
}

// Note: TypeSpec has NO way to express:
// - store state
// - pre/postconditions
// - invariants
// - the relationship between shorten and resolve
```

The syntax is clean and modern, the tooling is good (a VS Code extension, a compiler, a playground),
the decorator system extends cleanly, and it emits several formats. It is also purely structural. It
cannot express behavior, state, invariants, or pre/postconditions, and the `op` keyword names the
HTTP interface without saying what the operation does. There is no meaning beyond the shape of the
request and the response.

## Smithy

Smithy adds a resource to the API-description idea: operations cluster around a lifecycle (create,
read, update, delete, list), and that resource ties them to a data model. Operations have input and
output shapes, and traits annotate everything, much like TypeSpec's decorators. AWS generates all of
its SDKs from Smithy. The syntax is an IDL with `@trait` annotations over C-like structure
definitions.

```text
$version: "2"

namespace com.example.urlshortener

resource UrlMapping {
    identifiers: { code: ShortCode }
    properties: { url: LongURL, createdAt: Timestamp }
    create: Shorten
    read: Resolve
    delete: Remove
}

@pattern("^[a-zA-Z0-9]{6,10}$")
string ShortCode

@pattern("^https?://.*$")
string LongURL

@http(method: "POST", uri: "/shorten")
operation Shorten {
    input := {
        @required
        url: LongURL
    }
    output := {
        @required
        code: ShortCode
        @required
        shortUrl: String
    }
    errors: [ValidationError, InternalError]
}

@readonly
@http(method: "GET", uri: "/{code}")
operation Resolve {
    input := {
        @required
        @httpLabel
        code: ShortCode
    }
    output := {
        @required
        url: LongURL
    }
    errors: [NotFoundError]
}

@idempotent
@http(method: "DELETE", uri: "/{code}")
operation Remove {
    input := {
        @required
        @httpLabel
        code: ShortCode
    }
    errors: [NotFoundError]
}

// Note: Smithy has NO way to express:
// - state transitions
// - pre/postconditions (only @required, @pattern)
// - invariants beyond simple shape constraints
// - the semantic meaning of operations
```

Resources map well to REST, the trait system extends freely, it generates code for seven or more
languages through smithy-codegen, and traits like `@readonly` and `@idempotent` carry intent. It is
proven at AWS scale. Like TypeSpec, though, it stays structural: traits cannot state behavioral
constraints, there is no system state or transition, and a resource's lifecycle is implied rather
than defined.

## P language

P models a system as communicating state machines. A machine has states and event handlers, events
carry payloads, and an incoming event drives a transition. Correctness properties live in separate
monitor machines that watch the event stream and flag a violation. AWS uses P on S3, DynamoDB,
Aurora, and EC2.

```text
// P language version

type tShortenReq = (url: string);
type tShortenResp = (code: string, shortUrl: string);
type tResolveReq = (code: string);
type tResolveResp = (url: string);

event eShortenReq : tShortenReq;
event eShortenResp : tShortenResp;
event eResolveReq : tResolveReq;
event eResolveResp : tResolveResp;
event eError : string;

machine UrlShortenerService {
  var store: map[string, string];

  start state Ready {
    on eShortenReq do (req: tShortenReq) {
      if (!isValidURI(req.url)) {
        send client, eError, "invalid URL";
        return;
      }
      var code = generateCode();
      assert code !in store,
        "generated code already exists";
      store[code] = req.url;
      send client, eShortenResp,
        (code = code, shortUrl = format("/{0}", code));
    }

    on eResolveReq do (req: tResolveReq) {
      if (req.code !in store) {
        send client, eError, "not found";
        return;
      }
      send client, eResolveResp,
        (url = store[req.code]);
    }
  }
}

// Safety specification (monitor machine)
spec machine AllURLsValid observes eShortenReq, eShortenResp {
  start state Monitoring {
    on eShortenReq do (req: tShortenReq) {
      assert isValidURI(req.url),
        "invariant: all stored URLs must be valid";
    }
  }
}
```

The state-machine model fits service lifecycles, monitor machines are a tidy way to check
invariants, and the event-driven shape matches how REST handles requests; AWS runs it on critical
services. For us the gaps are familiar by now: no relational data modeling, no multiplicities, no
pre/postcondition form (it uses an imperative `assert`), verification by systematic test exploration
rather than proof, and no path to a service implementation.

## Comparative summary

| Feature                   | Alloy   | TLA+    | Quint    | VDM-SL  | Dafny    | Event-B | Z       | TypeSpec | Smithy   | P        |
| ------------------------- | ------- | ------- | -------- | ------- | -------- | ------- | ------- | -------- | -------- | -------- |
| Relational data model     | **Yes** | No      | No       | Partial | No       | Partial | Partial | No       | Partial  | No       |
| Multiplicities            | **Yes** | No      | No       | No      | No       | Partial | No      | No       | No       | No       |
| Pre/postconditions        | Pred    | Action  | Action   | **Yes** | **Yes**  | Guard   | Schema  | No       | No       | Assert   |
| Temporal logic            | Alloy6  | **Yes** | **Yes**  | No      | No       | No      | No      | No       | No       | Partial  |
| Invariants                | Fact    | Inv     | Val      | **Yes** | **Yes**  | **Yes** | Schema  | No       | Trait    | Assert   |
| HTTP awareness            | No      | No      | No       | No      | No       | No      | No      | **Yes**  | **Yes**  | No       |
| Code generation           | No      | No      | No       | Partial | **Yes**  | Partial | No      | **Yes**  | **Yes**  | No       |
| Developer-friendly syntax | Medium  | Low     | **High** | Medium  | **High** | Low     | Low     | **High** | **High** | **High** |
| State transitions         | Alloy6  | **Yes** | **Yes**  | Partial | Partial  | **Yes** | Delta   | No       | No       | **Yes**  |
| Bounded checking          | **Yes** | **Yes** | **Yes**  | No      | No       | Proof   | No      | No       | No       | **Yes**  |

No single language here has everything this project needs. The DSL has to borrow Alloy's relational
modeling and multiplicities, the pre/postcondition syntax of VDM and Dafny, Quint's developer-facing
syntax, and the optional HTTP override that TypeSpec and Smithy offer.
