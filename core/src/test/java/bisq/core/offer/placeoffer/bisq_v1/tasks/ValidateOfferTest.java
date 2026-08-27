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

package bisq.core.offer.placeoffer.bisq_v1.tasks;

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Volume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidateOfferTest {

    @Test
    void checkVolumeNotNullOrZeroAcceptsPositiveVolume() {
        // One on-chain unit of the 6-decimal coin, the smallest volume the
        // rounding can produce.
        assertDoesNotThrow(() ->
                ValidateOffer.checkVolumeNotNullOrZero(new Volume(Altcoin.valueOf("USDC", 100L)), "Volume"));
    }

    @Test
    void checkVolumeNotNullOrZeroRejectsNullAndZeroVolume() {
        assertThrows(NullPointerException.class,
                () -> ValidateOffer.checkVolumeNotNullOrZero(null, "Volume"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ValidateOffer.checkVolumeNotNullOrZero(new Volume(Altcoin.valueOf("USDC", 0L)), "MinVolume"));

        assertEquals("MinVolume must be positive. MinVolume=0", exception.getMessage());
    }
}
