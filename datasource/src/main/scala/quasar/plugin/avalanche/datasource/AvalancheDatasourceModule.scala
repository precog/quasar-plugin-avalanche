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

import scala._
import scala.collection.immutable.SortedSet
import scala.concurrent.duration._
import scala.util.Either

import java.lang.String
import java.net.URI
import java.util.UUID

import argonaut._, Argonaut._, ArgonautCats._

import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect._
import cats.implicits._

import doobie._

import quasar.RateLimiting
import quasar.api.datasource.{DatasourceType, DatasourceError}
import quasar.api.datasource.DatasourceError.ConfigurationError
import quasar.connector.{ByteStore, MonadResourceErr, ExternalCredentials}
import quasar.connector.datasource.{LightweightDatasourceModule, Reconfiguration}
import quasar.lib.jdbc.{JdbcDiscovery, Redacted, TableType, TransactorConfig}
import quasar.lib.jdbc.JdbcDriverConfig.JdbcDriverManagerConfig
import quasar.lib.jdbc.datasource.JdbcDatasourceModule

import org.slf4s.Logger

import scalaz.{NonEmptyList => ZNel}

object AvalancheDatasourceModule extends JdbcDatasourceModule[DatasourceConfig] {

  val DefaultConnectionMaxConcurrency: Int = 8
  // Avalanche cloud appears to terminate idle connections after 4 minutes
  val DefaultConnectionMaxLifetime: FiniteDuration = 3.minutes + 30.seconds

  val kind = DatasourceType("avalanche", 1L)

  val DiscoverableTableTypes: Option[ConnectionIO[NonEmptySet[TableType]]] =
    Some(for {
      catalog <- HC.getCatalog
      rs <- HC.getMetaData(FDMD.getTableTypes)
      names <- FC.embed(rs, HRS.build[SortedSet, String])
      pruned = names.filterNot(_ == "SYSTEM TABLE")
      default = NonEmptySet.of("TABLE", "VIEW")
      discoverable = NonEmptySet.fromSet(pruned) getOrElse default
    } yield discoverable.map(TableType(_)))

  def transactorConfig(config: DatasourceConfig): Either[NonEmptyList[String], TransactorConfig] =
    for {
      cc <- config.connection.validated.toEither

      jdbcUrl <-
        Either.catchNonFatal(new URI(cc.asJdbcUrl))
          .leftMap(_ => NonEmptyList.one("JDBC URL is not a valid URI"))

      driverCfg = JdbcDriverManagerConfig(jdbcUrl, Some("com.ingres.jdbc.IngresDriver"))

      maxConcurrency =
        cc.maxConcurrency getOrElse DefaultConnectionMaxConcurrency

      maxLifetime =
        cc.maxLifetime getOrElse DefaultConnectionMaxLifetime

    } yield {
      val tc = TransactorConfig
        .withDefaultTimeouts(
          driverConfig = driverCfg,
          connectionMaxConcurrency = maxConcurrency,
          connectionReadOnly = true)
      tc.copy(poolConfig = tc.poolConfig.map(_.copy(connectionMaxLifetime = maxLifetime)))
    }

  def sanitizeConfig(config: Json): Json =
    config.as[DatasourceConfig].toOption
      .fold(jEmptyObject)(_.sanitized.asJson)


  def migrateConfig[F[_]: Sync](from: Long, to: Long, config: Json): F[Either[ConfigurationError[Json], Json]] =
     Sync[F].pure(Right(config))

  def reconfigure(original: Json, patch: Json): Either[ConfigurationError[Json], (Reconfiguration, Json)] = {
    def decodeCfg(js: Json, name: String): Either[ConfigurationError[Json], DatasourceConfig] =
      js.as[DatasourceConfig].toEither.leftMap { case (m, c) =>
        DatasourceError.MalformedConfiguration(
          kind,
          jString(Redacted),
          s"Failed to decode $name config JSON at ${c.toList.map(_.show).mkString(", ")}")
      }

    for {
      prev <- decodeCfg(original, "original")
      next <- decodeCfg(patch, "new")

      result <-
        if (next.isSensitive)
          Left(DatasourceError.InvalidConfiguration(
            kind,
            next.sanitized.asJson,
            ZNel("New configuration contains sensitive information.")))
        else
          Right(next.mergeSensitive(prev))

    } yield (Reconfiguration.Reset, result.asJson)
  }

  def jdbcDatasource[F[_]: ConcurrentEffect: ContextShift: MonadResourceErr: Timer, A](
      config: DatasourceConfig,
      transactor: Transactor[F],
      rateLimiter: RateLimiting[F, A],
      byteStore: ByteStore[F],
      getAuth: UUID => F[Option[ExternalCredentials[F]]],
      log: Logger)
      : Resource[F, Either[InitError, LightweightDatasourceModule.DS[F]]] = {

    val discovery = JdbcDiscovery(DiscoverableTableTypes)

    AvalancheDatasource(transactor, discovery, log)
      .asRight[InitError]
      .pure[Resource[F, ?]]
  }
}
