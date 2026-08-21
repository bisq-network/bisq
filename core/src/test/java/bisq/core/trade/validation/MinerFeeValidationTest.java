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

package bisq.core.trade.validation;

import bisq.core.provider.fee.FeeService;
import bisq.core.trade.TradeFeeFactory;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinerFeeValidationTest {

    /* --------------------------------------------------------------------- */
    // Trade tx fee
    /* --------------------------------------------------------------------- */

    @Test
    void checkTradeTxFeeAcceptsPositiveFees() {
        Coin txFee = Coin.valueOf(300);

        assertSame(txFee, MinerFeeValidation.checkTradeTxFee(txFee));
        assertEquals(300, MinerFeeValidation.checkTradeTxFee(300));
    }

    @Test
    void checkTradeTxFeeAcceptsFeesWithinAllowedTolerance() {
        Coin txFee = Coin.valueOf(300);

        assertSame(txFee, MinerFeeValidation.checkTradeTxFeeIsInTolerance(txFee, Coin.valueOf(300)));
        assertEquals(1_500, MinerFeeValidation.checkFeeIsInTolerance(1_500, 1_000));
        assertEquals(500, MinerFeeValidation.checkFeeIsInTolerance(500, 1_000));
    }

    @Test
    void checkTradeTxFeeRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(0));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(-1));
    }

    @Test
    void checkTradeTxFeeRejectsFeesOutsideAllowedTolerance() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkFeeIsInTolerance(2_001, 1_000));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkFeeIsInTolerance(499, 1_000));
    }

    @Test
    void checkTradeTxFeeRejectsNullFees() {
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkTradeTxFee(null));
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(null, Coin.valueOf(300)));
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(300), (Coin) null));
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(300), (FeeService) null));
    }

    @Test
    void checkTradeTxFeeRejectsFeesBelowMinBound() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(Coin.valueOf(249)));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(249L));
    }

    @Test
    void checkTradeTxFeeRejectsFeesAboveMaxBound() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(Coin.valueOf(360_001)));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(360_001L));
    }

    @Test
    void checkTradeTxFeeAcceptsBoundaryValues() {
        Coin min = Coin.valueOf(MinerFeeValidation.MIN_TRADE_TX_FEE_SAT);
        Coin max = Coin.valueOf(MinerFeeValidation.MAX_TRADE_TX_FEE_SAT);
        assertSame(min, MinerFeeValidation.checkTradeTxFee(min));
        assertSame(max, MinerFeeValidation.checkTradeTxFee(max));
        assertEquals(MinerFeeValidation.MIN_TRADE_TX_FEE_SAT, MinerFeeValidation.checkTradeTxFee(MinerFeeValidation.MIN_TRADE_TX_FEE_SAT));
        assertEquals(MinerFeeValidation.MAX_TRADE_TX_FEE_SAT, MinerFeeValidation.checkTradeTxFee(MinerFeeValidation.MAX_TRADE_TX_FEE_SAT));
    }

    @Test
    void checkTradeTxFeeAcceptsCalculatedTxFee() {
        Coin txFee = TradeFeeFactory.getTradeTxFee(Coin.valueOf(2));

        assertSame(txFee, MinerFeeValidation.checkTradeTxFeeIsInTolerance(txFee, TradeFeeFactory.getTradeTxFee(Coin.valueOf(2))));
    }

    @Test
    void checkTradeTxFeeRejectsInvalidFeeServiceRate() {
        FeeService feeService = mock(FeeService.class);
        when(feeService.getTxFeePerVbyte()).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(300), feeService));

        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(300), feeService));
    }

    /* --------------------------------------------------------------------- */
    // Miner fee rate
    /* --------------------------------------------------------------------- */

    @Test
    void checkMinerFeeRateAcceptsFeesWithinAllowedTolerance() {
        assertEquals(1_500, MinerFeeValidation.checkMinerFeeRateIsInTolerance(1_500, 1_000));
        assertEquals(500, MinerFeeValidation.checkMinerFeeRateIsInTolerance(500, 1_000));
    }

    @Test
    void checkMinerFeeRateRejectsFeesOutsideAllowedTolerance() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkMinerFeeRateIsInTolerance(2_001, 1_000));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkMinerFeeRateIsInTolerance(499, 1_000));
    }

    @Test
    void checkMinerFeeRateRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkMinerFeeRateIsInTolerance(0, 1_000));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkMinerFeeRateIsInTolerance(-1, 1_000));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkMinerFeeRateIsInTolerance(1_000, 0));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkMinerFeeRateIsInTolerance(1_000, -1));
    }

}
