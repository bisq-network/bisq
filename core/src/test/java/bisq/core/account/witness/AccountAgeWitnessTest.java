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

package bisq.core.account.witness;

import org.bitcoinj.core.Utils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccountAgeWitnessTest {
    private static final long NOW = Instant.parse("2026-08-14T12:00:00Z").toEpochMilli();
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final long ONE_DAY = TimeUnit.DAYS.toMillis(1);

    @Test
    public void isDateInToleranceAcceptsDatesUpToOneDayOff() {
        assertTrue(witnessWithDate(NOW).isDateInTolerance(CLOCK));
        assertTrue(witnessWithDate(NOW - ONE_DAY).isDateInTolerance(CLOCK));
        assertTrue(witnessWithDate(NOW + ONE_DAY).isDateInTolerance(CLOCK));
    }

    @Test
    public void isDateInToleranceRejectsDatesMoreThanOneDayOff() {
        assertFalse(witnessWithDate(NOW - ONE_DAY - 1).isDateInTolerance(CLOCK));
        assertFalse(witnessWithDate(NOW + ONE_DAY + 1).isDateInTolerance(CLOCK));
    }

    @Test
    public void isDateInToleranceRejectsExtremeDates() {
        assertFalse(witnessWithDate(Long.MIN_VALUE).isDateInTolerance(CLOCK));
        assertFalse(witnessWithDate(Long.MAX_VALUE).isDateInTolerance(CLOCK));
        // This date makes (now - date) overflow to Long.MIN_VALUE. The previous implementation
        // used Math.abs(now - date), which stays negative in that case and accepted the payload.
        assertFalse(witnessWithDate(NOW + Long.MIN_VALUE).isDateInTolerance(CLOCK));
    }

    private AccountAgeWitness witnessWithDate(long date) {
        return new AccountAgeWitness(Utils.sha256hash160(new byte[]{1}), date);
    }
}
