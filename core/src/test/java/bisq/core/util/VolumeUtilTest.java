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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    public void testGetAdjustedAltcoinVolumeRoundsToZeroBelowHalfUnit() {
        // A volume below half of one on-chain unit rounds to ZERO instead of being inflated:
        // raising 0.4 of a 0-decimal coin to a whole coin would change the agreed rate by 150%.
        // Callers must reject such an amount as below the coin's minimum.
        assertEquals(0L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("SF", 40_000_000L), 0).getValue(),
                "A volume below half of one on-chain unit must round to zero, not be inflated.");
        assertEquals(0L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("USDC", 40L), 6).getValue(),
                "A volume below half of one on-chain unit must round to zero, not be inflated.");

        // From half of one unit upwards normal half-up rounding applies.
        assertEquals(100_000_000L,
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("SF", 50_000_000L), 0).getValue(),
                "Half of one on-chain unit must round up to one unit.");

        // Zero stays zero at every precision, also at 8 where step == 1. A floor clamp here
        // would turn zero into one atom and silently break the no-op invariant for
        // default-precision coins, BSQ included.
        for (int precision = 0; precision <= 8; precision++) {
            assertEquals(0L,
                    VolumeUtil.getAdjustedAltcoinVolume(altcoin("USDC", 0L), precision).getValue(),
                    "Zero volume must stay zero for precision " + precision);
        }
    }

    @Test
    public void testGetAdjustedAltcoinVolumeOverflowFailsLoudly() {
        // The half-up rounding add is the one overflow-capable step. A crafted value near
        // Long.MAX_VALUE must fail loudly instead of silently producing a wrong volume.
        assertThrows(ArithmeticException.class, () ->
                VolumeUtil.getAdjustedAltcoinVolume(altcoin("USDC", Long.MAX_VALUE - 10), 6));
    }
}
