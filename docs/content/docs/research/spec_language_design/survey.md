---
title: "Survey of specification languages"
description: "How Alloy, TLA+, Quint, VDM, Dafny, Event-B, Z, TypeSpec, Smithy, and P express the same concepts"
---

We survey nine specification languages across four categories: relational modeling (Alloy),
state-transition systems (TLA+, Quint, Event-B, P language), operation modeling with contracts
(VDM-SL, Dafny), formal schemas (Z notation), and API description languages (TypeSpec, Smithy). For
each language we show how it expresses the same four concepts:

- A "store" mapping short codes to URLs
- A "shorten" operation that adds a new mapping
- A "resolve" operation that looks up by code
- An invariant that all stored URLs are valid

### 1.1 Alloy 6

**Category.** Relational modeling and bounded analysis.

**Core concepts.** Signatures (sigs) define sets of atoms. Fields define relations between sigs.
Facts constrain the model globally. Predicates define named reusable constraints. Assertions state
properties to check. Alloy 6 adds temporal operators (`always`, `eventually`, `after`, primed
variables) for state over time.

**Syntax style.** Declarative, relational. Uses mathematical operators (`+` for union, `&` for
intersection, `.` for relational join, `~` for transpose, `^` for transitive closure).
Multiplicities (`one`, `lone`, `some`, `set`) constrain field arities.

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

**Strengths.** Powerful relational reasoning, transitive closure, bounded model checking finds
counterexamples automatically. Alloy 6 temporal operators enable stateful reasoning.

**Weaknesses for our use.** No concept of HTTP or services. Bounded semantics means it cannot prove
properties for all sizes. String handling is weak. No code generation path. The `sig`/`fact`/`pred`
vocabulary is unfamiliar to most developers.

### 1.2 TLA+ (Temporal Logic of Actions)

**Category.** State-transition systems with temporal logic.

**Core concepts.** Variables hold the full system state. Actions are predicates over current state
and next state (primed variables). Temporal formulas (`[]` = always, `<>` = eventually, `~>` =
leads-to) specify liveness and safety. The `UNCHANGED` keyword frames unchanged variables.

**Syntax style.** Mathematical, uses LaTeX-like operators in the pretty-printed form. ASCII form
uses `/\`, `\/`, `=>`, `[]`, `<>`, `EXCEPT`.

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

**Strengths.** Temporal reasoning, proven at scale at Amazon (S3, DynamoDB, EBS). TLC model checker
is industrial strength. Can express liveness and fairness.

**Weaknesses for our use.** Verbose, mathematical syntax. No data modeling beyond functions and
sets. No concept of input/output for operations. No code generation. State is modeled as global
variables with `EXCEPT` updates, which is unintuitive for developers.

### 1.3 Quint

**Category.** State-transition systems with TypeScript-like syntax.

**Core concepts.** Same semantic model as TLA+ (variables, actions, temporal properties), but with a
modern syntax designed for developers. Uses `var` for state variables, `action` for state
transitions, `val` for definitions, `temporal` for properties.

**Syntax style.** TypeScript-like with explicit primed state (`variable' = ...`).

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

**Strengths.** Familiar syntax for developers. Same verification power as TLA+ (compiles to TLA+ for
checking). Has a REPL for interactive exploration. Active development by Informal Systems.

**Weaknesses for our use.** No data modeling (entities, fields, multiplicities). No
pre/postcondition syntax (uses conjunction of boolean expressions). No concept of operation
input/output as separate from action parameters.

### 1.4 VDM-SL (Vienna Development Method, Specification Language)

**Category.** Operation modeling with pre/postconditions.

**Core concepts.** Types define data. State defines the mutable system state. Operations have
explicit `pre` and `post` conditions referencing old state (`~`) and new state. Functions are pure.
Invariants are attached to types and state.

**Syntax style.** Keyword-heavy, reads like pseudocode. Uses `inv`, `pre`, `post`, `ext`.

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
  post url = store~(RESULT) and
       RESULT not in set dom store~ and
       card dom store = card dom store~ + 1;

  Resolve: ShortCode ==> LongURL
  Resolve(code) ==
    return store(code)
  pre code in set dom store
  post RESULT = store(code) and
       store = store~;
```

**Strengths.** Cleanest pre/postcondition syntax of any specification language. The `ext rd/wr`
clause explicitly declares which state components an operation reads or writes. Type invariants are
declared inline. The `~` suffix for "old state" is elegant.

**Weaknesses for our use.** Dated syntax (1970s origins). No temporal reasoning. No relational
modeling beyond maps and sets. Limited tool support (Overture IDE). No concept of relations with
multiplicities.

### 1.5 Dafny

**Category.** Verified programming with contracts.

**Core concepts.** Classes and datatypes define data. Methods have `requires` and `ensures` clauses.
Loop invariants and `decreases` clauses enable automated verification. Ghost variables and functions
exist only for specification. The verifier (Boogie/Z3) checks all contracts at compile time.

**Syntax style.** C-like with specification keywords. Verification is auto-active (the programmer
writes specs, the verifier fills in proofs).

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

**Strengths.** Specifications are machine-verified at compile time. `old()` for referring to
pre-state is clean. `modifies` clause controls framing. Compiles to C#, Java, Go, JavaScript,
Python. Most mature verification-aware language.

**Weaknesses for our use.** Too low-level for service specification. No multiplicity constraints on
relations. No convention engine for HTTP mapping. The programmer must write the implementation body,
not just the contract. Verification of string operations is weak.

### 1.6 Event-B

**Category.** Refinement-based formal modeling.

**Core concepts.** Contexts define sets, constants, and axioms (the static part). Machines define
variables, invariants, and events (the dynamic part). Events have guards (preconditions) and actions
(state updates). Refinement allows progressive detail addition. The Rodin platform discharges proof
obligations.

**Syntax style.** Set-theoretic, keyword-heavy. Uses mathematical operators but in ASCII form.

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

**Strengths.** Proof obligations are automatically generated and discharged. Refinement enables
top-down design. Guards map naturally to HTTP preconditions. Has code generation plugins for Java,
C, Ada. Set-theoretic foundation is expressive.

**Weaknesses for our use.** Verbose, unfamiliar syntax. No temporal reasoning (events are atomic).
Refinement is powerful but heavyweight for REST services. Tooling (Rodin) is Eclipse-based and
aging. No multiplicity syntax for relations.

### 1.7 Z Notation

**Category.** Formal schema-based specification.

**Core concepts.** Schemas define state and operations as collections of declarations and
predicates. State schemas declare variables and their types. Operation schemas reference a state
schema and use `?` suffix for inputs, `!` suffix for outputs, and primed variables for after-state.
The Delta convention (`Delta State`) imports both before and after state.

**Syntax style.** Mathematical, uses box notation. We show the ASCII/LaTeX-like form.

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

**Strengths.** Extremely precise. The schema calculus allows composition (conjunction, disjunction,
piping of schemas). The `?`/`!`/`'` conventions are elegant and unambiguous. Delta/Xi conventions
for state-changing/non-state-changing operations.

**Weaknesses for our use.** Heavy mathematical notation. Schema boxes do not render well in plain
text. No tool support for executable checking (only type-checking via CZT/fuzz). No temporal
reasoning. No code generation. Very small user community today.

### 1.8 TypeSpec

**Category.** API description language.

**Core concepts.** Models define data shapes. Interfaces define operations grouped by resource.
Decorators (`@route`, `@get`, `@post`, `@query`, `@path`, `@body`) annotate HTTP semantics. Emits
OpenAPI, JSON Schema, Protobuf. Built by Microsoft as successor to Cadl.

**Syntax style.** TypeScript-like with decorator annotations.

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

**Strengths.** Clean, modern syntax. Excellent tooling (VS Code extension, compiler, playground).
Emits multiple formats. Decorator system is extensible. Active development.

**Weaknesses for our use.** Purely structural. Cannot express behavior, state, invariants,
pre/postconditions, or any semantic constraint. The `op` keyword describes the HTTP interface, not
what the operation does. There is no formal meaning beyond "this is the shape of the request and
response."

### 1.9 Smithy

**Category.** API description language with resource modeling.

**Core concepts.** Resources group operations around a lifecycle (create, read, update, delete,
list). Operations have input/output shapes. Traits annotate everything (like decorators). The
resource concept connects operations to a data model. Used by AWS for all SDK generation.

**Syntax style.** IDL with `@trait` annotations and C-like structure definitions.

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

**Strengths.** Resource-centric modeling maps well to REST. Trait system is extremely extensible.
Battle-tested at AWS scale. Code generation for 7+ languages via smithy-codegen. The `@readonly`,
`@idempotent` traits communicate intent.

**Weaknesses for our use.** Like TypeSpec, purely structural. Traits cannot express behavioral
constraints. There is no concept of system state or state transitions. Resource lifecycles are
implicit rather than formally defined.

### 1.10 P Language

**Category.** Communicating state machines for protocol verification.

**Core concepts.** Machines are state machines with event handlers. Events carry payloads. State
transitions are triggered by events. Specifications are monitor machines that observe events and
flag violations. Used at AWS for S3, DynamoDB, Aurora, EC2.

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

**Strengths.** State machine model maps naturally to service lifecycles. Monitor machines are
elegant for invariant checking. Used in production at AWS for critical services. Event-driven
architecture matches REST request handling.

**Weaknesses for our use.** No relational data modeling. No multiplicity constraints. No
pre/postcondition syntax (uses imperative assert). Verification is testing-based via systematic
exploration rather than proof-based. No code generation to service implementations.

### 1.11 Comparative summary

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

Our DSL must combine Alloy's relational modeling and multiplicities, VDM/Dafny's
pre/postcondition syntax, Quint's developer-friendly syntax, and TypeSpec/Smithy's optional HTTP
override capability. No existing language provides all of these.
