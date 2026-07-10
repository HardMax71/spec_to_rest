package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledService

private[codegen] object EmitShared:

  // The Dafny python backend doubles inner underscores and appends a trailing
  // underscore to python keywords and builtins (`id` compiles to `id_`).
  private val PyReserved = Set(
    "id",
    "type",
    "str",
    "int",
    "float",
    "bool",
    "list",
    "dict",
    "set",
    "hash",
    "input",
    "object",
    "property",
    "min",
    "max",
    "sum",
    "len",
    "filter",
    "map",
    "range",
    "bytes",
    "print",
    "vars",
    "dir",
    "next",
    "iter",
    "super",
    "format",
    "hex",
    "oct",
    "abs",
    "round",
    "pow",
    "repr",
    "zip",
    "all",
    "any",
    "class",
    "def",
    "from",
    "import",
    "return",
    "pass",
    "if",
    "else",
    "elif",
    "for",
    "while",
    "in",
    "is",
    "not",
    "and",
    "or",
    "None",
    "True",
    "False",
    "lambda",
    "global",
    "nonlocal",
    "del",
    "with",
    "as",
    "try",
    "except",
    "finally",
    "raise",
    "assert",
    "yield",
    "async",
    "await",
    "break",
    "continue"
  )

  def pyDafnySelector(fieldName: String): String =
    val doubled = fieldName.replace("_", "__")
    if PyReserved.contains(doubled) then doubled + "_" else doubled

  def doubleQuoted(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  // A nested entity (a Set[Entity] field's element) joins through its id
  // field: the owning row's JSON column stores ids, the entity's own table
  // stores the rows. No id field means the bridge cannot key the join and the
  // kind stays unsupported.
  def nestedEntity(
      profiled: specrest.profile.ProfiledService,
      entityName: String
  ): Option[specrest.profile.ProfiledEntity] =
    profiled.entities.find(e => e.entityName == entityName && e.fields.exists(_.fieldName == "id"))

  // A field's boundary-enforceable string refinement: the entity declaration's
  // alias type plus its inline where clause, reduced by StringRefinements.
  def entityFieldRefinement(
      profiled: specrest.profile.ProfiledService,
      entity: specrest.profile.ProfiledEntity,
      f: specrest.profile.ProfiledField
  ): specrest.convention.StringRefinements.Reduced =
    import specrest.ir.generated.SpecRestGenerated.*
    svcEntities(profiled.ir)
      .find(d => entName(d) == entity.entityName)
      .flatMap(d => entFields(d).find(fd => fldName(fd) == f.fieldName))
      .map(fd =>
        specrest.convention.StringRefinements.reduceField(fldType(fd), fldDefault(fd), profiled.ir)
      )
      .getOrElse(specrest.convention.StringRefinements.Reduced(None, None, Nil))

  def routeKindName(rk: route_kind): String = rk match
    case _: RkCreate   => "create"
    case _: RkRead     => "read"
    case _: RkList     => "list"
    case _: RkDelete   => "delete"
    case _: RkRedirect => "redirect"
    case _: RkOther    => "other"

  // Fewer path params = more specific (a static path beats a `{param}` catch-all),
  // so they sort first and a static path is never shadowed by a catch-all route.
  def byPathSpecificity(aPath: String, bPath: String): Boolean =
    aPath.count(_ == '{') < bPath.count(_ == '{')

  // Profile base types keyed by name, with type aliases resolved to their base
  // domain (cycle-guarded). Go and TS pass no optionWrap, so Option-typed
  // aliases stay unresolved (their historical behavior); Python wraps them as
  // `T | None`.
  def aliasResolvedDomainLookup(
      profiled: ProfiledService,
      optionWrap: Option[String => String] = None
  ): Map[String, String] =
    val base = profiled.profile.typeMap.map((k, v) => k -> v.domain)
    val aliasExprs =
      svcTypeAliases(profiled.ir).map(a => talName(a) -> talType(a)).toMap
    def resolve(te: type_expr, seen: Set[String]): Option[String] = te match
      case NamedTypeF(n, _) =>
        base
          .get(n)
          .orElse(
            if seen(n) then None
            else aliasExprs.get(n).flatMap(resolve(_, seen + n))
          )
      case OptionTypeF(inner, _) =>
        optionWrap.flatMap(w => resolve(inner, seen).map(w))
      case _ => None
    base ++ aliasExprs.flatMap((n, t) => resolve(t, Set.empty).map(n -> _))

  def paramType(
      te: type_expr,
      lookup: Map[String, String],
      default: String,
      optionWrap: String => String
  ): String = te match
    case NamedTypeF(n, _)      => lookup.getOrElse(n, default)
    case OptionTypeF(inner, _) => optionWrap(paramType(inner, lookup, default, optionWrap))
    case _                     => default

  def redirectTargetColumn(entity: ProfiledEntity): Option[String] =
    List("url", "location", "redirect_url").find(c => entity.fields.exists(_.columnName == c))

  // The row-lookup column for single-row routes: the first path param when it
  // names an entity column, otherwise the primary key.
  def lookupColumn(entity: ProfiledEntity, firstPathParam: Option[String]): String =
    firstPathParam match
      case Some(p) if entity.fields.exists(_.columnName == p) => p
      case _                                                  => "id"

  // The docker-compose set and .env.example are identical across targets, so all
  // three backends emit them from here instead of each re-listing the same files.
  def composeAndEnvFiles(
      composeIn: Compose.Inputs,
      authEnv: List[EnvExample.Entry]
  ): List[EmittedFile] =
    List(
      EmittedFile("docker-compose.yml", Compose.base(composeIn).yaml),
      EmittedFile("docker-compose.override.yml.example", Compose.overrideExample(composeIn).yaml),
      EmittedFile("docker-compose.staging.yml", Compose.staging(composeIn).yaml, preserve = true),
      EmittedFile("docker-compose.prod.yml", Compose.prod(composeIn).yaml, preserve = true),
      EmittedFile(".env.example", EnvExample.render(composeIn, authEnv))
    )

  final case class KernelOutputShape(
      entityOutput: Option[ProfiledEntity],
      seqEntityOutput: Option[ProfiledEntity],
      entityOutputFields: List[ProfiledField],
      outFieldKinds: Map[String, KernelTypes.Kind]
  )

  // A kernel result's output shape: the single entity output (or the element type
  // of a seq output), its read-shape fields with sensitive columns dropped, and the
  // payload kind per field. Computed identically by all three targets.
  def kernelOutputShape(
      specOutputs: List[param_decl],
      allEntities: List[ProfiledEntity],
      ir: ServiceIRFull
  ): KernelOutputShape =
    val entityOutput = specOutputs match
      case single :: Nil =>
        prmType(single) match
          case NamedTypeF(n, _) => allEntities.find(_.entityName == n)
          case _                => None
      case _ => None
    val seqEntityOutput = specOutputs match
      case single :: Nil =>
        prmType(single) match
          case SeqTypeF(NamedTypeF(n, _), _) => allEntities.find(_.entityName == n)
          case _                             => None
      case _ => None
    val entityOutputFields = entityOutput
      .orElse(seqEntityOutput)
      .map(_.fields.filterNot(f => SensitiveFields.isSensitive(f.columnName)))
      .getOrElse(Nil)
    val outFieldKinds: Map[String, KernelTypes.Kind] = entityOutput
      .orElse(seqEntityOutput)
      .map(e =>
        e.fields.flatMap { f =>
          KernelTypes
            .fieldKind(ir, e.entityName, f.fieldName)
            .map {
              case KernelTypes.Kind.OptOf(inner) => f.fieldName -> inner
              case other                         => f.fieldName -> other
            }
        }.toMap
      )
      .getOrElse(Map.empty)
    KernelOutputShape(entityOutput, seqEntityOutput, entityOutputFields, outFieldKinds)

  // Whether a kernel output field can be marshaled into the response. Foreign keys are
  // scalar aliases, so every marshalable field resolves to a kind; an unresolved field
  // is not kernel-marshalable and the operation falls back to direct emit.
  def outFieldOk(outFieldKinds: Map[String, KernelTypes.Kind], f: ProfiledField): Boolean =
    outFieldKinds.get(f.fieldName) match
      case Some(KernelTypes.Kind.Scalar(_))                            => true
      case Some(KernelTypes.Kind.EnumK(_))                             => true
      case Some(KernelTypes.Kind.SetOf(_) | KernelTypes.Kind.SeqOf(_)) => !f.nullable
      case Some(KernelTypes.Kind.EntitySetOf(_))                       => !f.nullable
      case _                                                           => false

  // Whether an operation's outputs can be marshaled from the kernel result: no output
  // is a plain effect call, an entity output needs every read-shape field marshalable,
  // and a scalar output needs every field non-nullable and in the target's kernel-convertible
  // set. That set is the only per-target input, since each language converts its own scalars.
  // Nullable scalar results defer (their rendered type is not a convertible key anyway).
  def kernelOutputsOk(
      shape: KernelOutputShape,
      specOutputs: List[param_decl],
      scalarOutputs: List[ProfiledField],
      scalarConvTypes: Set[String]
  ): Boolean =
    if specOutputs.isEmpty then true
    else if shape.entityOutput.isDefined || shape.seqEntityOutput.isDefined then
      shape.entityOutputFields.nonEmpty && shape.entityOutputFields.forall(outFieldOk(
        shape.outFieldKinds,
        _
      ))
    else
      scalarOutputs.nonEmpty && scalarOutputs.forall(f =>
        !f.nullable && scalarConvTypes.contains(f.domainType)
      )
