# Changelog

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
