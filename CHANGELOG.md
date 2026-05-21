# Changelog

## [2.1.0](https://github.com/HardMax71/spec_to_rest/compare/v2.0.0...v2.1.0) (2026-05-21)


### Features

* **ci:** automate releases via release-please (Conventional Commits) ([#292](https://github.com/HardMax71/spec_to_rest/issues/292)) ([43a9173](https://github.com/HardMax71/spec_to_rest/commit/43a9173986049dd6b4e64ae05feeddaca0c33a3f))
* go-chi native conformance (go test + rapid) via the backend seam ([#279](https://github.com/HardMax71/spec_to_rest/issues/279)) ([c971634](https://github.com/HardMax71/spec_to_rest/commit/c971634d92226607a5a956b0221e056c636f7c27))
* native-test backend seam + TypeScript (vitest+fast-check) backend ([#278](https://github.com/HardMax71/spec_to_rest/issues/278)) ([637c621](https://github.com/HardMax71/spec_to_rest/commit/637c62138dd39625027a91d888bbe8529249792e))
* SQLite + MySQL for ts/express + Prisma 5→6.2 ([#262](https://github.com/HardMax71/spec_to_rest/issues/262)) ([0c97254](https://github.com/HardMax71/spec_to_rest/commit/0c97254115eb68ff88f3561c6605e8bb6902e468))
* SQLite + MySQL support for go/chi via Bun dialect switch ([#261](https://github.com/HardMax71/spec_to_rest/issues/261)) ([68fe744](https://github.com/HardMax71/spec_to_rest/commit/68fe744daf3d1a9d1a5beb03f72fcd8736b9d068))
* structural-lite native fuzzer (ts/go) via the Strategies seam ([#280](https://github.com/HardMax71/spec_to_rest/issues/280)) ([ae8ec4f](https://github.com/HardMax71/spec_to_rest/commit/ae8ec4f88703b3f9c5b95356d1e1c545f82e4435))
* testgen for every fastapi dialect + go/ts tracking ([#266](https://github.com/HardMax71/spec_to_rest/issues/266)) ([6f5afac](https://github.com/HardMax71/spec_to_rest/commit/6f5afaca5227eb2527e684da35a3a225c021eccd))
* ts-express conformance — TS/Prisma test-admin router, honest skips, 64-bit PKs ([#277](https://github.com/HardMax71/spec_to_rest/issues/277)) ([76624b1](https://github.com/HardMax71/spec_to_rest/commit/76624b16402ab5bfa6899fc3a73167fe834e63ff))
* **verify:** extract value_has_ty + wire runtime type-check into Z3 decoder ([#289](https://github.com/HardMax71/spec_to_rest/issues/289)) ([05c4ffc](https://github.com/HardMax71/spec_to_rest/commit/05c4ffc7a15ac5d2ad3716e2213cc40d9fa79a33))


### Bug Fixes

* ::jsonb dialect leak + promote todo_list to a first-class CI fixture ([#268](https://github.com/HardMax71/spec_to_rest/issues/268)) ([4b8f1f3](https://github.com/HardMax71/spec_to_rest/commit/4b8f1f380fa13abb88d43beb361cd1819df29a17))
* 222: native macOS/Windows NCDFE (Nat/nat .class case-collision) ([#252](https://github.com/HardMax71/spec_to_rest/issues/252)) ([b2591e7](https://github.com/HardMax71/spec_to_rest/commit/b2591e718d1e3b2cd28dec806f908de34c4ef945))
* **cli:** correct stale --help strings falsified by native multi-target conformance ([#282](https://github.com/HardMax71/spec_to_rest/issues/282)) ([2aeb49a](https://github.com/HardMax71/spec_to_rest/commit/2aeb49a4e41d173840edfd99cd4168cbbe212b1c))
* close 3D lang/framework/db matrix drift ([#263](https://github.com/HardMax71/spec_to_rest/issues/263)) ([aa2312e](https://github.com/HardMax71/spec_to_rest/commit/aa2312e8de95e09dda4f9d703d214a8503ddadc4))
* enforce type-alias refinements as dialect-aware CHECKs; auth_service CI ([#270](https://github.com/HardMax71/spec_to_rest/issues/270)) ([5ce78e2](https://github.com/HardMax71/spec_to_rest/commit/5ce78e2d7f4118e21a06d98da5b91a2364770f5b))
* filtered-list ops are fail-loud stubs, not silent unfiltered lists ([#274](https://github.com/HardMax71/spec_to_rest/issues/274)) ([f916b5f](https://github.com/HardMax71/spec_to_rest/commit/f916b5fb48fb2ac5797cf90584054fe939f705e1))
* flatten `entity extends` inheritance into the IR ([#271](https://github.com/HardMax71/spec_to_rest/issues/271)) ([ff2de62](https://github.com/HardMax71/spec_to_rest/commit/ff2de622bfc26f9b4131b82b0e4392541459551a))
* generated FastAPI app correctness — route-shadow, lint, redaction, Makefile ([#267](https://github.com/HardMax71/spec_to_rest/issues/267)) ([52c5506](https://github.com/HardMax71/spec_to_rest/commit/52c5506a3656125b8a2d13c47641cbbd1f257c21))
* multi-entity go-chi codegen + alias params + dialect triggers/indexes; ecommerce CI ([#269](https://github.com/HardMax71/spec_to_rest/issues/269)) ([f1eb49f](https://github.com/HardMax71/spec_to_rest/commit/f1eb49f15dc67c4254869a40907b382ece9ca2e0))
* **proof:** parameterise value_has_ty by tyctx, tighten vt_entity_with (closes [#285](https://github.com/HardMax71/spec_to_rest/issues/285) review) ([#286](https://github.com/HardMax71/spec_to_rest/issues/286)) ([d7e1b19](https://github.com/HardMax71/spec_to_rest/commit/d7e1b19f71c5395bf3c6fae921655e034a4c6a1a))
* redirect ops with spec side-effects are fail-loud stubs (go/ts) ([#275](https://github.com/HardMax71/spec_to_rest/issues/275)) ([a5fefce](https://github.com/HardMax71/spec_to_rest/commit/a5fefcead93f40fede4717674e7d48db111c22ac))
* SQLite serial primary keys must map to INTEGER, not BIGINT ([#264](https://github.com/HardMax71/spec_to_rest/issues/264)) ([ce14697](https://github.com/HardMax71/spec_to_rest/commit/ce146972122d677e8edd413c0ff9b6b35d941bc9))
* ts-express synthesized PK is Prisma BigInt (matches BIGSERIAL migration) ([#273](https://github.com/HardMax71/spec_to_rest/issues/273)) ([90d5072](https://github.com/HardMax71/spec_to_rest/commit/90d5072e29879409b3f2d8c175034457ddc8707a))


### Code Refactoring

* derive on-disk layout from TargetKey axes (lang/framework[/db]) ([#257](https://github.com/HardMax71/spec_to_rest/issues/257)) ([1f8afbe](https://github.com/HardMax71/spec_to_rest/commit/1f8afbe5be60d2d24ef24b7936a681d27111cc52))
* extract renderer-agnostic AdminModel (foundation for go/ts conformance) ([#276](https://github.com/HardMax71/spec_to_rest/issues/276)) ([821a1cf](https://github.com/HardMax71/spec_to_rest/commit/821a1cf460b83d725f0c421d7fefdfb3350af32b))
* lift Scala expr_full walks + entity composites into Isabelle proofs (Phase 9v–9ww) ([#285](https://github.com/HardMax71/spec_to_rest/issues/285)) ([ee3df8d](https://github.com/HardMax71/spec_to_rest/commit/ee3df8da420dd62f0627a2c3abf7400ef23915e9))
* re-platform go/chi data layer from raw pgx to Bun ORM ([#259](https://github.com/HardMax71/spec_to_rest/issues/259)) ([bd21ed2](https://github.com/HardMax71/spec_to_rest/commit/bd21ed22a32751e2126a3b09a5d5d31aecfdf089))


### Performance

* **isabelle:** -54% wall-time on SpecRest build (168s → 77s) ([#241](https://github.com/HardMax71/spec_to_rest/issues/241)) ([960de38](https://github.com/HardMax71/spec_to_rest/commit/960de38df77ae109140d08a91b39291fa37a2f1a))


### Documentation

* literate CLI snippets, full AI-marker scrub, reviewer fixes ([#232](https://github.com/HardMax71/spec_to_rest/issues/232)) ([c291197](https://github.com/HardMax71/spec_to_rest/commit/c291197b50af1c3a22e740fd6a54cae355ba6847))
* reflect native multi-target conformance (go-chi + ts-express) ([#281](https://github.com/HardMax71/spec_to_rest/issues/281)) ([65b1dd3](https://github.com/HardMax71/spec_to_rest/commit/65b1dd3ed0237d36304c0d277cc6600616ec04ca))
* restructure Deployment Targets into lang/framework/db nav tree ([#258](https://github.com/HardMax71/spec_to_rest/issues/258)) ([890dd6e](https://github.com/HardMax71/spec_to_rest/commit/890dd6e383a53d9ca383234bb11a4159b35f29b6))
