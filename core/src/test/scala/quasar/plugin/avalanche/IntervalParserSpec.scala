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

import java.lang.String

import org.specs2.mutable.Specification

import qdata.time.DateTimeInterval

object IntervalParserSpec extends Specification {
  "INTERVAL DAY TO SECOND" >> {
    def parseAs(iso: String) =
      be_===(DateTimeInterval.parse(iso).get) ^^ { (s: String) =>
        IntervalParser.unsafeParseDayToSecond(s)
      }

    "min value" >> {
      "-3652047 23:59:59.999999" must parseAs("-P3652047DT23H59M59.999999S")
    }

    "max value" >> {
      "3652047 23:59:59.999999" must parseAs("P3652047DT23H59M59.999999S")
    }

    "all zero" >> {
      "0 0:0:0.0" must parseAs("P0D")
    }

    "day zero" >> {
      "0 11:11:11.11" must parseAs("P0DT11H11M11.11S")
    }

    "hour zero" >> {
      "11 0:11:11.11" must parseAs("P11DT11M11.11S")
    }

    "minute zero" >> {
      "11 11:0:11.11" must parseAs("P11DT11H11.11S")
    }

    "seconds zero" >> {
      "11 11:11:0.11" must parseAs("P11DT11H11M0.11S")
    }

    "fraction zero" >> {
      "11 11:11:11.0" must parseAs("P11DT11H11M11S")
    }

    "fraction omitted" >> {
      "11 11:11:11" must parseAs("P11DT11H11M11S")
    }

    "variable fraction precision" >> {
      "7 8:30:0.2" must parseAs("P7DT8H30M0.2S")
      "7 8:30:0.22" must parseAs("P7DT8H30M0.22S")
      "7 8:30:0.222" must parseAs("P7DT8H30M0.222S")
      "7 8:30:0.2222" must parseAs("P7DT8H30M0.2222S")
      "7 8:30:0.22222" must parseAs("P7DT8H30M0.22222S")
      "7 8:30:0.222222" must parseAs("P7DT8H30M0.222222S")
    }

    "variable day digits" >> {
      "1 10:00:00" must parseAs("P1DT10H")
      "12 10:00:00" must parseAs("P12DT10H")
      "123 10:00:00" must parseAs("P123DT10H")
      "1234 10:00:00" must parseAs("P1234DT10H")
      "12345 10:00:00" must parseAs("P12345DT10H")
      "123456 10:00:00" must parseAs("P123456DT10H")
      "1234567 10:00:00" must parseAs("P1234567DT10H")
    }

    "leading day zeros" >> {
      "01 10:00:00" must parseAs("P1DT10H")
      "001 10:00:00" must parseAs("P1DT10H")
      "0001 10:00:00" must parseAs("P1DT10H")
      "00001 10:00:00" must parseAs("P1DT10H")
      "000001 10:00:00" must parseAs("P1DT10H")
      "0000001 10:00:00" must parseAs("P1DT10H")
    }

    "hour digit padding" >> {
      "2 2:00:00" must parseAs("P2DT2H")
      "2 02:00:00" must parseAs("P2DT2H")
    }

    "minute digit padding" >> {
      "2 00:2:00" must parseAs("P2DT2M")
      "2 00:02:00" must parseAs("P2DT2M")
    }

    "second digit padding" >> {
      "2 00:00:2" must parseAs("P2DT2S")
      "2 00:00:02" must parseAs("P2DT2S")
    }
  }

  "INTERVAL YEAR TO MONTH" >> {
    def parseAs(iso: String) =
      be_===(DateTimeInterval.parse(iso).get) ^^ { (s: String) =>
        IntervalParser.unsafeParseYearToMonth(s)
      }

    "min value" >> {
      "-9999-11" must parseAs("-P9999Y11M")
    }

    "max value" >> {
      "9999-11" must parseAs("P9999Y11M")
    }

    "all zero" >> {
      "0-0" must parseAs("P0Y")
    }

    "year zero" >> {
      "0-3" must parseAs("P3M")
    }

    "month zero" >> {
      "7-0" must parseAs("P7Y")
    }

    "variable year digits" >> {
      "1-1" must parseAs("P1Y1M")
      "12-1" must parseAs("P12Y1M")
      "123-1" must parseAs("P123Y1M")
    }

    "leading year zeros" >> {
      "01-1" must parseAs("P1Y1M")
      "001-1" must parseAs("P1Y1M")
      "0001-1" must parseAs("P1Y1M")
    }

    "variable month digits" >> {
      "5-5" must parseAs("P5Y5M")
      "5-10" must parseAs("P5Y10M")
    }

    "leading month zeros" >> {
      "1-03" must parseAs("P1Y3M")
    }
  }
}
