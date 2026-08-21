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

package bisq.core.dao.state.model.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DaoArithmeticsTest {
    private static final int PRE_ACTIVATION_HEIGHT = 954_199;
    private static final int ACTIVATION_HEIGHT = 954_200;

    @Test
    void addLongUsesLegacyOverflowBeforeActivation() {
        assertEquals(Long.MIN_VALUE, DaoArithmetics.addLong(Long.MAX_VALUE, 1, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void addLongThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> DaoArithmetics.addLong(Long.MAX_VALUE, 1, ACTIVATION_HEIGHT));
    }

    @Test
    void addIntegerUsesLegacyOverflowBeforeActivation() {
        assertEquals(Integer.MIN_VALUE, DaoArithmetics.addInteger(Integer.MAX_VALUE, 1, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void addIntegerThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> DaoArithmetics.addInteger(Integer.MAX_VALUE, 1, ACTIVATION_HEIGHT));
    }

    @Test
    void multiplyAndDivideUsesLegacyIntermediateOverflowBeforeActivation() {
        long acceptedStake = Long.MAX_VALUE / 10_000 + 1;

        assertEquals(-9_999, DaoArithmetics.multiplyAndDivide(acceptedStake,
                10_000,
                acceptedStake + 1,
                PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void multiplyAndDivideKeepsOverflowingIntermediateExactAtActivation() {
        long acceptedStake = Long.MAX_VALUE / 10_000 + 1;

        assertEquals(9_999, DaoArithmetics.multiplyAndDivide(acceptedStake,
                10_000,
                acceptedStake + 1,
                ACTIVATION_HEIGHT));
    }

    @Test
    void multiplyLongUsesLegacyOverflowBeforeActivation() {
        assertEquals(Long.MIN_VALUE, DaoArithmetics.multiplyLong(Long.MAX_VALUE / 2 + 1,
                2,
                PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void multiplyLongThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> DaoArithmetics.multiplyLong(Long.MAX_VALUE / 2 + 1, 2, ACTIVATION_HEIGHT));
    }
}
