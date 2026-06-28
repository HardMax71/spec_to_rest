---
title: "Contract testing and fuzzing"
description: "Pact, Dredd, and RESTler"
---

## Pact

[Pact](https://docs.pact.io/getting_started/how_pact_works) is the most widely adopted
consumer-driven contract testing tool in microservices, and it is code-first: the contract emerges
from a test rather than being authored separately. In the consumer phase a developer writes a test
stating the request and response interactions it expects, Pact stands up a mock provider that records
them and checks the consumer's requests match, and the result is a pact file, a JSON contract of all
the interactions. In the provider phase each request from the pact file is replayed against the real
provider, and its response passes if it contains at least the expected data. Interactions can name
provider states ("user 123 exists") that the provider sets up before replay.

A [Pact Broker](https://pactflow.io/how-pact-works/) (PactFlow is SmartBear's hosted version) stores
the pact files, tracks which versions are compatible, and offers a `can-i-deploy` check that gates a
release on compatibility, with webhook and CI hooks. Bi-directional contract testing lets a provider
publish its own contract, an OpenAPI spec for instance, so Pact can compare consumer expectations
against it without running the provider.

The connection to tests runs opposite to a tool like Schemathesis: the consumer test creates the spec
(the pact file) and provider verification validates against it, so the spec comes out of code rather
than being written first. Pact is over a decade old and very mature, with
[implementations in more than ten languages](https://github.com/pact-foundation/pact-net) (JS, Java,
.NET, Python, Go, Ruby), the de facto standard for consumer-driven contracts, used at ING, Atlassian,
AWS, and gov.uk.

## Dredd

[Dredd](https://dredd.org/en/latest/how-it-works.html) is a command-line tool that validates a live
API against its OpenAPI (or API Blueprint) description: it reads the description, generates one HTTP
request per documented endpoint, sends it to the running API, compares the actual response against the
documented one (status, headers, body structure, data types), and reports mismatches as failures. A
hooks system in seven languages (Go, Node.js, Perl, PHP, Python, Ruby, Rust) handles setup and
teardown like authentication or database seeding. Like Schemathesis it treats the API description as
the test spec, but where Schemathesis generates thousands of random variations, Dredd checks the
documented happy-path examples, one per endpoint-and-response pair.

It is [archived](https://github.com/apiaryio/dredd) as of November 2024, read-only, around 4,200 stars
but no active development, last released as v14.1.0 in November 2021, with OpenAPI 3 support left
experimental and unfinished. The maintainers point successors at Schemathesis, at Prism or Spectral
for linting, and at Postman or Newman for execution.

## RESTler

[RESTler](https://github.com/microsoft/restler-fuzzer) is the first stateful REST API fuzzer, from
Microsoft Research, [published](https://www.microsoft.com/en-us/research/publication/restler-stateful-rest-api-fuzzing/)
across ICSE, ICST, ISSTA, and FSE. It runs in passes of increasing aggression: a compile step parses
the OpenAPI spec and infers producer-consumer dependencies between endpoints (a `POST /users` returns
an ID that `GET /users/{id}` consumes), a smoke test hits every endpoint to validate setup, a
fuzz-lean pass does one run per endpoint with the default bug checkers, and a full fuzz does an
aggressive breadth-first exploration of the API's state space. The dependency inference is the core:
RESTler reads the schema to find which response fields feed which request parameters, builds a
dependency graph, and generates request sequences that create resources before consuming them, which
is what lets it catch 500s, resource leaks, hierarchy violations, and use-after-free patterns in API
resources. It has around 2,900 stars, is open source (Python and F#), and is actively maintained.
