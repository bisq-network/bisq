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
        assertEquals(2_000, BsqSwapCalculation.getAdjustedTxFee(10, 200, 0));
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

    @Test
    void getAdjustedTxFeeClampsAtZeroWhenTradeFeeExceedsMinerPortion() {
        // Real-world reproduction of the take-offer crash: low txFeePerVbyte + small vBytes
        // (single segwit input) makes the miner-fee portion smaller than the BSQ trade fee.
        // Returning 0 means this side contributes 0 BTC toward the miner fee; the BSQ trade
        // fee burns directly into the miner-fee pool and the tx overpays slightly — still
        // valid, still broadcastable. Pre-fix this threw IllegalArgumentException and killed
        // the take-offer dialog. The boundary case (tradeFee == minerPortion) also returns 0
        // since the BSQ fee alone covers the full miner cost.
        assertEquals(0, BsqSwapCalculation.getAdjustedTxFee(1, 200, 500));
        assertEquals(0, BsqSwapCalculation.getAdjustedTxFee(1, 200, 200));
    }

    @Test
    void sellersAndBuyersFeeOverloadsClampAtZeroWhenTradeFeeExceedsMinerPortion() {
        // Through the seller-path wrapper that BsqSwapOfferModel.calculateInputAndPayout calls:
        // seller's BTC input collapses to just btcTradeAmount when their BSQ trade fee already
        // covers the miner portion.
        assertEquals(100_000_000L,
                BsqSwapCalculation.getSellersBtcInputValue(100_000_000L, 1L, 100, 500L).value);
        // Symmetric buyer-payout wrapper: buyer receives the full btcTradeAmount when their
        // trade fee already covers the miner portion.
        assertEquals(100_000_000L,
                BsqSwapCalculation.getBuyersBtcPayoutValue(100_000_000L, 1L, 100, 500L).value);
    }
}
