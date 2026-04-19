package specrest.ir

import io.circe.*
import io.circe.syntax.*

object Serialize:

  given Encoder[BinOp] = Encoder.encodeString.contramap:
    case BinOp.And       => "and"
    case BinOp.Or        => "or"
    case BinOp.Implies   => "implies"
    case BinOp.Iff       => "iff"
    case BinOp.Eq        => "="
    case BinOp.Neq       => "!="
    case BinOp.Lt        => "<"
    case BinOp.Gt        => ">"
    case BinOp.Le        => "<="
    case BinOp.Ge        => ">="
    case BinOp.In        => "in"
    case BinOp.NotIn     => "not_in"
    case BinOp.Subset    => "subset"
    case BinOp.Union     => "union"
    case BinOp.Intersect => "intersect"
    case BinOp.Diff      => "minus"
    case BinOp.Add       => "+"
    case BinOp.Sub       => "-"
    case BinOp.Mul       => "*"
    case BinOp.Div       => "/"

  given Decoder[BinOp] = Decoder.decodeString.emap:
    case "and"       => Right(BinOp.And)
    case "or"        => Right(BinOp.Or)
    case "implies"   => Right(BinOp.Implies)
    case "iff"       => Right(BinOp.Iff)
    case "="         => Right(BinOp.Eq)
    case "!="        => Right(BinOp.Neq)
    case "<"         => Right(BinOp.Lt)
    case ">"         => Right(BinOp.Gt)
    case "<="        => Right(BinOp.Le)
    case ">="        => Right(BinOp.Ge)
    case "in"        => Right(BinOp.In)
    case "not_in"    => Right(BinOp.NotIn)
    case "subset"    => Right(BinOp.Subset)
    case "union"     => Right(BinOp.Union)
    case "intersect" => Right(BinOp.Intersect)
    case "minus"     => Right(BinOp.Diff)
    case "+"         => Right(BinOp.Add)
    case "-"         => Right(BinOp.Sub)
    case "*"         => Right(BinOp.Mul)
    case "/"         => Right(BinOp.Div)
    case other       => Left(s"Unknown BinOp: $other")

  given Encoder[UnOp] = Encoder.encodeString.contramap:
    case UnOp.Not         => "not"
    case UnOp.Negate      => "negate"
    case UnOp.Cardinality => "cardinality"
    case UnOp.Power       => "power"

  given Decoder[UnOp] = Decoder.decodeString.emap:
    case "not"         => Right(UnOp.Not)
    case "negate"      => Right(UnOp.Negate)
    case "cardinality" => Right(UnOp.Cardinality)
    case "power"       => Right(UnOp.Power)
    case other         => Left(s"Unknown UnOp: $other")

  given Encoder[QuantKind] = Encoder.encodeString.contramap:
    case QuantKind.All    => "all"
    case QuantKind.Some   => "some"
    case QuantKind.No     => "no"
    case QuantKind.Exists => "exists"

  given Decoder[QuantKind] = Decoder.decodeString.emap:
    case "all"    => Right(QuantKind.All)
    case "some"   => Right(QuantKind.Some)
    case "no"     => Right(QuantKind.No)
    case "exists" => Right(QuantKind.Exists)
    case other    => Left(s"Unknown QuantKind: $other")

  given Encoder[Multiplicity] = Encoder.encodeString.contramap:
    case Multiplicity.One  => "one"
    case Multiplicity.Lone => "lone"
    case Multiplicity.Some => "some"
    case Multiplicity.Set  => "set"

  given Decoder[Multiplicity] = Decoder.decodeString.emap:
    case "one"  => Right(Multiplicity.One)
    case "lone" => Right(Multiplicity.Lone)
    case "some" => Right(Multiplicity.Some)
    case "set"  => Right(Multiplicity.Set)
    case other  => Left(s"Unknown Multiplicity: $other")

  given Encoder[BindingKind] = Encoder.encodeString.contramap:
    case BindingKind.In    => "in"
    case BindingKind.Colon => "colon"

  given Decoder[BindingKind] = Decoder.decodeString.emap:
    case "in"    => Right(BindingKind.In)
    case "colon" => Right(BindingKind.Colon)
    case other   => Left(s"Unknown BindingKind: $other")

  extension (obj: JsonObject)
    def addSpan(span: Option[Span]): JsonObject =
      span.fold(obj)(s => obj.add("span", s.asJson))

  private def kindObj(kind: String, fields: (String, Json)*): JsonObject =
    JsonObject.fromIterable(("kind" -> Json.fromString(kind)) +: fields)

  private def nullable[A: Encoder](o: Option[A]): Json =
    o.fold(Json.Null)(_.asJson)

  given Codec[Span] = Codec.from(
    Decoder.forProduct4("startLine", "startCol", "endLine", "endCol")(Span.apply),
    Encoder.AsObject.instance: s =>
      JsonObject(
        "startLine" -> s.startLine.asJson,
        "startCol"  -> s.startCol.asJson,
        "endLine"   -> s.endLine.asJson,
        "endCol"    -> s.endCol.asJson,
      ),
  )

  given typeExprEnc: Encoder[TypeExpr] = Encoder.AsObject.instance:
    case TypeExpr.NamedType(n, sp) =>
      kindObj("NamedType", "name" -> n.asJson).addSpan(sp)
    case TypeExpr.SetType(e, sp) =>
      kindObj("SetType", "elementType" -> e.asJson).addSpan(sp)
    case TypeExpr.MapType(k, v, sp) =>
      kindObj("MapType", "keyType" -> k.asJson, "valueType" -> v.asJson).addSpan(sp)
    case TypeExpr.SeqType(e, sp) =>
      kindObj("SeqType", "elementType" -> e.asJson).addSpan(sp)
    case TypeExpr.OptionType(i, sp) =>
      kindObj("OptionType", "innerType" -> i.asJson).addSpan(sp)
    case TypeExpr.RelationType(f, m, t, sp) =>
      kindObj(
        "RelationType",
        "fromType"     -> f.asJson,
        "multiplicity" -> m.asJson,
        "toType"       -> t.asJson,
      ).addSpan(sp)

  given typeExprDec: Decoder[TypeExpr] = Decoder.instance: c =>
    c.get[String]("kind").flatMap:
      case "NamedType" =>
        for
          n  <- c.get[String]("name")
          sp <- c.getOrElse[Option[Span]]("span")(None)
        yield TypeExpr.NamedType(n, sp)
      case "SetType" =>
        for
          e  <- c.get[TypeExpr]("elementType")
          sp <- c.getOrElse[Option[Span]]("span")(None)
        yield TypeExpr.SetType(e, sp)
      case "MapType" =>
        for
          k  <- c.get[TypeExpr]("keyType")
          v  <- c.get[TypeExpr]("valueType")
          sp <- c.getOrElse[Option[Span]]("span")(None)
        yield TypeExpr.MapType(k, v, sp)
      case "SeqType" =>
        for
          e  <- c.get[TypeExpr]("elementType")
          sp <- c.getOrElse[Option[Span]]("span")(None)
        yield TypeExpr.SeqType(e, sp)
      case "OptionType" =>
        for
          i  <- c.get[TypeExpr]("innerType")
          sp <- c.getOrElse[Option[Span]]("span")(None)
        yield TypeExpr.OptionType(i, sp)
      case "RelationType" =>
        for
          f  <- c.get[TypeExpr]("fromType")
          m  <- c.get[Multiplicity]("multiplicity")
          t  <- c.get[TypeExpr]("toType")
          sp <- c.getOrElse[Option[Span]]("span")(None)
        yield TypeExpr.RelationType(f, m, t, sp)
      case other => Left(DecodingFailure(s"Unknown TypeExpr kind: $other", c.history))

  given fieldAssignEnc: Encoder[FieldAssign] = Encoder.AsObject.instance: fa =>
    JsonObject("name" -> fa.name.asJson, "value" -> fa.value.asJson).addSpan(fa.span)

  given fieldAssignDec: Decoder[FieldAssign] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      v  <- c.get[Expr]("value")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield FieldAssign(n, v, sp)

  given mapEntryEnc: Encoder[MapEntry] = Encoder.AsObject.instance: m =>
    JsonObject("key" -> m.key.asJson, "value" -> m.value.asJson).addSpan(m.span)

  given mapEntryDec: Decoder[MapEntry] = Decoder.instance: c =>
    for
      k  <- c.get[Expr]("key")
      v  <- c.get[Expr]("value")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield MapEntry(k, v, sp)

  given quantBindingEnc: Encoder[QuantifierBinding] = Encoder.AsObject.instance: b =>
    JsonObject(
      "variable"    -> b.variable.asJson,
      "domain"      -> b.domain.asJson,
      "bindingKind" -> b.bindingKind.asJson,
    ).addSpan(b.span)

  given quantBindingDec: Decoder[QuantifierBinding] = Decoder.instance: c =>
    for
      v    <- c.get[String]("variable")
      d    <- c.get[Expr]("domain")
      bk   <- c.get[BindingKind]("bindingKind")
      sp   <- c.getOrElse[Option[Span]]("span")(None)
    yield QuantifierBinding(v, d, bk, sp)

  given exprEnc: Encoder[Expr] = Encoder.AsObject.instance:
    case Expr.BinaryOp(op, l, r, sp) =>
      kindObj("BinaryOp", "op" -> op.asJson, "left" -> l.asJson, "right" -> r.asJson).addSpan(sp)
    case Expr.UnaryOp(op, a, sp) =>
      kindObj("UnaryOp", "op" -> op.asJson, "operand" -> a.asJson).addSpan(sp)
    case Expr.Quantifier(q, bs, b, sp) =>
      kindObj(
        "Quantifier",
        "quantifier" -> q.asJson,
        "bindings"   -> bs.asJson,
        "body"       -> b.asJson,
      ).addSpan(sp)
    case Expr.SomeWrap(e, sp) =>
      kindObj("SomeWrap", "expr" -> e.asJson).addSpan(sp)
    case Expr.The(v, d, b, sp) =>
      kindObj("The", "variable" -> v.asJson, "domain" -> d.asJson, "body" -> b.asJson).addSpan(sp)
    case Expr.FieldAccess(b, f, sp) =>
      kindObj("FieldAccess", "base" -> b.asJson, "field" -> f.asJson).addSpan(sp)
    case Expr.EnumAccess(b, m, sp) =>
      kindObj("EnumAccess", "base" -> b.asJson, "member" -> m.asJson).addSpan(sp)
    case Expr.Index(b, i, sp) =>
      kindObj("Index", "base" -> b.asJson, "index" -> i.asJson).addSpan(sp)
    case Expr.Call(c, a, sp) =>
      kindObj("Call", "callee" -> c.asJson, "args" -> a.asJson).addSpan(sp)
    case Expr.Prime(e, sp) =>
      kindObj("Prime", "expr" -> e.asJson).addSpan(sp)
    case Expr.Pre(e, sp) =>
      kindObj("Pre", "expr" -> e.asJson).addSpan(sp)
    case Expr.With(b, u, sp) =>
      kindObj("With", "base" -> b.asJson, "updates" -> u.asJson).addSpan(sp)
    case Expr.If(c, t, e, sp) =>
      kindObj("If", "condition" -> c.asJson, "then" -> t.asJson, "else_" -> e.asJson).addSpan(sp)
    case Expr.Let(v, x, b, sp) =>
      kindObj("Let", "variable" -> v.asJson, "value" -> x.asJson, "body" -> b.asJson).addSpan(sp)
    case Expr.Lambda(p, b, sp) =>
      kindObj("Lambda", "param" -> p.asJson, "body" -> b.asJson).addSpan(sp)
    case Expr.Constructor(tn, f, sp) =>
      kindObj("Constructor", "typeName" -> tn.asJson, "fields" -> f.asJson).addSpan(sp)
    case Expr.SetLiteral(es, sp) =>
      kindObj("SetLiteral", "elements" -> es.asJson).addSpan(sp)
    case Expr.MapLiteral(en, sp) =>
      kindObj("MapLiteral", "entries" -> en.asJson).addSpan(sp)
    case Expr.SetComprehension(v, d, p, sp) =>
      kindObj(
        "SetComprehension",
        "variable"  -> v.asJson,
        "domain"    -> d.asJson,
        "predicate" -> p.asJson,
      ).addSpan(sp)
    case Expr.SeqLiteral(es, sp) =>
      kindObj("SeqLiteral", "elements" -> es.asJson).addSpan(sp)
    case Expr.Matches(e, p, sp) =>
      kindObj("Matches", "expr" -> e.asJson, "pattern" -> p.asJson).addSpan(sp)
    case Expr.IntLit(v, sp)    => kindObj("IntLit", "value" -> v.asJson).addSpan(sp)
    case Expr.FloatLit(v, sp)  => kindObj("FloatLit", "value" -> v.asJson).addSpan(sp)
    case Expr.StringLit(v, sp) => kindObj("StringLit", "value" -> v.asJson).addSpan(sp)
    case Expr.BoolLit(v, sp)   => kindObj("BoolLit", "value" -> v.asJson).addSpan(sp)
    case Expr.NoneLit(sp)      => kindObj("NoneLit").addSpan(sp)
    case Expr.Identifier(n, sp)  => kindObj("Identifier", "name" -> n.asJson).addSpan(sp)

  given exprDec: Decoder[Expr] = Decoder.instance: c =>
    val sp = c.getOrElse[Option[Span]]("span")(None)
    c.get[String]("kind").flatMap:
      case "BinaryOp" =>
        for
          op <- c.get[BinOp]("op")
          l  <- c.get[Expr]("left")
          r  <- c.get[Expr]("right")
          s  <- sp
        yield Expr.BinaryOp(op, l, r, s)
      case "UnaryOp" =>
        for
          op <- c.get[UnOp]("op")
          a  <- c.get[Expr]("operand")
          s  <- sp
        yield Expr.UnaryOp(op, a, s)
      case "Quantifier" =>
        for
          q  <- c.get[QuantKind]("quantifier")
          bs <- c.get[List[QuantifierBinding]]("bindings")
          b  <- c.get[Expr]("body")
          s  <- sp
        yield Expr.Quantifier(q, bs, b, s)
      case "SomeWrap" =>
        for
          e <- c.get[Expr]("expr")
          s <- sp
        yield Expr.SomeWrap(e, s)
      case "The" =>
        for
          v <- c.get[String]("variable")
          d <- c.get[Expr]("domain")
          b <- c.get[Expr]("body")
          s <- sp
        yield Expr.The(v, d, b, s)
      case "FieldAccess" =>
        for
          b <- c.get[Expr]("base")
          f <- c.get[String]("field")
          s <- sp
        yield Expr.FieldAccess(b, f, s)
      case "EnumAccess" =>
        for
          b <- c.get[Expr]("base")
          m <- c.get[String]("member")
          s <- sp
        yield Expr.EnumAccess(b, m, s)
      case "Index" =>
        for
          b <- c.get[Expr]("base")
          i <- c.get[Expr]("index")
          s <- sp
        yield Expr.Index(b, i, s)
      case "Call" =>
        for
          callee <- c.get[Expr]("callee")
          args   <- c.get[List[Expr]]("args")
          s      <- sp
        yield Expr.Call(callee, args, s)
      case "Prime" =>
        for
          e <- c.get[Expr]("expr")
          s <- sp
        yield Expr.Prime(e, s)
      case "Pre" =>
        for
          e <- c.get[Expr]("expr")
          s <- sp
        yield Expr.Pre(e, s)
      case "With" =>
        for
          b <- c.get[Expr]("base")
          u <- c.get[List[FieldAssign]]("updates")
          s <- sp
        yield Expr.With(b, u, s)
      case "If" =>
        for
          cond <- c.get[Expr]("condition")
          t    <- c.get[Expr]("then")
          e    <- c.get[Expr]("else_")
          s    <- sp
        yield Expr.If(cond, t, e, s)
      case "Let" =>
        for
          v <- c.get[String]("variable")
          x <- c.get[Expr]("value")
          b <- c.get[Expr]("body")
          s <- sp
        yield Expr.Let(v, x, b, s)
      case "Lambda" =>
        for
          p <- c.get[String]("param")
          b <- c.get[Expr]("body")
          s <- sp
        yield Expr.Lambda(p, b, s)
      case "Constructor" =>
        for
          tn <- c.get[String]("typeName")
          f  <- c.get[List[FieldAssign]]("fields")
          s  <- sp
        yield Expr.Constructor(tn, f, s)
      case "SetLiteral" =>
        for
          es <- c.get[List[Expr]]("elements")
          s  <- sp
        yield Expr.SetLiteral(es, s)
      case "MapLiteral" =>
        for
          en <- c.get[List[MapEntry]]("entries")
          s  <- sp
        yield Expr.MapLiteral(en, s)
      case "SetComprehension" =>
        for
          v <- c.get[String]("variable")
          d <- c.get[Expr]("domain")
          p <- c.get[Expr]("predicate")
          s <- sp
        yield Expr.SetComprehension(v, d, p, s)
      case "SeqLiteral" =>
        for
          es <- c.get[List[Expr]]("elements")
          s  <- sp
        yield Expr.SeqLiteral(es, s)
      case "Matches" =>
        for
          e <- c.get[Expr]("expr")
          p <- c.get[String]("pattern")
          s <- sp
        yield Expr.Matches(e, p, s)
      case "IntLit"    => for v <- c.get[Long]("value");    s <- sp yield Expr.IntLit(v, s)
      case "FloatLit"  => for v <- c.get[Double]("value");  s <- sp yield Expr.FloatLit(v, s)
      case "StringLit" => for v <- c.get[String]("value");  s <- sp yield Expr.StringLit(v, s)
      case "BoolLit"   => for v <- c.get[Boolean]("value"); s <- sp yield Expr.BoolLit(v, s)
      case "NoneLit"   => sp.map(Expr.NoneLit(_))
      case "Identifier" => for n <- c.get[String]("name"); s <- sp yield Expr.Identifier(n, s)
      case other       => Left(DecodingFailure(s"Unknown Expr kind: $other", c.history))

  given fieldDeclEnc: Encoder[FieldDecl] = Encoder.AsObject.instance: f =>
    kindObj(
      "Field",
      "name"       -> f.name.asJson,
      "typeExpr"   -> f.typeExpr.asJson,
      "constraint" -> nullable(f.constraint),
    ).addSpan(f.span)

  given fieldDeclDec: Decoder[FieldDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      t  <- c.get[TypeExpr]("typeExpr")
      k  <- c.getOrElse[Option[Expr]]("constraint")(None)
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield FieldDecl(n, t, k, sp)

  given entityDeclEnc: Encoder[EntityDecl] = Encoder.AsObject.instance: e =>
    kindObj(
      "Entity",
      "name"       -> e.name.asJson,
      "extends_"   -> nullable(e.extends_),
      "fields"     -> e.fields.asJson,
      "invariants" -> e.invariants.asJson,
    ).addSpan(e.span)

  given entityDeclDec: Decoder[EntityDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      ex <- c.getOrElse[Option[String]]("extends_")(None)
      f  <- c.getOrElse[List[FieldDecl]]("fields")(Nil)
      i  <- c.getOrElse[List[Expr]]("invariants")(Nil)
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield EntityDecl(n, ex, f, i, sp)

  given enumDeclEnc: Encoder[EnumDecl] = Encoder.AsObject.instance: e =>
    kindObj("Enum", "name" -> e.name.asJson, "values" -> e.values.asJson).addSpan(e.span)

  given enumDeclDec: Decoder[EnumDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      vs <- c.get[List[String]]("values")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield EnumDecl(n, vs, sp)

  given typeAliasDeclEnc: Encoder[TypeAliasDecl] = Encoder.AsObject.instance: t =>
    kindObj(
      "TypeAlias",
      "name"       -> t.name.asJson,
      "typeExpr"   -> t.typeExpr.asJson,
      "constraint" -> nullable(t.constraint),
    ).addSpan(t.span)

  given typeAliasDeclDec: Decoder[TypeAliasDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      t  <- c.get[TypeExpr]("typeExpr")
      k  <- c.getOrElse[Option[Expr]]("constraint")(None)
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield TypeAliasDecl(n, t, k, sp)

  given stateFieldDeclEnc: Encoder[StateFieldDecl] = Encoder.AsObject.instance: s =>
    kindObj("StateField", "name" -> s.name.asJson, "typeExpr" -> s.typeExpr.asJson).addSpan(s.span)

  given stateFieldDeclDec: Decoder[StateFieldDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      t  <- c.get[TypeExpr]("typeExpr")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield StateFieldDecl(n, t, sp)

  given stateDeclEnc: Encoder[StateDecl] = Encoder.AsObject.instance: s =>
    kindObj("State", "fields" -> s.fields.asJson).addSpan(s.span)

  given stateDeclDec: Decoder[StateDecl] = Decoder.instance: c =>
    for
      f  <- c.get[List[StateFieldDecl]]("fields")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield StateDecl(f, sp)

  given paramDeclEnc: Encoder[ParamDecl] = Encoder.AsObject.instance: p =>
    kindObj("Param", "name" -> p.name.asJson, "typeExpr" -> p.typeExpr.asJson).addSpan(p.span)

  given paramDeclDec: Decoder[ParamDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      t  <- c.get[TypeExpr]("typeExpr")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield ParamDecl(n, t, sp)

  given operationDeclEnc: Encoder[OperationDecl] = Encoder.AsObject.instance: o =>
    kindObj(
      "Operation",
      "name"     -> o.name.asJson,
      "inputs"   -> o.inputs.asJson,
      "outputs"  -> o.outputs.asJson,
      "requires" -> o.requires.asJson,
      "ensures"  -> o.ensures.asJson,
    ).addSpan(o.span)

  given operationDeclDec: Decoder[OperationDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      i  <- c.getOrElse[List[ParamDecl]]("inputs")(Nil)
      o  <- c.getOrElse[List[ParamDecl]]("outputs")(Nil)
      r  <- c.getOrElse[List[Expr]]("requires")(Nil)
      e  <- c.getOrElse[List[Expr]]("ensures")(Nil)
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield OperationDecl(n, i, o, r, e, sp)

  given transitionRuleEnc: Encoder[TransitionRule] = Encoder.AsObject.instance: t =>
    kindObj(
      "TransitionRule",
      "from"  -> t.from.asJson,
      "to"    -> t.to.asJson,
      "via"   -> t.via.asJson,
      "guard" -> nullable(t.guard),
    ).addSpan(t.span)

  given transitionRuleDec: Decoder[TransitionRule] = Decoder.instance: c =>
    for
      f  <- c.get[String]("from")
      t  <- c.get[String]("to")
      v  <- c.get[String]("via")
      g  <- c.getOrElse[Option[Expr]]("guard")(None)
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield TransitionRule(f, t, v, g, sp)

  given transitionDeclEnc: Encoder[TransitionDecl] = Encoder.AsObject.instance: t =>
    kindObj(
      "Transition",
      "name"       -> t.name.asJson,
      "entityName" -> t.entityName.asJson,
      "fieldName"  -> t.fieldName.asJson,
      "rules"      -> t.rules.asJson,
    ).addSpan(t.span)

  given transitionDeclDec: Decoder[TransitionDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      e  <- c.get[String]("entityName")
      f  <- c.get[String]("fieldName")
      r  <- c.getOrElse[List[TransitionRule]]("rules")(Nil)
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield TransitionDecl(n, e, f, r, sp)

  given invariantDeclEnc: Encoder[InvariantDecl] = Encoder.AsObject.instance: i =>
    kindObj("Invariant", "name" -> nullable(i.name), "expr" -> i.expr.asJson).addSpan(i.span)

  given invariantDeclDec: Decoder[InvariantDecl] = Decoder.instance: c =>
    for
      n  <- c.getOrElse[Option[String]]("name")(None)
      e  <- c.get[Expr]("expr")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield InvariantDecl(n, e, sp)

  given factDeclEnc: Encoder[FactDecl] = Encoder.AsObject.instance: f =>
    kindObj("Fact", "name" -> nullable(f.name), "expr" -> f.expr.asJson).addSpan(f.span)

  given factDeclDec: Decoder[FactDecl] = Decoder.instance: c =>
    for
      n  <- c.getOrElse[Option[String]]("name")(None)
      e  <- c.get[Expr]("expr")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield FactDecl(n, e, sp)

  given functionDeclEnc: Encoder[FunctionDecl] = Encoder.AsObject.instance: f =>
    kindObj(
      "Function",
      "name"       -> f.name.asJson,
      "params"     -> f.params.asJson,
      "returnType" -> f.returnType.asJson,
      "body"       -> f.body.asJson,
    ).addSpan(f.span)

  given functionDeclDec: Decoder[FunctionDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      p  <- c.get[List[ParamDecl]]("params")
      r  <- c.get[TypeExpr]("returnType")
      b  <- c.get[Expr]("body")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield FunctionDecl(n, p, r, b, sp)

  given predicateDeclEnc: Encoder[PredicateDecl] = Encoder.AsObject.instance: p =>
    kindObj(
      "Predicate",
      "name"   -> p.name.asJson,
      "params" -> p.params.asJson,
      "body"   -> p.body.asJson,
    ).addSpan(p.span)

  given predicateDeclDec: Decoder[PredicateDecl] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      p  <- c.get[List[ParamDecl]]("params")
      b  <- c.get[Expr]("body")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield PredicateDecl(n, p, b, sp)

  given conventionRuleEnc: Encoder[ConventionRule] = Encoder.AsObject.instance: r =>
    kindObj(
      "ConventionRule",
      "target"    -> r.target.asJson,
      "property"  -> r.property.asJson,
      "qualifier" -> nullable(r.qualifier),
      "value"     -> r.value.asJson,
    ).addSpan(r.span)

  given conventionRuleDec: Decoder[ConventionRule] = Decoder.instance: c =>
    for
      t  <- c.get[String]("target")
      p  <- c.get[String]("property")
      q  <- c.getOrElse[Option[String]]("qualifier")(None)
      v  <- c.get[Expr]("value")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield ConventionRule(t, p, q, v, sp)

  given conventionsDeclEnc: Encoder[ConventionsDecl] = Encoder.AsObject.instance: c =>
    kindObj("Conventions", "rules" -> c.rules.asJson).addSpan(c.span)

  given conventionsDeclDec: Decoder[ConventionsDecl] = Decoder.instance: c =>
    for
      r  <- c.get[List[ConventionRule]]("rules")
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield ConventionsDecl(r, sp)

  given serviceIREnc: Encoder[ServiceIR] = Encoder.AsObject.instance: s =>
    kindObj(
      "Service",
      "name"        -> s.name.asJson,
      "imports"     -> s.imports.asJson,
      "entities"    -> s.entities.asJson,
      "enums"       -> s.enums.asJson,
      "typeAliases" -> s.typeAliases.asJson,
      "state"       -> nullable(s.state),
      "operations"  -> s.operations.asJson,
      "transitions" -> s.transitions.asJson,
      "invariants"  -> s.invariants.asJson,
      "facts"       -> s.facts.asJson,
      "functions"   -> s.functions.asJson,
      "predicates"  -> s.predicates.asJson,
      "conventions" -> nullable(s.conventions),
    ).addSpan(s.span)

  given serviceIRDec: Decoder[ServiceIR] = Decoder.instance: c =>
    for
      n  <- c.get[String]("name")
      im <- c.getOrElse[List[String]]("imports")(Nil)
      en <- c.getOrElse[List[EntityDecl]]("entities")(Nil)
      es <- c.getOrElse[List[EnumDecl]]("enums")(Nil)
      ta <- c.getOrElse[List[TypeAliasDecl]]("typeAliases")(Nil)
      st <- c.getOrElse[Option[StateDecl]]("state")(None)
      op <- c.getOrElse[List[OperationDecl]]("operations")(Nil)
      tr <- c.getOrElse[List[TransitionDecl]]("transitions")(Nil)
      iv <- c.getOrElse[List[InvariantDecl]]("invariants")(Nil)
      fa <- c.getOrElse[List[FactDecl]]("facts")(Nil)
      fn <- c.getOrElse[List[FunctionDecl]]("functions")(Nil)
      pr <- c.getOrElse[List[PredicateDecl]]("predicates")(Nil)
      cv <- c.getOrElse[Option[ConventionsDecl]]("conventions")(None)
      sp <- c.getOrElse[Option[Span]]("span")(None)
    yield ServiceIR(n, im, en, es, ta, st, op, tr, iv, fa, fn, pr, cv, sp)

  def toJson(ir: ServiceIR): Json = ir.asJson

  def toPrettyString(ir: ServiceIR): String =
    ir.asJson.spaces2

  def fromJson(s: String): Either[Error, ServiceIR] =
    io.circe.parser.decode[ServiceIR](s)
