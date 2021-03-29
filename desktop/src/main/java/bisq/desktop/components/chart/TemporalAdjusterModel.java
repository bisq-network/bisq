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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TemporalAdjusterModel {
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    public enum Interval {
        YEAR(TemporalAdjusters.firstDayOfYear()),
        MONTH(TemporalAdjusters.firstDayOfMonth()),
        WEEK(TemporalAdjusters.next(DayOfWeek.MONDAY)),
        DAY(TemporalAdjusters.ofDateAdjuster(d -> d));

        @Getter
        private final TemporalAdjuster adjuster;

        Interval(TemporalAdjuster adjuster) {
            this.adjuster = adjuster;
        }
    }

    protected TemporalAdjuster temporalAdjuster = Interval.DAY.getAdjuster();

    public void setTemporalAdjuster(TemporalAdjuster temporalAdjuster) {
        this.temporalAdjuster = temporalAdjuster;
    }

    public TemporalAdjuster getTemporalAdjuster() {
        return temporalAdjuster;
    }

    public long toTimeInterval(Instant instant) {
        return toTimeInterval(instant, temporalAdjuster);
    }

    public long toTimeInterval(Instant instant, TemporalAdjuster temporalAdjuster) {
        return instant
                .atZone(ZONE_ID)
                .toLocalDate()
                .with(temporalAdjuster)
                .atStartOfDay(ZONE_ID)
                .toInstant()
                .getEpochSecond();
    }
}
