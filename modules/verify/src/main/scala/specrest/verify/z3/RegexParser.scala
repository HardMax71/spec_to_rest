package specrest.verify.z3

import scala.util.boundary
import scala.util.boundary.Label
import scala.util.boundary.break

private[z3] object RegexParser:

  def parse(raw: String): Option[Z3Regex] =
    boundary:
      val s        = strip(raw)
      val (r, pos) = Parser(s).alt(0)
      if pos >= s.length then Some(r) else break(None)

  private def strip(s: String): String =
    val a =
      if s.length >= 2 && s.head == '/' && s.last == '/' then s.substring(1, s.length - 1)
      else s
    val b = if a.startsWith("^") then a.drop(1) else a
    if b.endsWith("$") && !b.endsWith("\\$") then b.dropRight(1) else b

  final private class Parser(s: String)(using Label[Option[Z3Regex]]):
    private def fail(): Nothing      = break(None)
    private def has(p: Int): Boolean = p < s.length
    private def at(p: Int): Char     = s.charAt(p)

    def alt(p0: Int): (Z3Regex, Int) =
      val (first, p1) = concat(p0)
      def loop(acc: List[Z3Regex], p: Int): (List[Z3Regex], Int) =
        if has(p) && at(p) == '|' then
          val (c, p2) = concat(p + 1)
          loop(c :: acc, p2)
        else (acc.reverse, p)
      val (rest, pEnd) = loop(Nil, p1)
      rest match
        case Nil => (first, pEnd)
        case _   => (Z3Regex.Union(first :: rest), pEnd)

    private def concat(p0: Int): (Z3Regex, Int) =
      def loop(acc: List[Z3Regex], p: Int): (List[Z3Regex], Int) =
        if has(p) && at(p) != '|' && at(p) != ')' then
          val (q, p2) = quantified(p)
          loop(q :: acc, p2)
        else (acc.reverse, p)
      val (parts, pEnd) = loop(Nil, p0)
      parts match
        case Nil      => (Z3Regex.Str(""), pEnd)
        case h :: Nil => (h, pEnd)
        case xs       => (Z3Regex.Concat(xs), pEnd)

    private def quantified(p0: Int): (Z3Regex, Int) =
      val (a, p1) = atom(p0)
      if has(p1) then
        at(p1) match
          case '*' => (Z3Regex.Star(a), p1 + 1)
          case '+' => (Z3Regex.Plus(a), p1 + 1)
          case '?' => (Z3Regex.Opt(a), p1 + 1)
          case _   => (a, p1)
      else (a, p1)

    private def atom(p: Int): (Z3Regex, Int) =
      at(p) match
        case '('                         => group(p)
        case '['                         => charClass(p)
        case '.'                         => (Z3Regex.AnyChar, p + 1)
        case '\\'                        => escaped(p + 1)
        case '*' | '+' | '?' | '{' | '}' => fail()
        case '^' | '$'                   => fail()
        case c                           => (Z3Regex.Str(c.toString), p + 1)

    private def group(p0: Int): (Z3Regex, Int) =
      val p1 = p0 + 1
      if has(p1) && at(p1) == '?' then fail()
      val (r, p2) = alt(p1)
      if !has(p2) || at(p2) != ')' then fail()
      (r, p2 + 1)

    private def escaped(p: Int): (Z3Regex, Int) =
      if !has(p) then fail()
      val r = at(p) match
        case 'd' => Z3Regex.Range('0', '9')
        case 'w' =>
          Z3Regex.Union(
            List(
              Z3Regex.Range('a', 'z'),
              Z3Regex.Range('A', 'Z'),
              Z3Regex.Range('0', '9'),
              Z3Regex.Str("_")
            )
          )
        case 's' =>
          Z3Regex.Union(List(
            Z3Regex.Str(" "),
            Z3Regex.Str("\t"),
            Z3Regex.Str("\n"),
            Z3Regex.Str("\r")
          ))
        case c => Z3Regex.Str(c.toString)
      (r, p + 1)

    private def charClass(p0: Int): (Z3Regex, Int) =
      val (neg, p1) =
        if has(p0 + 1) && at(p0 + 1) == '^' then (true, p0 + 2) else (false, p0 + 1)
      def loop(acc: List[Z3Regex], p: Int): (List[Z3Regex], Int) =
        if !has(p) then fail()
        else if at(p) == ']' then (acc.reverse, p)
        else
          val (lo, pa) = classChar(p)
          if has(pa) && at(pa) == '-' && has(pa + 1) && at(pa + 1) != ']' then
            val (hi, pb) = classChar(pa + 1)
            loop(Z3Regex.Range(lo, hi) :: acc, pb)
          else loop(Z3Regex.Str(lo.toString) :: acc, pa)
      val (items, pEnd) = loop(Nil, p1)
      val union = items match
        case Nil      => fail()
        case h :: Nil => h
        case xs       => Z3Regex.Union(xs)
      val result = if neg then Z3Regex.Inter(List(Z3Regex.AnyChar, Z3Regex.Comp(union))) else union
      (result, pEnd + 1)

    private def classChar(p: Int): (Char, Int) =
      if at(p) == '\\' then
        if !has(p + 1) then fail()
        (at(p + 1), p + 2)
      else (at(p), p + 1)
