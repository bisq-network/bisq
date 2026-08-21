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

import bisq.common.util.MathUtils;
import bisq.common.util.Tuple3;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import java.math.RoundingMode;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.time.temporal.ChronoField.DAY_OF_YEAR;

@Slf4j
public class TemporalAdjusterModel {
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    protected TemporalAdjuster temporalAdjuster = Interval.MONTH.getAdjuster();

    private boolean enableCache;
    private Tuple3<LocalDate, Instant, Instant> cachedDateStartEndTuple;
    private Tuple3<LocalDate, TemporalAdjuster, Long> cachedTimeIntervalMapping;

    public TemporalAdjusterModel withCache() {
        var model = new TemporalAdjusterModel();
        model.temporalAdjuster = this.temporalAdjuster;
        model.enableCache = true;
        return model;
    }

    public enum Interval {
        YEAR(TemporalAdjusters.firstDayOfYear()),
        HALF_YEAR(temporal -> {
            long halfYear = temporal.range(DAY_OF_YEAR).getMaximum() / 2;
            int dayOfYear = 0;
            if (temporal instanceof LocalDate) {
                dayOfYear = ((LocalDate) temporal).getDayOfYear(); // getDayOfYear delivers 1-365 (366 in leap years)
            }
            if (dayOfYear <= halfYear) {
                return temporal.with(DAY_OF_YEAR, 1);
            } else {
                return temporal.with(DAY_OF_YEAR, halfYear + 1);
            }
        }),
        QUARTER(temporal -> {
            long quarter1 = temporal.range(DAY_OF_YEAR).getMaximum() / 4;
            long halfYear = temporal.range(DAY_OF_YEAR).getMaximum() / 2;
            long quarter3 = MathUtils.roundDoubleToLong(temporal.range(DAY_OF_YEAR).getMaximum() * 0.75, RoundingMode.FLOOR);
            int dayOfYear = 0;
            if (temporal instanceof LocalDate) {
                dayOfYear = ((LocalDate) temporal).getDayOfYear();
            }
            if (dayOfYear <= quarter1) {
                return temporal.with(DAY_OF_YEAR, 1);
            } else if (dayOfYear <= halfYear) {
                return temporal.with(DAY_OF_YEAR, quarter1 + 1);
            } else if (dayOfYear <= quarter3) {
                return temporal.with(DAY_OF_YEAR, halfYear + 1);
            } else {
                return temporal.with(DAY_OF_YEAR, quarter3 + 1);
            }
        }),
        MONTH(TemporalAdjusters.firstDayOfMonth()),
        WEEK(TemporalAdjusters.next(DayOfWeek.MONDAY)),
        DAY(TemporalAdjusters.ofDateAdjuster(d -> d));

        @Getter
        private final TemporalAdjuster adjuster;

        Interval(TemporalAdjuster adjuster) {
            this.adjuster = adjuster;
        }
    }

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
        LocalDate date = toLocalDate(instant);
        Tuple3<LocalDate, TemporalAdjuster, Long> tuple = cachedTimeIntervalMapping;
        long timeInterval;
        if (tuple != null && date.equals(tuple.first) && temporalAdjuster.equals(tuple.second)) {
            timeInterval = tuple.third;
        } else {
            timeInterval = date
                    .with(temporalAdjuster)
                    .atStartOfDay(ZONE_ID)
                    .toEpochSecond();
            if (enableCache) {
                cachedTimeIntervalMapping = new Tuple3<>(date, temporalAdjuster, timeInterval);
            }
        }
        return timeInterval;
    }

    private LocalDate toLocalDate(Instant instant) {
        LocalDate date;
        Tuple3<LocalDate, Instant, Instant> tuple = cachedDateStartEndTuple;
        if (tuple != null && !instant.isBefore(tuple.second) && instant.isBefore(tuple.third)) {
            date = tuple.first;
        } else {
            date = instant.atZone(ZONE_ID).toLocalDate();
            if (enableCache) {
                cachedDateStartEndTuple = new Tuple3<>(date,
                        date.atStartOfDay(ZONE_ID).toInstant(),
                        date.plusDays(1).atStartOfDay(ZONE_ID).toInstant());
            }
        }
        return date;
    }
}
