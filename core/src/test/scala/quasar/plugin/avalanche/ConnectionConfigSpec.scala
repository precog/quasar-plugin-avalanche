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

import scala._
import scala.concurrent.duration._

import argonaut._, Argonaut._

import cats.implicits._

import org.specs2.mutable.Specification

import quasar.plugin.jdbc.Redacted

object ConnectionConfigSpec extends Specification {
  val CC = ConnectionConfig.Optics
  val DP = DriverProperty.Optics

  "JSON codec" >> {
    val fullConfig =
      ConnectionConfig(
        "example.com:12354", "db",
        List(
          DriverProperty("UID", "alice"),
          DriverProperty("PWD", "secret"),
          DriverProperty("encryption", "off")),
        Some(4), Some(30.seconds))

    def roundtrips(cc: ConnectionConfig) =
      cc.asJson.as[ConnectionConfig].toEither must beRight(cc)

    "roundtrips full config" >> {
      roundtrips(fullConfig)
    }

    "roundtrips no properties" >> {
      roundtrips(CC.properties.set(Nil)(fullConfig))
    }

    "maxConcurrency is optional" >> {
      roundtrips(CC.maxConcurrency.set(None)(fullConfig))
    }

    "maxLifetimeSecs is optional" >> {
      roundtrips(CC.maxLifetime.set(None)(fullConfig))
    }

    "malformed when no scheme" >> {
      val json = """
        { "jdbcUrl": "ingres://example.com/db1" }
      """

      json.decodeEither[ConnectionConfig] must beLeft(contain("JDBC URL"))
    }

    "malformed when no ingres scheme" >> {
      val json = """
        { "jdbcUrl": "jdbc:notingres://example.com/db1" }
      """

      json.decodeEither[ConnectionConfig] must beLeft(contain("JDBC URL"))
    }

    "malformed when no server" >> {
      val json = """
        { "jdbcUrl": "jdbc:ingres:///db1" }
      """

      json.decodeEither[ConnectionConfig] must beLeft(contain("JDBC URL"))
    }

    "malformed when no database" >> {
      val json = """
        { "jdbcUrl": "jdbc:ingres://server.example.com:1232" }
      """

      json.decodeEither[ConnectionConfig] must beLeft(contain("JDBC URL"))
    }

    "malformed when attr/value malformed" >> {
      val json = """
        { "jdbcUrl": "jdbc:ingres://server.example.com:1232/db1;foo" }
      """

      json.decodeEither[ConnectionConfig] must beLeft(contain("driver property"))
    }

    "property with no value is empty" >> {
      val json = """
        { "jdbcUrl": "jdbc:ingres://server.example.com:1232/db1;foo=" }
      """

      val expected =
        ConnectionConfig("server.example.com:1232", "db1", List(DriverProperty("foo", "")), None, None)

      json.decodeEither[ConnectionConfig] must beRight(expected)
    }

    "preserves property names and order" >> {
      val json = """
        { "jdbcUrl": "jdbc:ingres://server.example.com:1232/db1;UID=bob;password=guess;VNODE=connect" }
      """

      val expected =
        ConnectionConfig(
          "server.example.com:1232", "db1",
          List(
            DriverProperty("UID", "bob"),
            DriverProperty("password", "guess"),
            DriverProperty("VNODE", "connect")),
          None, None)

      json.decodeEither[ConnectionConfig] must beRight(expected)
    }

    "correctly handles '=' in property values" >> {
      val json = """
        { "jdbcUrl": "jdbc:ingres://server.example.com:1232/db1;PWD==oiur3==fkj;UID=alice" }
      """

      val expected =
        ConnectionConfig(
          "server.example.com:1232", "db1",
          List(
            DriverProperty("PWD", "=oiur3==fkj"),
            DriverProperty("UID", "alice")),
          None, None)

      json.decodeEither[ConnectionConfig] must beRight(expected)
    }
  }

  "sanitization" >> {
    val config =
      ConnectionConfig("example.com:12354", "db", Nil, Some(4), Some(30.seconds))

    val pvalues = CC.driverProperties.composeLens(DP.value)

    "redacts password properties" >> {
      val pwds = List("password", "PWD", "dbms_password", "DBPWD")
      val props = pwds.map(DriverProperty(_, "secret"))

      val actual =
        CC.properties.set(props)(config)

      val expected =
        pvalues.set(Redacted)(actual)

      actual.sanitized must_=== expected
    }

    val roleProps =
      ConnectionConfig.RoleProps.toList.map(DriverProperty(_, "someRole"))

    "does not redact role name without password" >> {
      val withRole = CC.properties.set(roleProps)(config)
      withRole.sanitized must_=== withRole
    }

    "redacts role password, if present" >> {
      val withRolePw =
        CC.properties.set(roleProps.map(DP.value.set("admins|s3kre7")))(config)

      val expected =
        pvalues.set(s"admins|$Redacted")(withRolePw)

      withRolePw.sanitized must_=== expected
    }
  }

  "validation" >> {
    "rejects unsupported properties" >> {
      val c =
        ConnectionConfig(
          "example.com:12354", "db",
          List(
            DriverProperty("user", "alice"),
            DriverProperty("allowRootAccess", "true"),
            DriverProperty("encryption", "on"),
            DriverProperty("disableSecurity", "true")),
          None, None)

      c.validated.toEither.leftMap(_.toList) must beLeft(exactly(contain("allowRootAccess") and contain("disableSecurity")))
    }
  }
}
