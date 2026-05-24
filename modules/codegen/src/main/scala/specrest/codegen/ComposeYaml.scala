package specrest.codegen

import specrest.codegen.Compose.DependsCondition
import specrest.codegen.Compose.File
import specrest.codegen.Compose.Restart
import specrest.codegen.Compose.Service

object ComposeYaml:

  private val Indent = "  "

  def render(f: File): String =
    val sb = new StringBuilder
    f.header.foreach(sb.append)
    sb.append("services:\n")
    sb.append(f.services.map(renderService).mkString("\n"))
    if f.volumes.nonEmpty then
      sb.append("\nvolumes:\n")
      f.volumes.foreach(v => sb.append(s"$Indent$v:\n"))
    sb.toString

  private def renderService(s: Service): String =
    val out = new StringBuilder
    out.append(s"$Indent${s.name}:\n")
    val body = Indent * 2
    s.build.foreach(b => out.append(s"${body}build: $b\n"))
    s.image.foreach(i => out.append(s"${body}image: $i\n"))
    s.command.foreach: cmd =>
      out.append(s"${body}command: ${jsonStringArray(cmd)}\n")
    s.envFile.foreach(e => out.append(s"${body}env_file: $e\n"))
    if s.environment.nonEmpty then
      out.append(s"${body}environment:\n")
      s.environment.foreach((k, v) => out.append(s"${body}$Indent$k: $v\n"))
    if s.ports.nonEmpty then
      out.append(s"${body}ports:\n")
      s.ports.foreach(p => out.append(s"${body}$Indent- \"$p\"\n"))
    if s.volumes.nonEmpty then
      out.append(s"${body}volumes:\n")
      s.volumes.foreach(v => out.append(s"${body}$Indent- $v\n"))
    s.healthcheck.foreach: hc =>
      out.append(s"${body}healthcheck:\n")
      out.append(s"${body}${Indent}test: ${jsonStringArray(hc.test)}\n")
      out.append(s"${body}${Indent}interval: ${hc.interval}\n")
      out.append(s"${body}${Indent}timeout: ${hc.timeout}\n")
      out.append(s"${body}${Indent}retries: ${hc.retries}\n")
    if s.dependsOn.nonEmpty then
      out.append(s"${body}depends_on:\n")
      s.dependsOn.foreach: (svc, cond) =>
        out.append(s"${body}$Indent$svc:\n")
        out.append(s"${body}${Indent * 2}condition: ${conditionToString(cond)}\n")
    s.restart.foreach(r => out.append(s"${body}restart: ${restartToString(r)}\n"))
    s.deploy.foreach: d =>
      out.append(s"${body}deploy:\n")
      out.append(s"${body}${Indent}resources:\n")
      out.append(s"${body}${Indent * 2}limits:\n")
      out.append(s"${body}${Indent * 3}memory: ${d.limits.memory}\n")
      out.append(s"${body}${Indent * 3}cpus: '${d.limits.cpus}'\n")
    out.toString

  private def restartToString(r: Restart): String = r match
    case Restart.No            => "\"no\""
    case Restart.OnFailure     => "on-failure"
    case Restart.UnlessStopped => "unless-stopped"

  private def conditionToString(c: DependsCondition): String = c match
    case DependsCondition.Healthy               => "service_healthy"
    case DependsCondition.CompletedSuccessfully => "service_completed_successfully"

  private def jsonStringArray(xs: List[String]): String =
    xs.map(x => "\"" + x.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").mkString(
      "[",
      ", ",
      "]"
    )
