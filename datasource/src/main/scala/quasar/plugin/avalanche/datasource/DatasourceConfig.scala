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

import quasar.plugin.avalanche.ConnectionConfig

import scala.{Boolean, StringContext}

import argonaut._, Argonaut._

import cats.{Eq, Show}
import cats.implicits._

final case class DatasourceConfig(connection: ConnectionConfig) {
  def isSensitive: Boolean =
    this =!= sanitized

  def mergeSensitive(other: DatasourceConfig): DatasourceConfig =
    copy(connection = connection.mergeSensitive(other.connection))

  def sanitized: DatasourceConfig =
    copy(connection = connection.sanitized)
}

object DatasourceConfig {
  implicit val datasourceConfigCodecJson: CodecJson[DatasourceConfig] =
    casecodec1(DatasourceConfig.apply, DatasourceConfig.unapply)("connection")

  implicit val datasourceConfigEq: Eq[DatasourceConfig] =
    Eq.by(_.connection)

  implicit val datasourceConfigShow: Show[DatasourceConfig] =
    Show.show(c => s"DatasourceConfig(${c.connection.show})")
}
