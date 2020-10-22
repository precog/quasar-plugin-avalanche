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

import java.lang.CharSequence
import java.text.ParsePosition
import java.time.{Duration, Period}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField

import qdata.time.DateTimeInterval

/** Parsers for Avalanche intervals.
  *
  * @see https://docs.actian.com/avalanche/index.html#page/SQLLanguage%2FFloat_Point_Limitations.htm%23ww415213
  */
object IntervalParser {

  /** Returns the `DateTimeInterval` that corresponds to the given Avalanche
    * 'INTERVAL DAY TO SECOND' value.
    *
    * Days are interpreted as a `Period`, not a `Duration`.
    *
    * Range: -3652047 23:59:59.999999 to 3652047 23:59:59.999999
    */
  def unsafeParseDayToSecond(cs: CharSequence): DateTimeInterval = {
    val p = new ParsePosition(0)
    val t = DTSFormatter.parse(cs, p)

    var nanos = 0

    if ((p.getIndex + 1) < cs.length && cs.charAt(p.getIndex) == '.') {
      nanos = unsafeParseInt(cs, p.getIndex + 1, cs.length)

      while (nanos > 0 && nanos < PadLimit) {
        nanos = nanos * 10
      }
    }

    // We parsed the days value using the YEAR field to avoid overflow
    val period = Period.of(0, 0, t.get(ChronoField.YEAR))
    val duration = Duration.ofSeconds(t.getLong(ChronoField.SECOND_OF_DAY), nanos.toLong)

    DateTimeInterval(period, if (period.isNegative) duration.negated else duration)
  }

  /** Returns the `DateTimeInterval` that corresponds to the given Avalanche
    * 'INTERVAL YEAR TO MONTH' value.
    *
    * Range: -9999-11 to 9999-11
    */
  def unsafeParseYearToMonth(cs: CharSequence): DateTimeInterval = {
    val len: Int = cs.length
    var sign: Int = 1
    var y: Int = 0
    var h: Int = 1

    if (cs.charAt(0) == '-') {
      sign = -1
      y = 1
    }

    while (h < len && cs.charAt(h) != '-') {
      h += 1
    }

    DateTimeInterval.ofPeriod(Period.of(
      sign * unsafeParseInt(cs, y, h),
      sign * unsafeParseInt(cs, h + 1, len),
      0))
  }

  ////

  private val NanosInSecond: Int = 1000 * 1000 * 1000
  private val PadLimit: Int = NanosInSecond / 10

  // NB: We use 'u' (YEAR) in the pattern since the day value can overflow the day pattern
  private val DTSFormatter = DateTimeFormatter.ofPattern("u H:m:s")

  // Cribbed from https://github.com/precog/tectonic/blob/3ce1f15d4a3f1ca54678182c6f6e393312bbeca1/core/src/main/scala/tectonic/util/package.scala#L140
  private def unsafeParseInt(cs: CharSequence, start: Int, end: Int): Int = {
    // we store the inverse of the positive sum, to ensure we don't
    // incorrectly overflow on Int.MinValue. for positive numbers
    // this inverse sum will be inverted before being returned.
    var inverseSum: Int = 0
    var inverseSign: Int = -1
    var i: Int = start

    if (cs.charAt(i) == '-') {
      inverseSign = 1
      i += 1
    }

    while (i < end) {
      inverseSum = inverseSum * 10 - (cs.charAt(i).toInt - 48)
      i += 1
    }

    inverseSum * inverseSign
  }
}
