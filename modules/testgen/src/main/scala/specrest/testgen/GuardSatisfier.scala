package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

private[testgen] object GuardSatisfier:

  sealed trait FieldKind derives CanEqual
  case object DateTimeField extends FieldKind
  case object NumericField  extends FieldKind

  sealed trait Fix derives CanEqual:
    def writeKey: String
    def reads: Set[String] = Set.empty
    def lines: List[String]

  private case class OrderedShiftField(
      leftKey: String,
      rightKey: String,
      kind: FieldKind,
      rightOptional: Boolean,
      deltaSeconds: Int
  ) extends Fix:
    def writeKey: String            = leftKey
    override def reads: Set[String] = Set(rightKey)
    def lines: List[String] =
      val lk      = ExprToPython.pyString(leftKey)
      val rk      = ExprToPython.pyString(rightKey)
      val anchor  = "datetime.datetime(2024, 1, 1)"
      val parseR  = s"datetime.datetime.fromisoformat(row[$rk])"
      val deltaPy = s"datetime.timedelta(seconds=$deltaSeconds)"
      kind match
        case DateTimeField =>
          val anchorIfNone =
            if rightOptional then List(s"if row[$rk] is None: row[$rk] = $anchor.isoformat()")
            else Nil
          anchorIfNone ++ List(s"row[$lk] = ($parseR + $deltaPy).isoformat()")
        case NumericField =>
          val anchorIfNone =
            if rightOptional then List(s"if row[$rk] is None: row[$rk] = 0")
            else Nil
          val rhs =
            if deltaSeconds == 0 then s"row[$rk]"
            else if deltaSeconds > 0 then s"row[$rk] + $deltaSeconds"
            else s"row[$rk] - ${-deltaSeconds}"
          anchorIfNone ++ List(s"row[$lk] = $rhs")

  private case class OrderedShiftConst(
      field: String,
      kind: FieldKind,
      constPy: String,
      deltaSeconds: Int
  ) extends Fix:
    def writeKey: String = field
    def lines: List[String] =
      val k = ExprToPython.pyString(field)
      kind match
        case NumericField =>
          val rhs = deltaSeconds match
            case 0          => constPy
            case d if d > 0 => s"$constPy + $d"
            case d          => s"$constPy - ${-d}"
          List(s"row[$k] = $rhs")
        case DateTimeField =>
          // not currently reachable — spec syntax has no DateTime literal — but
          // keep the branch coherent by treating the const as an ISO string.
          val parsed = s"datetime.datetime.fromisoformat($constPy)"
          val delta  = s"datetime.timedelta(seconds=$deltaSeconds)"
          List(s"row[$k] = ($parsed + $delta).isoformat()")

  private case class Assign(field: String, pyValue: String) extends Fix:
    def writeKey: String = field
    def lines: List[String] =
      List(s"row[${ExprToPython.pyString(field)}] = $pyValue")

  private case class NotNoneAnchor(field: String, anchor: String) extends Fix:
    def writeKey: String = field
    def lines: List[String] =
      val k = ExprToPython.pyString(field)
      List(s"if row[$k] is None: row[$k] = $anchor")

  private case class ListOfSize(field: String, items: List[String]) extends Fix:
    def writeKey: String = field
    def lines: List[String] =
      val k = ExprToPython.pyString(field)
      List(s"row[$k] = [${items.mkString(", ")}]")

  private case class ListAppend(field: String, pyElem: String, optional: Boolean) extends Fix:
    def writeKey: String            = field
    override def reads: Set[String] = Set(field)
    def lines: List[String] =
      val k      = ExprToPython.pyString(field)
      val anchor = if optional then List(s"if row[$k] is None: row[$k] = []") else Nil
      anchor ++ List(s"row[$k] = list(row[$k]) + [$pyElem]")

  private case class NoOp(label: String) extends Fix:
    def writeKey: String    = s"__noop:$label"
    def lines: List[String] = Nil

  def recognize(
      guard: expr,
      entity: entity_decl,
      transitionField: String,
      from: String,
      ir: ServiceIRFull
  ): Option[List[String]] =
    collect(guard, entity, transitionField, from, ir).flatMap: fixes =>
      val realFixes = fixes.filter:
        case _: NoOp => false
        case _       => true
      val byKey = realFixes.groupBy(_.writeKey)
      val conflict = byKey.exists: (_, group) =>
        group.map(_.lines).distinct.size > 1
      if conflict then None
      else
        val deduped = byKey.toList.sortBy(_._1).map(_._2.head)
        topoOrder(deduped).map(_.flatMap(_.lines))

  @scala.annotation.tailrec
  private def topoStep(remaining: List[Fix], placed: List[Fix]): Option[List[Fix]] =
    if remaining.isEmpty then Some(placed.reverse)
    else
      val pendingWrites = remaining.map(_.writeKey).toSet
      val idx = remaining.indexWhere: f =>
        f.reads.filterNot(_ == f.writeKey).forall(r => !pendingWrites.contains(r))
      if idx < 0 then None
      else
        val (pre, mid) = remaining.splitAt(idx)
        val picked     = mid.head
        val rest       = pre ++ mid.tail
        topoStep(rest, picked :: placed)

  private def topoOrder(fixes: List[Fix]): Option[List[Fix]] = topoStep(fixes, Nil)

  private def collect(
      guard: expr,
      entity: entity_decl,
      transitionField: String,
      from: String,
      ir: ServiceIRFull
  ): Option[List[Fix]] = guard match

    case UnaryOpF(UNot(), inner, _) =>
      negate(inner).flatMap(collect(_, entity, transitionField, from, ir))

    case BinaryOpF(BAnd(), l, r, _) =>
      for
        a <- collect(l, entity, transitionField, from, ir)
        b <- collect(r, entity, transitionField, from, ir)
      yield a ++ b

    case BinaryOpF(BEq(), IdentifierF(a, _), rhs, _) if a == transitionField =>
      literalValueFor(rhs, ir).flatMap: py =>
        if py == ExprToPython.pyString(from) then Some(List(NoOp(s"$a=$from")))
        else None

    case BinaryOpF(op, IdentifierF(a, _), IdentifierF(b, _), _)
        if Set[bin_op](BGt(), BGe(), BLt(), BLe()).contains(op) =>
      if a == transitionField || b == transitionField then None
      else
        for
          fa <- findFieldDeclFull(entFields(entity), a)
          fb <- findFieldDeclFull(entFields(entity), b)
          kind <-
            if isDateTimeType(svcTypeAliases(ir), fldType(fa)) &&
              isDateTimeType(svcTypeAliases(ir), fldType(fb))
            then Some(DateTimeField)
            else if isNumericType(svcTypeAliases(ir), fldType(fa)) &&
              isNumericType(svcTypeAliases(ir), fldType(fb))
            then Some(NumericField)
            else None
        yield List(
          OrderedShiftField(
            leftKey = a,
            rightKey = b,
            kind = kind,
            rightOptional = isOptionalType(svcTypeAliases(ir), fldType(fb)),
            deltaSeconds = orderedDelta(op)
          )
        )

    case BinaryOpF(op, IdentifierF(a, _), rhs, _)
        if Set[bin_op](BGt(), BGe(), BLt(), BLe()).contains(op)
          && a != transitionField =>
      for
        fa <- findFieldDeclFull(entFields(entity), a)
        if isNumericType(svcTypeAliases(ir), fldType(fa))
        constPy <- numericLiteralPy(rhs)
      yield List(
        OrderedShiftConst(
          field = a,
          kind = NumericField,
          constPy = constPy,
          deltaSeconds = orderedDelta(op)
        )
      )

    case BinaryOpF(BEq(), IdentifierF(a, _), NoneLitF(_), _)
        if a != transitionField =>
      findFieldDeclFull(entFields(entity), a).flatMap: f =>
        if isOptionalType(svcTypeAliases(ir), fldType(f)) then
          Some(List(Assign(a, "None")))
        else None

    case BinaryOpF(BEq(), IdentifierF(a, _), rhs, _)
        if a != transitionField =>
      findFieldDeclFull(entFields(entity), a).flatMap: _ =>
        literalValueFor(rhs, ir).map(py => List(Assign(a, py)))

    case BinaryOpF(BNeq(), IdentifierF(a, _), NoneLitF(_), _)
        if a != transitionField =>
      findFieldDeclFull(entFields(entity), a).flatMap: f =>
        notNoneAnchorFor(f, ir).map(anchor => List(NotNoneAnchor(a, anchor)))

    case BinaryOpF(BIn(), lit, IdentifierF(field, _), _)
        if field != transitionField =>
      for
        f     <- findFieldDeclFull(entFields(entity), field)
        inner <- collectionElementType(svcTypeAliases(ir), fldType(f))
        py    <- literalForElementType(lit, inner, ir)
      yield List(ListAppend(field, py, isOptionalType(svcTypeAliases(ir), fldType(f))))

    case BinaryOpF(op, lenOrCard, IntLitF(n, _), _)
        if op match { case _: (BGt | BGe | BLt | BLe | BEq) => true; case _ => false } =>
      for
        field <- isLenOrCardOf(lenOrCard)
        if field != transitionField
        f       <- findFieldDeclFull(entFields(entity), field)
        inner   <- collectionElementType(svcTypeAliases(ir), fldType(f))
        size    <- desiredSize(op, n)
        fillers <- buildFillers(size.toInt, inner, ir)
      yield List(ListOfSize(field, fillers))

    case _ => None

  private def buildFillers(
      size: Int,
      inner: type_expr,
      ir: ServiceIRFull
  ): Option[List[String]] =
    if size == 0 then Some(Nil)
    else if isNumericType(svcTypeAliases(ir), inner) then
      Some((0 until size).map(_.toString).toList)
    else
      inner match
        case NamedTypeF("String", _) =>
          Some((0 until size).map(i => ExprToPython.pyString(s"x$i")).toList)
        case NamedTypeF("Bool", _) if size <= 2 =>
          Some(List("True", "False").take(size))
        case NamedTypeF(name, _) =>
          svcEnums(ir).find(e => enmName(e) == name) match
            case Some(e) if size <= enmVariants(e).size =>
              Some(enmVariants(e).take(size).map(ExprToPython.pyString))
            case _ => None
        case _ => None

  private def orderedDelta(op: bin_op): Int = op match
    case _: BGt => 1
    case _: BGe => 0
    case _: BLt => -1
    case _: BLe => 0
    case _      => 0

  private def numericLiteralPy(e: expr): Option[String] = e match
    case IntLitF(v, _)   => Some(v.toString)
    case FloatLitF(v, _) => Some(v.toString)
    case _               => None

  private def literalForElementType(
      lit: expr,
      inner: type_expr,
      ir: ServiceIRFull
  ): Option[String] =
    val _ = inner
    lit match
      case StringLitF(s, _)          => Some(ExprToPython.pyString(s))
      case IntLitF(v, _)             => Some(v.toString)
      case FloatLitF(v, _)           => Some(v.toString)
      case BoolLitF(v, _)            => Some(if v then "True" else "False")
      case EnumAccessF(_, member, _) => Some(ExprToPython.pyString(member))
      case IdentifierF(name, _) =>
        val enumNames = svcEnums(ir).flatMap(enmVariants).toSet
        if enumNames.contains(name) then Some(ExprToPython.pyString(name))
        else None
      case _ => None

  private def literalValueFor(rhs: expr, ir: ServiceIRFull): Option[String] =
    rhs match
      case EnumAccessF(_, member, _) => Some(ExprToPython.pyString(member))
      case IdentifierF(name, _) =>
        val enumNames = svcEnums(ir).flatMap(enmVariants).toSet
        if enumNames.contains(name) then Some(ExprToPython.pyString(name))
        else None
      case StringLitF(s, _) => Some(ExprToPython.pyString(s))
      case IntLitF(v, _)    => Some(v.toString)
      case BoolLitF(v, _)   => Some(if v then "True" else "False")
      case FloatLitF(v, _)  => Some(v.toString)
      case _                => None

  private def notNoneAnchorFor(f: field_decl, ir: ServiceIRFull): Option[String] =
    val inner = fldType(f) match
      case OptionTypeF(t, _) => t
      case t                 => t
    if isDateTimeType(svcTypeAliases(ir), inner) then
      Some("datetime.datetime(2024, 1, 1).isoformat()")
    else if isNumericType(svcTypeAliases(ir), inner) then Some("0")
    else
      inner match
        case NamedTypeF("String", _) => Some(ExprToPython.pyString("x"))
        case NamedTypeF("Bool", _)   => Some("True")
        case NamedTypeF(name, _) =>
          svcEnums(ir)
            .find(e => enmName(e) == name)
            .flatMap(e => enmVariants(e).headOption)
            .map(ExprToPython.pyString)
        case _ => None
