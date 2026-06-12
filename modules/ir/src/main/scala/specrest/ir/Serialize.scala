package specrest.ir

import io.circe.*
import io.circe.syntax.*
import specrest.ir.generated.SpecRestGenerated.*

object Serialize:

  given Encoder[bin_op] = Encoder.encodeString.contramap:
    case _: BAnd       => "and"
    case _: BOr        => "or"
    case _: BImplies   => "implies"
    case _: BIff       => "iff"
    case _: BEq        => "="
    case _: BNeq       => "!="
    case _: BLt        => "<"
    case _: BGt        => ">"
    case _: BLe        => "<="
    case _: BGe        => ">="
    case _: BIn        => "in"
    case _: BNotIn     => "not_in"
    case _: BSubset    => "subset"
    case _: BUnion     => "union"
    case _: BIntersect => "intersect"
    case _: BDiff      => "minus"
    case _: BAdd       => "+"
    case _: BSub       => "-"
    case _: BMul       => "*"
    case _: BDiv       => "/"

  given Decoder[bin_op] = Decoder.decodeString.emap:
    case "and"       => Right(BAnd())
    case "or"        => Right(BOr())
    case "implies"   => Right(BImplies())
    case "iff"       => Right(BIff())
    case "="         => Right(BEq())
    case "!="        => Right(BNeq())
    case "<"         => Right(BLt())
    case ">"         => Right(BGt())
    case "<="        => Right(BLe())
    case ">="        => Right(BGe())
    case "in"        => Right(BIn())
    case "not_in"    => Right(BNotIn())
    case "subset"    => Right(BSubset())
    case "union"     => Right(BUnion())
    case "intersect" => Right(BIntersect())
    case "minus"     => Right(BDiff())
    case "+"         => Right(BAdd())
    case "-"         => Right(BSub())
    case "*"         => Right(BMul())
    case "/"         => Right(BDiv())
    case other       => Left(s"Unknown BinOp: $other")

  given Encoder[un_op] = Encoder.encodeString.contramap:
    case _: UNot         => "not"
    case _: UNegate      => "negate"
    case _: UCardinality => "cardinality"
    case _: UPower       => "power"

  given Decoder[un_op] = Decoder.decodeString.emap:
    case "not"         => Right(UNot())
    case "negate"      => Right(UNegate())
    case "cardinality" => Right(UCardinality())
    case "power"       => Right(UPower())
    case other         => Left(s"Unknown UnOp: $other")

  given Encoder[quant_kind] = Encoder.encodeString.contramap:
    case _: QAll    => "all"
    case _: QSome   => "some"
    case _: QNo     => "no"
    case _: QExists => "exists"

  given Decoder[quant_kind] = Decoder.decodeString.emap:
    case "all"    => Right(QAll())
    case "some"   => Right(QSome())
    case "no"     => Right(QNo())
    case "exists" => Right(QExists())
    case other    => Left(s"Unknown QuantKind: $other")

  given Encoder[multiplicity] = Encoder.encodeString.contramap:
    case _: MultOne  => "one"
    case _: MultLone => "lone"
    case _: MultSome => "some"
    case _: MultSet  => "set"

  given Decoder[multiplicity] = Decoder.decodeString.emap:
    case "one"  => Right(MultOne())
    case "lone" => Right(MultLone())
    case "some" => Right(MultSome())
    case "set"  => Right(MultSet())
    case other  => Left(s"Unknown Multiplicity: $other")

  given Encoder[binding_kind] = Encoder.encodeString.contramap:
    case _: BkIn    => "in"
    case _: BkColon => "colon"

  given Decoder[binding_kind] = Decoder.decodeString.emap:
    case "in"    => Right(BkIn())
    case "colon" => Right(BkColon())
    case other   => Left(s"Unknown BindingKind: $other")

  extension (obj: JsonObject)
    def addSpan(span: Option[span_t]): JsonObject =
      span.fold(obj)(s => obj.add("span", s.asJson))

  private def kindObj(kind: String, fields: (String, Json)*): JsonObject =
    JsonObject.fromIterable(("kind" -> Json.fromString(kind)) +: fields)

  private def nullable[A: Encoder](o: Option[A]): Json =
    o.fold(Json.Null)(_.asJson)

  given Codec[span_t] = Codec.from(
    Decoder.instance: c =>
      for
        sl <- c.get[BigInt]("startLine")
        sc <- c.get[BigInt]("startCol")
        el <- c.get[BigInt]("endLine")
        ec <- c.get[BigInt]("endCol")
      yield SpanT(sl, sc, el, ec),
    Encoder.AsObject.instance:
      case SpanT(sl, sc, el, ec) =>
        JsonObject(
          "startLine" -> sl.asJson,
          "startCol"  -> sc.asJson,
          "endLine"   -> el.asJson,
          "endCol"    -> ec.asJson
        )
  )

  private lazy val typeExprEncoder: Encoder[type_expr] = Encoder.AsObject.instance: te =>
    given Encoder[type_expr] = typeExprEncoder
    te match
      case NamedTypeF(n, sp) =>
        kindObj("NamedType", "name" -> n.asJson).addSpan(sp)
      case SetTypeF(e, sp) =>
        kindObj("SetType", "elementType" -> e.asJson).addSpan(sp)
      case MapTypeF(k, v, sp) =>
        kindObj("MapType", "keyType" -> k.asJson, "valueType" -> v.asJson).addSpan(sp)
      case SeqTypeF(e, sp) =>
        kindObj("SeqType", "elementType" -> e.asJson).addSpan(sp)
      case OptionTypeF(i, sp) =>
        kindObj("OptionType", "innerType" -> i.asJson).addSpan(sp)
      case RelationTypeF(f, m, t, sp) =>
        kindObj(
          "RelationType",
          "fromType"     -> f.asJson,
          "multiplicity" -> m.asJson,
          "toType"       -> t.asJson
        ).addSpan(sp)

  private lazy val typeExprDecoder: Decoder[type_expr] = Decoder.instance: c =>
    given Decoder[type_expr] = typeExprDecoder
    c.get[String]("kind").flatMap:
      case "NamedType" =>
        for
          n  <- c.get[String]("name")
          sp <- c.getOrElse[Option[span_t]]("span")(None)
        yield NamedTypeF(n, sp)
      case "SetType" =>
        for
          e  <- c.get[type_expr]("elementType")
          sp <- c.getOrElse[Option[span_t]]("span")(None)
        yield SetTypeF(e, sp)
      case "MapType" =>
        for
          k  <- c.get[type_expr]("keyType")
          v  <- c.get[type_expr]("valueType")
          sp <- c.getOrElse[Option[span_t]]("span")(None)
        yield MapTypeF(k, v, sp)
      case "SeqType" =>
        for
          e  <- c.get[type_expr]("elementType")
          sp <- c.getOrElse[Option[span_t]]("span")(None)
        yield SeqTypeF(e, sp)
      case "OptionType" =>
        for
          i  <- c.get[type_expr]("innerType")
          sp <- c.getOrElse[Option[span_t]]("span")(None)
        yield OptionTypeF(i, sp)
      case "RelationType" =>
        for
          f  <- c.get[type_expr]("fromType")
          m  <- c.get[multiplicity]("multiplicity")
          t  <- c.get[type_expr]("toType")
          sp <- c.getOrElse[Option[span_t]]("span")(None)
        yield RelationTypeF(f, m, t, sp)
      case other => Left(DecodingFailure(s"Unknown TypeExpr kind: $other", c.history))

  given Encoder[type_expr] = typeExprEncoder
  given Decoder[type_expr] = typeExprDecoder

  private lazy val exprEncoder: Encoder[expr] = Encoder.AsObject.instance: e =>
    given Encoder[expr] = exprEncoder
    e match
      case BinaryOpF(op, l, r, sp) =>
        kindObj(
          "BinaryOp",
          "op"    -> op.asJson,
          "left"  -> l.asJson,
          "right" -> r.asJson
        ).addSpan(sp)
      case UnaryOpF(op, a, sp) =>
        kindObj("UnaryOp", "op" -> op.asJson, "operand" -> a.asJson).addSpan(sp)
      case QuantifierF(q, bs, b, sp) =>
        kindObj(
          "Quantifier",
          "quantifier" -> q.asJson,
          "bindings"   -> bs.asJson,
          "body"       -> b.asJson
        ).addSpan(sp)
      case SomeWrapF(x, sp) =>
        kindObj("SomeWrap", "expr" -> x.asJson).addSpan(sp)
      case TheF(v, d, b, sp) =>
        kindObj("The", "variable" -> v.asJson, "domain" -> d.asJson, "body" -> b.asJson)
          .addSpan(sp)
      case FieldAccessF(b, f, sp) =>
        kindObj("FieldAccess", "base" -> b.asJson, "field" -> f.asJson).addSpan(sp)
      case EnumAccessF(b, m, sp) =>
        kindObj("EnumAccess", "base" -> b.asJson, "member" -> m.asJson).addSpan(sp)
      case IndexF(b, i, sp) =>
        kindObj("Index", "base" -> b.asJson, "index" -> i.asJson).addSpan(sp)
      case CallF(callee, args, sp) =>
        kindObj("Call", "callee" -> callee.asJson, "args" -> args.asJson).addSpan(sp)
      case PrimeF(x, sp) =>
        kindObj("Prime", "expr" -> x.asJson).addSpan(sp)
      case PreF(x, sp) =>
        kindObj("Pre", "expr" -> x.asJson).addSpan(sp)
      case WithF(b, u, sp) =>
        kindObj("With", "base" -> b.asJson, "updates" -> u.asJson).addSpan(sp)
      case IfF(cond, t, el, sp) =>
        kindObj(
          "If",
          "condition" -> cond.asJson,
          "then"      -> t.asJson,
          "else_"     -> el.asJson
        ).addSpan(sp)
      case LetF(v, x, b, sp) =>
        kindObj("Let", "variable" -> v.asJson, "value" -> x.asJson, "body" -> b.asJson)
          .addSpan(sp)
      case LambdaF(p, b, sp) =>
        kindObj("Lambda", "param" -> p.asJson, "body" -> b.asJson).addSpan(sp)
      case ConstructorF(tn, f, sp) =>
        kindObj("Constructor", "typeName" -> tn.asJson, "fields" -> f.asJson).addSpan(sp)
      case SetLiteralF(es, sp) =>
        kindObj("SetLiteral", "elements" -> es.asJson).addSpan(sp)
      case MapLiteralF(en, sp) =>
        kindObj("MapLiteral", "entries" -> en.asJson).addSpan(sp)
      case SetComprehensionF(v, d, p, sp) =>
        kindObj(
          "SetComprehension",
          "variable"  -> v.asJson,
          "domain"    -> d.asJson,
          "predicate" -> p.asJson
        ).addSpan(sp)
      case SeqLiteralF(es, sp) =>
        kindObj("SeqLiteral", "elements" -> es.asJson).addSpan(sp)
      case MatchesF(x, p, sp) =>
        kindObj("Matches", "expr" -> x.asJson, "pattern" -> p.asJson).addSpan(sp)
      case IntLitF(v, sp)     => kindObj("IntLit", "value" -> v.asJson).addSpan(sp)
      case FloatLitF(v, sp)   => kindObj("FloatLit", "value" -> v.toDouble.asJson).addSpan(sp)
      case StringLitF(v, sp)  => kindObj("StringLit", "value" -> v.asJson).addSpan(sp)
      case BoolLitF(v, sp)    => kindObj("BoolLit", "value" -> v.asJson).addSpan(sp)
      case NoneLitF(sp)       => kindObj("NoneLit").addSpan(sp)
      case IdentifierF(n, sp) => kindObj("Identifier", "name" -> n.asJson).addSpan(sp)

  private lazy val exprDecoder: Decoder[expr] = Decoder.instance: c =>
    given Decoder[expr] = exprDecoder
    val sp              = c.getOrElse[Option[span_t]]("span")(None)
    c.get[String]("kind").flatMap:
      case "BinaryOp" =>
        for
          op <- c.get[bin_op]("op")
          l  <- c.get[expr]("left")
          r  <- c.get[expr]("right")
          s  <- sp
        yield BinaryOpF(op, l, r, s)
      case "UnaryOp" =>
        for
          op <- c.get[un_op]("op")
          a  <- c.get[expr]("operand")
          s  <- sp
        yield UnaryOpF(op, a, s)
      case "Quantifier" =>
        for
          q  <- c.get[quant_kind]("quantifier")
          bs <- c.get[List[quantifier_binding]]("bindings")
          b  <- c.get[expr]("body")
          s  <- sp
        yield QuantifierF(q, bs, b, s)
      case "SomeWrap" =>
        for
          e <- c.get[expr]("expr")
          s <- sp
        yield SomeWrapF(e, s)
      case "The" =>
        for
          v <- c.get[String]("variable")
          d <- c.get[expr]("domain")
          b <- c.get[expr]("body")
          s <- sp
        yield TheF(v, d, b, s)
      case "FieldAccess" =>
        for
          b <- c.get[expr]("base")
          f <- c.get[String]("field")
          s <- sp
        yield FieldAccessF(b, f, s)
      case "EnumAccess" =>
        for
          b <- c.get[expr]("base")
          m <- c.get[String]("member")
          s <- sp
        yield EnumAccessF(b, m, s)
      case "Index" =>
        for
          b <- c.get[expr]("base")
          i <- c.get[expr]("index")
          s <- sp
        yield IndexF(b, i, s)
      case "Call" =>
        for
          callee <- c.get[expr]("callee")
          args   <- c.get[List[expr]]("args")
          s      <- sp
        yield CallF(callee, args, s)
      case "Prime" =>
        for
          e <- c.get[expr]("expr")
          s <- sp
        yield PrimeF(e, s)
      case "Pre" =>
        for
          e <- c.get[expr]("expr")
          s <- sp
        yield PreF(e, s)
      case "With" =>
        for
          b <- c.get[expr]("base")
          u <- c.get[List[field_assign]]("updates")
          s <- sp
        yield WithF(b, u, s)
      case "If" =>
        for
          cond <- c.get[expr]("condition")
          t    <- c.get[expr]("then")
          e    <- c.get[expr]("else_")
          s    <- sp
        yield IfF(cond, t, e, s)
      case "Let" =>
        for
          v <- c.get[String]("variable")
          x <- c.get[expr]("value")
          b <- c.get[expr]("body")
          s <- sp
        yield LetF(v, x, b, s)
      case "Lambda" =>
        for
          p <- c.get[String]("param")
          b <- c.get[expr]("body")
          s <- sp
        yield LambdaF(p, b, s)
      case "Constructor" =>
        for
          tn <- c.get[String]("typeName")
          f  <- c.get[List[field_assign]]("fields")
          s  <- sp
        yield ConstructorF(tn, f, s)
      case "SetLiteral" =>
        for
          es <- c.get[List[expr]]("elements")
          s  <- sp
        yield SetLiteralF(es, s)
      case "MapLiteral" =>
        for
          en <- c.get[List[map_entry]]("entries")
          s  <- sp
        yield MapLiteralF(en, s)
      case "SetComprehension" =>
        for
          v <- c.get[String]("variable")
          d <- c.get[expr]("domain")
          p <- c.get[expr]("predicate")
          s <- sp
        yield SetComprehensionF(v, d, p, s)
      case "SeqLiteral" =>
        for
          es <- c.get[List[expr]]("elements")
          s  <- sp
        yield SeqLiteralF(es, s)
      case "Matches" =>
        for
          e <- c.get[expr]("expr")
          p <- c.get[String]("pattern")
          s <- sp
        yield MatchesF(e, p, s)
      case "IntLit" =>
        for v <- c.get[BigInt]("value"); s <- sp yield IntLitF(v, s)
      case "FloatLit" =>
        for v <- c.get[Double]("value"); s <- sp yield FloatLitF(v.toString, s)
      case "StringLit"  => for v <- c.get[String]("value"); s <- sp yield StringLitF(v, s)
      case "BoolLit"    => for v <- c.get[Boolean]("value"); s <- sp yield BoolLitF(v, s)
      case "NoneLit"    => sp.map(NoneLitF(_))
      case "Identifier" => for n <- c.get[String]("name"); s <- sp yield IdentifierF(n, s)
      case other        => Left(DecodingFailure(s"Unknown Expr kind: $other", c.history))

  given Encoder[expr] = exprEncoder
  given Decoder[expr] = exprDecoder

  given fieldAssignEnc: Encoder[field_assign] = Encoder.AsObject.instance:
    case FieldAssignFull(n, v, sp) =>
      JsonObject("name" -> n.asJson, "value" -> v.asJson).addSpan(sp)

  given fieldAssignDec: Decoder[field_assign] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      v  <- c.get[expr]("value")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield FieldAssignFull(n, v, sp)

  given mapEntryEnc: Encoder[map_entry] = Encoder.AsObject.instance:
    case MapEntryFull(k, v, sp) =>
      JsonObject("key" -> k.asJson, "value" -> v.asJson).addSpan(sp)

  given mapEntryDec: Decoder[map_entry] = Decoder.instance: c =>
    for
      k  <- c.get[expr]("key")
      v  <- c.get[expr]("value")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield MapEntryFull(k, v, sp)

  given quantBindingEnc: Encoder[quantifier_binding] = Encoder.AsObject.instance:
    case QuantifierBindingFull(v, d, bk, sp) =>
      JsonObject(
        "variable"    -> v.asJson,
        "domain"      -> d.asJson,
        "bindingKind" -> bk.asJson
      ).addSpan(sp)

  given quantBindingDec: Decoder[quantifier_binding] = Decoder.instance: c =>
    for
      v  <- c.get[String]("variable")
      d  <- c.get[expr]("domain")
      bk <- c.get[binding_kind]("bindingKind")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield QuantifierBindingFull(v, d, bk, sp)

  given fieldDeclEnc: Encoder[field_decl] = Encoder.AsObject.instance:
    case FieldDeclFull(n, t, c, sp) =>
      kindObj(
        "Field",
        "name"       -> n.asJson,
        "typeExpr"   -> t.asJson,
        "constraint" -> nullable(c)
      ).addSpan(sp)

  given fieldDeclDec: Decoder[field_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      t  <- c.get[type_expr]("typeExpr")
      k  <- c.getOrElse[Option[expr]]("constraint")(None)
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield FieldDeclFull(n, t, k, sp)

  given entityDeclEnc: Encoder[entity_decl] = Encoder.AsObject.instance:
    case EntityDeclFull(n, ex, f, i, sp) =>
      kindObj(
        "Entity",
        "name"       -> n.asJson,
        "extends_"   -> nullable(ex),
        "fields"     -> f.asJson,
        "invariants" -> i.asJson
      ).addSpan(sp)

  given entityDeclDec: Decoder[entity_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      ex <- c.getOrElse[Option[String]]("extends_")(None)
      f  <- c.getOrElse[List[field_decl]]("fields")(Nil)
      i  <- c.getOrElse[List[expr]]("invariants")(Nil)
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield EntityDeclFull(n, ex, f, i, sp)

  given enumDeclEnc: Encoder[enum_decl] = Encoder.AsObject.instance:
    case EnumDeclFull(n, vs, sp) =>
      kindObj("Enum", "name" -> n.asJson, "values" -> vs.asJson).addSpan(sp)

  given enumDeclDec: Decoder[enum_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      vs <- c.get[List[String]]("values")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield EnumDeclFull(n, vs, sp)

  given typeAliasDeclEnc: Encoder[type_alias_decl] = Encoder.AsObject.instance:
    case TypeAliasDeclFull(n, t, c, sp) =>
      kindObj(
        "TypeAlias",
        "name"       -> n.asJson,
        "typeExpr"   -> t.asJson,
        "constraint" -> nullable(c)
      ).addSpan(sp)

  given typeAliasDeclDec: Decoder[type_alias_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      t  <- c.get[type_expr]("typeExpr")
      k  <- c.getOrElse[Option[expr]]("constraint")(None)
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield TypeAliasDeclFull(n, t, k, sp)

  given stateFieldDeclEnc: Encoder[state_field_decl] = Encoder.AsObject.instance:
    case StateFieldDeclFull(n, t, sp) =>
      kindObj("StateField", "name" -> n.asJson, "typeExpr" -> t.asJson).addSpan(sp)

  given stateFieldDeclDec: Decoder[state_field_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      t  <- c.get[type_expr]("typeExpr")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield StateFieldDeclFull(n, t, sp)

  given stateDeclEnc: Encoder[state_decl] = Encoder.AsObject.instance:
    case StateDeclFull(f, sp) =>
      kindObj("State", "fields" -> f.asJson).addSpan(sp)

  given stateDeclDec: Decoder[state_decl] = Decoder.instance: c =>
    for
      f  <- c.get[List[state_field_decl]]("fields")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield StateDeclFull(f, sp)

  given paramDeclEnc: Encoder[param_decl] = Encoder.AsObject.instance:
    case ParamDeclFull(n, t, sp) =>
      kindObj("Param", "name" -> n.asJson, "typeExpr" -> t.asJson).addSpan(sp)

  given paramDeclDec: Decoder[param_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      t  <- c.get[type_expr]("typeExpr")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield ParamDeclFull(n, t, sp)

  given operationDeclEnc: Encoder[operation_decl] = Encoder.AsObject.instance:
    case OperationDeclFull(n, i, o, r, e, ra, sp) =>
      val base = kindObj(
        "Operation",
        "name"     -> n.asJson,
        "inputs"   -> i.asJson,
        "outputs"  -> o.asJson,
        "requires" -> r.asJson,
        "ensures"  -> e.asJson
      )
      ra.fold(base)(xs => base.add("requiresAuth", xs.asJson)).addSpan(sp)

  given operationDeclDec: Decoder[operation_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      i  <- c.getOrElse[List[param_decl]]("inputs")(Nil)
      o  <- c.getOrElse[List[param_decl]]("outputs")(Nil)
      r  <- c.getOrElse[List[expr]]("requires")(Nil)
      e  <- c.getOrElse[List[expr]]("ensures")(Nil)
      ra <- c.getOrElse[Option[List[String]]]("requiresAuth")(None)
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield OperationDeclFull(n, i, o, r, e, ra, sp)

  given transitionRuleEnc: Encoder[transition_rule] = Encoder.AsObject.instance:
    case TransitionRuleFull(f, t, v, g, sp) =>
      kindObj(
        "TransitionRule",
        "from"  -> f.asJson,
        "to"    -> t.asJson,
        "via"   -> v.asJson,
        "guard" -> nullable(g)
      ).addSpan(sp)

  given transitionRuleDec: Decoder[transition_rule] = Decoder.instance: c =>
    for
      f  <- c.get[String]("from")
      t  <- c.get[String]("to")
      v  <- c.get[String]("via")
      g  <- c.getOrElse[Option[expr]]("guard")(None)
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield TransitionRuleFull(f, t, v, g, sp)

  given transitionDeclEnc: Encoder[transition_decl] = Encoder.AsObject.instance:
    case TransitionDeclFull(n, e, f, r, sp) =>
      kindObj(
        "Transition",
        "name"       -> n.asJson,
        "entityName" -> e.asJson,
        "fieldName"  -> f.asJson,
        "rules"      -> r.asJson
      ).addSpan(sp)

  given transitionDeclDec: Decoder[transition_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      e  <- c.get[String]("entityName")
      f  <- c.get[String]("fieldName")
      r  <- c.getOrElse[List[transition_rule]]("rules")(Nil)
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield TransitionDeclFull(n, e, f, r, sp)

  given invariantDeclEnc: Encoder[invariant_decl] = Encoder.AsObject.instance:
    case InvariantDeclFull(n, e, sp) =>
      kindObj("Invariant", "name" -> nullable(n), "expr" -> e.asJson).addSpan(sp)

  given invariantDeclDec: Decoder[invariant_decl] = Decoder.instance: c =>
    for
      n  <- c.getOrElse[Option[String]]("name")(None)
      e  <- c.get[expr]("expr")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield InvariantDeclFull(n, e, sp)

  given temporalDeclEnc: Encoder[temporal_decl] = Encoder.AsObject.instance:
    case TemporalDeclFull(n, b, sp) =>
      kindObj("Temporal", "name" -> n.asJson, "expr" -> synthTemporalExpr(b).asJson).addSpan(sp)

  given temporalDeclDec: Decoder[temporal_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      e  <- c.get[expr]("expr")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield TemporalDeclFull(n, parseTemporalBody(e), sp)

  given factDeclEnc: Encoder[fact_decl] = Encoder.AsObject.instance:
    case FactDeclFull(n, e, sp) =>
      kindObj("Fact", "name" -> nullable(n), "expr" -> e.asJson).addSpan(sp)

  given factDeclDec: Decoder[fact_decl] = Decoder.instance: c =>
    for
      n  <- c.getOrElse[Option[String]]("name")(None)
      e  <- c.get[expr]("expr")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield FactDeclFull(n, e, sp)

  given functionDeclEnc: Encoder[function_decl] = Encoder.AsObject.instance:
    case FunctionDeclFull(n, p, r, b, sp) =>
      kindObj(
        "Function",
        "name"       -> n.asJson,
        "params"     -> p.asJson,
        "returnType" -> r.asJson,
        "body"       -> b.asJson
      ).addSpan(sp)

  given functionDeclDec: Decoder[function_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      p  <- c.get[List[param_decl]]("params")
      r  <- c.get[type_expr]("returnType")
      b  <- c.get[expr]("body")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield FunctionDeclFull(n, p, r, b, sp)

  given predicateDeclEnc: Encoder[predicate_decl] = Encoder.AsObject.instance:
    case PredicateDeclFull(n, p, b, sp) =>
      kindObj(
        "Predicate",
        "name"   -> n.asJson,
        "params" -> p.asJson,
        "body"   -> b.asJson
      ).addSpan(sp)

  given predicateDeclDec: Decoder[predicate_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      p  <- c.get[List[param_decl]]("params")
      b  <- c.get[expr]("body")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield PredicateDeclFull(n, p, b, sp)

  given conventionRuleEnc: Encoder[convention_rule] = Encoder.AsObject.instance:
    case ConventionRuleFull(t, p, q, v, sp) =>
      kindObj(
        "ConventionRule",
        "target"    -> t.asJson,
        "property"  -> p.asJson,
        "qualifier" -> nullable(q),
        "value"     -> synthConventionValue(v).asJson
      ).addSpan(sp)

  given conventionRuleDec: Decoder[convention_rule] = Decoder.instance: c =>
    for
      t  <- c.get[String]("target")
      p  <- c.get[String]("property")
      q  <- c.getOrElse[Option[String]]("qualifier")(None)
      v  <- c.get[expr]("value")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield ConventionRuleFull(t, p, q, parseConventionValue(p, v), sp)

  given conventionsDeclEnc: Encoder[conventions_decl] = Encoder.AsObject.instance:
    case ConventionsDeclFull(rules, sp) =>
      kindObj("Conventions", "rules" -> rules.asJson).addSpan(sp)

  given conventionsDeclDec: Decoder[conventions_decl] = Decoder.instance: c =>
    for
      r  <- c.get[List[convention_rule]]("rules")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield ConventionsDeclFull(r, sp)

  given securitySchemeKindEnc: Encoder[security_scheme_kind] = Encoder.instance:
    case SsBearer(fmt) =>
      Json.fromJsonObject(
        fmt.fold(JsonObject("scheme" -> "Bearer".asJson))(f =>
          JsonObject("scheme" -> "Bearer".asJson, "bearerFormat" -> f.asJson)
        )
      )
    case SsApiKey(location, name) =>
      Json.fromJsonObject(
        JsonObject(
          "scheme"   -> "ApiKey".asJson,
          "location" -> location.asJson,
          "name"     -> name.asJson
        )
      )
    case SsBasic() => Json.fromJsonObject(JsonObject("scheme" -> "Basic".asJson))

  given securitySchemeKindDec: Decoder[security_scheme_kind] = Decoder.instance: c =>
    c.get[String]("scheme").flatMap:
      case "Bearer" =>
        c.getOrElse[Option[String]]("bearerFormat")(None).map(SsBearer(_))
      case "ApiKey" =>
        for
          location <- c.get[String]("location")
          name     <- c.get[String]("name")
        yield SsApiKey(location, name)
      case "Basic" => Right(SsBasic())
      case other =>
        Left(io.circe.DecodingFailure(s"Unknown security scheme kind: $other", c.history))

  given securitySchemeDeclEnc: Encoder[security_scheme_decl] = Encoder.AsObject.instance:
    case SecuritySchemeDeclFull(n, k, sp) =>
      kindObj("SecurityScheme", "name" -> n.asJson, "kind" -> k.asJson).addSpan(sp)

  given securitySchemeDeclDec: Decoder[security_scheme_decl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      k  <- c.get[security_scheme_kind]("kind")
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield SecuritySchemeDeclFull(n, k, sp)

  given serviceIREnc: Encoder[service_ir] = Encoder.AsObject.instance:
    case ServiceIRFull(n, im, en, es, ta, st, op, tr, iv, tm, fa, fn, pr, cv, se, sp) =>
      val base = kindObj(
        "Service",
        "name"        -> n.asJson,
        "imports"     -> im.asJson,
        "entities"    -> en.asJson,
        "enums"       -> es.asJson,
        "typeAliases" -> ta.asJson,
        "state"       -> nullable(st),
        "operations"  -> op.asJson,
        "transitions" -> tr.asJson,
        "invariants"  -> iv.asJson,
        "temporals"   -> tm.asJson,
        "facts"       -> fa.asJson,
        "functions"   -> fn.asJson,
        "predicates"  -> pr.asJson,
        "conventions" -> nullable(cv)
      )
      (if se.isEmpty then base else base.add("security", se.asJson)).addSpan(sp)

  given serviceIRDec: Decoder[service_ir] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      im <- c.getOrElse[List[String]]("imports")(Nil)
      en <- c.getOrElse[List[entity_decl]]("entities")(Nil)
      es <- c.getOrElse[List[enum_decl]]("enums")(Nil)
      ta <- c.getOrElse[List[type_alias_decl]]("typeAliases")(Nil)
      st <- c.getOrElse[Option[state_decl]]("state")(None)
      op <- c.getOrElse[List[operation_decl]]("operations")(Nil)
      tr <- c.getOrElse[List[transition_decl]]("transitions")(Nil)
      iv <- c.getOrElse[List[invariant_decl]]("invariants")(Nil)
      tm <- c.getOrElse[List[temporal_decl]]("temporals")(Nil)
      fa <- c.getOrElse[List[fact_decl]]("facts")(Nil)
      fn <- c.getOrElse[List[function_decl]]("functions")(Nil)
      pr <- c.getOrElse[List[predicate_decl]]("predicates")(Nil)
      cv <- c.getOrElse[Option[conventions_decl]]("conventions")(None)
      se <- c.getOrElse[List[security_scheme_decl]]("security")(Nil)
      sp <- c.getOrElse[Option[span_t]]("span")(None)
    yield ServiceIRFull(n, im, en, es, ta, st, op, tr, iv, tm, fa, fn, pr, cv, se, sp)

  def toJson(ir: service_ir): Json = ir.asJson

  def toPrettyString(ir: service_ir): String =
    ir.asJson.spaces2

  def fromJson(s: String): Either[Error, service_ir] =
    io.circe.parser.decode[service_ir](s)
