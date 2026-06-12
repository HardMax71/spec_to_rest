package specrest.convention

import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.ir.idx

object ScalarState:

  val TableName   = "service_state"
  val SingletonId = 1

  // Bare `Int` only: a type alias to Int may carry a `where` refinement the
  // state table would not enforce (no derived CHECK yet), so alias-typed
  // scalars deliberately stay on the LLM path. A field named `id` would
  // collide with the singleton PK column. An entity mapped to the reserved
  // table name disables the feature for the service - those ops then route
  // to LLM synthesis, exactly the pre-#407 behaviour, rather than failing
  // the compile of a previously-valid spec. A field is also only backed
  // when a seed satisfying every invariant that mentions it can be derived
  // structurally - the seeded row is the service's initial state, and a
  // seed violating an invariant would fail conformance before any call.
  def fieldsWithSeeds(ir: ServiceIRFull): List[(state_field_decl, BigInt)] =
    svcState(ir) match
      case None => Nil
      case Some(sd) =>
        val scalars = stdFields(sd)
          .filter: sf =>
            stfType(sf) match
              case NamedTypeF("Int", _) => columnName(stfName(sf)) != "id"
              case _                    => false
          .flatMap(sf => seedFor(ir, stfName(sf)).map(sf -> _))
        if scalars.isEmpty || entityTableNames(ir).contains(TableName) then Nil
        else scalars

  def fields(ir: ServiceIRFull): List[state_field_decl] = fieldsWithSeeds(ir).map(_._1)

  def fieldNames(ir: ServiceIRFull): List[String] = fields(ir).map(stfName)

  def columnName(specName: String): String = Naming.toColumnName(specName)

  def stateTable(ir: ServiceIRFull): Option[table_spec] =
    val scalars = fieldsWithSeeds(ir)
    if scalars.isEmpty then None
    else
      val idCol = ColumnSpec("id", "BIGINT", false, None)
      val cols = scalars.map: (sf, seed) =>
        ColumnSpec(columnName(stfName(sf)), "BIGINT", false, Some(seed.toString))
      Some(
        TableSpec(
          TableName,
          "",
          idCol :: cols,
          "id",
          Nil,
          List(s"id = $SingletonId"),
          Nil
        )
      )

  private def seedFor(ir: ServiceIRFull, name: String): Option[BigInt] =
    val atoms    = svcInvariants(ir).flatMap(inv => flattenEnsures(List(invBody(inv))))
    val relevant = atoms.filter(a => free_vars(a).contains(name))
    val parsed   = relevant.map(a => scalarGuardOf(List(name), a))
    if parsed.exists(_.isEmpty) then None
    else
      val cmps = parsed.flatten.collect { case SgCmp(_, c, k) => (c, k) }
      val lo = (BigInt(0) :: cmps.collect {
        case (_: ScGe, k) => k
        case (_: ScGt, k) => k + 1
        case (_: ScEq, k) => k
      }).max
      // bump past != constraints, then require every atom to hold
      Iterator
        .iterate(lo)(_ + 1)
        .take(cmps.size + 1)
        .find(v => cmps.forall((c, k) => cmpHolds(v, c, k)))

  private def cmpHolds(v: BigInt, c: scalar_cmp, k: BigInt): Boolean = c match
    case _: ScGt  => v > k
    case _: ScGe  => v >= k
    case _: ScLt  => v < k
    case _: ScLe  => v <= k
    case _: ScEq  => v == k
    case _: ScNeq => v != k

  private def entityTableNames(ir: ServiceIRFull): Set[String] =
    ir.idx.entities.map { entity =>
      Path
        .getConvention(svcConventions(ir), entName(entity), "db_table")
        .getOrElse(Naming.toTableName(entName(entity)))
    }.toSet
