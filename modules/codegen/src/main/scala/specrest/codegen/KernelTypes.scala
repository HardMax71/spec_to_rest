package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.NamedTypeF
import specrest.ir.generated.SpecRestGenerated.OptionTypeF
import specrest.ir.generated.SpecRestGenerated.SeqTypeF
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.SetTypeF
import specrest.ir.generated.SpecRestGenerated.entFields
import specrest.ir.generated.SpecRestGenerated.entName
import specrest.ir.generated.SpecRestGenerated.enumNameFull
import specrest.ir.generated.SpecRestGenerated.fldName
import specrest.ir.generated.SpecRestGenerated.fldType
import specrest.ir.generated.SpecRestGenerated.svcEntities
import specrest.ir.generated.SpecRestGenerated.svcEnums
import specrest.ir.generated.SpecRestGenerated.svcTypeAliases
import specrest.ir.generated.SpecRestGenerated.talName
import specrest.ir.generated.SpecRestGenerated.talType
import specrest.ir.generated.SpecRestGenerated.type_expr

object KernelTypes:

  enum Kind:
    case Scalar(base: String)
    case EnumK(name: String)
    case SetOf(elem: String)
    case SeqOf(elem: String)
    case OptOf(inner: Kind)

  private val PrimitiveBases =
    Map("String" -> "str", "Int" -> "int", "Bool" -> "bool", "DateTime" -> "datetime")

  def resolve(ir: ServiceIRFull, t: type_expr, fuel: Int = 8): Option[Kind] =
    if fuel <= 0 then None
    else
      t match
        case NamedTypeF(name, _) =>
          PrimitiveBases.get(name).map(Kind.Scalar(_)).orElse {
            if svcEnums(ir).exists(e => enumNameFull(e) == name) then Some(Kind.EnumK(name))
            else
              svcTypeAliases(ir)
                .find(a => talName(a) == name)
                .flatMap(a => resolve(ir, talType(a), fuel - 1))
          }
        case SetTypeF(inner, _) => elemBase(ir, inner, fuel - 1).map(Kind.SetOf(_))
        case SeqTypeF(inner, _) => elemBase(ir, inner, fuel - 1).map(Kind.SeqOf(_))
        case OptionTypeF(inner, _) =>
          resolve(ir, inner, fuel - 1).collect {
            case k @ (Kind.Scalar(_) | Kind.EnumK(_) | Kind.SetOf(_) | Kind.SeqOf(_)) =>
              Kind.OptOf(k)
          }
        case _ => None

  private def elemBase(ir: ServiceIRFull, inner: type_expr, fuel: Int): Option[String] =
    resolve(ir, inner, fuel).collect { case Kind.Scalar(b) if b == "str" || b == "int" => b }

  def fieldKind(ir: ServiceIRFull, entityName: String, fieldName: String): Option[Kind] =
    svcEntities(ir)
      .find(e => entName(e) == entityName)
      .flatMap(e => entFields(e).find(f => fldName(f) == fieldName))
      .flatMap(f => resolve(ir, fldType(f)))
