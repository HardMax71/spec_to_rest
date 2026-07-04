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
            intAliasBounds(ir, stfType(sf)).isDefined && columnName(stfName(sf)) != "id"
          .flatMap: sf =>
            val bounds = intAliasBounds(ir, stfType(sf)).getOrElse((None, None))
            seedFor(ir, stfName(sf), bounds).map(sf -> _)
        if scalars.isEmpty || entityTableNames(ir).contains(TableName) then Nil
        else scalars

  // Bare Int, or an alias chain ending in Int whose where-refinement reduces
  // to plain bounds; the bounds join the seed search so the seeded value
  // satisfies the alias refinement too.
  private def intAliasBounds(
      ir: ServiceIRFull,
      t: type_expr
  ): Option[(Option[BigInt], Option[BigInt])] =
    t match
      case NamedTypeF("Int", _) => Some((None, None))
      case NamedTypeF(name, _) =>
        svcTypeAliases(ir)
          .find(a => talName(a) == name)
          .flatMap: a =>
            // Bounds intersect down the alias chain: an intermediate alias
            // without its own where must not erase what deeper aliases pin.
            intAliasBounds(ir, talType(a)).flatMap: (innerLo, innerHi) =>
              talConstraint(a) match
                case None => Some((innerLo, innerHi))
                case Some(c) =>
                  walkIntConstraint(c) match
                    case (IntConstraint(mn, mx, Nil), Nil) =>
                      val lo = (innerLo.toList ::: mn.toList).maxOption
                      val hi = (innerHi.toList ::: mx.toList).minOption
                      Some((lo, hi))
                    case _ => None
      case _ => None

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

  private def seedFor(
      ir: ServiceIRFull,
      name: String,
      aliasBounds: (Option[BigInt], Option[BigInt])
  ): Option[BigInt] =
    val stateRelNames =
      svcState(ir).map(sd => stdFields(sd).map(stfName).toSet).getOrElse(Set.empty)
    // A universal over a state relation holds vacuously in the seeded initial
    // state (tables start empty), so it cannot constrain the seed. Anything
    // else that reads state (a cardinality, say) still fails closed.
    def vacuousAtEmptySeed(a: expr): Boolean = a match
      case QuantifierF(QAll(), bs, _, _) =>
        bs.forall:
          case QuantifierBindingFull(_, dom, _, _) =>
            identName(dom).exists(stateRelNames.contains)
      case _ => false
    val atoms    = svcInvariants(ir).flatMap(inv => flattenEnsures(List(invBody(inv))))
    val relevant = atoms.filter(a => free_vars(a).contains(name)).filterNot(vacuousAtEmptySeed)
    val parsed   = relevant.map(a => scalarGuardOf(List(name), a))
    if parsed.exists(_.isEmpty) then None
    else
      val aliasCmps =
        aliasBounds._1.map(k => (ScGe(): scalar_cmp, k)).toList :::
          aliasBounds._2.map(k => (ScLe(): scalar_cmp, k)).toList
      val cmps = aliasCmps ::: parsed.flatten.collect { case SgCmp(_, c, k) => (c, k) }
      val lowers = cmps.collect:
        case (_: ScGe, k) => k
        case (_: ScGt, k) => k + 1
        case (_: ScEq, k) => k
      val uppers = cmps.collect:
        case (_: ScLe, k) => k
        case (_: ScLt, k) => k - 1
        case (_: ScEq, k) => k
      // Prefer the value nearest 0 inside the bounds (so unconstrained and
      // non-negative fields keep seeding 0, and negative-only invariants
      // like `x < 0` seed -1), then search outward past any != atoms.
      val clamped = (lowers.maxOption, uppers.minOption) match
        case (Some(l), _) if l > 0 => l
        case (_, Some(h)) if h < 0 => h
        case _                     => BigInt(0)
      (0 to cmps.size).iterator
        .flatMap(i => List(clamped + i, clamped - i).distinct)
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
