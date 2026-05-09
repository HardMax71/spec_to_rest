package specrest.synth

import specrest.convention.dafny.DafnyMethodHeader

object SkeletonGenerator:

  def fallbackBody(
      header: DafnyMethodHeader,
      attempts: Int,
      finalStrategy: String,
      finalModel: String,
      reason: String
  ): String =
    val outs       = parseOutParams(header.signature)
    val payload    = formatPayload(header.name, attempts, finalStrategy, finalModel, reason)
    val expectLine = s"""expect false, "$payload";"""
    val havocLines = outs.map((n, _) => s"$n := *;")
    (expectLine :: havocLines).mkString("\n")

  private def formatPayload(
      opName: String,
      attempts: Int,
      finalStrategy: String,
      finalModel: String,
      reason: String
  ): String =
    val sanitized = reason.replace('"', '\'').replace('\n', ' ').take(200)
    s"FALLBACK SKELETON [op=$opName]: not verified — " +
      s"attempts=$attempts strategy=$finalStrategy model=$finalModel reason=$sanitized"

  private def parseOutParams(signature: String): List[(String, String)] =
    val idx = signature.indexOf("returns")
    if idx < 0 then Nil
    else
      val tail   = signature.substring(idx + "returns".length).trim
      val opened = tail.indexOf('(')
      if opened < 0 then Nil
      else
        val closed = matchingParen(tail, opened)
        if closed < 0 then Nil
        else
          val inside = tail.substring(opened + 1, closed)
          splitTopLevelCommas(inside).flatMap: chunk =>
            val colon = chunk.indexOf(':')
            if colon < 0 then None
            else
              val name = chunk.substring(0, colon).trim
              val ty   = chunk.substring(colon + 1).trim
              if name.isEmpty || ty.isEmpty then None else Some(name -> ty)

  private def matchingParen(s: String, openIdx: Int): Int =
    @scala.annotation.tailrec
    def loop(i: Int, depth: Int): Int =
      if i >= s.length then -1
      else
        val c = s.charAt(i)
        if c == '(' || c == '<' || c == '[' then loop(i + 1, depth + 1)
        else if c == ')' || c == '>' || c == ']' then
          val next = depth - 1
          if next == 0 && c == ')' then i
          else loop(i + 1, next)
        else loop(i + 1, depth)
    loop(openIdx, 0)

  private def splitTopLevelCommas(s: String): List[String] =
    final case class S(out: List[String], cur: String, depth: Int)
    val folded = s.foldLeft(S(Nil, "", 0)): (st, c) =>
      val cs = c.toString
      if c == '(' || c == '<' || c == '[' then S(st.out, st.cur + cs, st.depth + 1)
      else if c == ')' || c == '>' || c == ']' then S(st.out, st.cur + cs, st.depth - 1)
      else if c == ',' && st.depth == 0 then S(st.out :+ st.cur.trim, "", st.depth)
      else S(st.out, st.cur + cs, st.depth)
    val finalOut =
      if folded.cur.nonEmpty then folded.out :+ folded.cur.trim else folded.out
    finalOut.filter(_.nonEmpty)
