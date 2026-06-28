---
title: "Reusing an API-language parser"
description: "TypeSpec, Smithy, and Ballerina as the parser front end"
---

The API description languages come at the problem from the opposite side. They are strong on the
structural surface, shapes, HTTP bindings, enums, and nearly empty on the behavioral half that is
this project's whole point. Against the fourteen constructs they score the lowest of any candidate:

| Construct                 | TypeSpec            | Smithy               | Ballerina             |
| ------------------------- | ------------------- | -------------------- | --------------------- |
| Entity declarations       | partial, `model`    | partial, `resource`  | partial, `type`       |
| State declarations        | none                | none                 | partial               |
| Operations with contracts | partial, `op`       | partial, `operation` | partial, `resource fn`|
| Invariants and facts      | none                | none                 | none                  |
| Transition declarations   | none                | none                 | none                  |
| Convention blocks         | full, decorators    | full, traits         | full, native HTTP     |
| Multiplicity annotations  | none                | minimal, `@required` | minimal, `?`          |
| Quantified expressions    | none                | none                 | none                  |
| Primed and pre-state      | none                | none                 | none                  |
| Relational types          | none                | none                 | none                  |
| Refinement types          | partial, decorators | partial, constraints | none                  |
| Cardinality operator      | none                | none                 | partial, `.length()`  |
| Collection types          | partial, arrays     | partial, list/map    | partial, map/array    |
| Enum declarations         | full                | full                 | full                  |

The shape of that table is the argument. All three cover HTTP conventions and enums outright and
handle entities, operations, and collections structurally, yet every one scores none on state,
invariants, quantifiers, primed variables, relations, multiplicities, and transitions. They describe
the part of a service this compiler does not need to invent, and nothing of the part it does.

## TypeSpec

TypeSpec (custom recursive-descent parser, TypeScript,
[microsoft/typespec](https://github.com/microsoft/typespec)) has the best HTTP modeling and the most
mature extension model of the lot: decorators, emitters, typekits, npm libraries. None of it reaches
behavior, since TypeSpec has no expression language at all, no state, no invariants. The tempting move
is to smuggle contracts in as decorator strings:

```typespec
@requires("code not in store.keys()")
@ensures("store'[code] = url")
op shorten(@body request: ShortenRequest): ShortenResponse;
```

Those strings are opaque: TypeSpec stores them as metadata and cannot parse, typecheck, or verify a
word of them. To make them mean anything you build a full expression parser, at which point TypeSpec
is an awkward wrapper around the parser you were trying to avoid writing. Wrong direction. It is,
though, a fine output target; the compiler can emit TypeSpec or OpenAPI through it.

## Smithy

Smithy (hand-written Java parser, [smithy-lang/smithy](https://github.com/smithy-lang/smithy)) is the
same story with a more interesting precedent. Its `resource` and `operation` shapes and `@http` traits
model REST well, and custom traits could carry `requires` and `ensures` lists, but those condition
strings are exactly as opaque as TypeSpec's decorators, so the same expression parser has to be built
regardless, inside Smithy's Java validator lifecycle this time.

What makes Smithy worth the look is
[smithy-dafny](https://github.com/smithy-lang/smithy-dafny): AWS generates a Dafny API skeleton from a
Smithy model, the developer fills in a verified body, and codegen emits glue for half a dozen
languages. It maps Smithy's structural constraints (`@pattern`, `@length`, `@range`) to Dafny, but
stops at structure and still expects a hand-written body. The lesson is precise, and this project took
it: the right move is to generate a verified representation from a model, not to cram the
specification into the model's annotations. Valuable as a pipeline component or an emit target, not as
the parser base.

## Ballerina

Ballerina ([ballerina-platform/ballerina-lang](https://github.com/ballerina-platform/ballerina-lang),
a three-phase JVM compiler whose parser is hand-written in Java) is a category error rather than a
near miss. Its HTTP support is first-class, `resource function get users() returns User[]` is native
syntax, but it is an implementation language, you write the service in it, not a specification
language that describes what the service must do. Reusing it means either writing services directly in
Ballerina, which deletes the need for this compiler and every formal-spec benefit with it, or
embedding a spec sublanguage inside it, which means building the parser anyway. Not applicable, though
again a perfectly good compilation target.
