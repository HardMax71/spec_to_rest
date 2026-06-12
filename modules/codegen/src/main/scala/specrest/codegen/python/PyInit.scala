package specrest.codegen.python

import specrest.codegen.ScalarOps
import specrest.ir.Naming
import specrest.profile.ProfiledService

object PyInit:

  private def render(imports: List[(String, List[String])], header: List[String] = Nil): String =
    val sorted = imports.sortBy(_._1)
    val lines  = header ++ sorted.map(_._1)
    val exports = (header.map(_.split(" import ").last) ++ sorted.flatMap(_._2))
      .map(n => s"    \"$n\",")
    s"${lines.mkString("\n")}\n\n__all__ = [\n${exports.mkString("\n")}\n]\n"

  def models(profiled: ProfiledService): String =
    val entityImports = profiled.entities.map: e =>
      s"from app.models.${Naming.toSnakeCase(e.entityName)} import ${e.modelClassName}" ->
        List(e.modelClassName)
    val state =
      if ScalarOps.stateFields(profiled).nonEmpty then
        List("from app.models.service_state import ServiceState" -> List("ServiceState"))
      else Nil
    render(entityImports ++ state, header = List("from app.db.base import Base"))

  def schemas(profiled: ProfiledService): String =
    val imports = profiled.entities.map: e =>
      val names = List(e.createSchemaName, e.readSchemaName, e.updateSchemaName)
      s"from app.schemas.${Naming.toSnakeCase(e.entityName)} import (\n${names
          .map(n => s"    $n,")
          .mkString("\n")}\n)" -> names
    render(imports)

  def routers(profiled: ProfiledService): String =
    val modules = ("admin" ::
      profiled.entities.map(e => Naming.toSnakeCase(Naming.pluralize(e.entityName))) :::
      (if ScalarOps.views(profiled).nonEmpty then List("state_ops") else Nil)).sorted
    val exports = modules.map(m => s"    \"$m\",").mkString("\n")
    s"from app.routers import ${modules.mkString(", ")}\n\n__all__ = [\n$exports\n]\n"

  def services(profiled: ProfiledService): String =
    val entityImports = profiled.entities.map: e =>
      val svc = s"${e.entityName}Service"
      s"from app.services.${Naming.toSnakeCase(e.entityName)} import $svc" -> List(svc)
    val state =
      if ScalarOps.views(profiled).nonEmpty then
        List("from app.services.state_ops import StateOpsService" -> List("StateOpsService"))
      else Nil
    render(entityImports ++ state)
