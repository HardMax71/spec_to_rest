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
      funcText: String,
      needsUtf8: Boolean
  )

  final case class FileValidations(
      patternVars: List[String],
      structs: List[StructValidation]
  ):
    def needsUtf8: Boolean   = structs.exists(_.needsUtf8)
    def needsRegexp: Boolean = patternVars.nonEmpty
    def isEmpty: Boolean     = structs.isEmpty

  private def pascal(name: String): String =
    Naming.toPascalCase(name, Naming.PascalStrategy.Go)

  // One compiled var per distinct pattern text, shared across structs and
  // fields, so identical refinements cannot drift apart when edited. The
  // prefix must be unique per file: every entity's models file shares the one
  // go package, so bare names would collide across entities.
  def forStructs(prefix: String, structs: List[(String, List[FieldRule])]): FileValidations =
    val pool = scala.collection.mutable.LinkedHashMap.empty[String, String]
    def poolVar(anchored: String): String =
      pool.getOrElseUpdate(
        anchored,
        s"${Naming.toCamelCase(prefix, Naming.CamelStrategy.Plain)}ValidationPattern${pool.size + 1}"
      )
    val validations = structs.flatMap { (structName, rules) =>
      val active = rules.filter(r => !r.reduced.isEmpty && r.field.domainType == "string")
      Option.when(active.nonEmpty):
        val body = new StringBuilder
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
          r.reduced.patterns.foreach { p =>
            val v = poolVar(StringRefinements.fullMatch(p))
            checks ++= s"\tif !$v.MatchString($access) {\n"
            checks ++= s"\t\treturn fmt.Errorf(\"${r.field.fieldName}: does not match the required pattern\")\n\t}\n"
          }
          if r.field.nullable then
            val indented = checks.toString.linesIterator.map("\t" + _).mkString("\n")
            body ++= s"\tif b.$goField != nil {\n$indented\n\t}\n"
          else body ++= checks.toString
        val func =
          s"func (b $structName) Validate() error {\n${body.toString}\treturn nil\n}"
        StructValidation(structName, func, needsUtf8)
    }
    val vars = pool.toList.map((pat, name) => s"var $name = regexp.MustCompile(`$pat`)")
    FileValidations(vars, validations)
