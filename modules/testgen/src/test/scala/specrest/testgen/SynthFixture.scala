package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.profile.Annotate
import specrest.profile.ProfiledService

// Emission/translation unit tests exercise logic that only applies to *implemented*
// operations. The orthogonal "skip fail-loud stubs" gate (StubOps / Finding 1) is
// covered by its own focused tests; everywhere else, ops that would otherwise be
// fail-loud stubs are marked synthesized so Finding 1 is transparent and the logic
// under test actually runs.
object SynthFixture:

  def asSynthesized(profiled: ProfiledService): ProfiledService =
    Annotate.attachDafnyMethods(
      profiled,
      profiled.operations
        .filter(op => StubOps.isStub(profiled, op))
        .map(op => op.operationName -> s"_dafny_kernel.${op.operationName}")
        .toMap
    )

  def profiled(ir: ServiceIRFull, target: String = "python-fastapi-postgres"): ProfiledService =
    asSynthesized(Annotate.buildProfiledService(ir, target))
