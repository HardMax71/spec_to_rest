package specrest.codegen.go

import specrest.convention.StringRefinements
import specrest.ir.Naming
import specrest.profile.ProfiledField

// Emits Validate() methods on request models from the spec's string
// refinements, the same reduction the python schemas and the test oracle use.
// Length bounds count runes (spec len is characters, go len is bytes), and
// each pattern anchors and checks separately because RE2 has no lookaheads.
object GoValidation:

  final case class FieldRule(field: ProfiledField, reduced: StringRefinements.Reduced)

  final case class StructValidation(
      structName: String,
      patternVars: List[String],
      funcText: String,
      needsUtf8: Boolean
  )

  private def pascal(name: String): String =
    Naming.toPascalCase(name, Naming.PascalStrategy.Go)

  private def patternVar(structName: String, fieldName: String, idx: Int): String =
    val suffix = if idx == 0 then "" else (idx + 1).toString
    Naming.toCamelCase(structName, Naming.CamelStrategy.Plain) + pascal(fieldName) +
      s"Pattern$suffix"

  def forStruct(structName: String, rules: List[FieldRule]): Option[StructValidation] =
    val active = rules.filter(r => !r.reduced.isEmpty && r.field.domainType == "string")
    Option.when(active.nonEmpty):
      val patternVars = List.newBuilder[String]
      val body        = new StringBuilder
      val needsUtf8 =
        active.exists(r => r.reduced.minLen.isDefined || r.reduced.maxLen.isDefined)
      for r <- active do
        val goField = pascal(r.field.fieldName)
        val access  = if r.field.nullable then s"*b.$goField" else s"b.$goField"
        val checks  = new StringBuilder
        r.reduced.minLen.foreach { n =>
          checks ++= s"\tif utf8.RuneCountInString($access) < $n {\n"
          checks ++= s"\t\treturn fmt.Errorf(\"${r.field.fieldName}: shorter than minimum length $n\")\n\t}\n"
        }
        r.reduced.maxLen.foreach { n =>
          checks ++= s"\tif utf8.RuneCountInString($access) > $n {\n"
          checks ++= s"\t\treturn fmt.Errorf(\"${r.field.fieldName}: longer than maximum length $n\")\n\t}\n"
        }
        r.reduced.patterns.zipWithIndex.foreach { (p, i) =>
          val v = patternVar(structName, r.field.fieldName, i)
          patternVars += s"var $v = regexp.MustCompile(`${StringRefinements.fullMatch(p)}`)"
          checks ++= s"\tif !$v.MatchString($access) {\n"
          checks ++= s"\t\treturn fmt.Errorf(\"${r.field.fieldName}: does not match the required pattern\")\n\t}\n"
        }
        if r.field.nullable then
          val indented = checks.toString.linesIterator.map("\t" + _).mkString("\n")
          body ++= s"\tif b.$goField != nil {\n$indented\n\t}\n"
        else body ++= checks.toString
      val func =
        s"func (b $structName) Validate() error {\n${body.toString}\treturn nil\n}"
      StructValidation(structName, patternVars.result(), func, needsUtf8)
