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
    serverName: String,
    databaseName: String,
    properties: List[DriverProperty],
    maxConcurrency: Option[Int],
    maxLifetime: Option[FiniteDuration]) {

  import ConnectionConfig._

  def sanitized: ConnectionConfig = {
    val sanitizedProps = properties map {
      case DriverProperty(name, value) if RoleProps(name) =>
        // Roles may include a role password via 'name|password',
        // so we need to redact the password, if present
        val parts = value.split('|')
        DriverProperty(name, if (parts.length > 1) s"${parts(0)}|$Redacted" else parts(0))

      case DriverProperty(name, _) if SensitiveProps(name) =>
        DriverProperty(name, Redacted)

      case other => other
    }

    copy(properties = sanitizedProps)
  }

  def validated: ValidatedNel[String, ConnectionConfig] = {
    val invalidProps = properties collect {
      case DriverProperty(name, _) if !ConfigurableProps(name) => name
    }

    Validated.condNel(
      invalidProps.isEmpty,
      this,
      invalidProps.mkString("Unsupported properties: ", ", ", ""))
  }

  def asJdbcUrl: String = {
    val nonProps = s"jdbc:ingres://$serverName/$databaseName"
    (nonProps :: properties.map(_.forUrl)).mkString(";")
  }
}

object ConnectionConfig {
  private val Pattern: Regex = "jdbc:ingres://([^/]+)/([^;]+)(?:;(.*))?".r

  val RoleProps: Set[String] =
    Set("role", "ROLE")

  /** Properties having values that should never be displayed. */
  val SensitiveProps: Set[String] =
    Set("dbms_password", "DBPWD", "password", "PWD") ++ RoleProps

  /** The configurable Avalanche driver properties. */
  val ConfigurableProps: Set[String] =
    Set(
      "user", "UID",
      "group", "GRP",
      "dbms_user", "DBUSR",
      "compression", "COMPRESS",
      "vnode_usage", "VNODE",
      "encryption", "ENCRYPT",
      "char_encode", "ENCODE"
    ) ++ SensitiveProps

  object Optics {
    val serverName: Lens[ConnectionConfig, String] =
      Lens[ConnectionConfig, String](_.serverName)(n => _.copy(serverName = n))

    val databaseName: Lens[ConnectionConfig, String] =
      Lens[ConnectionConfig, String](_.databaseName)(n => _.copy(databaseName = n))

    val properties: Lens[ConnectionConfig, List[DriverProperty]] =
      Lens[ConnectionConfig, List[DriverProperty]](_.properties)(ps => _.copy(properties = ps))

    val driverProperties: Traversal[ConnectionConfig, DriverProperty] =
      properties.composeTraversal(Traversal.fromTraverse[List, DriverProperty])

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
        maxLifetime = maxLifetimeSecs.map(_.seconds)

        urlCursor = cursor --\ "jdbcUrl"

        jdbcUrl <- urlCursor.as[String]

        (server, db, propStr) <- jdbcUrl match {
          case Pattern(srv, db, ps) => DecodeResult.ok((srv, db, ps))
          case _ => DecodeResult.fail("Malformed JDBC URL", urlCursor.history)
        }

        separated = Option(propStr).fold[List[String]](Nil)(_.split(';').toList)

        properties <- separated traverse {
          case DriverProperty.AttrValue(name, value) =>
            DecodeResult.ok(DriverProperty(name, value))

          case _ =>
            DecodeResult.fail[DriverProperty]("Malformed driver property", urlCursor.history)
        }
      } yield ConnectionConfig(server, db, properties, maxConcurrency, maxLifetime))

  implicit val connectionConfigEq: Eq[ConnectionConfig] =
    Eq.by(cc => (
      cc.serverName,
      cc.databaseName,
      cc.properties,
      cc.maxConcurrency,
      cc.maxLifetime))

  implicit val connectionConfigShow: Show[ConnectionConfig] =
    Show show { cc =>
      s"ConnectionConfig(${cc.asJdbcUrl}, ${cc.maxConcurrency}, ${cc.maxLifetime})"
    }
}
