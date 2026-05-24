package specrest.codegen

import specrest.convention.DatabaseSchema
import specrest.profile.ProfiledService

final case class EmittedFile(path: String, content: String, preserve: Boolean = false)

final case class EmitOptions(
    createdDate: Option[String] = None,
    revision: Option[String] = None,
    dafnyKernel: Option[DafnyKernel] = None,
    previousSnapshot: Option[DatabaseSchema] = None,
    existingRevisions: List[String] = Nil
)

object Emit:

  def emitProject(profiled: ProfiledService, opts: EmitOptions = EmitOptions()): List[EmittedFile] =
    profiled.profile.language match
      case "python" => specrest.codegen.python.EmitPython.emit(profiled, opts)
      case "go"     => specrest.codegen.go.EmitGo.emit(profiled, opts)
      case "ts"     => specrest.codegen.ts.EmitTs.emit(profiled, opts)
      case other =>
        throw new RuntimeException(
          s"unsupported profile language '$other' (known: python, go, ts)"
        )
