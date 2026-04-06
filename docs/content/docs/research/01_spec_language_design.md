---
title: "Spec Language Design"
description: "Grammar, type system, semantics, and worked examples for the DSL"
---

> Research document for the spec-to-REST compiler's domain-specific language (DSL). Covers a survey
> of 9 existing specification languages, the complete grammar design, formal semantic model, worked
> examples, type system, error reporting, and a comparison with alternatives.

---

## Table of Contents

1. [Survey of Existing Spec Language Syntax and Semantics](#1-survey-of-existing-spec-language-syntax-and-semantics)
2. [Grammar Design](#2-grammar-design)
3. [Semantic Model](#3-semantic-model)
4. [Worked Examples](#4-worked-examples)
5. [Type System Deep Dive](#5-type-system-deep-dive)
6. [Error Messages and Developer Experience](#6-error-messages-and-developer-experience)
7. [Comparison with Alternatives](#7-comparison-with-alternatives)

---

## 1. Survey of Existing Spec Language Syntax and Semantics

We survey nine specification languages across four categories: relational modeling (Alloy),
state-transition systems (TLA+, Quint, Event-B, P language), operation modeling with contracts
(VDM-SL, Dafny), formal schemas (Z notation), and API description languages (TypeSpec, Smithy). For
each language we show how it expresses the same four concepts:

- **A "store" mapping short codes to URLs**
- **A "shorten" operation that adds a new mapping**
- **A "resolve" operation that looks up by code**
- **An invariant that all stored URLs are valid**

### 1.1 Alloy 6

**Category:** Relational modeling and bounded analysis.

**Core concepts:** Signatures (sigs) define sets of atoms. Fields define relations between sigs.
Facts constrain the model globally. Predicates define named reusable constraints. Assertions state
properties to check. Alloy 6 adds temporal operators (`always`, `eventually`, `after`, primed
variables) for state over time.

**Syntax style:** Declarative, relational. Uses mathematical operators (`+` for union, `&` for
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

**Strengths:** Powerful relational reasoning, transitive closure, bounded model checking finds
counterexamples automatically. Alloy 6 temporal operators enable stateful reasoning.

**Weaknesses for our use:** No concept of HTTP or services. Bounded semantics means it cannot prove
properties for all sizes. String handling is weak. No code generation path. The `sig`/`fact`/`pred`
vocabulary is unfamiliar to most developers.

### 1.2 TLA+ (Temporal Logic of Actions)

**Category:** State-transition systems with temporal logic.

**Core concepts:** Variables hold the full system state. Actions are predicates over current state
and next state (primed variables). Temporal formulas (`[]` = always, `<>` = eventually, `~>` =
leads-to) specify liveness and safety. The `UNCHANGED` keyword frames unchanged variables.

**Syntax style:** Mathematical, uses LaTeX-like operators in the pretty-printed form. ASCII form
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

**Strengths:** Temporal reasoning, proven at scale at Amazon (S3, DynamoDB, EBS). TLC model checker
is industrial strength. Can express liveness and fairness.

**Weaknesses for our use:** Verbose, mathematical syntax. No data modeling beyond functions and
sets. No concept of input/output for operations. No code generation. State is modeled as global
variables with `EXCEPT` updates, which is unintuitive for developers.

### 1.3 Quint

**Category:** State-transition systems with TypeScript-like syntax.

**Core concepts:** Same semantic model as TLA+ (variables, actions, temporal properties), but with a
modern syntax designed for developers. Uses `var` for state variables, `action` for state
transitions, `val` for definitions, `temporal` for properties.

**Syntax style:** TypeScript-like with explicit primed state (`variable' = ...`).

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

**Strengths:** Familiar syntax for developers. Same verification power as TLA+ (compiles to TLA+ for
checking). Has a REPL for interactive exploration. Active development by Informal Systems.

**Weaknesses for our use:** No data modeling (entities, fields, multiplicities). No
pre/postcondition syntax (uses conjunction of boolean expressions). No concept of operation
input/output as separate from action parameters.

### 1.4 VDM-SL (Vienna Development Method - Specification Language)

**Category:** Operation modeling with pre/postconditions.

**Core concepts:** Types define data. State defines the mutable system state. Operations have
explicit `pre` and `post` conditions referencing old state (`~`) and new state. Functions are pure.
Invariants are attached to types and state.

**Syntax style:** Keyword-heavy, reads like pseudocode. Uses `inv`, `pre`, `post`, `ext`.

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

**Strengths:** Cleanest pre/postcondition syntax of any specification language. The `ext rd/wr`
clause explicitly declares which state components an operation reads or writes. Type invariants are
declared inline. The `~` suffix for "old state" is elegant.

**Weaknesses for our use:** Dated syntax (1970s origins). No temporal reasoning. No relational
modeling beyond maps and sets. Limited tool support (Overture IDE). No concept of relations with
multiplicities.

### 1.5 Dafny

**Category:** Verified programming with contracts.

**Core concepts:** Classes and datatypes define data. Methods have `requires` and `ensures` clauses.
Loop invariants and `decreases` clauses enable automated verification. Ghost variables and functions
exist only for specification. The verifier (Boogie/Z3) checks all contracts at compile time.

**Syntax style:** C-like with specification keywords. Verification is auto-active (the programmer
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

**Strengths:** Specifications are machine-verified at compile time. `old()` for referring to
pre-state is clean. `modifies` clause controls framing. Compiles to C#, Java, Go, JavaScript,
Python. Most mature verification-aware language.

**Weaknesses for our use:** Too low-level for service specification. No multiplicity constraints on
relations. No convention engine for HTTP mapping. The programmer must write the implementation body,
not just the contract. Verification of string operations is weak.

### 1.6 Event-B

**Category:** Refinement-based formal modeling.

**Core concepts:** Contexts define sets, constants, and axioms (the static part). Machines define
variables, invariants, and events (the dynamic part). Events have guards (preconditions) and actions
(state updates). Refinement allows progressive detail addition. The Rodin platform discharges proof
obligations.

**Syntax style:** Set-theoretic, keyword-heavy. Uses mathematical operators but in ASCII form.

```
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

**Strengths:** Proof obligations are automatically generated and discharged. Refinement enables
top-down design. Guards map naturally to HTTP preconditions. Has code generation plugins for Java,
C, Ada. Set-theoretic foundation is expressive.

**Weaknesses for our use:** Verbose, unfamiliar syntax. No temporal reasoning (events are atomic).
Refinement is powerful but heavyweight for REST services. Tooling (Rodin) is Eclipse-based and
aging. No multiplicity syntax for relations.

### 1.7 Z Notation

**Category:** Formal schema-based specification.

**Core concepts:** Schemas define state and operations as collections of declarations and
predicates. State schemas declare variables and their types. Operation schemas reference a state
schema and use `?` suffix for inputs, `!` suffix for outputs, and primed variables for after-state.
The Delta convention (`Delta State`) imports both before and after state.

**Syntax style:** Mathematical, uses box notation. We show the ASCII/LaTeX-like form.

```
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

**Strengths:** Extremely precise. The schema calculus allows composition (conjunction, disjunction,
piping of schemas). The `?`/`!`/`'` conventions are elegant and unambiguous. Delta/Xi conventions
for state-changing/non-state-changing operations.

**Weaknesses for our use:** Heavy mathematical notation. Schema boxes do not render well in plain
text. No tool support for executable checking (only type-checking via CZT/fuzz). No temporal
reasoning. No code generation. Very small user community today.

### 1.8 TypeSpec

**Category:** API description language.

**Core concepts:** Models define data shapes. Interfaces define operations grouped by resource.
Decorators (`@route`, `@get`, `@post`, `@query`, `@path`, `@body`) annotate HTTP semantics. Emits
OpenAPI, JSON Schema, Protobuf. Built by Microsoft as successor to Cadl.

**Syntax style:** TypeScript-like with decorator annotations.

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

**Strengths:** Clean, modern syntax. Excellent tooling (VS Code extension, compiler, playground).
Emits multiple formats. Decorator system is extensible. Active development.

**Weaknesses for our use:** Purely structural. Cannot express behavior, state, invariants,
pre/postconditions, or any semantic constraint. The `op` keyword describes the HTTP interface, not
what the operation does. There is no formal meaning beyond "this is the shape of the request and
response."

### 1.9 Smithy

**Category:** API description language with resource modeling.

**Core concepts:** Resources group operations around a lifecycle (create, read, update, delete,
list). Operations have input/output shapes. Traits annotate everything (like decorators). The
resource concept connects operations to a data model. Used by AWS for all SDK generation.

**Syntax style:** IDL with `@trait` annotations and C-like structure definitions.

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

**Strengths:** Resource-centric modeling maps well to REST. Trait system is extremely extensible.
Battle-tested at AWS scale. Code generation for 7+ languages via smithy-codegen. The `@readonly`,
`@idempotent` traits communicate intent.

**Weaknesses for our use:** Like TypeSpec, purely structural. Traits cannot express behavioral
constraints. There is no concept of system state or state transitions. Resource lifecycles are
implicit, not formally defined.

### 1.10 P Language

**Category:** Communicating state machines for protocol verification.

**Core concepts:** Machines are state machines with event handlers. Events carry payloads. State
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

**Strengths:** State machine model maps naturally to service lifecycles. Monitor machines are
elegant for invariant checking. Used in production at AWS for critical services. Event-driven
architecture matches REST request handling.

**Weaknesses for our use:** No relational data modeling. No multiplicity constraints. No
pre/postcondition syntax (uses imperative assert). Verification is testing-based (systematic
exploration), not proof-based. No code generation to service implementations.

### 1.11 Comparative Summary

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

**Our DSL must combine:** Alloy's relational modeling and multiplicities, VDM/Dafny's
pre/postcondition syntax, Quint's developer-friendly syntax, and TypeSpec/Smithy's optional HTTP
override capability. No existing language provides all of these.

---

## 2. Grammar Design

### 2.1 Design Principles

The grammar follows these priorities:

1. **Readability over conciseness** -- prefer keywords to symbols
2. **Familiar syntax** -- brace-delimited blocks, dot-access, infix operators
3. **Minimal ceremony** -- no import boilerplate for common cases
4. **Unambiguous** -- every construct has exactly one parse
5. **Incremental** -- a minimal spec is valid; more detail can be added progressively

### 2.2 Lexical Rules

```text
(* ---- Lexical Grammar ---- *)

(* Identifiers *)
IDENT           = LETTER (LETTER | DIGIT | '_')* ;
UPPER_IDENT     = UPPER_LETTER (LETTER | DIGIT | '_')* ;
LOWER_IDENT     = LOWER_LETTER (LETTER | DIGIT | '_')* ;

LETTER          = 'a'..'z' | 'A'..'Z' ;
UPPER_LETTER    = 'A'..'Z' ;
LOWER_LETTER    = 'a'..'z' ;
DIGIT           = '0'..'9' ;

(* Literals *)
INT_LIT         = DIGIT+ ;
FLOAT_LIT       = DIGIT+ '.' DIGIT+ ;
STRING_LIT      = '"' (CHAR | ESCAPE)* '"' ;
BOOL_LIT        = 'true' | 'false' ;
CHAR            = <any Unicode except '"' and '\'> ;
ESCAPE          = '\' ('n' | 't' | 'r' | '\' | '"') ;
REGEX_LIT       = '/' (REGEX_CHAR)+ '/' ;
REGEX_CHAR      = <any Unicode except '/' and unescaped newline> ;

(* Keywords -- reserved, cannot be used as identifiers *)
KEYWORD         = 'service' | 'entity' | 'state' | 'operation'
                | 'input' | 'output' | 'requires' | 'ensures'
                | 'invariant' | 'fact' | 'conventions' | 'import'
                | 'module' | 'type' | 'enum' | 'transition'
                | 'one' | 'lone' | 'some' | 'set' | 'seq'
                | 'all' | 'no' | 'exists' | 'let' | 'in'
                | 'and' | 'or' | 'not' | 'implies' | 'iff'
                | 'if' | 'then' | 'else'
                | 'true' | 'false' | 'none'
                | 'pre' | 'where' | 'with' | 'the' | 'matches'
                | 'extends' | 'via' | 'when'
                | 'union' | 'intersect' | 'minus' | 'subset'
                | 'function' | 'predicate'
                | 'String' | 'Int' | 'Bool' | 'Float'
                | 'DateTime' | 'Duration'
                | 'Set' | 'Map' | 'Seq' | 'Option' ;

(* Operators *)
ARROW           = '->' ;
FAT_ARROW       = '=>' ;
PRIME           = "'" ;
DOT             = '.' ;
COMMA           = ',' ;
COLON           = ':' ;
PIPE            = '|' ;
HASH            = '#' ;
CARET           = '^' ;
AMPERSAND       = '&' ;
AT              = '@' ;
EQ              = '=' ;
NEQ             = '!=' ;
LT              = '<' ;
GT              = '>' ;
LTE             = '<=' ;
GTE             = '>=' ;
PLUS            = '+' ;
MINUS           = '-' ;
STAR            = '*' ;
SLASH           = '/' ;
LBRACE          = '{' ;
RBRACE          = '}' ;
LPAREN          = '(' ;
RPAREN          = ')' ;
LBRACKET        = '[' ;
RBRACKET        = ']' ;

(* Comments *)
LINE_COMMENT    = '//' <any>* NEWLINE ;
BLOCK_COMMENT   = '/*' <any>* '*/' ;

(* Whitespace -- ignored except as separator *)
WS              = (' ' | '\t' | '\r' | '\n')+ ;
```

### 2.3 Full EBNF Grammar

```text
(* ============================================================ *)
(* Top-Level Structure                                           *)
(* ============================================================ *)

spec_file       = { import_decl } service_decl ;

import_decl     = 'import' STRING_LIT ;

service_decl    = 'service' UPPER_IDENT '{'
                    { service_member }
                  '}' ;

service_member  = entity_decl
                | enum_decl
                | type_alias
                | state_decl
                | operation_decl
                | transition_decl
                | invariant_decl
                | fact_decl
                | function_decl
                | predicate_decl
                | convention_block
                ;

(* ============================================================ *)
(* Entity Declarations                                           *)
(* ============================================================ *)

entity_decl     = 'entity' UPPER_IDENT ['extends' UPPER_IDENT] '{'
                    { entity_member }
                  '}' ;

entity_member   = field_decl
                | entity_invariant
                ;

field_decl      = LOWER_IDENT ':' type_expr [field_constraint] ;

field_constraint = 'where' expr ;

entity_invariant = 'invariant' ':' expr ;

(* ============================================================ *)
(* Enum Declarations                                             *)
(* ============================================================ *)

enum_decl       = 'enum' UPPER_IDENT '{'
                    enum_value { ',' enum_value } [',']
                  '}' ;

enum_value      = UPPER_IDENT ;

(* ============================================================ *)
(* Type Aliases and Refinement Types                             *)
(* ============================================================ *)

type_alias      = 'type' UPPER_IDENT '=' type_expr ['where' expr] ;

(* ============================================================ *)
(* Type Expressions                                              *)
(* ============================================================ *)

(* Note: type_expr uses a base + suffix design to avoid left    *)
(* recursion. The relation arrow '->' is parsed as an optional  *)
(* suffix on a base type, not as a separate recursive rule.     *)

type_expr       = base_type [ '->' multiplicity base_type ] ;

base_type       = primitive_type
                | compound_type
                | option_type
                | UPPER_IDENT          (* entity, enum, or type alias ref *)
                ;

primitive_type  = 'String' | 'Int' | 'Bool' | 'Float'
                | 'DateTime' | 'Duration' ;

compound_type   = set_type | map_type | seq_type ;

set_type        = 'Set' '[' type_expr ']' ;

map_type        = 'Map' '[' type_expr ',' type_expr ']' ;

seq_type        = 'Seq' '[' type_expr ']' ;

option_type     = 'Option' '[' type_expr ']' ;

multiplicity    = 'one' | 'lone' | 'some' | 'set' ;

(* ============================================================ *)
(* State Declarations                                            *)
(* ============================================================ *)

state_decl      = 'state' '{'
                    { state_field }
                  '}' ;

state_field     = LOWER_IDENT ':' type_expr ;

(* ============================================================ *)
(* Operation Declarations                                        *)
(* ============================================================ *)

operation_decl  = 'operation' UPPER_IDENT '{'
                    [input_clause]
                    [output_clause]
                    [requires_clause]
                    [ensures_clause]
                  '}' ;

input_clause    = 'input' ':' param_list ;

output_clause   = 'output' ':' param_list ;

param_list      = param { ',' param } ;

param           = LOWER_IDENT ':' type_expr ;

requires_clause = 'requires' ':' expr_list ;

ensures_clause  = 'ensures' ':' expr_list ;

(* expr_list uses NEWLINE as separator. Within requires/ensures *)
(* blocks, each line is an independent expression; all lines    *)
(* are implicitly conjoined (AND'd). A NEWLINE is significant   *)
(* here: it separates consecutive expressions. Explicit 'and'   *)
(* within a single line still works for inline conjunction.     *)

expr_list       = expr { NEWLINE expr } ;

(* ============================================================ *)
(* Transition Declarations (State Machines)                      *)
(* ============================================================ *)

transition_decl = 'transition' UPPER_IDENT '{'
                    'entity' ':' UPPER_IDENT
                    'field' ':' LOWER_IDENT
                    { transition_rule }
                  '}' ;

transition_rule = enum_value '->' enum_value 'via' UPPER_IDENT
                  ['when' expr] ;

(* ============================================================ *)
(* Invariants and Facts                                          *)
(* ============================================================ *)

invariant_decl  = 'invariant' [LOWER_IDENT] ':' expr ;

fact_decl       = 'fact' [LOWER_IDENT] ':' expr ;

(* ============================================================ *)
(* User-Defined Functions and Predicates                         *)
(* ============================================================ *)

(* Functions return a typed value; predicates return Bool.       *)
(* Both are pure (no state mutation).                            *)

function_decl   = 'function' LOWER_IDENT '(' [param_list] ')'
                  ':' type_expr '=' expr ;

predicate_decl  = 'predicate' LOWER_IDENT '(' [param_list] ')'
                  '=' expr ;

(* ============================================================ *)
(* Convention Overrides                                           *)
(* ============================================================ *)

convention_block = 'conventions' '{'
                     { convention_rule }
                   '}' ;

convention_rule  = convention_target '.' convention_prop '=' convention_val ;

convention_target = UPPER_IDENT ;

convention_prop  = LOWER_IDENT
                 | LOWER_IDENT STRING_LIT   (* e.g., http_header "Location" *)
                 ;

convention_val   = STRING_LIT | INT_LIT | BOOL_LIT | expr ;

(* ============================================================ *)
(* Expressions                                                   *)
(* ============================================================ *)

(* Precedence from lowest (top) to highest (bottom).            *)
(* See section 2.4 for the full precedence table.               *)

expr            = or_expr ;

or_expr         = and_expr { 'or' and_expr } ;

and_expr        = not_expr { 'and' not_expr } ;

not_expr        = 'not' not_expr
                | implies_expr
                ;

implies_expr    = comparison_expr ['implies' comparison_expr]
                | comparison_expr ['iff' comparison_expr]
                ;

comparison_expr = set_op_expr { comp_op set_op_expr } ;

comp_op         = '=' | '!=' | '<' | '>' | '<=' | '>='
                | 'in' | 'not' 'in'
                | 'subset'                       (* A subset B *)
                | 'matches'                      (* s matches /regex/ *)
                ;

(* ---- Set-level infix operators ---- *)
(* These sit between comparison and additive so that             *)
(* 'A union B subset C' parses as '(A union B) subset C'.       *)

set_op_expr     = additive_expr
                  { ('union' | 'intersect' | 'minus') additive_expr } ;

additive_expr   = multiplicative_expr { ('+' | '-') multiplicative_expr } ;

multiplicative_expr = unary_expr { ('*' | '/') unary_expr } ;

unary_expr      = '#' unary_expr          (* cardinality *)
                | '-' unary_expr          (* negation *)
                | '^' unary_expr          (* transitive closure *)
                | with_expr
                ;

(* ---- 'with' record-update expression ---- *)
(* Parsed at a level between unary and postfix so that           *)
(*   pre(todos)[id] with { status = DONE }                      *)
(* parses as (pre(todos)[id]) with { status = DONE }.           *)

with_expr       = postfix_expr ['with' '{' field_assign
                    { ',' field_assign } '}'] ;

field_assign    = LOWER_IDENT '=' expr ;

(* ---- Postfix operators ---- *)

postfix_expr    = primary_expr { postfix_op } ;

postfix_op      = PRIME                   (* primed: store' *)
                | '.' LOWER_IDENT         (* field access *)
                | '[' expr ']'            (* indexing *)
                | '(' [arg_list] ')'      (* function call *)
                ;

arg_list        = expr { ',' expr } ;

(* ---- Primary expressions ---- *)

primary_expr    = INT_LIT
                | FLOAT_LIT
                | STRING_LIT
                | BOOL_LIT
                | REGEX_LIT
                | 'none'                          (* Option empty value *)
                | IDENT
                | 'pre' '(' IDENT ')'            (* pre-state reference *)
                | quantifier_expr
                | the_expr
                | some_wrap_expr
                | set_or_map_expr
                | sequence_literal
                | constructor_expr
                | lambda_expr
                | if_expr
                | let_expr
                | '(' expr ')'
                ;

(* ---- Quantifier expressions ---- *)
(* 'some' as quantifier: 'some x in S | P' -- uses 'in' and '|'. *)
(* Multi-variable: 'all x in A, y in B | P'.                     *)

quantifier_expr = quantifier quant_binding { ',' quant_binding }
                  '|' expr ;

quant_binding   = LOWER_IDENT 'in' expr ;

quantifier      = 'all' | 'some' | 'no' | 'exists' ;

(* ---- Disambiguation: some(expr) vs some x in ... ---- *)
(* 'some' followed by '(' is ambiguous. We resolve it by        *)
(* lookahead: if we see 'some' '(' expr ')' where the token     *)
(* after ')' is NOT 'in', it is an Option wrapper. If 'some'    *)
(* is followed by LOWER_IDENT 'in', it is a quantifier (handled *)
(* by quantifier_expr above). The dedicated some_wrap_expr rule *)
(* matches only the Option-wrapping case.                        *)

some_wrap_expr  = 'some' '(' expr ')' ;
  (* Constraint: must not be followed by 'in'; if the intent is *)
  (* a quantifier with parenthesized domain, use explicit form  *)
  (* 'some x in (S) | P'.                                       *)

(* ---- 'the' expression (definite description) ---- *)
(* Selects the unique element satisfying a predicate.            *)
(* Example: the s in sessions | sessions[s].access_token = t    *)

the_expr        = 'the' LOWER_IDENT 'in' expr '|' expr ;

(* ---- Lambda expressions ---- *)
(* Used in higher-order built-in calls like sum(coll, i => ...) *)

lambda_expr     = LOWER_IDENT '=>' expr ;

(* ---- Constructor expressions ---- *)
(* Creates an entity/record value: Name { field = val, ... }     *)

constructor_expr = UPPER_IDENT '{' field_assign { ',' field_assign } '}' ;

(* ---- Set, map, and relation literals ---- *)
(* We unify set literals, set comprehensions, and map/relation   *)
(* pair literals into a single rule with ordered alternatives.   *)

set_or_map_expr = '{' '}'                                   (* empty set/map *)
                | '{' LOWER_IDENT 'in' expr '|' expr '}'   (* set comprehension *)
                | '{' expr '->' expr
                    { ',' expr '->' expr } '}'              (* map/relation literal *)
                | '{' expr { ',' expr } '}'                 (* set literal *)
                ;

(* ---- Sequence literals ---- *)

sequence_literal = '[' ']'                                  (* empty sequence *)
                 | '[' expr { ',' expr } ']'                (* non-empty sequence *)
                 ;

(* ---- Conditional and let ---- *)

if_expr         = 'if' expr 'then' expr 'else' expr ;

let_expr        = 'let' LOWER_IDENT '=' expr 'in' expr ;

(* ============================================================ *)
(* Module System                                                 *)
(* ============================================================ *)

(* Files can import other spec files. The import makes all
   entities, types, and enums from the imported file available
   in the current scope. *)

(* Example:
     import "common/types.spec"
     import "auth/entities.spec"
*)
```

### 2.4 Operator Precedence (highest to lowest)

| Precedence  | Operators                                                            | Associativity |
| ----------- | -------------------------------------------------------------------- | ------------- |
| 1 (highest) | `#` (cardinality), unary `-`, `^` (transitive closure)               | Right         |
| 2           | `'` (prime), `.` (access), `[]` (index), `()` (call)                 | Left          |
| 2.5         | `with { ... }` (record update)                                       | Left          |
| 3           | `*`, `/`                                                             | Left          |
| 4           | `+`, `-`                                                             | Left          |
| 4.5         | `union`, `intersect`, `minus`                                        | Left          |
| 5           | `=`, `!=`, `<`, `>`, `<=`, `>=`, `in`, `not in`, `subset`, `matches` | Non-assoc     |
| 6           | `not`                                                                | Right         |
| 7           | `implies`, `iff`                                                     | Right         |
| 8           | `and`                                                                | Left          |
| 9           | `or`                                                                 | Left          |
| 10 (lowest) | `all`, `some`, `no`, `exists`, `the` (quantifiers)                   | N/A           |

### 2.5 Syntactic Sugar

The grammar supports several conveniences:

- **Trailing commas** are allowed in enum values, param lists, and set literals
- **Multi-line ensures/requires** -- each line in an ensures or requires block is implicitly
  conjoined (AND'd together); newlines are significant separators within `expr_list` (see the
  `expr_list` production). Explicit `and` within a single line still works for inline conjunction.
- **Primed state shorthand** -- `store'` means "the state of `store` after this operation executes"
- **pre() shorthand** -- `pre(store)` is equivalent to referring to `store` without a prime (the
  state before the operation); it exists for readability in ensures clauses where the unprimed name
  might be ambiguous
- **Cardinality shorthand** -- `#store` means `|store|` (the number of entries)
- **Record update** -- `expr with { field = val, ... }` creates a copy of the record with specified
  fields changed
- **some(v)** -- wraps a value in `Option[T]`; distinct from the `some` quantifier which always uses
  the `some x in S | P` form
- **Constructors** -- `TypeName { field = val, ... }` creates a new entity/record value
- **Map/relation pairs** -- `{a -> b, c -> d}` creates a map/relation literal
- **Sequence literals** -- `[a, b, c]` creates a `Seq` value
- **Lambda shorthand** -- `x => expr` for inline functions passed to `sum`, etc.

---

## 3. Semantic Model

### 3.1 What is "State"?

State is a **snapshot of all declared state fields at a point in time**. Formally:

```
State = { field_name -> value | field_name in state_decl.fields }
```

A state field holds a value of its declared type. For relation types (e.g.,
`store: ShortCode -> lone LongURL`), the value is a set of tuples. For simple types, the value is a
single element.

The system begins in the **initial state** where all relation-typed fields are empty sets/maps and
all scalar fields hold their type's default value (empty string, 0, false, epoch time).

Each operation transforms one state into another. The sequence of states forms a **trace**:
`S_0, S_1, S_2, ...` where `S_0` is the initial state and each subsequent state is the result of
applying an operation.

### 3.2 What does `pre(store)` mean?

Within an `ensures` clause, `pre(store)` refers to **the value of `store` in the state immediately
before the operation executed**. It is equivalent to `old(store)` in Dafny or `store~` in VDM-SL.

```
operation Shorten {
  ensures:
    code not in pre(store)    // code was NOT in store before this operation
    store'[code] = url        // code IS in store after this operation
}
```

The `pre()` function can wrap any state field name. It is only valid inside `ensures` clauses.
Inside `requires` clauses, all state field references implicitly refer to the pre-state (since the
requires clause is checked before the operation runs).

### 3.3 What does `store'` mean?

The prime notation `store'` refers to **the value of `store` in the state immediately after the
operation executes**. This is the post-state.

Formally, if operation `Op` transforms state `S` to state `S'`:

- `store` (unprimed, in requires) = `S.store`
- `pre(store)` (in ensures) = `S.store`
- `store'` (primed, in ensures) = `S'.store`

The ensures clause is a **predicate over the pre-state and post-state**. It must hold for the
operation to be correct.

In an `ensures` clause, an unprimed state field reference (`store`) refers to the **pre-state** (the
state before the operation), following the TLA+ convention. This means `pre(store)` and `store` are
synonymous in ensures clauses -- `pre()` exists for readability when the intent might otherwise be
ambiguous.

```
ensures:
  store'[code] = url          // post-state: code maps to url
  #store' = #store + 1        // post-size = pre-size + 1 (unprimed = pre-state)
  store' = store + {code -> url}  // post = pre + new mapping
```

### 3.4 How Do Quantifiers Work?

Four quantifier forms:

| Quantifier        | Syntax                  | Meaning                                                                                    |
| ----------------- | ----------------------- | ------------------------------------------------------------------------------------------ |
| Universal         | `all x in S \| P(x)`    | For every element `x` in collection `S`, predicate `P(x)` holds                            |
| Existential       | `some x in S \| P(x)`   | There exists at least one element `x` in `S` where `P(x)` holds                            |
| Existential (alt) | `exists x in S \| P(x)` | Synonym for `some`                                                                         |
| Negated universal | `no x in S \| P(x)`     | There is no element `x` in `S` where `P(x)` holds (equivalent to `all x in S \| not P(x)`) |

The collection `S` can be any expression that evaluates to a set, sequence, map (iterates over
keys), or the domain/range of a relation.

Nested quantifiers are supported:

```
all c in store | all d in store |
  (c != d) implies (store[c] != store[d])
// No two codes map to the same URL
```

### 3.5 How Do Set Operations Work?

| Operation      | Syntax                          | Meaning                            |
| -------------- | ------------------------------- | ---------------------------------- |
| Union          | `A + B` or `A union B`          | All elements in A or B             |
| Intersection   | `A & B` or `A intersect B`      | All elements in both A and B       |
| Difference     | `A - B` or `A minus B`          | Elements in A but not in B         |
| Cardinality    | `#A`                            | Number of elements in A            |
| Membership     | `x in A`                        | True if x is an element of A       |
| Non-membership | `x not in A`                    | True if x is not an element of A   |
| Subset         | `A subset B`                    | True if every element of A is in B |
| Empty check    | `#A = 0` or `no x in A \| true` | True if A has no elements          |

Set comprehensions create new sets:

```
{ c in store | store[c].startsWith("https") }
// The set of all codes whose URLs start with https
```

### 3.6 How Do Relation Operations Work?

Relations are sets of tuples. A state field `store: ShortCode -> lone LongURL` is a set of
`(ShortCode, LongURL)` pairs.

| Operation                    | Syntax                  | Meaning                                                               |
| ---------------------------- | ----------------------- | --------------------------------------------------------------------- |
| Lookup                       | `store[code]`           | The LongURL associated with code (if multiplicity allows exactly one) |
| Domain                       | `dom(store)`            | The set of all ShortCodes that have mappings                          |
| Range                        | `ran(store)`            | The set of all LongURLs that are mapped to                            |
| Override                     | `store + {code -> url}` | Store with the code->url mapping added or replaced                    |
| Removal                      | `store - {code}`        | Store with the code mapping removed                                   |
| Restriction                  | `store \| S`            | Store restricted to codes in set S                                    |
| Composition                  | `R . S`                 | Relational join: `{(a,c) \| exists b: (a,b) in R and (b,c) in S}`     |
| Transitive closure           | `^R`                    | R composed with itself until fixed point                              |
| Reflexive-transitive closure | `*R`                    | `^R + identity`                                                       |

### 3.7 How Do Multiplicities Constrain Relations?

Multiplicities appear in relation type declarations and constrain how many target values each source
value can map to:

| Multiplicity | Meaning                        | SQL Analogy                | Example                            |
| ------------ | ------------------------------ | -------------------------- | ---------------------------------- |
| `one`        | Exactly one target per source  | `NOT NULL` foreign key     | `owner: User -> one Team`          |
| `lone`       | Zero or one target per source  | Nullable foreign key       | `store: ShortCode -> lone LongURL` |
| `some`       | One or more targets per source | Junction table, `NOT NULL` | `tags: Item -> some Tag`           |
| `set`        | Any number (zero or more)      | Junction table, nullable   | `followers: User -> set User`      |

Multiplicities generate proof obligations:

- `one`: every operation that adds to the source domain must also add a target
- `lone`: operations may leave the target as "none" for a source
- `some`: every operation that adds a source must add at least one target
- `set`: no constraints on the number of targets

When the convention engine maps these to a database schema:

- `one` and `lone` become foreign key columns on the source table
- `some` and `set` become junction tables with appropriate constraints

---

## 4. Worked Examples

### 4.1 Example 1: URL Shortener

This is the running example used throughout the document.

```
service UrlShortener {

  // --- Type Definitions ---

  type ShortCode = String where len(value) >= 6 and len(value) <= 10
                              and value matches /^[a-zA-Z0-9]+$/

  type LongURL = String where len(value) > 0 and isValidURI(value)

  type BaseURL = String where isValidURI(value)

  // --- Entities ---

  entity UrlMapping {
    code: ShortCode
    url: LongURL
    created_at: DateTime
    click_count: Int where value >= 0

    invariant: isValidURI(url)
  }

  // --- State ---

  state {
    store: ShortCode -> lone LongURL       // partial function
    metadata: ShortCode -> lone UrlMapping  // full entity lookup
    base_url: BaseURL                       // configuration
  }

  // --- Operations ---

  operation Shorten {
    input:  url: LongURL
    output: code: ShortCode, short_url: String

    requires:
      isValidURI(url)

    ensures:
      code not in pre(store)                  // code was fresh
      store' = pre(store) + {code -> url}     // mapping added
      short_url = base_url + "/" + code       // URL constructed
      #store' = #pre(store) + 1               // exactly one added
      metadata'[code].url = url               // entity synced
      metadata'[code].click_count = 0         // initialized
  }

  operation Resolve {
    input:  code: ShortCode
    output: url: LongURL

    requires:
      code in store

    ensures:
      url = store[code]                       // correct lookup
      store' = store                          // state unchanged
      metadata'[code].click_count =
        pre(metadata)[code].click_count + 1   // increment clicks
  }

  operation Delete {
    input: code: ShortCode

    requires:
      code in store

    ensures:
      code not in store'                      // removed
      code not in metadata'                   // entity removed
      #store' = #pre(store) - 1               // exactly one removed
  }

  operation ListAll {
    output: entries: Set[UrlMapping]

    requires:
      true                                    // no precondition

    ensures:
      entries = { m in metadata | true }      // all mappings
      store' = store                          // no mutation
  }

  // --- Global Invariants ---

  invariant allURLsValid:
    all c in store | isValidURI(store[c])

  invariant metadataConsistent:
    dom(store) = dom(metadata)

  invariant clickCountNonNegative:
    all c in metadata | metadata[c].click_count >= 0

  // --- Convention Overrides ---

  conventions {
    Shorten.http_method = "POST"
    Shorten.http_path = "/shorten"
    Shorten.http_status_success = 201

    Resolve.http_method = "GET"
    Resolve.http_path = "/{code}"
    Resolve.http_status_success = 302
    Resolve.http_header "Location" = output.url

    Delete.http_method = "DELETE"
    Delete.http_path = "/{code}"
    Delete.http_status_success = 204

    ListAll.http_method = "GET"
    ListAll.http_path = "/urls"
    ListAll.http_status_success = 200
  }
}
```

**Convention engine output (without overrides):**

Without the conventions block, the engine would infer:

| Operation | Inferred Method                     | Inferred Path          | Inferred Status |
| --------- | ----------------------------------- | ---------------------- | --------------- |
| Shorten   | POST (mutates state, has input)     | `/url-mappings`        | 201             |
| Resolve   | GET (reads state, no mutation)      | `/url-mappings/{code}` | 200             |
| Delete    | DELETE (removes from state)         | `/url-mappings/{code}` | 204             |
| ListAll   | GET (reads collection, no mutation) | `/url-mappings`        | 200             |

The conventions block overrides these defaults to give a cleaner API.

### 4.2 Example 2: Todo List API

```
service TodoList {

  // --- Enums ---

  enum Status {
    TODO,
    IN_PROGRESS,
    DONE,
    ARCHIVED
  }

  enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
  }

  // --- Entities ---

  entity Todo {
    id: Int where value > 0
    title: String where len(value) >= 1 and len(value) <= 200
    description: Option[String]
    status: Status
    priority: Priority
    created_at: DateTime
    updated_at: DateTime
    completed_at: Option[DateTime]
    tags: Set[String]

    invariant: status = DONE implies completed_at != none
    invariant: status != DONE implies completed_at = none
    invariant: updated_at >= created_at
  }

  // --- State ---

  state {
    todos: Int -> lone Todo         // id to todo mapping
    next_id: Int                    // auto-increment counter
  }

  // --- State Machine ---

  transition TodoLifecycle {
    entity: Todo
    field: status

    TODO        -> IN_PROGRESS  via StartWork
    TODO        -> ARCHIVED     via Archive
    IN_PROGRESS -> DONE         via Complete
    IN_PROGRESS -> TODO         via PauseWork
    DONE        -> ARCHIVED     via Archive
    DONE        -> IN_PROGRESS  via Reopen     when updated_at > completed_at
  }

  // --- Operations ---

  operation CreateTodo {
    input:  title: String, description: Option[String],
            priority: Priority, tags: Set[String]
    output: todo: Todo

    requires:
      len(title) >= 1

    ensures:
      todo.id = pre(next_id)
      todo.title = title
      todo.description = description
      todo.status = TODO
      todo.priority = priority
      todo.tags = tags
      todo.completed_at = none
      next_id' = pre(next_id) + 1
      todos' = pre(todos) + {todo.id -> todo}
      #todos' = #pre(todos) + 1
  }

  operation GetTodo {
    input:  id: Int
    output: todo: Todo

    requires:
      id in todos

    ensures:
      todo = todos[id]
      todos' = todos
  }

  operation ListTodos {
    input:  status_filter: Option[Status],
            priority_filter: Option[Priority],
            tag_filter: Option[String]
    output: results: Seq[Todo]

    requires:
      true

    ensures:
      all t in results |
        t in ran(todos)
        and (status_filter = none or t.status = status_filter)
        and (priority_filter = none or t.priority = priority_filter)
        and (tag_filter = none or tag_filter in t.tags)
      todos' = todos
  }

  operation UpdateTodo {
    input:  id: Int, title: Option[String],
            description: Option[String], priority: Option[Priority],
            tags: Option[Set[String]]
    output: todo: Todo

    requires:
      id in todos

    ensures:
      todo.id = id
      title != none implies todo.title = title
      description != none implies todo.description = description
      priority != none implies todo.priority = priority
      tags != none implies todo.tags = tags
      title = none implies todo.title = pre(todos)[id].title
      todo.status = pre(todos)[id].status
      todo.updated_at >= pre(todos)[id].updated_at
      todos' = pre(todos) + {id -> todo}
  }

  operation StartWork {
    input: id: Int
    output: todo: Todo

    requires:
      id in todos
      todos[id].status = TODO

    ensures:
      todo = pre(todos)[id] with { status = IN_PROGRESS }
      todos' = pre(todos) + {id -> todo}
  }

  operation Complete {
    input: id: Int
    output: todo: Todo

    requires:
      id in todos
      todos[id].status = IN_PROGRESS

    ensures:
      todo = pre(todos)[id] with {
        status = DONE,
        completed_at = some(now())
      }
      todos' = pre(todos) + {id -> todo}
  }

  operation Reopen {
    input: id: Int
    output: todo: Todo

    requires:
      id in todos
      todos[id].status = DONE

    ensures:
      todo = pre(todos)[id] with {
        status = IN_PROGRESS,
        completed_at = none
      }
      todos' = pre(todos) + {id -> todo}
  }

  operation Archive {
    input: id: Int

    requires:
      id in todos
      todos[id].status in {TODO, DONE}

    ensures:
      todos'[id].status = ARCHIVED
      #todos' = #pre(todos)
  }

  operation DeleteTodo {
    input: id: Int

    requires:
      id in todos

    ensures:
      id not in todos'
      #todos' = #pre(todos) - 1
  }

  // --- Global Invariants ---

  invariant idsPositive:
    all id in todos | id > 0

  invariant nextIdMonotonic:
    next_id > 0

  invariant nextIdFresh:
    next_id not in todos

  invariant completedImpliesDone:
    all id in todos |
      todos[id].status = DONE iff todos[id].completed_at != none

  // --- Conventions ---

  conventions {
    CreateTodo.http_path = "/todos"
    CreateTodo.http_status_success = 201

    GetTodo.http_path = "/todos/{id}"
    ListTodos.http_path = "/todos"

    StartWork.http_method = "POST"
    StartWork.http_path = "/todos/{id}/start"

    Complete.http_method = "POST"
    Complete.http_path = "/todos/{id}/complete"

    Reopen.http_method = "POST"
    Reopen.http_path = "/todos/{id}/reopen"

    Archive.http_method = "POST"
    Archive.http_path = "/todos/{id}/archive"
  }
}
```

### 4.3 Example 3: User Authentication Service

```
service AuthService {

  // --- Types ---

  type Email = String where value matches /^[^@]+@[^@]+\.[^@]+$/
  type PasswordHash = String where len(value) = 64   // SHA-256 hex
  type Token = String where len(value) = 128
  type UserId = Int where value > 0

  // --- Enums ---

  enum TokenType {
    ACCESS,
    REFRESH,
    RESET
  }

  // --- Entities ---

  entity User {
    id: UserId
    email: Email
    password_hash: PasswordHash
    display_name: String where len(value) >= 1 and len(value) <= 100
    created_at: DateTime
    last_login: Option[DateTime]
    is_active: Bool

    invariant: len(email) > 0
  }

  entity Session {
    id: Int where value > 0
    user_id: UserId
    access_token: Token
    refresh_token: Token
    access_expires_at: DateTime
    refresh_expires_at: DateTime
    created_at: DateTime
    is_revoked: Bool

    invariant: access_expires_at > created_at
    invariant: refresh_expires_at > access_expires_at
    invariant: access_token != refresh_token
  }

  entity LoginAttempt {
    email: Email
    timestamp: DateTime
    success: Bool
  }

  // --- State ---

  state {
    users: UserId -> lone User
    sessions: Int -> lone Session
    login_attempts: Seq[LoginAttempt]
    user_by_email: Email -> lone User          // index
    next_user_id: UserId
    next_session_id: Int
  }

  // --- Operations ---

  operation Register {
    input:  email: Email, password: String,
            display_name: String
    output: user: User

    requires:
      email not in user_by_email               // email unique
      len(password) >= 8                       // password policy
      len(display_name) >= 1

    ensures:
      user.id = pre(next_user_id)
      user.email = email
      user.password_hash = hash(password)
      user.display_name = display_name
      user.is_active = true
      user.last_login = none
      next_user_id' = pre(next_user_id) + 1
      users' = pre(users) + {user.id -> user}
      user_by_email' = pre(user_by_email) + {email -> user}
      #users' = #pre(users) + 1
  }

  operation Login {
    input:  email: Email, password: String
    output: session: Session

    requires:
      email in user_by_email
      user_by_email[email].is_active = true
      user_by_email[email].password_hash = hash(password)
      recentFailedAttempts(email) < 5          // rate limiting

    ensures:
      let user = user_by_email[email] in
        session.user_id = user.id
        and session.is_revoked = false
        and session.access_expires_at > now()
        and session.refresh_expires_at > session.access_expires_at
        and sessions' = pre(sessions) + {session.id -> session}
        and users'[user.id].last_login = some(now())
        and login_attempts' = pre(login_attempts)
             + [LoginAttempt { email = email,
                               timestamp = now(),
                               success = true }]
  }

  operation LoginFailed {
    input: email: Email

    requires:
      email in user_by_email
      user_by_email[email].password_hash != hash(input_password)

    ensures:
      sessions' = sessions
      users' = users
      login_attempts' = pre(login_attempts)
        + [LoginAttempt { email = email,
                          timestamp = now(),
                          success = false }]
  }

  operation RefreshToken {
    input:  refresh_token: Token
    output: new_session: Session

    requires:
      some s in sessions |
        sessions[s].refresh_token = refresh_token
        and sessions[s].is_revoked = false
        and sessions[s].refresh_expires_at > now()

    ensures:
      let old_session = (the s in sessions |
        sessions[s].refresh_token = refresh_token) in
        // Old session revoked
        sessions'[old_session].is_revoked = true
        // New session created
        and new_session.user_id = pre(sessions)[old_session].user_id
        and new_session.is_revoked = false
        and new_session.access_expires_at > now()
        and sessions' = pre(sessions)
             + {old_session -> pre(sessions)[old_session]
                  with { is_revoked = true }}
             + {new_session.id -> new_session}
  }

  operation RequestPasswordReset {
    input:  email: Email
    output: reset_token: Token

    requires:
      email in user_by_email
      user_by_email[email].is_active = true

    ensures:
      // A new session of type RESET is created
      // (modeled as a session with special properties)
      some s in sessions' |
        s not in pre(sessions)
        and sessions'[s].user_id = user_by_email[email].id
        and sessions'[s].access_expires_at > now()
      users' = users
  }

  operation ResetPassword {
    input:  reset_token: Token, new_password: String

    requires:
      some s in sessions |
        sessions[s].access_token = reset_token
        and sessions[s].is_revoked = false
        and sessions[s].access_expires_at > now()
      len(new_password) >= 8

    ensures:
      let s = (the s in sessions |
        sessions[s].access_token = reset_token) in
        let user_id = pre(sessions)[s].user_id in
          users'[user_id].password_hash = hash(new_password)
          and sessions'[s].is_revoked = true
  }

  operation Logout {
    input: access_token: Token

    requires:
      some s in sessions |
        sessions[s].access_token = access_token
        and sessions[s].is_revoked = false

    ensures:
      let s = (the s in sessions |
        sessions[s].access_token = access_token) in
        sessions'[s].is_revoked = true
        and users' = users
  }

  // --- Helper Functions ---

  fact recentFailedAttemptsDef:
    all email in dom(user_by_email) |
      recentFailedAttempts(email) =
        #{ a in login_attempts |
           a.email = email
           and a.success = false
           and a.timestamp > now() - minutes(15) }

  // --- Global Invariants ---

  invariant uniqueEmails:
    all u1 in users, u2 in users |
      u1 != u2 implies users[u1].email != users[u2].email

  invariant emailIndexConsistent:
    all email in user_by_email |
      user_by_email[email] in ran(users)
      and user_by_email[email].email = email

  invariant sessionBelongsToUser:
    all s in sessions |
      sessions[s].user_id in users

  invariant revokedSessionsStayRevoked:
    all s in sessions |
      pre(sessions)[s].is_revoked = true implies sessions'[s].is_revoked = true

  invariant accessTokensUnique:
    all s1 in sessions, s2 in sessions |
      s1 != s2 implies
        sessions[s1].access_token != sessions[s2].access_token

  invariant rateLimitEnforced:
    all email in user_by_email |
      recentFailedAttempts(email) < 5 or
        (no op: Login | op.input.email = email)

  // --- Conventions ---

  conventions {
    Register.http_path = "/auth/register"
    Register.http_status_success = 201

    Login.http_path = "/auth/login"
    Login.http_method = "POST"
    Login.http_status_success = 200

    RefreshToken.http_path = "/auth/refresh"
    RefreshToken.http_method = "POST"

    RequestPasswordReset.http_path = "/auth/password-reset"
    RequestPasswordReset.http_method = "POST"

    ResetPassword.http_path = "/auth/password-reset/confirm"
    ResetPassword.http_method = "POST"

    Logout.http_path = "/auth/logout"
    Logout.http_method = "POST"
    Logout.http_status_success = 204
  }
}
```

### 4.4 Example 4: E-commerce Order Service

```
service OrderService {

  // --- Types ---

  type Money = Int where value >= 0           // cents
  type Quantity = Int where value > 0
  type SKU = String where len(value) >= 3 and len(value) <= 20
  type OrderId = Int where value > 0
  type CustomerId = Int where value > 0

  // --- Enums ---

  enum OrderStatus {
    DRAFT,
    PLACED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURNED
  }

  enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    REFUNDED,
    FAILED
  }

  // --- Entities ---

  entity Product {
    sku: SKU
    name: String where len(value) >= 1
    price: Money
    description: Option[String]

    invariant: price > 0
  }

  entity LineItem {
    id: Int where value > 0
    product_sku: SKU
    quantity: Quantity
    unit_price: Money
    line_total: Money

    invariant: line_total = unit_price * quantity
    invariant: unit_price > 0
  }

  entity Order {
    id: OrderId
    customer_id: CustomerId
    status: OrderStatus
    items: Set[LineItem]
    subtotal: Money
    tax: Money
    total: Money
    created_at: DateTime
    updated_at: DateTime
    shipped_at: Option[DateTime]
    delivered_at: Option[DateTime]

    invariant: subtotal = sum(items, i => i.line_total)
    invariant: total = subtotal + tax
    invariant: #items > 0 implies total > 0
    invariant: status = SHIPPED implies shipped_at != none
    invariant: status = DELIVERED implies delivered_at != none
    invariant: delivered_at != none implies shipped_at != none
  }

  entity Payment {
    id: Int where value > 0
    order_id: OrderId
    amount: Money
    status: PaymentStatus
    created_at: DateTime

    invariant: amount > 0
  }

  entity InventoryEntry {
    sku: SKU
    available: Int where value >= 0
    reserved: Int where value >= 0

    invariant: available >= 0
    invariant: reserved >= 0
  }

  // --- State ---

  state {
    orders: OrderId -> lone Order
    products: SKU -> lone Product
    inventory: SKU -> lone InventoryEntry
    payments: Int -> lone Payment
    next_order_id: OrderId
    next_payment_id: Int
  }

  // --- State Machine ---

  transition OrderLifecycle {
    entity: Order
    field: status

    DRAFT      -> PLACED     via PlaceOrder
    DRAFT      -> CANCELLED  via CancelOrder
    PLACED     -> PAID       via RecordPayment     when paymentCaptured(order_id)
    PLACED     -> CANCELLED  via CancelOrder
    PAID       -> SHIPPED    via ShipOrder
    PAID       -> CANCELLED  via CancelOrder       when refundIssued(order_id)
    SHIPPED    -> DELIVERED  via ConfirmDelivery
    DELIVERED  -> RETURNED   via ProcessReturn      when withinReturnWindow(order_id)
  }

  // --- Operations ---

  operation CreateDraftOrder {
    input:  customer_id: CustomerId
    output: order: Order

    requires:
      true

    ensures:
      order.id = pre(next_order_id)
      order.customer_id = customer_id
      order.status = DRAFT
      order.items = {}
      order.subtotal = 0
      order.tax = 0
      order.total = 0
      next_order_id' = pre(next_order_id) + 1
      orders' = pre(orders) + {order.id -> order}
  }

  operation AddLineItem {
    input:  order_id: OrderId, sku: SKU, quantity: Quantity
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = DRAFT
      sku in products
      sku in inventory
      inventory[sku].available >= quantity

    ensures:
      let product = products[sku] in
      let item = LineItem {
        product_sku = sku,
        quantity = quantity,
        unit_price = product.price,
        line_total = product.price * quantity
      } in
        order = pre(orders)[order_id] with {
          items = pre(orders)[order_id].items + {item},
          subtotal = pre(orders)[order_id].subtotal + item.line_total,
          total = pre(orders)[order_id].subtotal + item.line_total
                  + pre(orders)[order_id].tax
        }
        orders' = pre(orders) + {order_id -> order}
        // Reserve inventory
        inventory'[sku].reserved =
          pre(inventory)[sku].reserved + quantity
        inventory'[sku].available =
          pre(inventory)[sku].available - quantity
  }

  operation RemoveLineItem {
    input:  order_id: OrderId, item_id: Int
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = DRAFT
      some i in orders[order_id].items | i.id = item_id

    ensures:
      let removed = (the i in pre(orders)[order_id].items |
                      i.id = item_id) in
        order = pre(orders)[order_id] with {
          items = pre(orders)[order_id].items - {removed},
          subtotal = pre(orders)[order_id].subtotal - removed.line_total,
          total = pre(orders)[order_id].subtotal - removed.line_total
                  + pre(orders)[order_id].tax
        }
        orders' = pre(orders) + {order_id -> order}
        // Release reserved inventory
        inventory'[removed.product_sku].reserved =
          pre(inventory)[removed.product_sku].reserved - removed.quantity
        inventory'[removed.product_sku].available =
          pre(inventory)[removed.product_sku].available + removed.quantity
  }

  operation PlaceOrder {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = DRAFT
      #orders[order_id].items > 0

    ensures:
      order = pre(orders)[order_id] with { status = PLACED }
      orders' = pre(orders) + {order_id -> order}
      inventory' = inventory    // reservations stay
  }

  operation RecordPayment {
    input:  order_id: OrderId, amount: Money
    output: payment: Payment

    requires:
      order_id in orders
      orders[order_id].status = PLACED
      amount = orders[order_id].total

    ensures:
      payment.order_id = order_id
      payment.amount = amount
      payment.status = CAPTURED
      orders'[order_id].status = PAID
      payments' = pre(payments) + {payment.id -> payment}
      next_payment_id' = pre(next_payment_id) + 1
  }

  operation ShipOrder {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = PAID

    ensures:
      order = pre(orders)[order_id] with {
        status = SHIPPED,
        shipped_at = some(now())
      }
      orders' = pre(orders) + {order_id -> order}
      // Committed inventory: reduce reserved count
      all item in order.items |
        inventory'[item.product_sku].reserved =
          pre(inventory)[item.product_sku].reserved - item.quantity
  }

  operation ConfirmDelivery {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = SHIPPED

    ensures:
      order = pre(orders)[order_id] with {
        status = DELIVERED,
        delivered_at = some(now())
      }
      orders' = pre(orders) + {order_id -> order}
  }

  operation CancelOrder {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status in {DRAFT, PLACED, PAID}

    ensures:
      order = pre(orders)[order_id] with { status = CANCELLED }
      orders' = pre(orders) + {order_id -> order}
      // Release all reserved inventory
      all item in pre(orders)[order_id].items |
        inventory'[item.product_sku].available =
          pre(inventory)[item.product_sku].available + item.quantity
        and inventory'[item.product_sku].reserved =
          pre(inventory)[item.product_sku].reserved - item.quantity
      // Refund if paid
      orders[order_id].status = PAID implies
        some p in payments' |
          p not in pre(payments)
          and payments'[p].order_id = order_id
          and payments'[p].status = REFUNDED
  }

  operation ProcessReturn {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders
      orders[order_id].status = DELIVERED
      // Within 30-day return window
      orders[order_id].delivered_at != none
      now() - orders[order_id].delivered_at <= days(30)

    ensures:
      order = pre(orders)[order_id] with { status = RETURNED }
      orders' = pre(orders) + {order_id -> order}
      // Restore inventory
      all item in pre(orders)[order_id].items |
        inventory'[item.product_sku].available =
          pre(inventory)[item.product_sku].available + item.quantity
      // Issue refund
      some p in payments' |
        p not in pre(payments)
        and payments'[p].order_id = order_id
        and payments'[p].status = REFUNDED
        and payments'[p].amount = pre(orders)[order_id].total
  }

  operation GetOrder {
    input:  order_id: OrderId
    output: order: Order

    requires:
      order_id in orders

    ensures:
      order = orders[order_id]
      orders' = orders
  }

  operation ListOrders {
    input:  customer_id: Option[CustomerId],
            status_filter: Option[OrderStatus]
    output: results: Seq[Order]

    requires:
      true

    ensures:
      all o in results |
        o in ran(orders)
        and (customer_id = none or o.customer_id = customer_id)
        and (status_filter = none or o.status = status_filter)
      orders' = orders
  }

  // --- Global Invariants ---

  invariant orderTotalConsistency:
    all oid in orders |
      orders[oid].total =
        sum(orders[oid].items, i => i.line_total) + orders[oid].tax

  invariant lineItemTotalConsistency:
    all oid in orders |
      all item in orders[oid].items |
        item.line_total = item.unit_price * item.quantity

  invariant inventoryNonNegative:
    all sku in inventory |
      inventory[sku].available >= 0
      and inventory[sku].reserved >= 0

  invariant placedOrdersHaveItems:
    all oid in orders |
      orders[oid].status != DRAFT implies #orders[oid].items > 0

  invariant paidOrdersHavePayments:
    all oid in orders |
      orders[oid].status in {PAID, SHIPPED, DELIVERED} implies
        some pid in payments |
          payments[pid].order_id = oid
          and payments[pid].status = CAPTURED

  invariant cancelledPaidOrdersHaveRefunds:
    all oid in orders |
      orders[oid].status = CANCELLED implies
        (all pid in payments |
          payments[pid].order_id = oid and payments[pid].status = CAPTURED
          implies
          some rid in payments |
            payments[rid].order_id = oid and payments[rid].status = REFUNDED)

  invariant inventoryReservationsMatchOrders:
    all sku in inventory |
      inventory[sku].reserved =
        sum({ item in { oid in orders | orders[oid].status in {DRAFT, PLACED, PAID} } |
              some i in orders[item].items | i.product_sku = sku },
            order_id => sum(
              { i in orders[order_id].items | i.product_sku = sku },
              i => i.quantity))

  // --- Conventions ---

  conventions {
    CreateDraftOrder.http_path = "/orders"
    CreateDraftOrder.http_status_success = 201

    AddLineItem.http_method = "POST"
    AddLineItem.http_path = "/orders/{order_id}/items"
    AddLineItem.http_status_success = 201

    RemoveLineItem.http_method = "DELETE"
    RemoveLineItem.http_path = "/orders/{order_id}/items/{item_id}"

    PlaceOrder.http_method = "POST"
    PlaceOrder.http_path = "/orders/{order_id}/place"

    RecordPayment.http_method = "POST"
    RecordPayment.http_path = "/orders/{order_id}/payments"
    RecordPayment.http_status_success = 201

    ShipOrder.http_method = "POST"
    ShipOrder.http_path = "/orders/{order_id}/ship"

    ConfirmDelivery.http_method = "POST"
    ConfirmDelivery.http_path = "/orders/{order_id}/deliver"

    CancelOrder.http_method = "POST"
    CancelOrder.http_path = "/orders/{order_id}/cancel"

    ProcessReturn.http_method = "POST"
    ProcessReturn.http_path = "/orders/{order_id}/return"

    GetOrder.http_path = "/orders/{order_id}"
    ListOrders.http_path = "/orders"
  }
}
```

---

## 5. Type System Deep Dive

### 5.1 Primitive Types and Their Constraints

| Type       | Values                       | Default | SQL Mapping                | JSON Mapping        |
| ---------- | ---------------------------- | ------- | -------------------------- | ------------------- |
| `String`   | Unicode text                 | `""`    | `TEXT` or `VARCHAR(n)`     | `string`            |
| `Int`      | Arbitrary-precision integers | `0`     | `INTEGER` or `BIGINT`      | `integer`           |
| `Float`    | IEEE 754 double              | `0.0`   | `DOUBLE PRECISION`         | `number`            |
| `Bool`     | `true`, `false`              | `false` | `BOOLEAN`                  | `boolean`           |
| `DateTime` | UTC timestamp                | epoch   | `TIMESTAMP WITH TIME ZONE` | `string` (ISO 8601) |

Primitive types have no subtypes. Each primitive can be refined with a `where` clause to create a
named constrained type.

### 5.2 Compound Types

**Set[T]** -- An unordered collection of unique values of type T.

```
tags: Set[String]           // SQL: junction table or JSON array
                            // JSON: array (unique values enforced at app layer)
```

**Seq[T]** -- An ordered sequence of values of type T (may contain duplicates).

```
login_attempts: Seq[LoginAttempt]   // SQL: table with ordering column
                                    // JSON: array
```

**Map[K, V]** -- A mapping from keys of type K to values of type V.

```
settings: Map[String, String]       // SQL: key-value table or JSON column
                                    // JSON: object
```

**Option[T]** -- Either a value of type T or `none`.

```
description: Option[String]         // SQL: nullable column
                                    // JSON: field may be absent or null
```

### 5.3 Entity Types and Database Tables

Each `entity` declaration maps to a database table. The mapping follows these rules:

| Entity Feature                           | Database Mapping                                                           |
| ---------------------------------------- | -------------------------------------------------------------------------- |
| Entity name                              | Table name (pluralized, snake_case)                                        |
| Field with primitive type                | Column                                                                     |
| Field with `Option[T]`                   | Nullable column                                                            |
| Field with `Set[T]` where T is primitive | JSON array column or junction table                                        |
| Field with `Set[T]` where T is entity    | Junction table                                                             |
| Field with entity type                   | Foreign key column                                                         |
| `where` clause on field                  | `CHECK` constraint                                                         |
| Entity `invariant`                       | `CHECK` constraint (if expressible in SQL) or application-level validation |

Example:

```
entity Todo {
  id: Int where value > 0
  title: String where len(value) >= 1 and len(value) <= 200
  status: Status
  tags: Set[String]
}
```

Generates:

```sql
CREATE TABLE todos (
  id INTEGER PRIMARY KEY CHECK (id > 0),
  title VARCHAR(200) NOT NULL CHECK (length(title) >= 1),
  status VARCHAR(20) NOT NULL CHECK (status IN ('TODO','IN_PROGRESS','DONE','ARCHIVED')),
  tags JSONB DEFAULT '[]'
);
```

### 5.4 Relation Types and Database Foreign Keys

A relation type `A -> mult B` declares a relationship between type A and type B with the given
multiplicity.

| Relation Type | Database Mapping                                                   |
| ------------- | ------------------------------------------------------------------ |
| `A -> one B`  | Column on A's table: `b_id INTEGER NOT NULL REFERENCES b(id)`      |
| `A -> lone B` | Column on A's table: `b_id INTEGER REFERENCES b(id)` (nullable)    |
| `A -> some B` | Junction table with at least-one constraint (trigger or app-level) |
| `A -> set B`  | Junction table: `a_b(a_id REFERENCES a, b_id REFERENCES b)`        |

State-level relations map to the core data model:

```
state {
  store: ShortCode -> lone LongURL
}
```

This creates either a single table with both columns or two tables with a foreign key, depending on
whether ShortCode and LongURL are entities or value types.

### 5.5 Parametric Types

The type system supports parameterized types:

```
Set[T]          -- for any type T
Map[K, V]       -- for any types K, V
Seq[T]          -- for any type T
Option[T]       -- for any type T
```

These are the only parametric types in the language. Users cannot define their own generic types.
This keeps the type system simple and ensures every type has a clear database and JSON mapping.

### 5.6 Refinement Types (Constrained Types)

The `where` clause creates a refinement type -- a base type with an additional predicate that must
always hold:

```
type ShortCode = String where len(value) >= 6 and len(value) <= 10
                            and value matches /^[a-zA-Z0-9]+$/

type Money = Int where value >= 0

type Percentage = Float where value >= 0.0 and value <= 100.0

type Email = String where value matches /^[^@]+@[^@]+\.[^@]+$/
```

Within the `where` clause, `value` refers to the instance being constrained.

Refinement types generate:

- **Database**: `CHECK` constraints
- **API validation**: Request body validation rules (e.g., JSON Schema `pattern`, `minimum`,
  `maximum`)
- **OpenAPI**: Corresponding schema constraints
- **Runtime**: Validation functions that throw on violation

Refinement types are **not** separate types from their base -- a `ShortCode` is a `String` with
extra constraints. Any function that accepts `String` also accepts `ShortCode`. But a function that
requires `ShortCode` does not accept an arbitrary `String` (the constraint must be proven or checked
at runtime).

### 5.7 Type Inference Rules

The spec language uses explicit types in declarations but infers types in expressions:

| Expression                  | Inferred Type                                                                  |
| --------------------------- | ------------------------------------------------------------------------------ |
| Integer literal             | `Int`                                                                          |
| Float literal               | `Float`                                                                        |
| String literal              | `String`                                                                       |
| Boolean literal             | `Bool`                                                                         |
| `field_name` (in operation) | Type of the field from state declaration                                       |
| `entity.field`              | Type of that field in the entity                                               |
| `store[key]`                | Target type of the relation (e.g., `LongURL` from `ShortCode -> lone LongURL`) |
| `dom(rel)`                  | `Set[SourceType]`                                                              |
| `ran(rel)`                  | `Set[TargetType]`                                                              |
| `#collection`               | `Int`                                                                          |
| `a + b` (both Int)          | `Int`                                                                          |
| `a + b` (sets)              | Same set type                                                                  |
| `a and b`                   | `Bool`                                                                         |
| `x in S`                    | `Bool`                                                                         |
| `pre(field)`                | Same type as `field`                                                           |
| `field'`                    | Same type as `field`                                                           |
| `if c then a else b`        | Least common supertype of a and b                                              |
| Quantifier expression       | `Bool`                                                                         |

### 5.8 Subtyping and Compatibility Rules

The type system has a simple subtyping hierarchy:

1. **Refinement subtyping**: `type ShortCode = String where P` means `ShortCode <: String`. Any
   `ShortCode` value can be used where a `String` is expected, but not vice versa.

2. **Option subtyping**: `T <: Option[T]` -- any value of type T can be used where `Option[T]` is
   expected (it is implicitly wrapped in `some`).

3. **Enum subtyping**: Enum types do not participate in subtyping. Each enum is a distinct type.

4. **Entity subtyping**: `entity Child extends Parent` means `Child <: Parent`. A `Child` value can
   be used where a `Parent` is expected. Child inherits all fields and invariants, and may add new
   ones.

5. **Collection subtyping**: Collections are **invariant** in their type parameter. `Set[Child]` is
   NOT a subtype of `Set[Parent]`. This prevents runtime type errors.

6. **Numeric compatibility**: `Int` values can be used where `Float` is expected (widening), but not
   vice versa.

Type errors are reported at spec-check time, before any code generation occurs.

---

## 6. Error Messages and Developer Experience

### 6.1 Categories of Errors the Spec Checker Can Catch

The spec checker validates the specification before any code generation. It detects errors in
several categories:

**Category 1: Syntax Errors**

Standard parse errors. The parser reports the exact location and suggests corrections.

```
error[E001]: Expected ':' after field name
  --> url_shortener.spec:12:15
   |
12 |     code ShortCode
   |          ^^^^^^^^^^ expected ':' here
   |
help: add a colon between the field name and type
   |
12 |     code: ShortCode
   |         +
```

**Category 2: Type Errors**

Type mismatches in expressions, wrong argument types, missing fields.

```
error[E101]: Type mismatch in ensures clause
  --> url_shortener.spec:28:7
   |
28 |     code not in pre(store)
   |     ^^^^ expected ShortCode, found LongURL
   |
note: 'code' is declared as type LongURL in the output clause (line 24)
note: 'store' maps ShortCode -> LongURL, so membership test requires ShortCode
help: did you mean to check the output 'code' field? check the output type declaration
```

**Category 3: Multiplicity Violations**

Operations that violate the declared multiplicity constraints.

```
error[E201]: Multiplicity violation in ensures clause
  --> url_shortener.spec:30:7
   |
30 |     store'[code] = url1
31 |     store'[code] = url2
   |     ^^^^^^^^^^^^^^^^^^ 'store' has multiplicity 'lone' (at most one target per source)
   |     but postcondition assigns two different values for the same key
   |
note: store is declared as 'ShortCode -> lone LongURL' at line 18
note: 'lone' means each ShortCode maps to at most one LongURL
```

**Category 4: Unreachable Operations**

Operations whose preconditions can never be satisfied given the invariants and other operations.

```
warning[W301]: Operation 'Resolve' may be unreachable
  --> url_shortener.spec:33:3
   |
33 |   operation Resolve {
   |   ^^^^^^^^^^^^^^^^^
   |
note: Resolve requires 'code in store' (line 37)
note: No operation in this service adds entries to 'store'
      (Shorten's ensures clause does not establish 'code in store' for arbitrary codes)
help: verify that at least one operation's postcondition establishes the precondition
```

**Category 5: Conflicting Invariants**

Invariants that cannot all hold simultaneously.

```
error[E401]: Conflicting invariants detected
  --> order_service.spec:145:3
   |
145 |   invariant: all oid in orders | #orders[oid].items > 0
   |   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
   |
  --> order_service.spec:48:7
   |
48  |     order.items = {}      // CreateDraftOrder ensures empty items
   |     ^^^^^^^^^^^^^^^^
   |
note: Invariant on line 145 requires all orders to have at least one item
note: But CreateDraftOrder (line 42) creates orders with empty items
note: This means the invariant is violated immediately after CreateDraftOrder
help: Either weaken the invariant to exclude DRAFT orders:
   |
145 |   invariant: all oid in orders |
   |     orders[oid].status != DRAFT implies #orders[oid].items > 0
   |
      Or change CreateDraftOrder to require at least one item
```

**Category 6: Incomplete Frame Conditions**

Operations that do not specify what happens to all state fields.

```
warning[W501]: Incomplete frame condition in operation 'Shorten'
  --> url_shortener.spec:25:3
   |
25 |   operation Shorten {
   |   ^^^^^^^^^^^^^^^^^
   |
note: State has fields: store, metadata, base_url
note: Shorten's ensures clause mentions: store', metadata'
note: Shorten's ensures clause does NOT mention: base_url
help: If base_url should not change, add: base_url' = base_url
      If this omission is intentional, the checker assumes unchanged fields
      remain unchanged (closed-world assumption)
```

**Category 7: Postcondition Not Achievable**

The postcondition cannot be satisfied given the precondition.

```
error[E601]: Postcondition may not be achievable
  --> url_shortener.spec:29:7
   |
29 |     #store' = #pre(store) + 2
   |     ^^^^^^^^^^^^^^^^^^^^^^^^^
   |
note: Operation Shorten adds exactly one mapping (store' = pre(store) + {code -> url})
note: But the cardinality postcondition claims two entries were added
note: Counterexample: pre(store) = {}, after adding one mapping, #store' = 1, not 2
help: Did you mean #store' = #pre(store) + 1?
```

**Category 8: Transition Violations**

State machine transitions that violate the declared transition rules.

```
error[E701]: Invalid state transition
  --> todo_service.spec:62:7
   |
62 |     todo = pre(todos)[id] with { status = DONE }
   |                                          ^^^^
   |
note: Operation 'StartWork' transitions status from TODO
note: But DONE is not a valid target from TODO
note: Valid transitions from TODO: IN_PROGRESS (via StartWork), ARCHIVED (via Archive)
note: Defined in transition 'TodoLifecycle' at line 38
help: Did you mean status = IN_PROGRESS?
```

### 6.2 IDE Support Considerations

The spec language should support modern IDE features via a Language Server Protocol (LSP)
implementation.

**Syntax Highlighting:**

Token categories for TextMate/tree-sitter grammars:

- `keyword.control` -- `service`, `entity`, `operation`, `requires`, `ensures`, `invariant`,
  `state`, `transition`, `conventions`
- `keyword.operator` -- `and`, `or`, `not`, `implies`, `iff`, `in`, `all`, `some`, `no`, `exists`
- `storage.type` -- `String`, `Int`, `Bool`, `Float`, `DateTime`, `Set`, `Map`, `Seq`, `Option`
- `storage.modifier` -- `one`, `lone`, `some`, `set`
- `entity.name.type` -- user-defined entity names and type aliases
- `entity.name.function` -- operation names
- `variable.other` -- field names, parameter names
- `string.quoted.double` -- string literals
- `constant.numeric` -- integer and float literals
- `constant.language` -- `true`, `false`, `none`
- `comment.line` -- `//` comments
- `comment.block` -- `/* */` comments

**Auto-complete suggestions:**

| Context                         | Suggestions                                                                                      |
| ------------------------------- | ------------------------------------------------------------------------------------------------ |
| After `requires:` or `ensures:` | State field names, input/output parameter names, built-in functions                              |
| After a state field name        | `.` for field access, `'` for prime, `[` for index                                               |
| After `->` in type expr         | Multiplicity keywords (`one`, `lone`, `some`, `set`)                                             |
| Inside `conventions { }`        | Operation names, convention properties                                                           |
| After `in`                      | State fields that are collections, `dom()`, `ran()`                                              |
| After `entity` keyword          | `extends` for inheritance                                                                        |
| Top level of service            | `entity`, `enum`, `type`, `state`, `operation`, `transition`, `invariant`, `fact`, `conventions` |

**Diagnostics:**

Real-time type checking and invariant consistency checking as the user types. Squiggly underlines
for errors and warnings. Quick-fix suggestions for common mistakes.

**Go-to-definition:**

Click on a type name to jump to its entity/type declaration. Click on a state field to jump to its
declaration. Click on an operation name in a transition rule to jump to the operation.

**Hover information:**

Hovering over a state field shows its type and the invariants that mention it. Hovering over an
operation shows a summary: inputs, outputs, what state it reads and writes. Hovering over a
multiplicity keyword shows a plain-English explanation.

**Code lenses:**

Above each operation: "2 invariants apply" (clickable to list them). Above each invariant: "Checked
by operations: Shorten, Delete" (clickable). Above each transition: a visual state machine diagram
rendered inline.

---

## 7. Comparison with Alternatives

### 7.1 Why Not Just Use Alloy Directly?

**What Alloy offers:** The most expressive relational data modeling of any specification language.
Multiplicities, transitive closure, set comprehensions, and the Alloy Analyzer for bounded model
checking.

**Why it is insufficient for our purpose:**

1. **No HTTP concepts.** Alloy has no notion of requests, responses, status codes, or endpoints.
   Every REST mapping would need to be encoded as a convention outside Alloy.

2. **Bounded semantics.** The Alloy Analyzer only checks properties up to a finite bound (e.g., "for
   all models with at most 5 ShortCodes and 5 LongURLs"). This is useful for finding bugs but cannot
   prove properties for all sizes.

3. **No code generation.** Alloy is analysis-only. Alchemy (2008) attempted Alloy-to-code
   compilation but it was a research prototype that died. There is no maintained path from Alloy to
   running code.

4. **Unfamiliar syntax.** Alloy's `sig`/`fact`/`pred`/`assert` vocabulary and relational operators
   (`.`, `~`, `^`, `+`, `&`) are unfamiliar to most developers. The learning curve is significant.

5. **No input/output modeling.** Alloy predicates take parameters but there is no distinction
   between "input to an operation" and "output from an operation." This makes it impossible to
   derive request/response schemas automatically.

6. **Weak string handling.** Alloy treats strings as opaque atoms. There is no way to express
   constraints like "length >= 6" or "matches regex" natively.

**What we take from Alloy:** Relational data modeling, multiplicities, the `sig`-like entity
concept, bounded analysis as a verification backend.

### 7.2 Why Not Just Use TLA+/Quint?

**What TLA+/Quint offers:** The most powerful state-transition modeling of any specification
language. Temporal logic for safety and liveness properties. Industrial validation at Amazon (S3,
DynamoDB, EBS, and others).

**Why it is insufficient:**

1. **No data modeling.** TLA+ has no concept of entities, fields, or multiplicities. Everything is a
   mathematical function or set. There is no way to declare that "a ShortCode has a value field of
   type String" -- you just have sets and functions.

2. **No code generation.** TLA+ and Quint are verification-only. The model checker explores states
   but produces no implementation code. Quint can generate traces but not service code.

3. **No pre/postcondition structure.** TLA+ actions are predicates over current and next state.
   There is no syntactic distinction between preconditions (what must hold before) and
   postconditions (what must hold after). This makes it harder to derive HTTP status codes
   (precondition failure = 4xx) and validation logic.

4. **Global state model.** TLA+ uses global variables with `UNCHANGED` for framing. This does not
   map well to REST services where each operation should declare what state it touches.

5. **No type constraints.** TLA+ is untyped (Quint adds types but not refinement types). There is no
   way to express "ShortCode is a String of length 6 to 10."

**What we take from TLA+/Quint:** Primed variables for post-state (`store'`), action semantics (each
operation is a predicate over pre/post state), Quint's developer-friendly syntax style.

### 7.3 Why Not Just Use OpenAPI?

**What OpenAPI offers:** The industry standard for describing REST API structure. Massive ecosystem
of tools (editors, validators, code generators, testing tools).

**Why it is insufficient:**

1. **No behavioral specification.** OpenAPI describes the shape of requests and responses but says
   nothing about what the operations do. You can say "POST /shorten accepts a URL and returns a
   code" but you cannot say "the returned code was not previously in the store" or "the store now
   has one more entry."

2. **No state model.** OpenAPI has no concept of server-side state. There is no way to express "the
   service maintains a mapping of codes to URLs" or "deleting a code removes it from the mapping."

3. **No invariants.** OpenAPI cannot express "all stored URLs are valid" or "no two users share the
   same email." Schema constraints (patterns, min/max) apply to individual fields, not cross-entity
   relationships.

4. **No relationship between operations.** OpenAPI cannot express that "Resolve returns the URL that
   was stored by a previous Shorten call." Each endpoint is specified independently.

5. **Verbose.** An OpenAPI spec for a simple URL shortener is hundreds of lines of YAML. Our DSL
   expresses the same structural information plus behavioral specs in under 80 lines.

**What we take from OpenAPI:** It is a compilation target. The convention engine emits OpenAPI specs
from our DSL, gaining the entire OpenAPI ecosystem (Swagger UI, Schemathesis, client generators) for
free.

### 7.4 Why Not Just Use Dafny?

**What Dafny offers:** The most mature auto-active verification language. Pre/postconditions,
invariants, termination proofs, and compilation to 5+ languages.

**Why it is insufficient:**

1. **No convention engine.** Dafny requires the programmer to write the full implementation,
   including HTTP routing, request parsing, response serialization, database queries, and error
   handling. Our DSL delegates all of this to the convention engine.

2. **Too low-level.** A Dafny implementation of a URL shortener requires explicit loop invariants,
   framing conditions (`modifies`), and implementation details that are not part of the
   specification. Our DSL only requires the "what," not the "how."

3. **No relational modeling.** Dafny uses maps, sequences, and sets but has no concept of
   multiplicities or relational joins. The type `map<string, string>` does not communicate that this
   is a partial function from codes to URLs.

4. **No HTTP awareness.** Dafny has no decorators, annotations, or conventions for mapping
   operations to REST endpoints.

5. **String verification is weak.** Dafny struggles to verify properties of string operations
   (length, pattern matching, concatenation). This is a limitation of the underlying Z3 SMT solver.

**What we take from Dafny:** The `requires`/`ensures` syntax, `old()` for pre-state reference, and
Dafny as the intermediate verification target for business logic synthesis.

### 7.5 Why Not Just Use TypeSpec?

**What TypeSpec offers:** A clean, modern DSL for API description with excellent tooling and
multiple output formats (OpenAPI, JSON Schema, Protobuf).

**Why it is insufficient:**

1. **No behavioral verification.** TypeSpec describes API structure but cannot express what
   operations do, what state they modify, or what invariants they maintain. It is a schema language,
   not a specification language.

2. **No state model.** TypeSpec has no concept of server-side state. It describes the interface, not
   the behavior behind it.

3. **No pre/postconditions.** TypeSpec's `@doc` decorators can describe behavior in natural language
   but this is not machine-checkable.

4. **No invariants.** TypeSpec can constrain field values (patterns, min/max) but cannot express
   cross-field or cross-entity invariants.

**What we take from TypeSpec:** The decorator/annotation pattern for HTTP overrides, the syntax
style (TypeScript-like), and the multi-output compilation model.

### 7.6 What Does Our Language Add That None of These Have?

Our DSL is the first language that combines all of the following in a single formalism:

| Capability                                          | Source of Inspiration              | Status in Existing Languages                      |
| --------------------------------------------------- | ---------------------------------- | ------------------------------------------------- |
| Relational data model with multiplicities           | Alloy                              | Only in Alloy (no code gen)                       |
| Pre/postcondition operation contracts               | VDM, Dafny                         | Only in verification languages (no HTTP)          |
| State-transition modeling with primed vars          | TLA+, Quint                        | Only in temporal logic tools (no data model)      |
| Enum-based state machines with transition rules     | P language, Event-B                | Only in protocol verifiers (no REST)              |
| Refinement types with constraints                   | Dafny, Liquid Haskell              | Only in type-theory languages                     |
| Convention-based HTTP/DB mapping                    | JHipster (implicit)                | No spec language has this                         |
| Convention override blocks                          | TypeSpec decorators, Smithy traits | Only in API description languages (no behavior)   |
| Global invariants generating DB constraints + tests | Event-B (partial)                  | No language generates both                        |
| Single-file service specification                   | None                               | Each existing language covers part of the picture |

The key innovation is not any individual feature but the combination of all of them in a language
designed specifically for REST service specification. The convention engine is the bridge that makes
a behavioral specification into a running service without requiring the developer to write HTTP
routes, SQL queries, or serialization code.

**The design philosophy is:** The developer specifies WHAT the service should do (entities, state,
operations, invariants). The convention engine decides HOW to implement it (HTTP methods, database
schema, API paths, error responses). The developer can override any convention decision, but the
defaults should be correct for 90% of cases.

This is analogous to how a modern web framework works -- the developer writes business logic and the
framework handles routing, serialization, and middleware -- but elevated to the specification level.
Instead of writing business logic code, the developer writes behavioral contracts, and the compiler
generates verified business logic code.

---

## Appendix A: Built-in Functions

The following functions are available in all expression contexts:

| Function                | Signature                   | Meaning                                     |
| ----------------------- | --------------------------- | ------------------------------------------- |
| `len(s)`                | `String -> Int`             | Length of string                            |
| `isValidURI(s)`         | `String -> Bool`            | String is a valid URI (RFC 3986)            |
| `startsWith(s, prefix)` | `String, String -> Bool`    | String starts with prefix                   |
| `endsWith(s, suffix)`   | `String, String -> Bool`    | String ends with suffix                     |
| `contains(s, sub)`      | `String, String -> Bool`    | String contains substring                   |
| `matches(s, regex)`     | `String, Regex -> Bool`     | String matches regex pattern                |
| `dom(r)`                | `Relation -> Set`           | Domain of a relation                        |
| `ran(r)`                | `Relation -> Set`           | Range of a relation                         |
| `#expr`                 | `Collection -> Int`         | Cardinality (number of elements)            |
| `sum(coll, fn)`         | `Collection, Lambda -> Int` | Sum of fn applied to each element           |
| `min(a, b)`             | `Int, Int -> Int`           | Minimum                                     |
| `max(a, b)`             | `Int, Int -> Int`           | Maximum                                     |
| `abs(n)`                | `Int -> Int`                | Absolute value                              |
| `now()`                 | `-> DateTime`               | Current timestamp                           |
| `days(n)`               | `Int -> Duration`           | Duration of n days                          |
| `hours(n)`              | `Int -> Duration`           | Duration of n hours                         |
| `minutes(n)`            | `Int -> Duration`           | Duration of n minutes                       |
| `hash(s)`               | `String -> String`          | Cryptographic hash (implementation-defined) |
| `some(v)`               | `T -> Option[T]`            | Wrap value in Option                        |
| `none`                  | `-> Option[T]`              | Empty Option                                |

## Appendix B: Convention Engine Default Rules

The convention engine maps spec elements to REST/HTTP/DB artifacts using these default rules. All
defaults can be overridden in the `conventions` block.

**HTTP Method Inference:**

| Operation Characteristic                                           | Default Method |
| ------------------------------------------------------------------ | -------------- |
| Has ensures with state mutation, has input, creates new entity     | `POST`         |
| No state mutation (all primed = unprimed)                          | `GET`          |
| Removes entity from state                                          | `DELETE`       |
| Modifies existing entity (primed state field changes, no new keys) | `PATCH`        |
| Replaces existing entity entirely                                  | `PUT`          |

**HTTP Path Inference:**

| Operation Characteristic   | Default Path                               |
| -------------------------- | ------------------------------------------ |
| Creates entity of type T   | `/{t-plural}`                              |
| Reads single entity by key | `/{t-plural}/{key}`                        |
| Lists entities             | `/{t-plural}`                              |
| State transition on entity | `/{t-plural}/{key}/{operation-name-kebab}` |
| Deletes entity by key      | `/{t-plural}/{key}`                        |

**HTTP Status Code Inference:**

| Outcome                                               | Default Status             |
| ----------------------------------------------------- | -------------------------- |
| Successful creation (POST)                            | `201 Created`              |
| Successful read (GET)                                 | `200 OK`                   |
| Successful update (PATCH/PUT)                         | `200 OK`                   |
| Successful deletion (DELETE)                          | `204 No Content`           |
| Precondition fails: entity not found (`x in store`)   | `404 Not Found`            |
| Precondition fails: validation (`isValidURI(url)`)    | `422 Unprocessable Entity` |
| Precondition fails: state conflict (`status = DRAFT`) | `409 Conflict`             |
| Invariant would be violated                           | `422 Unprocessable Entity` |

**Database Mapping:**

| Spec Element                       | Default DB Artifact                            |
| ---------------------------------- | ---------------------------------------------- |
| Entity                             | Table (name: snake_case plural of entity name) |
| Entity field (primitive)           | Column                                         |
| Entity field (Option[T])           | Nullable column                                |
| Entity field (Set[T], T primitive) | JSONB column or junction table                 |
| Entity field (Set[T], T entity)    | Junction table                                 |
| State field (A -> one B)           | Non-nullable foreign key on A's table          |
| State field (A -> lone B)          | Nullable foreign key on A's table              |
| State field (A -> some/set B)      | Junction table                                 |
| Type constraint (where)            | CHECK constraint                               |
| Invariant (field-level)            | CHECK constraint                               |
| Invariant (cross-entity)           | Trigger or application-level enforcement       |
| Enum                               | PostgreSQL ENUM type or CHECK constraint       |

## Appendix C: Formal Notation Comparison Cheat Sheet

This table maps common formal notation to our DSL syntax, showing how we replace mathematical
symbols with readable keywords.

| Concept               | Math / Alloy / TLA+       | Our DSL                    |
| --------------------- | ------------------------- | -------------------------- |
| For all               | `\A x \in S`, `all x: S`  | `all x in S \| P(x)`      |
| There exists          | `\E x \in S`, `some x: S` | `some x in S \| P(x)`     |
| None satisfy          | `no x: S`                 | `no x in S \| P(x)`       |
| Not element of        | `\notin`, `not in`        | `x not in S`              |
| Element of            | `\in`, `in`               | `x in S`                  |
| Union                 | `\cup`, `+`               | `A + B`                   |
| Intersection          | `\cap`, `&`               | `A & B`                   |
| Difference            | `\setminus`, `-`          | `A - B`                   |
| Cardinality           | `\|S\|`, `#S`             | `#S`                      |
| Next-state (primed)   | `x'`, `EXCEPT`            | `x'`                      |
| Previous state        | `~`, `old()`              | `pre(x)`                  |
| Logical and           | `\land`, `/\`             | `and`                     |
| Logical or            | `\lor`, `\/`              | `or`                      |
| Logical not           | `\lnot`, `~`              | `not`                     |
| Implies               | `=>`, `\implies`          | `implies`                 |
| If and only if        | `<=>`, `\iff`             | `iff`                     |
| Partial function      | `+->`, `-> lone`          | `-> lone`                 |
| Total function        | `-->`, `-> one`           | `-> one`                  |
| Always (temporal)     | `[]`, `always`            | (future: `always`)        |
| Eventually (temporal) | `<>`, `eventually`        | (future: `eventually`)    |

---

<!-- Added: auth/authz model (gap analysis) -->

## Appendix D: Authentication and Authorization in the Spec Language

The spec language must model authentication and authorization as first-class constructs so that
access control is formally verifiable, not just a middleware afterthought. This addresses OWASP API
Top 10 #1 (Broken Object Level Authorization), #2 (Broken Authentication), and #5 (Broken Function
Level Authorization).

### D.1 Caller Context

Every operation implicitly has access to a `caller` context representing the authenticated
principal. The `caller` entity is built-in:

```
builtin entity Caller {
  id: Int
  role: Role           // enum defined per-service
  authenticated: Bool
  scopes: Set[String]  // OAuth-style scopes
}
```

### D.2 Authentication Guards

The simplest guard requires only that a caller is authenticated:

```
operation CreatePost {
  input:  title: String, body: String
  output: post: Post

  requires:
    authenticated                         // shorthand for caller.authenticated = true
    len(title) >= 1

  ensures:
    post.author_id = caller.id
    posts' = pre(posts) + {post.id -> post}
}
```

The convention engine maps `requires: authenticated` to JWT/bearer token validation middleware.
Unauthenticated requests receive HTTP 401.

### D.3 Role-Based Access Control (RBAC)

Role checks use the `caller.role` field against a service-defined enum:

```
enum Role { User, Moderator, Admin }

operation DeleteUser {
  input:  user_id: Int

  requires:
    authenticated
    caller.role in {Admin}                // only admins

  ensures:
    user_id not in users'
}

operation BanUser {
  input:  user_id: Int

  requires:
    authenticated
    caller.role in {Admin, Moderator}     // admins or moderators

  ensures:
    users'[user_id].banned = true
}
```

The convention engine maps `caller.role in {X, Y}` to role-checking middleware. Unauthorized callers
receive HTTP 403.

### D.4 Resource Ownership (Object-Level Authorization)

The most critical pattern -- preventing users from accessing or modifying resources they do not own:

```
operation UpdatePost {
  input:  post_id: Int, body: String
  output: post: Post

  requires:
    authenticated
    post_id in posts
    caller.id = posts[post_id].author_id   // ownership check

  ensures:
    post.body = body
    posts' = pre(posts) + {post_id -> post}
}
```

The convention engine maps `caller.id = resource.owner_field` to a generated authorization check
that runs after resource lookup. Violations return HTTP 403 with an error body
`{ "code": "FORBIDDEN", "detail": "You do not own this resource" }`.

### D.5 Scope-Based Access (OAuth/API Key Scopes)

For APIs that use OAuth scopes or API key permissions:

```
operation ReadAnalytics {
  input:  report_id: Int
  output: report: AnalyticsReport

  requires:
    authenticated
    "analytics:read" in caller.scopes

  ensures:
    report = analytics[report_id]
}
```

### D.6 Convention Engine Mapping

| Spec Construct                            | Convention Output                                |
| ----------------------------------------- | ------------------------------------------------ |
| `requires: authenticated`                 | Bearer token middleware; HTTP 401 on failure     |
| `requires: caller.role in {Admin}`        | Role-check middleware; HTTP 403 on failure       |
| `requires: caller.id = resource.owner_id` | Post-lookup ownership check; HTTP 403 on failure |
| `requires: "scope" in caller.scopes`      | Scope validation middleware; HTTP 403 on failure |
| No auth `requires` on an operation        | Public endpoint (no middleware)                  |

The auth model is formally verifiable: the model checker can confirm that every state mutation is
reachable only through operations with appropriate guards, and that no operation accidentally
exposes state without an ownership check.

---

<!-- Added: concurrent access modeling (gap analysis) -->

## Appendix E: Concurrent Access and Conflict Detection

REST services must handle concurrent requests that modify the same resource. The spec language
models this through optimistic concurrency control using a built-in `version` mechanism and explicit
conflict detection in preconditions.

### E.1 Version Fields

Any entity can declare a `version` field that the runtime auto-increments on each mutation:

```
entity Order {
  id: Int
  status: OrderStatus
  total: Float
  version: Int          // auto-incremented on each write
}
```

### E.2 Optimistic Locking in Operations

Operations that update existing resources can require version matching:

```
operation UpdateOrder {
  input:  order_id: Int, total: Float, expected_version: Int
  output: order: Order

  requires:
    order_id in orders
    orders[order_id].version = expected_version   // conflict detection

  ensures:
    order.total = total
    order.version = pre(orders)[order_id].version + 1
    orders' = pre(orders) + {order_id -> order}
}
```

The convention engine maps `version = expected_version` preconditions to:

- An `If-Match` / ETag header check (HTTP layer)
- A `WHERE version = $expected` clause in the UPDATE SQL (DB layer)
- HTTP 409 Conflict response when the version does not match

### E.3 Convention Engine Output

| Spec Pattern                                    | Generated Artifact                                                        |
| ----------------------------------------------- | ------------------------------------------------------------------------- |
| `entity.version: Int`                           | DB column `version INT NOT NULL DEFAULT 0`, auto-increment trigger        |
| `requires: resource.version = expected_version` | `UPDATE ... WHERE version = $v` + row-count check                         |
| Version mismatch detected                       | HTTP 409 with `{ "code": "CONFLICT", "detail": "Resource was modified" }` |
| `ETag` header                                   | Generated from `"{entity_type}:{id}:{version}"`                           |

---

<!-- Added: external service calls as abstract effects (gap analysis) -->

## Appendix F: External Service Calls as Abstract Effects

Real services call external systems: payment gateways, email providers, SMS APIs, third-party data
sources. The spec language models these as **abstract effects** -- declared interfaces with
postconditions but no implementation, similar to Haskell's IO monad or algebraic effects.

### F.1 Declaring External Effects

```
effect PaymentGateway {
  action Charge {
    input:  amount: Float, currency: String, token: String
    output: charge_id: String, success: Bool

    ensures:
      success implies len(charge_id) > 0
  }
}

effect EmailService {
  action Send {
    input:  to: String, subject: String, body: String
    output: sent: Bool
  }
}
```

### F.2 Using Effects in Operations

Operations declare which effects they use. The convention engine generates a dependency injection
interface; the actual implementation is provided by the developer or by a generated client stub.

```
operation PlaceOrder {
  input:  order_id: Int, payment_token: String
  output: confirmation: OrderConfirmation
  uses:   PaymentGateway, EmailService

  requires:
    order_id in orders
    orders[order_id].status = Pending

  ensures:
    let charge = PaymentGateway.Charge(orders[order_id].total, "USD", payment_token)
    charge.success implies orders'[order_id].status = Confirmed
    not charge.success implies orders'[order_id].status = PaymentFailed
    charge.success implies EmailService.Send(customer_email, "Order Confirmed", ...)
}
```

### F.3 Convention Engine Mapping

| Spec Construct                  | Convention Output                                                        |
| ------------------------------- | ------------------------------------------------------------------------ |
| `effect PaymentGateway { ... }` | Abstract interface/protocol (Python ABC, Go interface, Java interface)   |
| `uses: PaymentGateway`          | Constructor dependency injection parameter                               |
| Effect `action` postconditions  | Property tests for the mock; integration test contract for the real impl |

The model checker verifies operation logic assuming effect postconditions hold. The conformance test
suite provides mock implementations that satisfy the declared postconditions. The developer supplies
production implementations.

---

<!-- Added: non-determinism handling (gap analysis) -->

## Appendix G: Handling Non-Determinism in Specifications

Specifications must handle values that are non-deterministic at spec time but deterministic at
runtime: UUIDs, timestamps, random codes, auto-increment IDs. The spec language uses **existential
binding** and **abstract functions** to model these.

### G.1 Fresh Value Generation

When an operation produces a value that must be unique but whose exact value is unspecified, use
existential binding:

```
operation Shorten {
  input:  url: LongURL
  output: code: ShortCode

  requires: isValidURI(url.value)

  ensures:
    code not in pre(store)             // code is fresh (existential: SOME code exists)
    store'[code] = url
    len(code.value) >= 6 and len(code.value) <= 10
}
```

The spec says "there exists a code that satisfies these constraints" without specifying which one.
The LLM synthesis engine or convention engine chooses the algorithm (hash, random, counter). The
verifier checks that any conforming implementation is correct.

### G.2 Timestamps via `now()`

The built-in function `now()` represents the current time at operation execution. The spec
constrains its relationship to other values without fixing it:

```
ensures:
  order.created_at = now()                        // assigned at execution time
  order.created_at >= pre(orders)[last_id].created_at   // monotonic
```

For verification, `now()` is treated as a fresh value satisfying `now() >= any previous now()`. For
property tests, the test harness injects a controllable clock.

### G.3 Auto-Increment IDs

```
ensures:
  post.id = max(dom(posts)) + 1    // next sequential ID
  post.id not in pre(posts)         // always fresh
```

The convention engine maps `max(dom(...)) + 1` to `BIGSERIAL` / auto-increment in the database. The
spec states the semantic property (fresh, sequential) without naming the mechanism.
