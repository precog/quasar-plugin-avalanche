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

import scala.StringContext

import java.lang.String
import java.text.ParseException

import argonaut._, Argonaut._

import org.specs2.mutable.Specification

import quasar.api.datasource.DatasourceError
import quasar.connector.datasource.Reconfiguration

object AvalancheDatasourceModuleSpec extends Specification {
  "reconfigure" >> {
    def unsafeJson(s: String): Json =
      s.parse.fold(err => throw new ParseException(err, -1), js => js)

    "fails if original malformed" >> {
      val orig = unsafeJson("""{"foobar": "baz"}""")
      val next = unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://example.com/db"}}""")

      AvalancheDatasourceModule
        .reconfigure(orig, next)
        .must(beLeft.like {
          case DatasourceError.MalformedConfiguration(_, _, m) =>
            m must contain("original")
        })
    }

    "fails if new malformed" >> {
      val orig = unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://example.com/db"}}""")
      val next = unsafeJson("""{"foobar": "baz"}""")

      AvalancheDatasourceModule
        .reconfigure(orig, next)
        .must(beLeft.like {
          case DatasourceError.MalformedConfiguration(_, _, m) =>
            m must contain("new")
        })
    }

    ConnectionConfig.SensitiveProps foreach { name =>
      s"fails if new contains '$name' property" >> {
        val orig = unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://example.com:123/db"}}""")
        val next = unsafeJson(s"""{"connection": {"jdbcUrl": "jdbc:ingres://other.example.com:123/db;$name=something"}}""")

      AvalancheDatasourceModule
        .reconfigure(orig, next)
        .must(beLeft.like {
          case DatasourceError.InvalidConfiguration(_, _, m) =>
            m.head.toLowerCase must (contain("new") and contain("sensitive"))
        })
      }
    }

    ConnectionConfig.RoleProps foreach { name =>
      s"fails if new contains '$name' with a password" >> {
        val orig = unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://example.com:123/db"}}""")
        val next = unsafeJson(s"""{"connection": {"jdbcUrl": "jdbc:ingres://other.example.com:123/db;$name=someName|somePass"}}""")

      AvalancheDatasourceModule
        .reconfigure(orig, next)
        .must(beLeft.like {
          case DatasourceError.InvalidConfiguration(_, _, m) =>
            m.head.toLowerCase must (contain("new") and contain("sensitive"))
        })
      }
    }

    "merges original sensitive properties with new" >> {
      val orig = unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://example.com:123/db;UID=alice;PWD=secret"}}""")
      val next = unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://other.example.com:123/db;UID=alice"}}""")

      val expected =
        unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://other.example.com:123/db;UID=alice;PWD=secret"}}""")

      AvalancheDatasourceModule.reconfigure(orig, next) must beRight((Reconfiguration.Reset, expected))
    }

    "new role without password is not replaced" >> {
      val orig = unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://example.com:123/db;UID=alice;PWD=secret;ROLE=admin|root"}}""")
      val next = unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://other.example.com:123/db;UID=alice;role=user"}}""")

      val expected =
        unsafeJson("""{"connection": {"jdbcUrl": "jdbc:ingres://other.example.com:123/db;UID=alice;role=user;PWD=secret"}}""")

      AvalancheDatasourceModule.reconfigure(orig, next) must beRight((Reconfiguration.Reset, expected))
    }
  }
}
