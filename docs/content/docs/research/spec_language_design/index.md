---
title: "Spec language foundations"
description: "Survey, grammar, semantics, and rationale behind the spec DSL"
---

> The design study behind the spec language: a survey of nine specification languages, the grammar,
> the formal semantic model, four worked examples, the type system, and the case for building a new
> language instead of reusing one. For how to write a spec today, see the
> [Spec Language Reference](/spec-language).

This section is split into eight pages:

- [Survey of specification languages](/research/spec_language_design/survey), how Alloy, TLA+, Quint, VDM, Dafny, Event-B, Z, TypeSpec, Smithy, and P each express the same handful of concepts.
- [Grammar design](/research/spec_language_design/grammar), the design principles, lexical rules, full EBNF, precedence, and sugar.
- [Semantic model](/research/spec_language_design/semantics), what state, `pre`, and primed values mean, and how quantifiers and relations behave.
- [Worked examples](/research/spec_language_design/worked-examples), four complete specs from a URL shortener to an e-commerce service.
- [Type system](/research/spec_language_design/type-system), primitives, compounds, entities, relations, refinements, and inference.
- [Errors and developer experience](/research/spec_language_design/developer-experience), what the checker catches and how it reports it.
- [Why not an existing language](/research/spec_language_design/comparison), the case against reusing Alloy, TLA+, OpenAPI, Dafny, or TypeSpec.
- [Extensions and appendices](/research/spec_language_design/extensions), built-ins, convention defaults, and sketches for auth, concurrency, external effects, and non-determinism.
