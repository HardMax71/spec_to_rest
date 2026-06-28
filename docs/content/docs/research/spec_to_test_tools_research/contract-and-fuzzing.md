---
title: "Contract testing and fuzzing"
description: "Pact, Dredd, and RESTler"
---

## 6. Pact, consumer-driven contract testing

**What it is.** Code-first consumer-driven contract testing framework. The most widely adopted
contract testing tool in microservices architectures.

### How it works

### Phase 1: Consumer test

1. Developer writes a test specifying expected request/response interactions.
2. Pact provides a mock provider that records these expectations.
3. Consumer code talks to the mock; Pact verifies the request matches expectations.
4. A **pact file** (JSON contract) is generated containing all interactions.

### Phase 2: Provider verification

1. Each request from the pact file is replayed against the real provider.
2. The provider's actual response is compared with the minimal expected response.
3. Verification passes if the actual response contains _at least_ the expected data.

### Provider states

Interactions can specify preconditions ("provider states") like "user 123 exists." The provider sets
up these states before each interaction is replayed.

### Pact broker / pactflow

- Central repository for pact files
- Tracks which versions are compatible
- `can-i-deploy` tool: checks if a version is safe to deploy to an environment
- Supports webhooks, CI/CD integration

### Bi-directional contract testing

Provider publishes its own contract (e.g., OpenAPI spec); Pact compares consumer expectations
against the provider's published contract without running the provider.

**How specs connect to tests.** The consumer test _creates_ the spec (the pact file). The provider
verification _validates against_ the spec. The spec emerges from code rather than being authored
separately. This is the inverse of tools like Schemathesis where the spec is written first.

#### Maturity

- 10+ years old, very mature
- Implementations in 10+ languages (JS, Java, .NET, Python, Go, Ruby, etc.)
- PactFlow: commercial hosted broker by SmartBear
- De facto standard for consumer-driven contract testing
- Used at ING, Atlassian, AWS, gov.uk, many others

#### Key sources

- https://docs.pact.io/getting_started/how_pact_works
- https://pactflow.io/how-pact-works/
- https://github.com/pact-foundation/pact-net

## 7. Dredd, API description validation

**What it is.** Command-line tool that validates a live API against its OpenAPI (or API Blueprint)
description document.

### How it works

1. Reads your API description (OpenAPI 2, OpenAPI 3 experimental, API Blueprint).
2. For each documented endpoint, generates an HTTP request.
3. Sends the request to the running API.
4. Compares the actual response against the documented response (status code, headers, body
   structure, data types).
5. Reports mismatches as test failures.

**Hooks system.** Supports setup/teardown code in 7 languages (Go, Node.js, Perl, PHP, Python, Ruby,
Rust) for authentication, database seeding, etc.

**How specs connect to tests.** Like Schemathesis, the API description _is_ the test spec. Dredd
generates one test per documented endpoint/response combination. The difference: Dredd tests the
"happy path" documented examples, while Schemathesis generates thousands of random variations.

### Maturity

- ARCHIVED (November 2024, read-only repository)
- 4,200+ GitHub stars, but no active development
- Last release: v14.1.0 (November 2021)
- OpenAPI 3 support was experimental and never completed
- Successor recommendation. Schemathesis, Prism (Stoplight), or Spectral for linting +
  Postman/Newman for execution

### Key sources

- https://github.com/apiaryio/dredd
- https://dredd.org/en/latest/how-it-works.html

## Appendix A: RESTler (Microsoft Research)

**What it is.** The first stateful REST API fuzzing tool. Automatically tests cloud services through
their REST APIs.

### How it works

1. **Compile.** Parses OpenAPI spec, infers producer-consumer dependencies between endpoints (e.g.,
   POST /users returns an ID consumed by GET /users/{id}).
2. **Test/Smoketest.** Quickly hits all endpoints to validate setup.
3. **Fuzz-lean.** One pass per endpoint with default bug checkers.
4. **Fuzz.** Aggressive breadth-first exploration of the API state space.

**Dependency inference.** RESTler analyzes the OpenAPI schema to find which response fields map to
which request parameters. It builds a dependency graph and generates request sequences that create
resources before consuming them.

### Bug detection

- HTTP 500 errors
- Resource leaks
- Hierarchy violations
- Use-after-free patterns in API resources

### Maturity

- 2,900+ GitHub stars, Microsoft Research backed
- Published at ICSE, ICST, ISSTA, FSE
- Open source (Python + F#)
- Actively maintained

### Key sources

- https://github.com/microsoft/restler-fuzzer
- https://www.microsoft.com/en-us/research/publication/restler-stateful-rest-api-fuzzing/
