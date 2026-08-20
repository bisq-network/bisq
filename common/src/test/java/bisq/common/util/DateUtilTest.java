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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateUtilTest {
    private static final long REFERENCE = 1_776_326_400_000L;
    private static final long TOLERANCE = 1_000L;

    @Test
    void acceptsValuesWithinBounds() {
        assertTrue(DateUtil.isWithinBounds(REFERENCE, REFERENCE - TOLERANCE, REFERENCE + TOLERANCE));
        assertTrue(DateUtil.isWithinBounds(REFERENCE - TOLERANCE, REFERENCE - TOLERANCE, REFERENCE + TOLERANCE));
        assertTrue(DateUtil.isWithinBounds(REFERENCE + TOLERANCE, REFERENCE - TOLERANCE, REFERENCE + TOLERANCE));
    }

    @Test
    void rejectsValuesOutsideBounds() {
        assertFalse(DateUtil.isWithinBounds(REFERENCE - TOLERANCE - 1,
                REFERENCE - TOLERANCE,
                REFERENCE + TOLERANCE));
        assertFalse(DateUtil.isWithinBounds(REFERENCE + TOLERANCE + 1,
                REFERENCE - TOLERANCE,
                REFERENCE + TOLERANCE));
    }

    @Test
    void rejectsAllValuesForInvertedBounds() {
        assertFalse(DateUtil.isWithinBounds(REFERENCE, REFERENCE + TOLERANCE, REFERENCE - TOLERANCE));
        assertFalse(DateUtil.isWithinBounds(REFERENCE + TOLERANCE, REFERENCE + TOLERANCE, REFERENCE - TOLERANCE));
        assertFalse(DateUtil.isWithinBounds(REFERENCE - TOLERANCE, REFERENCE + TOLERANCE, REFERENCE - TOLERANCE));
    }

    @Test
    void acceptsInclusiveToleranceBounds() {
        assertTrue(DateUtil.isWithinTolerance(REFERENCE - TOLERANCE, REFERENCE, TOLERANCE));
        assertTrue(DateUtil.isWithinTolerance(REFERENCE, REFERENCE, TOLERANCE));
        assertTrue(DateUtil.isWithinTolerance(REFERENCE + TOLERANCE, REFERENCE, TOLERANCE));
    }

    @Test
    void rejectsValuesOutsideTolerance() {
        assertFalse(DateUtil.isWithinTolerance(REFERENCE - TOLERANCE - 1, REFERENCE, TOLERANCE));
        assertFalse(DateUtil.isWithinTolerance(REFERENCE + TOLERANCE + 1, REFERENCE, TOLERANCE));
    }

    @Test
    void acceptsOnlyTheReferenceValueForZeroTolerance() {
        assertTrue(DateUtil.isWithinTolerance(REFERENCE, REFERENCE, 0));
        assertFalse(DateUtil.isWithinTolerance(REFERENCE - 1, REFERENCE, 0));
        assertFalse(DateUtil.isWithinTolerance(REFERENCE + 1, REFERENCE, 0));
    }

    @Test
    void rejectsValuesThatWouldOverflowADifferenceCheck() {
        // REFERENCE + Long.MIN_VALUE is the value for which "REFERENCE - timestamp" is Long.MIN_VALUE.
        // Math.abs of that value stays negative, so a difference based check would accept it.
        assertFalse(DateUtil.isWithinTolerance(REFERENCE + Long.MIN_VALUE, REFERENCE, TOLERANCE));
        assertFalse(DateUtil.isWithinTolerance(Long.MIN_VALUE, REFERENCE, TOLERANCE));
        assertFalse(DateUtil.isWithinTolerance(Long.MAX_VALUE, REFERENCE, TOLERANCE));
    }

    @Test
    void saturatesBoundsForExtremeReferenceValues() {
        assertTrue(DateUtil.isWithinTolerance(Long.MIN_VALUE, Long.MIN_VALUE, TOLERANCE));
        assertTrue(DateUtil.isWithinTolerance(Long.MAX_VALUE, Long.MAX_VALUE, TOLERANCE));
        assertFalse(DateUtil.isWithinTolerance(Long.MIN_VALUE + TOLERANCE + 1,
                Long.MIN_VALUE,
                TOLERANCE));
        assertFalse(DateUtil.isWithinTolerance(Long.MAX_VALUE - TOLERANCE - 1,
                Long.MAX_VALUE,
                TOLERANCE));
    }

    @Test
    void rejectsNegativeTolerance() {
        assertThrows(IllegalArgumentException.class,
                () -> DateUtil.isWithinTolerance(REFERENCE, REFERENCE, -1));
    }
}
