/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.account.witness;

import bisq.common.util.Hex;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitnessReputationPrivacyTest {
    @Test
    void derivesTheSpecifiedNullifierVector() {
        byte[] nullifier = WitnessReputationPrivacy.deriveNullifier(
                new byte[]{1, 2, 3},
                new byte[]{4, 5, 6, 7});

        assertArrayEquals(Hex.decode(
                        "bb5f53a7503c5c0e449a77ff08c8442031459163848135ef0dbef5364e51f09e"),
                nullifier);
    }

    @Test
    void usesTheSamePreimageEquivalenceAsTheHistoricalWitnessHash() {
        byte[] first = WitnessReputationPrivacy.deriveNullifier(
                new byte[]{1},
                new byte[]{2, 3});
        byte[] second = WitnessReputationPrivacy.deriveNullifier(
                new byte[]{1, 2},
                new byte[]{3});

        assertArrayEquals(first, second);
    }

    @Test
    void differentHistoricalWitnessPreimagesProduceDifferentNullifiers() {
        byte[] first = WitnessReputationPrivacy.deriveNullifier(
                new byte[]{1},
                new byte[]{2, 3});
        byte[] second = WitnessReputationPrivacy.deriveNullifier(
                new byte[]{1},
                new byte[]{2, 4});

        assertNotEquals(Hex.encode(first), Hex.encode(second));
    }

    @Test
    void dateBucketContainsButDoesNotRevealTheExactDate() {
        long bucketSize = WitnessReputationPrivacy.DATE_BUCKET_SIZE_MILLIS;
        long exactDate = 1_700_000_123_456L;

        long dateBucket = WitnessReputationPrivacy.toDateBucket(exactDate);

        assertEquals(1_699_920_000_000L, dateBucket);
        assertTrue(dateBucket <= exactDate);
        assertTrue(exactDate < dateBucket + bucketSize);
        assertNotEquals(exactDate, dateBucket);
        assertEquals(1, TimeUnit.MILLISECONDS.toDays(bucketSize));
    }
}
