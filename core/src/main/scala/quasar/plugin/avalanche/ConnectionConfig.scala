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

package quasar.plugin.avalanche

import scala._, Predef._
import scala.concurrent.duration._
import scala.util.matching.Regex

import java.lang.String

import argonaut._, Argonaut._, ArgonautCats._

import cats.{Eq, Show}
import cats.data.{Validated, ValidatedNel}
import cats.implicits._

import monocle.{Lens, Traversal}

import quasar.plugin.jdbc.Redacted

import shims.traverseToScalaz

/** Avalanche connection configuration.
  *
  * @see https://docs.actian.com/avalanche/index.html#page/Connectivity%2FJDBC_Driver_Properties.htm%23
  */
final case class ConnectionConfig(
    jdbcUrl: String,
    maxConcurrency: Option[Int],
    maxLifetime: Option[FiniteDuration]) {

  import ConnectionConfig._

  /** Merges sensitive information from `other` that isn't defined here. */
  def mergeSensitive(other: ConnectionConfig): ConnectionConfig = {
    other
  }

  def sanitized: ConnectionConfig = {
    this
  }

  def validated: ValidatedNel[String, ConnectionConfig] = {
    val invalidProps = List()

    Validated.condNel(
      invalidProps.isEmpty,
      this,
      invalidProps.mkString("Unsupported properties: ", ", ", ""))
  }

  def asJdbcUrl: String = {
    jdbcUrl
  }
}

object ConnectionConfig {
  object Optics {
    val jdbcUrl: Lens[ConnectionConfig, String] =
      Lens[ConnectionConfig, String](_.jdbcUrl)(n => _.copy(jdbcUrl = n))

    val maxConcurrency: Lens[ConnectionConfig, Option[Int]] =
      Lens[ConnectionConfig, Option[Int]](_.maxConcurrency)(n => _.copy(maxConcurrency = n))

    val maxLifetime: Lens[ConnectionConfig, Option[FiniteDuration]] =
      Lens[ConnectionConfig, Option[FiniteDuration]](_.maxLifetime)(d => _.copy(maxLifetime = d))
  }

  implicit val connectionConfigCodecJson: CodecJson[ConnectionConfig] =
    CodecJson(
      cc =>
        ("jdbcUrl" := cc.asJdbcUrl) ->:
        ("maxConcurrency" :=? cc.maxConcurrency) ->?:
        ("maxLifetimeSecs" :=? cc.maxLifetime.map(_.toSeconds)) ->?:
        jEmptyObject,

      cursor => for {
        maxConcurrency <- (cursor --\ "maxConcurrency").as[Option[Int]]
        maxLifetimeSecs <- (cursor --\ "maxLifetimeSecs").as[Option[Int]]
        jdbcUrl <- (cursor --\ "jdbcUrl").as[String]
        maxLifetime = maxLifetimeSecs.map(_.seconds)

      } yield ConnectionConfig(jdbcUrl, maxConcurrency, maxLifetime))

  implicit val connectionConfigEq: Eq[ConnectionConfig] =
    Eq.by(cc => (
      cc.jdbcUrl,
      cc.maxConcurrency,
      cc.maxLifetime))

  implicit val connectionConfigShow: Show[ConnectionConfig] =
    Show show { cc =>
      s"ConnectionConfig(${cc.asJdbcUrl}, ${cc.maxConcurrency}, ${cc.maxLifetime})"
    }
}
