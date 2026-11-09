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
        Coin txFee = Coin.valueOf(500);

        assertSame(txFee, MinerFeeValidation.checkTradeTxFee(txFee));
        assertEquals(500, MinerFeeValidation.checkTradeTxFee(500));
    }

    @Test
    void checkTradeTxFeeAcceptsFeesWithinAllowedTolerance() {
        Coin txFee = Coin.valueOf(500);

        assertSame(txFee, MinerFeeValidation.checkTradeTxFeeIsInTolerance(txFee, Coin.valueOf(500)));
        assertEquals(1_500, MinerFeeValidation.checkFeeIsInTolerance(1_500, 1_000));
        assertEquals(500, MinerFeeValidation.checkFeeIsInTolerance(500, 1_000));
        assertEquals(4_000, MinerFeeValidation.checkFeeIsInTolerance(4_000, 1_000));
        assertEquals(250, MinerFeeValidation.checkFeeIsInTolerance(250, 1_000));
    }

    // The trade tx fee of a peer may differ by a factor of 4 from the expected fee, as fee providers differ.
    // The taker checks its own fee with the factor of 2 that makers up to v1.10.11 apply.
    @Test
    void ownTradeTxFeeUsesTheFactorOfEarlierMakers() {
        FeeService feeService = mock(FeeService.class);
        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.valueOf(10));
        Coin twiceExpected = Coin.valueOf(4_240);
        Coin aboveTwiceExpected = Coin.valueOf(4_241);

        assertSame(twiceExpected, MinerFeeValidation.checkOwnTradeTxFeeIsInTolerance(twiceExpected, feeService));
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkOwnTradeTxFeeIsInTolerance(aboveTwiceExpected, feeService));
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkOwnTradeTxFeeIsInTolerance(Coin.valueOf(1_059), feeService));
        assertSame(aboveTwiceExpected, MinerFeeValidation.checkTradeTxFeeIsInTolerance(aboveTwiceExpected, feeService));
        assertEquals(8_480, MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(8_480), feeService).value);
        assertEquals(530, MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(530), feeService).value);
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(8_481), feeService));
    }

    @Test
    void checkTradeTxFeeRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(0));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(-1));
    }

    @Test
    void checkTradeTxFeeRejectsFeesOutsideAllowedTolerance() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkFeeIsInTolerance(4_001, 1_000));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkFeeIsInTolerance(249, 1_000));
    }

    @Test
    void checkTradeTxFeeRejectsNullFees() {
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkTradeTxFee(null));
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(null, Coin.valueOf(500)));
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(500), (Coin) null));
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(500), (FeeService) null));
    }

    @Test
    void checkTradeTxFeeRejectsFeesBelowMinBound() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(Coin.valueOf(423)));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(423L));
    }

    // 2 sat/vbyte for the 212 vbytes that the maker expects. Changing either value changes which take
    // offer requests makers of different versions accept.
    @Test
    void minTradeTxFeeIsTheTradeTxFeeAtTheMinTradeTxFeeRate() {
        assertEquals(2, FeeService.MIN_TRADE_TX_FEE_RATE);
        assertEquals(424, MinerFeeValidation.MIN_TRADE_TX_FEE_SAT);
        assertEquals(TradeFeeFactory.getTradeTxFee(Coin.valueOf(FeeService.MIN_TRADE_TX_FEE_RATE)).value,
                MinerFeeValidation.MIN_TRADE_TX_FEE_SAT);
    }

    // A trade object is also created when the trade is loaded from disk, so it accepts the fees down to
    // 250 sat that makers up to v1.10.11 accepted, while a new take offer request needs 424 sat.
    @Test
    void checkStoredTradeTxFeeAcceptsTheMinimumOfEarlierVersions() {
        Coin minOfEarlierVersions = Coin.valueOf(250);

        assertSame(minOfEarlierVersions, MinerFeeValidation.checkStoredTradeTxFee(minOfEarlierVersions));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkTradeTxFee(minOfEarlierVersions));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkStoredTradeTxFee(Coin.valueOf(249)));
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkStoredTradeTxFee(Coin.valueOf(360_001)));
        assertThrows(NullPointerException.class, () -> MinerFeeValidation.checkStoredTradeTxFee(null));
    }

    // The buyer's check of the delayed payout tx is a check of an existing trade, so it also accepts the fees down to
    // 250 sat, with the factor of 4 against the own fee rate.
    @Test
    void checkStoredTradeTxFeeIsInToleranceAcceptsTheMinimumOfEarlierVersions() {
        FeeService feeService = mock(FeeService.class);
        // The expected fee is 424 sat, and the factor of 4 accepts 106 to 1696 sat
        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.valueOf(2));

        assertEquals(250, MinerFeeValidation.checkStoredTradeTxFeeIsInTolerance(250, feeService));
        assertEquals(1_696, MinerFeeValidation.checkStoredTradeTxFeeIsInTolerance(1_696, feeService));
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkStoredTradeTxFeeIsInTolerance(249, feeService));
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkStoredTradeTxFeeIsInTolerance(1_697, feeService));
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(250), feeService));
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
                () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(500), feeService));

        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> MinerFeeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(500), feeService));
    }

    /* --------------------------------------------------------------------- */
    // Miner fee rate
    /* --------------------------------------------------------------------- */

    @Test
    void checkMinerFeeRateAcceptsFeesWithinAllowedTolerance() {
        assertEquals(1_500, MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(1_500, 1_000));
        assertEquals(500, MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(500, 1_000));
        assertEquals(2_000, MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(2_000, 1_000));
    }

    // The BSQ swap maker pays its own share of the miner fee at the rate of the taker's request, so it keeps the
    // factor of 2, while a peer's trade tx fee may differ by a factor of 4.
    @Test
    void checkMinerFeeRateRejectsFeesOutsideAllowedTolerance() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(2_001, 1_000));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(499, 1_000));
    }

    @Test
    void checkMinerFeeRateRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(0, 1_000));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(-1, 1_000));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(1_000, 0));
        assertThrows(IllegalArgumentException.class, () -> MinerFeeValidation.checkBsqSwapMinerFeeRateIsInTolerance(1_000, -1));
    }

}
