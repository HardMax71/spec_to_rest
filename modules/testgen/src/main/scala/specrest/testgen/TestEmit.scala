package specrest.testgen

import specrest.codegen.EmittedFile
import specrest.profile.ProfiledService

object TestEmit:

  def emit(profiled: ProfiledService): List[EmittedFile] =
    val _ = profiled
    Nil
