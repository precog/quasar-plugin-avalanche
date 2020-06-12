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

import doobie.enum.JdbcType

import quasar.api.ColumnType
import quasar.plugin.jdbc._

/** JdbcType(Avalanche type name): Class repr
  * -----------------------------------------
  * Char(char): java.lang.String
  * Char(nchar): java.lang.String
  * VarChar(varchar): java.lang.String
  * VarChar(nvarchar): java.lang.String
  * TinyInt(integer1): java.lang.Integer
  * SmallInt(smallint): java.lang.Integer
  * Integer(integer): java.lang.Integer
  * BigInt(bigint): java.lang.Long
  * Decimal(decimal): java.math.BigDecimal
  * Double(float): java.lang.Double
  * Date(ansidate): java.sql.Date
  * Time(time without time zone): java.sql.Time
  * Time(time with time zone): java.sql.Time
  * Time(time with local time zone): java.sql.Time
  * Timestamp(timestamp without time zone): java.sql.Timestamp
  * Timestamp(timestamp with time zone): java.sql.Timestamp
  * Timestamp(timestamp with local time zone): java.sql.Timestamp
  * VarChar(interval year to month): java.lang.String
  * VarChar(interval day to second): java.lang.String
  * Decimal(money): java.math.BigDecimal
  * Binary(ipv4): Array[Byte]
  * Binary(ipv6): Array[Byte]
  * Binary(uuid): Array[Byte]
  * Boolean(boolean): java.lang.Boolean
  *
  * @see https://docs.actian.com/avalanche/index.html#page/SQLLanguage%2F2._SQL_Data_Types.htm%23ww414616
  * @see https://docs.actian.com/avalanche/index.html#page/SQLLanguage%2FFloat_Point_Limitations.htm%23ww415213
  */
object Types {
  import JdbcType._

  // BINARY
  val IPv4 = "ipv4"
  val IPv6 = "ipv6"
  val UUID = "uuid"

  // DATE
  val AnsiDate = "ansidate"

  // DECIMAL
  val Money = "money"

  // TIME
  val TimeWithoutTimeZone = "time without time zone"
  val TimeWithTimeZone = "time with time zone"
  val TimeWithLocalTimeZone = "time with local time zone"

  // TIMESTAMP
  val TimestampWithoutTimeZone = "timestamp without time zone"
  val TimestampWithTimeZone = "timestamp with time zone"
  val TimestampWithLocalTimeZone = "timestamp with local time zone"

  // VARCHAR
  val IntervalYearToMonth = "interval year to month"
  val IntervalDayToSecond = "interval day to second"

  /** Mapping between supported JDBC types and Quasar column type. */
  val JdbcColumnTypes: Map[JdbcType, ColumnType.Scalar] =
    Map(
      Boolean -> ColumnType.Boolean,
      Char -> ColumnType.String,
      Date -> ColumnType.LocalDate,
      Decimal -> ColumnType.Number,
      Double -> ColumnType.Number,
      Float -> ColumnType.Number,
      Integer -> ColumnType.Number,
      NChar -> ColumnType.String,
      Null -> ColumnType.Null,
      Numeric -> ColumnType.Number,
      NVarChar -> ColumnType.String,
      Real -> ColumnType.Number,
      SmallInt -> ColumnType.Number,
      Time -> ColumnType.LocalTime,
      Timestamp -> ColumnType.LocalDateTime,
      TinyInt -> ColumnType.Number,
      VarChar -> ColumnType.String)

  /** Mapping between vendor types and their Quasar column type, when it differs
    * from the JDBC representation.
    */
  val AvalancheColumnTypes: Map[VendorType, ColumnType.Scalar] =
    Map(
      IPv4 -> ColumnType.String,
      IPv6 -> ColumnType.String,
      UUID -> ColumnType.String,
      IntervalDayToSecond -> ColumnType.Interval,
      IntervalYearToMonth -> ColumnType.Interval,
      TimeWithTimeZone -> ColumnType.OffsetTime,
      TimeWithLocalTimeZone -> ColumnType.OffsetTime,
      TimestampWithTimeZone -> ColumnType.OffsetDateTime,
      TimestampWithLocalTimeZone -> ColumnType.OffsetDateTime)
}
