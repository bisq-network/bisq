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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.TradeValidationTestUtils.GENESIS_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DelayedPayoutTxValidationTest {
    static final int GRID_SIZE = DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE;

    @Test
    void checkBurningManSelectionHeightAcceptsSameHeight() {
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 139, GRID_SIZE, 0));

        assertEquals(130,
                DelayedPayoutTxValidation.checkBurningManSelectionHeight(130, delayedPayoutTxReceiverService(130)));
    }

    @Test
    void checkBurningManSelectionHeightAcceptsMakerOneGridAhead() {
        assertEquals(120,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 134, GRID_SIZE, 0));
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 135, GRID_SIZE, 0));

        assertEquals(120,
                DelayedPayoutTxValidation.checkBurningManSelectionHeight(120, delayedPayoutTxReceiverService(130)));
    }

    @Test
    void checkBurningManSelectionHeightAcceptsTakerOneGridAhead() {
        assertEquals(120,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 134, GRID_SIZE, 0));
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 135, GRID_SIZE, 0));

        assertEquals(130,
                DelayedPayoutTxValidation.checkBurningManSelectionHeight(130, delayedPayoutTxReceiverService(120)));
    }

    @Test
    void checkBurningManSelectionHeightRejectsPeerHeightZero() {
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkBurningManSelectionHeight(0, delayedPayoutTxReceiverService(10)));
    }

    @Test
    void checkBurningManSelectionHeightRejectsHeightsMoreThanOneGridApart() {
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkBurningManSelectionHeight(120, delayedPayoutTxReceiverService(140)));
    }

    @Test
    void checkBurningManSelectionHeightRejectsLocalHeightZero() {
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkBurningManSelectionHeight(10, delayedPayoutTxReceiverService(0)));
    }

    @Test
    void checkDelayedPayoutTxInputAmountAcceptsExpectedInputAmount() {
        Offer offer = TradeValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = TradeValidationTestUtils.trade(offer, Coin.valueOf(300), Coin.valueOf(20_000));
        long expectedInputAmount = 42_300;

        assertEquals(expectedInputAmount,
                DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(expectedInputAmount, trade));
    }

    @Test
    void checkDelayedPayoutTxInputAmountRejectsUnexpectedInputAmount() {
        Offer offer = TradeValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = TradeValidationTestUtils.trade(offer, Coin.valueOf(300), Coin.valueOf(20_000));

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(42_299, trade));
    }

    @Test
    void checkDelayedPayoutTxInputAmountRejectsZeroAndNegativeInputAmount() {
        Offer offer = TradeValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = TradeValidationTestUtils.trade(offer, Coin.valueOf(300), Coin.valueOf(20_000));

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(0, trade));
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(-1, trade));
    }

    @Test
    void checkDelayedPayoutTxInputAmountRejectsNullTrade() {
        assertThrows(NullPointerException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(1, null));
    }

    @Test
    void checkValueInToleranceRejectsInvalidExpectedValueAndFactor() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidationUtils.checkValueInTolerance(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> TradeValidationUtils.checkValueInTolerance(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> TradeValidationUtils.checkValueInTolerance(1, 1, 0.99));
    }

    @Test
    void checkLockTimeAcceptsExpectedLockTimeAndAllowedDeviation() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getBestChainHeight()).thenReturn(1_000);
        long expectedLockTime = 1_000 + Restrictions.getLockTime(true);

        assertEquals(expectedLockTime, DelayedPayoutTxValidation.checkLockTime(expectedLockTime,
                true,
                btcWalletService,
                true));
        assertEquals(expectedLockTime + DelayedPayoutTxValidation.MAX_LOCKTIME_BLOCK_DEVIATION,
                DelayedPayoutTxValidation.checkLockTime(expectedLockTime + DelayedPayoutTxValidation.MAX_LOCKTIME_BLOCK_DEVIATION,
                        true,
                        btcWalletService,
                        true));
    }

    @Test
    void checkLockTimeRejectsLockTimeBeyondAllowedDeviation() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getBestChainHeight()).thenReturn(1_000);
        long invalidLockTime = 1_000 + Restrictions.getLockTime(false) +
                DelayedPayoutTxValidation.MAX_LOCKTIME_BLOCK_DEVIATION + 1;

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkLockTime(invalidLockTime, false, btcWalletService, true));
    }

    @Test
    void checkLockTimeSkipsHeightToleranceOnNonMainnet() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getBestChainHeight()).thenReturn(1_000);
        long lockTimeOutsideMainnetTolerance = 1_000 + Restrictions.getLockTime(false) +
                DelayedPayoutTxValidation.MAX_LOCKTIME_BLOCK_DEVIATION + 1;

        assertEquals(lockTimeOutsideMainnetTolerance,
                DelayedPayoutTxValidation.checkLockTime(lockTimeOutsideMainnetTolerance, false, btcWalletService, false));
    }

    @Test
    void checkLockTimeRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> DelayedPayoutTxValidation.checkLockTime(1, true, null, true));
    }

    @Test
    void checkLockTimeRejectsNonPositiveLockTime() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkLockTime(0, true, btcWalletService, true));
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkLockTime(-1, true, btcWalletService, true));
    }

    private static DelayedPayoutTxReceiverService delayedPayoutTxReceiverService(int burningManSelectionHeight) {
        DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = mock(DelayedPayoutTxReceiverService.class);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight()).thenReturn(burningManSelectionHeight);
        return delayedPayoutTxReceiverService;
    }
}
