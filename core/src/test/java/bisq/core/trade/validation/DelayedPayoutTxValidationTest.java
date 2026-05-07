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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.script.ScriptBuilder;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.ValidationTestUtils.GENESIS_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelayedPayoutTxValidationTest {
    static final int GRID_SIZE = DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE;

    /* --------------------------------------------------------------------- */
    // Delayed payout tx
    /* --------------------------------------------------------------------- */

    @Test
    void checkDelayedPayoutTxAcceptsExpectedTransaction() {
        Trade trade = tradeWithLockTime(144);
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144);

        assertEquals(delayedPayoutTx,
                DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx,
                        trade,
                        ValidationTestUtils.btcWalletService()));
    }

    @Test
    void checkDelayedPayoutTxRejectsUnexpectedLockTime() {
        Trade trade = tradeWithLockTime(144);
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 145);

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx,
                        trade,
                        ValidationTestUtils.btcWalletService()));
    }

    @Test
    void checkDelayedPayoutTxRejectsUnexpectedSequence() {
        Trade trade = tradeWithLockTime(144);
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144);
        delayedPayoutTx.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE);

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx,
                        trade,
                        ValidationTestUtils.btcWalletService()));
    }

    @Test
    void checkDelayedPayoutTxRejectsUnexpectedInputCount() {
        Trade trade = tradeWithLockTime(144);
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144);
        delayedPayoutTx.addInput(depositTx(1).getOutput(0));

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx,
                        trade,
                        ValidationTestUtils.btcWalletService()));
    }

    @Test
    void checkDelayedPayoutTxRejectsStructurallyInvalidTransaction() {
        Trade trade = tradeWithLockTime(144);
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = new Transaction(ValidationTestUtils.PARAMS);
        delayedPayoutTx.addInput(depositTx.getOutput(0));
        delayedPayoutTx.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE - 1);
        delayedPayoutTx.setLockTime(144);

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx,
                        trade,
                        ValidationTestUtils.btcWalletService()));
    }

    @Test
    void checkDelayedPayoutTxRejectsNullArguments() {
        Trade trade = tradeWithLockTime(144);
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144);
        BtcWalletService btcWalletService = ValidationTestUtils.btcWalletService();

        assertThrows(NullPointerException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(null, trade, btcWalletService));
        assertThrows(NullPointerException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx, null, btcWalletService));
        assertThrows(NullPointerException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx, trade, null));
    }

    @Test
    void checkDelayedPayoutTxAcceptsExpectedLegacyBurningManOutput() {
        String outputAddress = SegwitAddress.fromKey(ValidationTestUtils.PARAMS, new ECKey()).toString();
        Trade trade = legacyBurningManTrade(144,
                Coin.valueOf(10_000),
                Coin.valueOf(12_000),
                Coin.valueOf(20_000));
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx,
                144,
                Coin.valueOf(42_000),
                outputAddress);
        AtomicReference<String> consumedAddress = new AtomicReference<>();

        assertEquals(delayedPayoutTx,
                DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx,
                        trade,
                        ValidationTestUtils.btcWalletService(),
                        consumedAddress::set));
        assertEquals(outputAddress, consumedAddress.get());
    }

    @Test
    void checkDelayedPayoutTxRejectsUnexpectedLegacyBurningManOutputAmount() {
        Trade trade = legacyBurningManTrade(144,
                Coin.valueOf(10_000),
                Coin.valueOf(12_000),
                Coin.valueOf(20_000));
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144, Coin.valueOf(41_999));

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx,
                        trade,
                        ValidationTestUtils.btcWalletService()));
    }

    @Test
    void checkDelayedPayoutTxRejectsUnexpectedLegacyBurningManOutputCount() {
        Trade trade = legacyBurningManTrade(144,
                Coin.valueOf(10_000),
                Coin.valueOf(12_000),
                Coin.valueOf(20_000));
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144, Coin.valueOf(42_000));
        delayedPayoutTx.addOutput(Coin.SATOSHI, ScriptBuilder.createP2WPKHOutputScript(new ECKey()));

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTx(delayedPayoutTx,
                        trade,
                        ValidationTestUtils.btcWalletService()));
    }

    @Test
    void checkDelayedPayoutTxInputAcceptsInputSpendingDepositTxOutputZero() {
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144);

        assertEquals(delayedPayoutTx,
                DelayedPayoutTxValidation.checkDelayedPayoutTxInput(delayedPayoutTx, depositTx));
    }

    @Test
    void checkDelayedPayoutTxInputRejectsInputNotSpendingDepositTxOutputZero() {
        Transaction depositTx = depositTx();
        Transaction otherDepositTx = depositTx(1);
        Transaction delayedPayoutTx = delayedPayoutTx(otherDepositTx, 144);

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInput(delayedPayoutTx, depositTx));
    }

    @Test
    void checkDelayedPayoutTxInputRejectsNullTransactions() {
        Transaction depositTx = depositTx();
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144);

        assertThrows(NullPointerException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInput(null, depositTx));
        assertThrows(NullPointerException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInput(delayedPayoutTx, null));
    }

    /* --------------------------------------------------------------------- */
    // Burning Man selection height
    /* --------------------------------------------------------------------- */

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

    /* --------------------------------------------------------------------- */
    // Delayed payout tx input amount
    /* --------------------------------------------------------------------- */

    @Test
    void checkDelayedPayoutTxInputAmountAcceptsExpectedInputAmount() {
        Offer offer = ValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = ValidationTestUtils.trade(offer, Coin.valueOf(300), Coin.valueOf(20_000));
        long expectedInputAmount = 42_300;

        assertEquals(expectedInputAmount,
                DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(expectedInputAmount, trade));
    }

    @Test
    void checkDelayedPayoutTxInputAmountRejectsUnexpectedInputAmount() {
        Offer offer = ValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = ValidationTestUtils.trade(offer, Coin.valueOf(300), Coin.valueOf(20_000));

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(42_299, trade));
    }

    @Test
    void checkDelayedPayoutTxInputAmountRejectsZeroAndNegativeInputAmount() {
        Offer offer = ValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = ValidationTestUtils.trade(offer, Coin.valueOf(300), Coin.valueOf(20_000));

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
    void checkDelayedPayoutTxInputAmountRejectsMissingTradeOffer() {
        Trade trade = mock(Trade.class);
        when(trade.getAmountAsLong()).thenReturn(20_000L);
        when(trade.getTradeTxFeeAsLong()).thenReturn(300L);

        assertThrows(NullPointerException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(42_300, trade));
    }

    @Test
    void checkDelayedPayoutTxInputAmountRejectsNonPositiveTradeFields() {
        Offer offer = ValidationTestUtils.offer(true, Coin.ZERO, Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = ValidationTestUtils.trade(offer, Coin.valueOf(300), Coin.valueOf(20_000));

        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(32_300, trade));

        Offer offerWithPositiveDeposits = ValidationTestUtils.offer(true,
                Coin.valueOf(10_000),
                Coin.valueOf(12_000),
                Coin.valueOf(40_000));
        Trade tradeWithoutAmount = ValidationTestUtils.trade(offerWithPositiveDeposits, Coin.valueOf(300), Coin.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount(22_300, tradeWithoutAmount));
    }

    /* --------------------------------------------------------------------- */
    // Lock time
    /* --------------------------------------------------------------------- */

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

    private static Trade tradeWithLockTime(long lockTime) {
        Trade trade = mock(Trade.class);
        when(trade.getLockTime()).thenReturn(lockTime);
        when(trade.isUsingLegacyBurningMan()).thenReturn(false);
        return trade;
    }

    private static Trade legacyBurningManTrade(long lockTime,
                                               Coin buyerSecurityDeposit,
                                               Coin sellerSecurityDeposit,
                                               Coin tradeAmount) {
        Offer offer = ValidationTestUtils.offer(true,
                buyerSecurityDeposit,
                sellerSecurityDeposit,
                Coin.valueOf(40_000));
        Trade trade = mock(Trade.class);
        when(trade.getLockTime()).thenReturn(lockTime);
        when(trade.isUsingLegacyBurningMan()).thenReturn(true);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getAmount()).thenReturn(tradeAmount);
        return trade;
    }

    private static Transaction depositTx() {
        return depositTx(0);
    }

    private static Transaction depositTx(long outpointIndex) {
        Transaction transaction = new Transaction(ValidationTestUtils.PARAMS);
        transaction.addInput(new TransactionInput(ValidationTestUtils.PARAMS,
                transaction,
                new byte[]{},
                new TransactionOutPoint(ValidationTestUtils.PARAMS, outpointIndex, Sha256Hash.ZERO_HASH),
                Coin.valueOf(2_000)));
        transaction.addOutput(Coin.valueOf(1_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        return transaction;
    }

    private static Transaction delayedPayoutTx(Transaction depositTx, long lockTime) {
        return delayedPayoutTx(depositTx, lockTime, Coin.valueOf(500));
    }

    private static Transaction delayedPayoutTx(Transaction depositTx, long lockTime, Coin outputAmount) {
        return delayedPayoutTx(depositTx,
                lockTime,
                outputAmount,
                SegwitAddress.fromKey(ValidationTestUtils.PARAMS, new ECKey()).toString());
    }

    private static Transaction delayedPayoutTx(Transaction depositTx,
                                               long lockTime,
                                               Coin outputAmount,
                                               String outputAddress) {
        Transaction transaction = new Transaction(ValidationTestUtils.PARAMS);
        transaction.addInput(depositTx.getOutput(0));
        transaction.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE - 1);
        transaction.setLockTime(lockTime);
        transaction.addOutput(outputAmount, Address.fromString(ValidationTestUtils.PARAMS, outputAddress));
        return transaction;
    }
}
