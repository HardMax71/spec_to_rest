package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

// Boundary-enforceable reduction of a string refinement: the extracted
// constraint walker plus arity-1 matches-predicate inlining. Anything the
// reduction cannot express (opaque predicates, filter helpers) is dropped,
// never enforced, so emitters using this stay sound but not complete.
object StringRefinements:

  final case class Reduced(
      minLen: Option[BigInt],
      maxLen: Option[BigInt],
      patterns: List[String]
  ):
    def isEmpty: Boolean = minLen.isEmpty && maxLen.isEmpty && patterns.isEmpty
    def merge(other: Reduced): Reduced =
      Reduced(
        List(minLen, other.minLen).flatten.maxOption,
        List(maxLen, other.maxLen).flatten.minOption,
        (patterns ++ other.patterns).distinct
      )

  def inlineMatchesPredicate(name: String, ir: ServiceIRFull): Option[String] =
    svcPredicates(ir)
      .find(p => prdName(p) == name)
      .filter(pr => prdParams(pr).size == 1)
      .flatMap: pr =>
        matchesIdentityShape(prdBody(pr), prmName(prdParams(pr).head))

  def fullMatch(pattern: String): String = s"^(?:$pattern)$$"

  // Regex engines applied with search semantics need anchoring; multiple
  // patterns conjoin as anchored lookaheads.
  def combinedPattern(patterns: List[String]): Option[String] = patterns match
    case Nil      => None
    case p :: Nil => Some(fullMatch(p))
    case many     => Some("^" + many.map(p => s"(?=(?:$p)$$)").mkString + ".*$")

  def reduce(c: Option[expr], ir: ServiceIRFull): Reduced =
    c match
      case None => Reduced(None, None, Nil)
      case Some(e) =>
        val (raw, skips) = walkStringConstraint(e)
        val inlined = skips.foldLeft(raw): (acc, name) =>
          inlineMatchesPredicate(name, ir) match
            case Some(p) =>
              mergeStringConstraint(acc, StringConstraint(None, None, List(p), Nil, Nil))
            case None => acc
        inlined match
          case StringConstraint(mn, mx, pats, _, _) => Reduced(mn, mx, pats)

  // A field's enforceable constraint: its declared type's alias refinement
  // (one alias level, matching the domain-lookup convention) merged with any
  // inline where-clause on the field itself.
  def reduceField(t: type_expr, inlineWhere: Option[expr], ir: ServiceIRFull): Reduced =
    val aliasPart = t match
      case OptionTypeF(inner, _) => reduceField(inner, None, ir)
      case NamedTypeF(name, _) =>
        svcTypeAliases(ir).find(a => talName(a) == name) match
          case Some(alias) => reduce(talConstraint(alias), ir)
          case None        => Reduced(None, None, Nil)
      case _ => Reduced(None, None, Nil)
    aliasPart.merge(reduce(inlineWhere, ir))
