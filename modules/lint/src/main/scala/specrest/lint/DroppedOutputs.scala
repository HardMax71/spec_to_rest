package specrest.lint

import specrest.convention.Classify
import specrest.convention.Path
import specrest.ir.generated.SpecRestGenerated.*

object DroppedOutputs extends LintPass:
  val code = "L07"

  def run(ir: service_ir): List[LintDiagnostic] = ir match
    case full: ServiceIRFull => svcOperations(full).flatMap(check(_, full))

  private def check(op: operation_decl, ir: ServiceIRFull): List[LintDiagnostic] =
    val outputs = operOutputs(op)
    if outputs.isEmpty || isSingleBoolFlag(outputs) then Nil
    else
      val classification = Classify.classifyOperation(op, ir)
      val conv           = svcConventions(ir)
      val opName         = classificationOperationName(classification)
      val method = resolveMethod(
        Path.getConvention(conv, opName, "http_method").flatMap(parseHttpMethod),
        classificationMethod(classification)
      )
      val status = resolveStatus(
        Path.getConvention(conv, opName, "http_status_success"),
        method,
        classificationKind(classification)
      )
      if status == "204" then
        val dropped = outputs.map(p => s"'${prmName(p)}'").mkString(", ")
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operation '$opName' resolves to HTTP 204 No Content, so its declared outputs ($dropped) never reach the response; set '$opName.http_status_success = 200' in the conventions block",
            operSpan(op)
          )
        )
      else Nil

  // A lone Bool output on a 204 route is the designed delete pattern: the flag
  // selects 204-vs-404 and is never a response body.
  private def isSingleBoolFlag(outputs: List[param_decl]): Boolean =
    outputs match
      case p :: Nil =>
        prmType(p) match
          case NamedTypeF("Bool", _) => true
          case _                     => false
      case _ => false
