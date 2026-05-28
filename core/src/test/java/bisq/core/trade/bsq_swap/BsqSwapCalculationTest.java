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

package bisq.core.trade.bsq_swap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BsqSwapCalculationTest {
    @Test
    void monetaryCalculationsReturnExpectedValues() {
        assertEquals(5_000_050, BsqSwapCalculation.getBuyersBsqInputValue(5_000_000, 50).value);
        assertEquals(99_998_050, BsqSwapCalculation.getBuyersBtcPayoutValue(100_000_000,
                10,
                200,
                50).value);
        assertEquals(100_001_850, BsqSwapCalculation.getSellersBtcInputValue(100_000_000, 1_850).value);
        assertEquals(4_999_850, BsqSwapCalculation.getSellersBsqPayoutValue(5_000_000, 150).value);
        assertEquals(1_950, BsqSwapCalculation.getAdjustedTxFee(10, 200, 50));
        // 0 trade fee is allowed a seller has no trade fee
        assertEquals(1_997, BsqSwapCalculation.getAdjustedTxFee(10, 200, 3));
    }

    @Test
    void monetaryCalculationsRejectOverflow() {
        assertThrows(ArithmeticException.class,
                () -> BsqSwapCalculation.getBuyersBsqInputValue(Long.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class,
                () -> BsqSwapCalculation.getSellersBtcInputValue(Long.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class,
                () -> BsqSwapCalculation.getSellersBsqPayoutValue(Long.MIN_VALUE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> BsqSwapCalculation.getAdjustedTxFee(Long.MAX_VALUE, 2, -1));
    }
}
