# Changelog

## [3.2.0](https://github.com/HardMax71/spec_to_rest/compare/v3.1.0...v3.2.0) (2026-07-02)


### Features

* **synth:** machine-verify few-shot examples in CI ([#490](https://github.com/HardMax71/spec_to_rest/issues/490)) ([19b8ad6](https://github.com/HardMax71/spec_to_rest/commit/19b8ad6eca78c0891d3903248bc112e4116ae5fa))
* **synth:** route fallback escalation across provider families ([#493](https://github.com/HardMax71/spec_to_rest/issues/493)) ([ebe0b30](https://github.com/HardMax71/spec_to_rest/commit/ebe0b30d880a8453eeeb47dfb4cd145fd3a463c7))


### Code Refactoring

* fold copy-pasted testgen/codegen helpers into shared ones ([#495](https://github.com/HardMax71/spec_to_rest/issues/495)) ([b3a30d9](https://github.com/HardMax71/spec_to_rest/commit/b3a30d90195f34c252cf0585a6f2039125ecde53))
* **proofs:** drop the dead schema_ subset leftovers from [#391](https://github.com/HardMax71/spec_to_rest/issues/391) ([#497](https://github.com/HardMax71/spec_to_rest/issues/497)) ([189400c](https://github.com/HardMax71/spec_to_rest/commit/189400c9553b16f762f443f3add0d40fac761f12))
* shared skeletons for testgen backends, Z3Expr walks, and emitter decisions ([#498](https://github.com/HardMax71/spec_to_rest/issues/498)) ([34d585e](https://github.com/HardMax71/spec_to_rest/commit/34d585ea408ecd715cc1ec48e51c970735af6579))
* **verify:** consolidate the seven Consistency check runners into two ([#496](https://github.com/HardMax71/spec_to_rest/issues/496)) ([58f6b15](https://github.com/HardMax71/spec_to_rest/commit/58f6b15f0c10e3f9dd42530aabd0cbe18fa21bc5))


### Documentation

* **design:** split the Isabelle proofs page into a subsection and refresh it ([#486](https://github.com/HardMax71/spec_to_rest/issues/486)) ([bbdfa22](https://github.com/HardMax71/spec_to_rest/commit/bbdfa225f59c56663462e59557f1b0453764decc))
* **pipelines:** split the test generation page into a subsection and refresh it ([#488](https://github.com/HardMax71/spec_to_rest/issues/488)) ([fab3cb1](https://github.com/HardMax71/spec_to_rest/commit/fab3cb12b536825ddeae0148cb119f63c20d9f1d))
* **pipelines:** split the verification engine page into a subsection and refresh it ([#487](https://github.com/HardMax71/spec_to_rest/issues/487)) ([4abdc79](https://github.com/HardMax71/spec_to_rest/commit/4abdc79eeb85a0935636269043d6cb7d5b367473))
* re-baseline proof references stale since the one-IR collapse ([#494](https://github.com/HardMax71/spec_to_rest/issues/494)) ([fc0962a](https://github.com/HardMax71/spec_to_rest/commit/fc0962a734fac5851f6c6d3b9de841a0c6c88347))
* **research:** restructure the research docs into subsections, rewritten against the code ([#482](https://github.com/HardMax71/spec_to_rest/issues/482)) ([627a2e4](https://github.com/HardMax71/spec_to_rest/commit/627a2e46a3e2da8bb7e2075c89e9911d29879a77))
* **roadmap:** drop duplicate counterexample-formatting follow-up row ([#491](https://github.com/HardMax71/spec_to_rest/issues/491)) ([e8ab4bc](https://github.com/HardMax71/spec_to_rest/commit/e8ab4bc60b66c02f1b7c199d5296747065cf51e1))
* **roadmap:** ground the closed-issue decisions and tidy the page ([#484](https://github.com/HardMax71/spec_to_rest/issues/484)) ([09733f5](https://github.com/HardMax71/spec_to_rest/commit/09733f5af7c155a302e0556b253c659d4091718c))
* **roadmap:** prune shipped and not-planned rows from the follow-up tables ([#485](https://github.com/HardMax71/spec_to_rest/issues/485)) ([704d259](https://github.com/HardMax71/spec_to_rest/commit/704d2593245cf0e08827d07d53d6df63b759930c))
* split the synthesis page into a subsection and rewrite the homepage intro ([#489](https://github.com/HardMax71/spec_to_rest/issues/489)) ([2cf90a2](https://github.com/HardMax71/spec_to_rest/commit/2cf90a27aed66659f6960d5fc066bfbd3844219a))
* **synth:** fix stale escalation example and dangling follow-up link ([#492](https://github.com/HardMax71/spec_to_rest/issues/492)) ([379c302](https://github.com/HardMax71/spec_to_rest/commit/379c3027a41122834007e4b9e24b8fc10cf6c873))

## [3.1.0](https://github.com/HardMax71/spec_to_rest/compare/v3.0.1...v3.1.0) (2026-06-27)


### Features

* **auth_service:** verifiable login rate-limiting — auth_service now fully verifies ([#461](https://github.com/HardMax71/spec_to_rest/issues/461)) ([ca1a12f](https://github.com/HardMax71/spec_to_rest/commit/ca1a12f77ed93eba4fbf25546ad45d3ebdf76d05))
* **cli:** unify exit codes into ExitStatus and describe them in output ([#421](https://github.com/HardMax71/spec_to_rest/issues/421)) ([99bcc30](https://github.com/HardMax71/spec_to_rest/commit/99bcc30366967feeb5e676f6a3f308e65e09c2a2))
* **codegen:** paginate list endpoints with limit/offset query params ([#464](https://github.com/HardMax71/spec_to_rest/issues/464)) ([b755cf7](https://github.com/HardMax71/spec_to_rest/commit/b755cf7d5d27191ed1d90f9ac919dbd61a9c617f))
* **codegen:** synthesize verified Go service bodies via the Dafny kernel ([#465](https://github.com/HardMax71/spec_to_rest/issues/465)) ([033f779](https://github.com/HardMax71/spec_to_rest/commit/033f77967ec374bb059c1d701b1b2be214524114))
* **codegen:** synthesize verified TypeScript service bodies via the Dafny kernel ([#466](https://github.com/HardMax71/spec_to_rest/issues/466)) ([5fa2900](https://github.com/HardMax71/spec_to_rest/commit/5fa2900fce0d709105934ea5493cd0b9d2d76d44))
* **proofs:** lift sequence-append (seq + [elem]) into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#434](https://github.com/HardMax71/spec_to_rest/issues/434)) ([0f8ad6b](https://github.com/HardMax71/spec_to_rest/commit/0f8ad6bea59e5a5bdf90b9d4f07d19fe80d6ad2d))
* **proofs:** lift the 1-arg builtin hash(x) into the verified subset as an uninterpreted String function ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#433](https://github.com/HardMax71/spec_to_rest/issues/433)) ([11cc1eb](https://github.com/HardMax71/spec_to_rest/commit/11cc1ebb11c4f50a12d65ef8dbaa7e7d7cf23df1))
* **verify:** cardinality and domain-membership on primed/pre-state relations ([#425](https://github.com/HardMax71/spec_to_rest/issues/425)) ([6ca2438](https://github.com/HardMax71/spec_to_rest/commit/6ca24388c241e84a33364b4a17b15afd12b09112))
* **verify:** coerce optional-numeric operands in arithmetic ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#446](https://github.com/HardMax71/spec_to_rest/issues/446)) ([b16bb30](https://github.com/HardMax71/spec_to_rest/commit/b16bb30626fe53c6149c043176a251ac0e2234f9))
* **verify:** conditional relation-insert in ensures frames ([#456](https://github.com/HardMax71/spec_to_rest/issues/456)) ([512a5dc](https://github.com/HardMax71/spec_to_rest/commit/512a5dc40c1307eec02988c8337e76bb16bd9db0))
* **verify:** encode set difference (set - set) in the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#445](https://github.com/HardMax71/spec_to_rest/issues/445)) ([8a6d8c6](https://github.com/HardMax71/spec_to_rest/commit/8a6d8c619a1779a7bb21f87f9e348319f4e1c9ca))
* **verify:** encode set union (set + set) in the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#441](https://github.com/HardMax71/spec_to_rest/issues/441)) ([776e21e](https://github.com/HardMax71/spec_to_rest/commit/776e21ea9a6b579931dcbad282c426957e551c21))
* **verify:** first-class String sort for String-refinement aliases via E-matching triggers ([#427](https://github.com/HardMax71/spec_to_rest/issues/427)) ([e300cb5](https://github.com/HardMax71/spec_to_rest/commit/e300cb58ffee3adca6de544724a8439733318c9a))
* **verify:** frame relation-assignments nested in let/and + chained relation-insert ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#430](https://github.com/HardMax71/spec_to_rest/issues/430)) ([046dede](https://github.com/HardMax71/spec_to_rest/commit/046dede9340aba80dba6aebea3a6f4199fbe3de8))
* **verify:** generalize the sum aggregate to computed lambda bodies ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#449](https://github.com/HardMax71/spec_to_rest/issues/449)) ([c10169c](https://github.com/HardMax71/spec_to_rest/commit/c10169c4f5f5944f53f14e755dfe89cb385e3413))
* **verify:** infer the none-literal element sort from the with-update field type ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#432](https://github.com/HardMax71/spec_to_rest/issues/432)) ([f6bdbe1](https://github.com/HardMax71/spec_to_rest/commit/f6bdbe1409a468c43b668e0230fb679c9e55b4da))
* **verify:** lift #(set-valued expr) cardinality into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#439](https://github.com/HardMax71/spec_to_rest/issues/439)) ([da9602a](https://github.com/HardMax71/spec_to_rest/commit/da9602a5beee28a65f779c24c7019187fc628fce))
* **verify:** lift `all k in pre(X)` quantifiers into the verified subset ([#459](https://github.com/HardMax71/spec_to_rest/issues/459)) ([097c557](https://github.com/HardMax71/spec_to_rest/commit/097c55709f39fc6c8e003a3020d48da0aeb04483))
* **verify:** lift existential/negative quantifiers over set domains into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#443](https://github.com/HardMax71/spec_to_rest/issues/443)) ([9df7c9b](https://github.com/HardMax71/spec_to_rest/commit/9df7c9b718f2ef9ba3ef78a5e2743ee168c9e73e))
* **verify:** lift quantifiers over primed/pre-state relations into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#442](https://github.com/HardMax71/spec_to_rest/issues/442)) ([99a4b73](https://github.com/HardMax71/spec_to_rest/commit/99a4b73d946d147c675d4f21fceab946f15df96a))
* **verify:** lift range-membership + optional-vs-base membership, flipping todo_list ListTodos ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#436](https://github.com/HardMax71/spec_to_rest/issues/436)) ([66d933e](https://github.com/HardMax71/spec_to_rest/commit/66d933e1d220ae4b0ae5836cf9d5faffa05d746b))
* **verify:** lift relation literal-insert into the verified subset ([#426](https://github.com/HardMax71/spec_to_rest/issues/426)) ([47c967f](https://github.com/HardMax71/spec_to_rest/commit/47c967f75f141a6eee7f64c48569077bc9b02641))
* **verify:** lift sum(coll, i =&gt; i.field) aggregates into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#437](https://github.com/HardMax71/spec_to_rest/issues/437)) ([7769d36](https://github.com/HardMax71/spec_to_rest/commit/7769d367913c45fb20d7ff546d0b0de936b61808))
* **verify:** lift the (definite description) over set domains into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#444](https://github.com/HardMax71/spec_to_rest/issues/444)) ([3f4462c](https://github.com/HardMax71/spec_to_rest/commit/3f4462c0a8647e101f5b7abb381f493df9839e88))
* **verify:** lift the days(n) builtin into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#447](https://github.com/HardMax71/spec_to_rest/issues/447)) ([974564b](https://github.com/HardMax71/spec_to_rest/commit/974564b327d4a4d6bfcb3d05da7a4498d4693ff8))
* **verify:** lift the len(s) builtin into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#448](https://github.com/HardMax71/spec_to_rest/issues/448)) ([6392c35](https://github.com/HardMax71/spec_to_rest/commit/6392c352724bcdff36e5cdcf6044d60697bfa2ac))
* **verify:** lift universal quantification over field-access sets into the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#440](https://github.com/HardMax71/spec_to_rest/issues/440)) ([57f1f14](https://github.com/HardMax71/spec_to_rest/commit/57f1f1460633ce2aeed90e0190cb155ffcb62733))
* **verify:** model #-cardinality on entity-field Sets ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#438](https://github.com/HardMax71/spec_to_rest/issues/438)) ([510f5c2](https://github.com/HardMax71/spec_to_rest/commit/510f5c2600394602bd38ab0c756223f25ef7edb2))
* **verify:** model Duration as the Int sort, completing the [#377](https://github.com/HardMax71/spec_to_rest/issues/377) temporal lift ([#422](https://github.com/HardMax71/spec_to_rest/issues/422)) ([c799c38](https://github.com/HardMax71/spec_to_rest/commit/c799c38ab842cf3bd37336592e4d4cbee69e1f98))
* **verify:** model now() as an uninterpreted Int constant in the verified subset ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#429](https://github.com/HardMax71/spec_to_rest/issues/429)) ([4464a34](https://github.com/HardMax71/spec_to_rest/commit/4464a34aa8b908e82437db709a90c54060ebe5d7))
* **verify:** resolve bare enum members + coerce base-vs-optional equality ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#431](https://github.com/HardMax71/spec_to_rest/issues/431)) ([823d392](https://github.com/HardMax71/spec_to_rest/commit/823d392619ab01556fd770d6f6c2631ed74409c6))
* **verify:** support lexicographic ordering on String via str.&lt; ([#423](https://github.com/HardMax71/spec_to_rest/issues/423)) ([545ede2](https://github.com/HardMax71/spec_to_rest/commit/545ede2ef89c9419f821503beef000663cfe6163))
* **verify:** support string concatenation via str.++ ([#424](https://github.com/HardMax71/spec_to_rest/issues/424)) ([69cbe31](https://github.com/HardMax71/spec_to_rest/commit/69cbe317c8a30f2e7f04cce59f7cb3fc59b396f8))
* **verify:** type-directed range projection for value-sorted set comprehensions ([#420](https://github.com/HardMax71/spec_to_rest/issues/420)) ([#428](https://github.com/HardMax71/spec_to_rest/issues/428)) ([87363fe](https://github.com/HardMax71/spec_to_rest/commit/87363fec465ddbdaaebadfc844b2d9bd4fccaeef))


### Bug Fixes

* **auth_service:** hoist Logout's the so its session-update frames ([#457](https://github.com/HardMax71/spec_to_rest/issues/457)) ([e1dfe0b](https://github.com/HardMax71/spec_to_rest/commit/e1dfe0b3b8cde82c0adeebaa5cea76fba9c5c6e2))
* **ecommerce:** complete payment/order consistency invariants — ecommerce fully verifies ([#462](https://github.com/HardMax71/spec_to_rest/issues/462)) ([3907959](https://github.com/HardMax71/spec_to_rest/commit/3907959d906b8bf04bdd7989cf449db69cf84524))
* **parser:** `implies`/`iff` bind looser than `and`/`or` ([#463](https://github.com/HardMax71/spec_to_rest/issues/463)) ([7b27c88](https://github.com/HardMax71/spec_to_rest/commit/7b27c8866d1f1cc9fd1da0f3e11abe71956a578e))
* **parser:** a let-binding scopes the rest of its requires/ensures block ([#453](https://github.com/HardMax71/spec_to_rest/issues/453)) ([400cdc0](https://github.com/HardMax71/spec_to_rest/commit/400cdc024997379177559a0afd8674034155be31))
* **proofs:** exclude callee names from free_vars; recognize mirrored refinement atoms ([#473](https://github.com/HardMax71/spec_to_rest/issues/473)) ([9d93fbd](https://github.com/HardMax71/spec_to_rest/commit/9d93fbdd743988f2c9fec26a44ee0356aebbcaf5))
* **testgen:** point Go/TS pending-skip messages at the real tracking issue ([#468](https://github.com/HardMax71/spec_to_rest/issues/468)) ([fd39b90](https://github.com/HardMax71/spec_to_rest/commit/fd39b90b3a91e67f71ef6f02b031d2718bbc6dbf))
* **todo_list:** make nextIdFresh inductive so CreateTodo preserves it ([#455](https://github.com/HardMax71/spec_to_rest/issues/455)) ([57bdb53](https://github.com/HardMax71/spec_to_rest/commit/57bdb5391e0f0a5d2853cefc3023ec682308b5de))
* **verify:** synthesize relation frames through let-bindings ([#458](https://github.com/HardMax71/spec_to_rest/issues/458)) ([3a2fd29](https://github.com/HardMax71/spec_to_rest/commit/3a2fd29d57e545715a28b0a79e4f4dfa90a206a6))


### Code Refactoring

* **auth_service:** model password-reset tokens as a dedicated relation ([#460](https://github.com/HardMax71/spec_to_rest/issues/460)) ([d2bb9e9](https://github.com/HardMax71/spec_to_rest/commit/d2bb9e93696a372063a2fb3bfa2d88b6f5837ef9))
* **codegen:** dedup identical route-kind / path-specificity helpers across emitters ([#476](https://github.com/HardMax71/spec_to_rest/issues/476)) ([3d94199](https://github.com/HardMax71/spec_to_rest/commit/3d941998c9e56e3e3f16f84bda7127fde86bc267))
* decouple Z3 bridge support ([#452](https://github.com/HardMax71/spec_to_rest/issues/452)) ([885bc66](https://github.com/HardMax71/spec_to_rest/commit/885bc66595887aca3c0d2d0348a4185b6695b338))
* **proofs:** centralize name/variable mechanics into Names.thy ([#479](https://github.com/HardMax71/spec_to_rest/issues/479)) ([3bf142d](https://github.com/HardMax71/spec_to_rest/commit/3bf142d997c33f586e351cb8701fac68f725579f))
* **proofs:** define requiresAlloy as a fun, dropping the 31-lemma simp wall ([#477](https://github.com/HardMax71/spec_to_rest/issues/477)) ([adfb890](https://github.com/HardMax71/spec_to_rest/commit/adfb890321e67cec6741a581813271663860aae6))
* **proofs:** modularize the Isabelle proof layer (session + theory splits) ([#472](https://github.com/HardMax71/spec_to_rest/issues/472)) ([66c7257](https://github.com/HardMax71/spec_to_rest/commit/66c725793dbd044a65bbf02c8737435f6dc9ae35))
* **proofs:** move Semantics_Inlining to the Soundness session ([#480](https://github.com/HardMax71/spec_to_rest/issues/480)) ([9e6c9fe](https://github.com/HardMax71/spec_to_rest/commit/9e6c9fe2548fc73d647aef02906b6d8c20759684))
* **testgen:** split Behavioral.scala into cohesive sub-modules ([#474](https://github.com/HardMax71/spec_to_rest/issues/474)) ([0e11281](https://github.com/HardMax71/spec_to_rest/commit/0e1128108b57a3004537fcaec235676855e5d9e9))


### Performance

* **proofs:** prune BNF plugins on codegen datatypes (Codegen session ~20% faster) ([#478](https://github.com/HardMax71/spec_to_rest/issues/478)) ([967bb49](https://github.com/HardMax71/spec_to_rest/commit/967bb494c6e893d9ad46d7d5df7cc95fc1f70f65))


### Documentation

* **roadmap:** mark phases 7-8 shipped, reconcile stale ticket states ([#418](https://github.com/HardMax71/spec_to_rest/issues/418)) ([803cac1](https://github.com/HardMax71/spec_to_rest/commit/803cac1c8ac53a12af132d61ae511f5377803907))
* scrub AI-writing tells from prose ([#481](https://github.com/HardMax71/spec_to_rest/issues/481)) ([c7bf8dc](https://github.com/HardMax71/spec_to_rest/commit/c7bf8dc27eea8d5d92de21eafad89efaa6b56d7a))
* sync docs with shipped [#420](https://github.com/HardMax71/spec_to_rest/issues/420) work (precedence, synthesis targets, pagination, exit codes) ([#467](https://github.com/HardMax71/spec_to_rest/issues/467)) ([c21b486](https://github.com/HardMax71/spec_to_rest/commit/c21b486384376995d3d33e1ea092445db5cf8cfd))

## [3.0.1](https://github.com/HardMax71/spec_to_rest/compare/v3.0.0...v3.0.1) (2026-06-13)


### Bug Fixes

* **native-image:** pass --exclude-config as two args so release binaries build ([#417](https://github.com/HardMax71/spec_to_rest/issues/417)) ([a714f53](https://github.com/HardMax71/spec_to_rest/commit/a714f532d3ed9f9a6abcc1459b43bfe209801f69))


### Documentation

* fix diagram rendering (stretched width, no theme, mobile legibility) ([#414](https://github.com/HardMax71/spec_to_rest/issues/414)) ([52524a2](https://github.com/HardMax71/spec_to_rest/commit/52524a21ba817670afce6292726df52eb70752a7))

## [3.0.0](https://github.com/HardMax71/spec_to_rest/compare/v2.2.0...v3.0.0) (2026-06-12)


### ⚠ BREAKING CHANGES

* **proofs:** collapse the two-language IR into one verified IR ([#391](https://github.com/HardMax71/spec_to_rest/issues/391)) (#397)

### Features

* **codegen:** always-mounted bearer-guarded /admin surface (replaces ENABLE_TEST_ADMIN) ([#409](https://github.com/HardMax71/spec_to_rest/issues/409)) ([9226ed0](https://github.com/HardMax71/spec_to_rest/commit/9226ed04df778ce7b1929be68506cbf642c271d0))
* **codegen:** direct-emit scalar state operations ([#407](https://github.com/HardMax71/spec_to_rest/issues/407)) ([#408](https://github.com/HardMax71/spec_to_rest/issues/408)) ([15f9d8f](https://github.com/HardMax71/spec_to_rest/commit/15f9d8f05cb15276c1f10a39e4adf6b78aeaf4cc))
* **codegen:** emit spec-declared security schemes into OpenAPI (M8.2) ([#412](https://github.com/HardMax71/spec_to_rest/issues/412)) ([58796f8](https://github.com/HardMax71/spec_to_rest/commit/58796f81be2979fbb417be11d8d163b5d2ca9978))
* **parser:** authentication DSL syntax + IR support (M8.1) ([#411](https://github.com/HardMax71/spec_to_rest/issues/411)) ([5c85c69](https://github.com/HardMax71/spec_to_rest/commit/5c85c6953972e323b888b03d409b236107e770fd))
* playground archive download + scrollable output panes ([#374](https://github.com/HardMax71/spec_to_rest/issues/374)) ([e74128a](https://github.com/HardMax71/spec_to_rest/commit/e74128afa66fcfb1ec91bb021c249c57e048c9a2))
* **proofs:** collapse the two-language IR into one verified IR ([#391](https://github.com/HardMax71/spec_to_rest/issues/391)) ([#397](https://github.com/HardMax71/spec_to_rest/issues/397)) ([0843cf4](https://github.com/HardMax71/spec_to_rest/commit/0843cf4e5037e1d1a524a850ab8ccf7b1553cb94))
* **proofs:** extend H3 typing layer to the lifted native sorts ([#383](https://github.com/HardMax71/spec_to_rest/issues/383)) ([#404](https://github.com/HardMax71/spec_to_rest/issues/404)) ([2c2cd12](https://github.com/HardMax71/spec_to_rest/commit/2c2cd126836e6dee9c79d148395b9335458daf99))
* **proofs:** lift CallF via trusted pre-lower inlining (function/predicate calls) ([#390](https://github.com/HardMax71/spec_to_rest/issues/390)) ([2947cb2](https://github.com/HardMax71/spec_to_rest/commit/2947cb2ae496651106bc7c6acdf81db20b8cf501))
* **proofs:** lift If to a verified Ite construct (Z3 ite) ([#378](https://github.com/HardMax71/spec_to_rest/issues/378)) ([6a2042a](https://github.com/HardMax71/spec_to_rest/commit/6a2042ad13e5adca8eab25f3502e010a72e9797c))
* **proofs:** lift Matches, TheF, ConstructorF to the verified subset ([#389](https://github.com/HardMax71/spec_to_rest/issues/389)) ([254fd56](https://github.com/HardMax71/spec_to_rest/commit/254fd560e707fd7226840ca48d1929ec5fcf7a2a))
* **proofs:** lift Option (none/some) to a verified Z3 datatype ([#379](https://github.com/HardMax71/spec_to_rest/issues/379)) ([9ef8af7](https://github.com/HardMax71/spec_to_rest/commit/9ef8af786c581489c2e58b4309a6adb8d6639d41))
* **proofs:** lift Seq + Map literals to Z3 native theories ([#381](https://github.com/HardMax71/spec_to_rest/issues/381)) ([c484a08](https://github.com/HardMax71/spec_to_rest/commit/c484a08c846a2386874afd5f2d7f67b599f82455))
* **proofs:** lift set comprehension into the verified Z3 subset ([#386](https://github.com/HardMax71/spec_to_rest/issues/386)) ([6855539](https://github.com/HardMax71/spec_to_rest/commit/68555391c10c5e6f971da95aaf4e03c57779c415))
* **proofs:** lift set subset to the verified Z3 subset ([#385](https://github.com/HardMax71/spec_to_rest/issues/385)) ([3a8fd05](https://github.com/HardMax71/spec_to_rest/commit/3a8fd05c08e860162ef6ab7213f520230b5e1f4c))
* **proofs:** lift StringLit to the Z3 native String theory ([#380](https://github.com/HardMax71/spec_to_rest/issues/380)) ([062da04](https://github.com/HardMax71/spec_to_rest/commit/062da040a81f66af46345aac7444fca68c62ec53))
* **proofs:** prove inline_calls meaning-preserving via eval_full ([#392](https://github.com/HardMax71/spec_to_rest/issues/392)) ([f9cbf55](https://github.com/HardMax71/spec_to_rest/commit/f9cbf55d7e2bf1f20fd9185f7551b5b79b64e1ef))
* **verify:** model temporal and fractional types as Int/Real sorts ([#377](https://github.com/HardMax71/spec_to_rest/issues/377)) ([3f374b1](https://github.com/HardMax71/spec_to_rest/commit/3f374b15d5bfa53e33c2b90ae0004abe68faf44e))


### Bug Fixes

* **ci:** eliminate build warnings at their sources ([#403](https://github.com/HardMax71/spec_to_rest/issues/403)) ([61e6e66](https://github.com/HardMax71/spec_to_rest/commit/61e6e6691e9b04d76786abd96bf3caa9f680da8b))
* **codegen:** lint-clean generated output + security audit jobs in generated CI ([#410](https://github.com/HardMax71/spec_to_rest/issues/410)) ([50f5125](https://github.com/HardMax71/spec_to_rest/commit/50f5125e082839c93b1cc64177e56d4ce6422ac3))
* **verify:** decode and type-check native-sort counterexample values ([#383](https://github.com/HardMax71/spec_to_rest/issues/383)) ([#405](https://github.com/HardMax71/spec_to_rest/issues/405)) ([b5c106b](https://github.com/HardMax71/spec_to_rest/commit/b5c106bdf24d36c446c07d107f1e53fae129a084))
* **verify:** normalize Z3 rationals at the decode boundary ([#402](https://github.com/HardMax71/spec_to_rest/issues/402)) ([f399942](https://github.com/HardMax71/spec_to_rest/commit/f39994236246b36fe25caff58045504bfcc9f2b8))


### Code Refactoring

* **proofs:** replace the reserved 0cmp binder with computed fresh names ([#398](https://github.com/HardMax71/spec_to_rest/issues/398)) ([8b3027b](https://github.com/HardMax71/spec_to_rest/commit/8b3027ba50fa05457645a944b1bc0cb1ba0793d6))
* **proofs:** split the Soundness session into parallel branches ([#399](https://github.com/HardMax71/spec_to_rest/issues/399)) ([f0d69af](https://github.com/HardMax71/spec_to_rest/commit/f0d69af8eb1659686288cb73009915716711227c))


### Performance

* **proofs:** flatten re-detonated matches and factor the translate/wf_z3 RHS ([#400](https://github.com/HardMax71/spec_to_rest/issues/400)) ([f04cc30](https://github.com/HardMax71/spec_to_rest/commit/f04cc30a403d9235d0e04e4bf79b83594648261f))
* **proofs:** flatten relation-reference recognisers (-30s build) ([#384](https://github.com/HardMax71/spec_to_rest/issues/384)) ([e978b0c](https://github.com/HardMax71/spec_to_rest/commit/e978b0ce425b0baa6c89a70ce7ce829a29edcf33))
* **proofs:** parallelize CI build and cut the 89s preservation proof ([#393](https://github.com/HardMax71/spec_to_rest/issues/393)) ([9dccbc2](https://github.com/HardMax71/spec_to_rest/commit/9dccbc2b593745d81471ded07bbb6e99fc2bf7cd))


### Documentation

* add DeepWiki badge to README ([#382](https://github.com/HardMax71/spec_to_rest/issues/382)) ([c4e883e](https://github.com/HardMax71/spec_to_rest/commit/c4e883e0d932a417fcfd853a10f43cfe7261f8cf))

## [2.2.0](https://github.com/HardMax71/spec_to_rest/compare/v2.1.0...v2.2.0) (2026-05-31)


### Features

* **cli:** default-on test emission + --no-tests opt-out ([#140](https://github.com/HardMax71/spec_to_rest/issues/140)) ([#305](https://github.com/HardMax71/spec_to_rest/issues/305)) ([6728e12](https://github.com/HardMax71/spec_to_rest/commit/6728e1280df7cac2727c3a476f7f596e801713e8))
* **codegen:** extension files preserved across regeneration (closes [#62](https://github.com/HardMax71/spec_to_rest/issues/62)) ([#316](https://github.com/HardMax71/spec_to_rest/issues/316)) ([b29621d](https://github.com/HardMax71/spec_to_rest/commit/b29621de397ba1bbc033a72fb77a045a601d7ec9))
* **codegen:** multi-env compose overlays + Scala-first builder ([#317](https://github.com/HardMax71/spec_to_rest/issues/317)) ([76a0230](https://github.com/HardMax71/spec_to_rest/commit/76a023076252766db59cc7f2f201a1a73e157c41))
* **dafny:** kernel verifier-friendliness for auth_service ([#149](https://github.com/HardMax71/spec_to_rest/issues/149) phase 2) ([#309](https://github.com/HardMax71/spec_to_rest/issues/309)) ([3695d32](https://github.com/HardMax71/spec_to_rest/commit/3695d32c0d72ef6f83be012ee2822a7d9ae4ca17))
* **ir:** extract Isabelle int as native BigInt ([#358](https://github.com/HardMax71/spec_to_rest/issues/358)) ([2bfdd4c](https://github.com/HardMax71/spec_to_rest/commit/2bfdd4c559529346bae5a41dfec14ce1348422f8))
* **playground:** wire verify + compile + synth into the API and UI ([8265565](https://github.com/HardMax71/spec_to_rest/commit/8265565e0e2c5a69a44e733f6cf29d9e152abf55))
* **proofs:** lift Alloy buildSigs (entity/enum/state/input sig builder) ([#357](https://github.com/HardMax71/spec_to_rest/issues/357)) ([9e54353](https://github.com/HardMax71/spec_to_rest/commit/9e54353b707ff3f0d9250010b3a55176f65c38d0))
* **proofs:** lift Alloy translator type kernel + renderExpr classifiers ([#353](https://github.com/HardMax71/spec_to_rest/issues/353)) ([67968b6](https://github.com/HardMax71/spec_to_rest/commit/67968b6fccf63fa001702611c534658b0214d774))
* **proofs:** lift applyPartialIndexConventions kernel ([#328](https://github.com/HardMax71/spec_to_rest/issues/328)) ([db16f1b](https://github.com/HardMax71/spec_to_rest/commit/db16f1bf13581f9dff8ebb7d5bd40af05bc39b3f))
* **proofs:** lift asStableMap name-disambiguation + showNat primitive ([#327](https://github.com/HardMax71/spec_to_rest/issues/327)) ([3390aa1](https://github.com/HardMax71/spec_to_rest/commit/3390aa17214f38d0e327b6557a31ebd1aa83174d))
* **proofs:** lift classifyColumnCheckAtom (column-refinement CHECK derivation) ([#351](https://github.com/HardMax71/spec_to_rest/issues/351)) ([2ff31e7](https://github.com/HardMax71/spec_to_rest/commit/2ff31e711401a05fb417293b54fb8b33d8b08779))
* **proofs:** lift classifyColumnType (Schema.mapTypeToColumn kernel) ([#323](https://github.com/HardMax71/spec_to_rest/issues/323)) ([963bb80](https://github.com/HardMax71/spec_to_rest/commit/963bb803369dfd9dff1f58dfcd505b29f27cb95d))
* **proofs:** lift classifyInvariantAtom (Schema.scala SQL CHECK derivation) ([#340](https://github.com/HardMax71/spec_to_rest/issues/340)) ([c1a0edc](https://github.com/HardMax71/spec_to_rest/commit/c1a0edc97a49b8118cc55f1921ac0064b1d0d0b9))
* **proofs:** lift classifyOpenApiNamedType (OpenApi.namedTypeSchema kernel) ([#325](https://github.com/HardMax71/spec_to_rest/issues/325)) ([196cfea](https://github.com/HardMax71/spec_to_rest/commit/196cfea4bbdda8916dea9c676b706774d14797f6))
* **proofs:** lift derivePathPattern + pathWithIdSuffix ([#331](https://github.com/HardMax71/spec_to_rest/issues/331)) ([b03a080](https://github.com/HardMax71/spec_to_rest/commit/b03a0808b5123971cf4aad27c7e0af3266b01987))
* **proofs:** lift detectEntityFieldCollisions ([#335](https://github.com/HardMax71/spec_to_rest/issues/335)) ([34ad808](https://github.com/HardMax71/spec_to_rest/commit/34ad808c497449aefa83ea46f39cb9172ae83f64))
* **proofs:** lift detectTriggerCandidate (Schema.detectAggregateTriggers kernel) ([#324](https://github.com/HardMax71/spec_to_rest/issues/324)) ([79530bb](https://github.com/HardMax71/spec_to_rest/commit/79530bbe49db4fac407fe5a49685317d662f3cbe))
* **proofs:** lift dialect_caps (Dialect extras follow-up to [#349](https://github.com/HardMax71/spec_to_rest/issues/349)) ([#350](https://github.com/HardMax71/spec_to_rest/issues/350)) ([0671237](https://github.com/HardMax71/spec_to_rest/commit/06712370c9d403f4982ba2ab0412c3b4dc59aeb3))
* **proofs:** lift enumValuesForType/Field + fieldNameIfStateIndex ([#345](https://github.com/HardMax71/spec_to_rest/issues/345)) ([cc0dce5](https://github.com/HardMax71/spec_to_rest/commit/cc0dce5ca473a6aa2fe1973c07696f8bf77774ee))
* **proofs:** lift extractVerbBeforeKebab + literal slice helpers ([#333](https://github.com/HardMax71/spec_to_rest/issues/333)) ([046d997](https://github.com/HardMax71/spec_to_rest/commit/046d997ffb8cc58bc13c28b3b3e6ebd8f108851d))
* **proofs:** lift findEnumValuesInType (OpenApi.Constraints type-walk) ([#322](https://github.com/HardMax71/spec_to_rest/issues/322)) ([5ba1f15](https://github.com/HardMax71/spec_to_rest/commit/5ba1f1534ab3d944893e906993383c508e0b897a))
* **proofs:** lift findIdParam + literalEndsWith ([#332](https://github.com/HardMax71/spec_to_rest/issues/332)) ([d8dc6a4](https://github.com/HardMax71/spec_to_rest/commit/d8dc6a4b5a74dc9c1b06db8111e2ae048f27c14a))
* **proofs:** lift HttpMethod + OperationKind enums into Isabelle ([#319](https://github.com/HardMax71/spec_to_rest/issues/319)) ([f668b4c](https://github.com/HardMax71/spec_to_rest/commit/f668b4c10c1c39d7873cddf28df49252dc55a039))
* **proofs:** lift keyExistencePair + desiredSize, drop dup relationTargetsEntity ([#344](https://github.com/HardMax71/spec_to_rest/issues/344)) ([6e7a3e7](https://github.com/HardMax71/spec_to_rest/commit/6e7a3e7ac7f7107f22a1c4d3489e1fce69e3b58a))
* **proofs:** lift MissingEnsures + open LintAnalysis.thy ([#339](https://github.com/HardMax71/spec_to_rest/issues/339)) ([7231032](https://github.com/HardMax71/spec_to_rest/commit/72310322e69826eaa25903968e0b2728ca18be71))
* **proofs:** lift Narration helpers + matchesIdentityShape recognizer ([#343](https://github.com/HardMax71/spec_to_rest/issues/343)) ([f5bbf2c](https://github.com/HardMax71/spec_to_rest/commit/f5bbf2c2da55dfe3d27f8ef7415d12f8906e7fc7))
* **proofs:** lift OpenAPI schema_object + typeExprToSchema recursive walker ([#352](https://github.com/HardMax71/spec_to_rest/issues/352)) ([4753005](https://github.com/HardMax71/spec_to_rest/commit/4753005d2637928f780a2b1dac26115c7b541ab4))
* **proofs:** lift parsedValueToString + showInt/showBool helpers ([#338](https://github.com/HardMax71/spec_to_rest/issues/338)) ([972e294](https://github.com/HardMax71/spec_to_rest/commit/972e294cadc42b1190fdb3a8fd69c718fe17553b))
* **proofs:** lift Path's HTTP-method/status decisions into Isabelle ([#321](https://github.com/HardMax71/spec_to_rest/issues/321)) ([9283f0e](https://github.com/HardMax71/spec_to_rest/commit/9283f0e20a71b1175c9d40992e91d2f5bcdae5e9))
* **proofs:** lift SQL Dialect — sequential mega-lift ([#349](https://github.com/HardMax71/spec_to_rest/issues/349)) ([56360aa](https://github.com/HardMax71/spec_to_rest/commit/56360aa79cb8bf72b145ff79b0c9fc3c85a373dd))
* **proofs:** lift synthConventionValue + round-trip proofs ([#337](https://github.com/HardMax71/spec_to_rest/issues/337)) ([b397351](https://github.com/HardMax71/spec_to_rest/commit/b397351f7e8799bad8bc64e02fe7b2ffe5dbabd5))
* **proofs:** lift synthTemporalExpr + prove parseTemporalBody round-trip ([#336](https://github.com/HardMax71/spec_to_rest/issues/336)) ([105093d](https://github.com/HardMax71/spec_to_rest/commit/105093dd2d944d767ca5f7ef7989184204992fae))
* **proofs:** lift topo_sort + schema ADTs + migration_op into Isabelle ([#318](https://github.com/HardMax71/spec_to_rest/issues/318)) ([4912334](https://github.com/HardMax71/spec_to_rest/commit/49123340404a4cf2af3e3e7b8247d38690b8e62d))
* **proofs:** lift TypeMismatch (L01) per-node classifier ([#342](https://github.com/HardMax71/spec_to_rest/issues/342)) ([6d14953](https://github.com/HardMax71/spec_to_rest/commit/6d149531860901ed762b29f35f7c24108a592be1))
* **proofs:** lift UnusedEntity + UndefinedRef walkers ([#341](https://github.com/HardMax71/spec_to_rest/issues/341)) ([5d1fe11](https://github.com/HardMax71/spec_to_rest/commit/5d1fe11e7a76a79242aaa5a436f5f429709118dc))
* **proofs:** lift validateIrContext (Validate.scala) ([#334](https://github.com/HardMax71/spec_to_rest/issues/334)) ([9558dc6](https://github.com/HardMax71/spec_to_rest/commit/9558dc6351afbe8ede58574b73ea395d16135407))
* **proofs:** lift verifier dispatch + trust classifier (Classifier + Trust) ([#354](https://github.com/HardMax71/spec_to_rest/issues/354)) ([5914284](https://github.com/HardMax71/spec_to_rest/commit/5914284cda76f2806e6d5d642142654d08f65152))
* **proofs:** lift visitConstraintOpenApi (OpenApi.Constraints Int subset) ([#326](https://github.com/HardMax71/spec_to_rest/issues/326)) ([c89546c](https://github.com/HardMax71/spec_to_rest/commit/c89546c5fc115d650a82dddad756167caf6f03ad))
* **site:** pivot docs to Fly.io container (Next.js + bundled CLI, auto-redeploy on release) ([#311](https://github.com/HardMax71/spec_to_rest/issues/311)) ([28d76a7](https://github.com/HardMax71/spec_to_rest/commit/28d76a70ee3abcd3c8c8245894c8e770be86b1a6))
* **testgen:** close translation gap for auth_service ([#149](https://github.com/HardMax71/spec_to_rest/issues/149) phase 1) ([#307](https://github.com/HardMax71/spec_to_rest/issues/307)) ([29cbf52](https://github.com/HardMax71/spec_to_rest/commit/29cbf524e082926c29bdef638eee97f43c6c087f))


### Bug Fixes

* **ci:** add Cambridge mirror + force IPv4 + diagnose curl failures ([#303](https://github.com/HardMax71/spec_to_rest/issues/303)) ([d720ec2](https://github.com/HardMax71/spec_to_rest/commit/d720ec2e05b8fab5bcd03f025c9ace75c401fd5c))
* **ci:** always run actionlint + zizmor so required checks stop stalling ([#315](https://github.com/HardMax71/spec_to_rest/issues/315)) ([8859516](https://github.com/HardMax71/spec_to_rest/commit/8859516a34f89c5edc0a621e54119506d8441688))
* **deploy:** ship Alloy stdlib so verify on cardinality specs works ([#314](https://github.com/HardMax71/spec_to_rest/issues/314)) ([9049692](https://github.com/HardMax71/spec_to_rest/commit/9049692a5c1dd1bfc7abe6c0a7f36aa22379eba3))
* **docs:** static search returned no results — basePath not prefixed onto api URL ([#295](https://github.com/HardMax71/spec_to_rest/issues/295)) ([1611004](https://github.com/HardMax71/spec_to_rest/commit/161100446136e60b51b8ad04d1f6bc6f8e02dc80))
* **fly:** pin fly.toml to actual Fly app name (spec-to-rest-site) ([2b394ec](https://github.com/HardMax71/spec_to_rest/commit/2b394ec9a2f09f23898154d70d9f45e27d326ee1))
* **migration:** reject AlterColumnType across auto-increment identity ([#249](https://github.com/HardMax71/spec_to_rest/issues/249)) ([#304](https://github.com/HardMax71/spec_to_rest/issues/304)) ([42361db](https://github.com/HardMax71/spec_to_rest/commit/42361dbdf86a9ece291c6a4a3a40c7a940fd9675))
* **playground:** use bare CLI framework IDs (chi / express / fastapi) ([#313](https://github.com/HardMax71/spec_to_rest/issues/313)) ([a901d31](https://github.com/HardMax71/spec_to_rest/commit/a901d313c614b177f98b86384e06496e3b047113))


### Code Refactoring

* **ir:** parse-don't-validate for convention rules ([#329](https://github.com/HardMax71/spec_to_rest/issues/329)) ([b3cff3f](https://github.com/HardMax71/spec_to_rest/commit/b3cff3ff8e871c2cccc087f57778818e44f6d681))
* **ir:** parse-don't-validate for temporal declarations ([#330](https://github.com/HardMax71/spec_to_rest/issues/330)) ([4ff3487](https://github.com/HardMax71/spec_to_rest/commit/4ff348779e3e657ba15c1b7c90f3ca445b43425f))
* lift 8 more recognizers/predicates to Isabelle (Phase 9ε) ([#300](https://github.com/HardMax71/spec_to_rest/issues/300)) ([61c8d1f](https://github.com/HardMax71/spec_to_rest/commit/61c8d1f90864106d8324ff22fc2f2cdb1a0eaa90))
* lift expression walkers + recognizers to Isabelle (Phase 9α/β/γ) ([#297](https://github.com/HardMax71/spec_to_rest/issues/297)) ([3a2fd79](https://github.com/HardMax71/spec_to_rest/commit/3a2fd798902e641ac99ed62bb33fbb092e3e0295))
* lift isCardinalityRhs + isKeyExistsConj to Isabelle (Phase 9η) ([#301](https://github.com/HardMax71/spec_to_rest/issues/301)) ([5bc8ba7](https://github.com/HardMax71/spec_to_rest/commit/5bc8ba78afe979781ce570f24d8b113e2fc45d19))
* lift second wave of pattern matches to Isabelle (Phase 9δ) ([#298](https://github.com/HardMax71/spec_to_rest/issues/298)) ([7f0eccb](https://github.com/HardMax71/spec_to_rest/commit/7f0eccbaaeb94db5c3060aae6c7d8442e7cdf378))
* **proofs:** drop 6 vestigial verified-subset IR records ([#363](https://github.com/HardMax71/spec_to_rest/issues/363)) ([0bd8e83](https://github.com/HardMax71/spec_to_rest/commit/0bd8e8329216f57ccaab646b863809afb0d109e5))
* **proofs:** Soundness cleanup — drop dead, inline step, fold dup lemmas ([#360](https://github.com/HardMax71/spec_to_rest/issues/360)) ([306aecf](https://github.com/HardMax71/spec_to_rest/commit/306aecf326d124db2dfab2b25920d984b28dc83e))
* **proofs:** split Isabelle session for incremental builds ([#359](https://github.com/HardMax71/spec_to_rest/issues/359)) ([8ad58cb](https://github.com/HardMax71/spec_to_rest/commit/8ad58cb00e7d2bcf990027b0eb893e4b831309e1))
* **proofs:** unify function/accessor names to camelCase ([#320](https://github.com/HardMax71/spec_to_rest/issues/320)) ([7d8e6ef](https://github.com/HardMax71/spec_to_rest/commit/7d8e6ef3078b21d06a1864d1504962de8b547681))
* **scala:** drop SpecRestGenerated. qualifier noise + trivial wrappers ([#348](https://github.com/HardMax71/spec_to_rest/issues/348)) ([05987ef](https://github.com/HardMax71/spec_to_rest/commit/05987ef1df09910b7c78f77ab1d2f7325e88034d))
* **testgen:** Builtins registry — single source of truth across Py/TS/Go backends + lint ([#308](https://github.com/HardMax71/spec_to_rest/issues/308)) ([96c9c55](https://github.com/HardMax71/spec_to_rest/commit/96c9c55b2547e05e033a3730466e8dcbc591e8ff))


### Performance

* **isabelle:** -74% wall-time via dead-code purge + file split + def-over-fun ([#299](https://github.com/HardMax71/spec_to_rest/issues/299)) ([0ff62f5](https://github.com/HardMax71/spec_to_rest/commit/0ff62f5a5f53a27d755a81ef3c33c54bc47abce6))


### Documentation

* add project logo, favicon, and social preview ([#368](https://github.com/HardMax71/spec_to_rest/issues/368)) ([f5a1bed](https://github.com/HardMax71/spec_to_rest/commit/f5a1bedfb3cb83e5f32df8a28a055ebb37d51552))
* add status badges to the README ([#373](https://github.com/HardMax71/spec_to_rest/issues/373)) ([866d89f](https://github.com/HardMax71/spec_to_rest/commit/866d89f4bb29342545018ccdeba1bd78d952c99b))
* redesign landing, playground, and roadmap ([#369](https://github.com/HardMax71/spec_to_rest/issues/369)) ([5884794](https://github.com/HardMax71/spec_to_rest/commit/58847947316fcf40442f3900fd5b2be1e968283b))

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
