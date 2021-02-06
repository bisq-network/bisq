/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.components.chart;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import lombok.Getter;

public class TemporalAdjusterUtil {
    public enum Interval {
        YEAR(TemporalAdjusters.firstDayOfYear()),
        MONTH(TemporalAdjusters.firstDayOfMonth()),
        WEEK(TemporalAdjusters.ofDateAdjuster(date -> date.plusWeeks(1))),
        DAY(TemporalAdjusters.ofDateAdjuster(d -> d));

        @Getter
        private final TemporalAdjuster adjuster;

        Interval(TemporalAdjuster adjuster) {
            this.adjuster = adjuster;
        }
    }

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    public static long toTimeInterval(Instant instant, TemporalAdjuster temporalAdjuster) {
        return instant
                .atZone(ZONE_ID)
                .toLocalDate()
                .with(temporalAdjuster)
                .atStartOfDay(ZONE_ID)
                .toInstant()
                .getEpochSecond();
    }
}
