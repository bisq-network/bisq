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

package bisq.core.util;

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Volume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VolumeUtilTest {

    // Altcoin volumes are stored at 10^-8 precision; the value passed here is in those units.
    private static Volume altcoin(String code, long internalValue) {
        return new Volume(Altcoin.valueOf(code, internalValue));
    }

    @Test
    public void testGetAdjustedAltcoinVolume() {
        // 0.12345678 rounded to a 6-decimal coin (e.g. USD Coin) -> 0.123457 (step 10^(8-6) = 100).
        assertEquals(12_345_700L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("USDC", 12_345_678L), 6).getValue(),
                "6-decimal volume should round to the coin's 6th decimal.");

        // 0.12345678 rounded to a 2-decimal coin (e.g. TurtleCoin) -> 0.12 (step 10^6).
        assertEquals(12_000_000L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("TRTL", 12_345_678L), 2).getValue(),
                "2-decimal volume should round to the coin's 2nd decimal.");

        // A coin using the full 8 decimals is left untouched (step 1).
        assertEquals(12_345_678L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("XMR", 12_345_678L), 8).getValue(),
                "8-decimal precision should not round.");

        // Precision 8 must stay bit-exact even above 2^53, where a long -> double cast would drift.
        assertEquals(12_345_678_901_234_567L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("XMR", 12_345_678_901_234_567L), 8).getValue(),
                "8-decimal precision must be an exact no-op even above 2^53.");

        // Currency code is preserved.
        assertEquals("USDC",
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("USDC", 12_345_678L), 6).getCurrencyCode());
    }

    @Test
    public void testGetAdjustedAltcoinVolumeNeverZero() {
        // A 0-decimal coin (whole units only, step 10^8): 0.4 of a coin must NOT collapse to zero;
        // it is raised to exactly one whole coin so it stays sendable.
        assertEquals(100_000_000L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("SF", 40_000_000L), 0).getValue(),
                "A sub-unit volume on a 0-decimal coin must round up to one whole unit, not zero.");

        // A tiny volume below one on-chain unit of a 6-decimal coin (0.0000004, step 100) -> 0.000001.
        assertEquals(100L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("USDC", 40L), 6).getValue(),
                "A volume below one on-chain unit must round up to one unit, not zero.");

        // Guard: the result is never zero, whatever the precision.
        for (int precision = 0; precision <= 8; precision++) {
            assertTrue(VolumeUtil.getAdjustedAltcoinVolume(altcoin("USDC", 1L), precision).getValue() > 0,
                    "Rounded volume must stay positive for precision " + precision);
        }
    }
}
