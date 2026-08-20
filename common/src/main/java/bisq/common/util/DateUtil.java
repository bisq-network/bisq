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

package bisq.common.util;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateUtil {
    /**
     * Returns whether {@code timestamp} falls within the inclusive bounds. An inverted range contains no
     * value, thus we return false for it instead of throwing. Bounds can be derived from independent
     * sources (for instance a stored trade date and the local clock), and validation of peer controlled
     * data must fail closed rather than let the caller fail with an exception.
     */
public static boolean isWithinBounds(long timestamp, long lowerBound, long upperBound) {
        if (lowerBound > upperBound) {
            return false;
        }
        return timestamp >= lowerBound && timestamp <= upperBound;
    }

    /**
     * Returns whether {@code timestamp} is within the inclusive tolerance around {@code referenceTimestamp}.
     * The bounds are saturated so attacker-controlled extreme values cannot exploit signed-long overflow.
     */
    public static boolean isWithinTolerance(long timestamp, long referenceTimestamp, long tolerance) {
        if (tolerance < 0) {
            throw new IllegalArgumentException("tolerance must not be negative");
        }

        long lowerBound = referenceTimestamp < Long.MIN_VALUE + tolerance
                ? Long.MIN_VALUE
                : referenceTimestamp - tolerance;
        long upperBound = referenceTimestamp > Long.MAX_VALUE - tolerance
                ? Long.MAX_VALUE
                : referenceTimestamp + tolerance;
        return isWithinBounds(timestamp, lowerBound, upperBound);
    }

    /**
     *
     * @param date      The date which should be reset to first day of month
     * @return First day in given date with time set to zero.
     */
    public static Date getStartOfMonth(Date date) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     *
     * @param year      The year
     * @param month     The month starts with 0 for January
     * @return First day in given month with time set to zero.
     */
    public static Date getStartOfMonth(int year, int month) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(new Date());
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date time = calendar.getTime();
        return time;
    }
}
