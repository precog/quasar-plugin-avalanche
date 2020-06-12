/*
 * Copyright 2020 Precog Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.plugin.avalanche.datasource

import quasar.plugin.avalanche._

import scala._, Predef._

import cats.implicits._

import doobie.ConnectionIO

import quasar.ScalarStages
import quasar.api.ColumnType
import quasar.plugin.jdbc._
import quasar.plugin.jdbc.datasource._

private[datasource] object MaskInterpreter {
  type I = AvalancheHygiene.HygienicIdent

  def apply(discovery: JdbcDiscovery)
      : JdbcLoader.Args[I] => ConnectionIO[(I, Option[I], ColumnSelection[I], ScalarStages)] = {
    case (table, schema, stages) =>
      columnScalars(discovery, table, schema) map { scalars =>
        val (selection, nextStages) =
          MaskedScalarColumns((n, ts) => scalars.get(n).exists(ts(_)))(stages)

        (table, schema, selection.map(AvalancheHygiene.hygienicIdent), nextStages)
      }
  }

  ////

  private def columnScalars(discovery: JdbcDiscovery, table: I, schema: Option[I])
      : ConnectionIO[Map[ColumnName, ColumnType.Scalar]] =
    discovery.tableColumns(table.asIdent, schema.map(_.asIdent))
      .map(m =>
        Types.AvalancheColumnTypes.get(m.vendorType)
          .orElse(Types.JdbcColumnTypes.get(m.jdbcType))
          .tupleLeft(m.name))
      .unNone
      .compile.to(Map)
}
