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

import scala.{Stream => _, _}, Predef.classOf
import scala.annotation.switch

import java.net.InetAddress
import java.nio.ByteBuffer
import java.sql.ResultSet
import java.time._
import java.util.UUID

import cats.effect.Resource
import cats.implicits._

import doobie._
import doobie.enum.JdbcType
import doobie.implicits._

import fs2.Stream

import quasar.ScalarStages
import quasar.common.data.{QDataRValue, RValue}
import quasar.connector.QueryResult
import quasar.connector.datasource.BatchLoader
import quasar.plugin.jdbc._
import quasar.plugin.jdbc.datasource._

private[datasource] object AvalancheLoader {
  type I = AvalancheHygiene.HygienicIdent
  type Args = (I, Option[I], ColumnSelection[I], ScalarStages)

  def apply(logHandler: LogHandler, resultChunkSize: Int)
      : BatchLoader[Resource[ConnectionIO, ?], Args, QueryResult[ConnectionIO]] =
    BatchLoader.Full[Resource[ConnectionIO, ?], Args, QueryResult[ConnectionIO]] {
      case (table, schema, columns, stages) =>
        val dbObject0 =
          schema.fold(table.fr0)(_.fr0 ++ Fragment.const0(".") ++ table.fr0)

        val projections = Some(columns) collect {
          case ColumnSelection.Explicit(idents) =>
            idents.map(_.fr0).intercalate(fr",")

          case ColumnSelection.All => fr0"*"
        }

        val rvalues = projections match {
          case Some(prjs) =>
            val sql =
              (fr"SELECT" ++ prjs ++ fr" FROM" ++ dbObject0).query[Unit].sql

            val ps =
              FC.prepareStatement(
                sql,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY)

            loggedRValueQuery(sql, ps, resultChunkSize, logHandler)(isSupported, unsafeRValue)

          case None =>
            (Stream.empty: Stream[ConnectionIO, RValue]).pure[Resource[ConnectionIO, ?]]
        }

        rvalues.map(QueryResult.parsed(QDataRValue, _, stages))
    }

  def isSupported(sqlType: SqlType, avalancheType: VendorType): Boolean =
    SupportedSqlTypes(sqlType) || Types.AvalancheColumnTypes.contains(avalancheType)

  def unsafeRValue(rs: ResultSet, col: ColumnNum, sqlType: SqlType, vendorType: VendorType): RValue = {
    import java.sql.Types._

    def unlessNull[A](a: A)(f: A => RValue): RValue =
      if (a == null) null else f(a)

    (sqlType: @switch) match {
      case CHAR | NCHAR | NVARCHAR =>
        unlessNull(rs.getString(col))(RValue.rString(_))

      case VARCHAR =>
        unlessNull(rs.getString(col)) { s =>
          if (vendorType == Types.IntervalYearToMonth)
            RValue.rInterval(IntervalParser.unsafeParseYearToMonth(s))
          else if (vendorType == Types.IntervalDayToSecond)
            RValue.rInterval(IntervalParser.unsafeParseDayToSecond(s))
          else
            RValue.rString(s)
        }

      case TINYINT | SMALLINT | INTEGER | BIGINT =>
        unlessNull(rs.getLong(col))(RValue.rLong(_))

      case DOUBLE | FLOAT | REAL =>
        unlessNull(rs.getDouble(col))(RValue.rDouble(_))

      case DECIMAL | NUMERIC =>
        unlessNull(rs.getBigDecimal(col))(RValue.rNum(_))

      case BOOLEAN =>
        unlessNull(rs.getBoolean(col))(RValue.rBoolean(_))

      case DATE =>
        unlessNull(rs.getObject(col, classOf[LocalDate]))(RValue.rLocalDate(_))

      case TIME =>
        if (vendorType == Types.TimeWithTimeZone || vendorType == Types.TimeWithLocalTimeZone)
          unlessNull(rs.getObject(col, classOf[OffsetTime]))(RValue.rOffsetTime(_))
        else
          unlessNull(rs.getObject(col, classOf[LocalTime]))(RValue.rLocalTime(_))

      case TIMESTAMP =>
        if (vendorType == Types.TimestampWithTimeZone || vendorType == Types.TimestampWithLocalTimeZone)
          unlessNull(rs.getObject(col, classOf[OffsetDateTime]))(RValue.rOffsetDateTime(_))
        else
          unlessNull(rs.getObject(col, classOf[LocalDateTime]))(RValue.rLocalDateTime(_))

      case otherSql => vendorType match {
        case Types.IPv4 | Types.IPv6 =>
          unlessNull(rs.getBytes(col)) { bs =>
            RValue.rString(InetAddress.getByAddress(bs).getHostAddress)
          }

        case Types.UUID =>
          unlessNull(rs.getBytes(col)) { bs =>
            val bb = ByteBuffer.wrap(bs)
            RValue.rString((new UUID(bb.getLong, bb.getLong)).toString)
          }

        case otherVendor =>
          RValue.rString(unsupportedColumnTypeMsg(JdbcType.fromInt(otherSql), otherVendor))
      }
    }
  }

  ////

  private val SupportedSqlTypes = Types.JdbcColumnTypes.keySet.map(_.toInt)
}
