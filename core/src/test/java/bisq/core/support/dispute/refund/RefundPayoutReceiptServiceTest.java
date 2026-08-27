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

package bisq.core.support.dispute.refund;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;

import bisq.common.persistence.PersistenceManager;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundPayoutReceiptServiceTest {
    private static final String DEPOSIT_A = "ab".repeat(32);
    private static final String DEPOSIT_B = "cd".repeat(32);
    private static final String DELAYED_PAYOUT_A = "12".repeat(32);
    private static final String DELAYED_PAYOUT_B = "34".repeat(32);
    private static final String PAYOUT_TX_ID = "56".repeat(32);

    private RefundDisputeList disputeList;
    private PersistenceManager<RefundDisputeList> persistenceManager;
    private BtcWalletService btcWalletService;
    private RefundPayoutReceiptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        disputeList = new RefundDisputeList();
        RefundDisputeListService disputeListService = mock(RefundDisputeListService.class);
        persistenceManager = mock(PersistenceManager.class);
        btcWalletService = mock(BtcWalletService.class);

        when(disputeListService.getDisputeList()).thenReturn(disputeList);
        when(disputeListService.getPersistenceManager()).thenReturn(persistenceManager);
        when(btcWalletService.getTransactions(true)).thenReturn(Set.of());

        service = new RefundPayoutReceiptService(disputeListService, btcWalletService);
    }

    @Test
    void findsPersistedPayoutAcrossRowsUsingCanonicalTxIds() {
        disputeList.add(dispute(DEPOSIT_A, DELAYED_PAYOUT_A, PAYOUT_TX_ID));
        Dispute replay = dispute(DEPOSIT_A.toUpperCase(), DELAYED_PAYOUT_A.toUpperCase(), null);
        disputeList.add(replay);

        assertEquals(PAYOUT_TX_ID, service.findPayoutTxId(replay).orElseThrow());
    }

    @Test
    void treatsEitherSharedFundingTransactionAsConsumed() {
        disputeList.add(dispute(DEPOSIT_A, DELAYED_PAYOUT_A, PAYOUT_TX_ID));

        assertEquals(PAYOUT_TX_ID,
                service.findPayoutTxId(dispute(DEPOSIT_A, DELAYED_PAYOUT_B, null)).orElseThrow());
        assertEquals(PAYOUT_TX_ID,
                service.findPayoutTxId(dispute(DEPOSIT_B, DELAYED_PAYOUT_A, null)).orElseThrow());
        assertTrue(service.findPayoutTxId(dispute(DEPOSIT_B, DELAYED_PAYOUT_B, null)).isEmpty());
    }

    @Test
    void findsPayoutFromWalletMemoWhenDisputeMarkerIsMissing() {
        Transaction walletTx = mock(Transaction.class);
        when(walletTx.getMemo()).thenReturn(
                RefundPayoutReceipt.MEMO_PREFIX + DEPOSIT_A + ":" + DELAYED_PAYOUT_A);
        when(walletTx.getTxId()).thenReturn(Sha256Hash.wrap(PAYOUT_TX_ID));
        when(btcWalletService.getTransactions(true)).thenReturn(Set.of(walletTx));

        assertEquals(PAYOUT_TX_ID,
                service.findPayoutTxId(dispute(DEPOSIT_A, DELAYED_PAYOUT_A, null)).orElseThrow());
    }

    @Test
    void capsPayoutByBothContractAndOnChainReceipt() {
        Coin contractPayoutAmount = Coin.valueOf(100_000);

        assertEquals(contractPayoutAmount, RefundPayoutReceiptService.calculateMaximumPayoutAmount(
                contractPayoutAmount,
                Coin.valueOf(101_000),
                1_000));
        assertEquals(Coin.valueOf(100_000), RefundPayoutReceiptService.calculateMaximumPayoutAmount(
                Coin.valueOf(200_000),
                Coin.valueOf(101_000),
                1_000));
        assertEquals(contractPayoutAmount, RefundPayoutReceiptService.calculateMaximumPayoutAmount(
                contractPayoutAmount,
                Coin.valueOf(101_000),
                0));
        assertEquals(Coin.ZERO, RefundPayoutReceiptService.calculateMaximumPayoutAmount(
                contractPayoutAmount,
                Coin.valueOf(1_000),
                2_000));
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistsReservationBeforeCompletingAndMarksEveryMatchingRow() {
        Dispute selectedDispute = dispute(DEPOSIT_A, DELAYED_PAYOUT_A, null);
        Dispute peerDispute = dispute(DEPOSIT_A, DELAYED_PAYOUT_A, null);
        disputeList.add(selectedDispute);
        disputeList.add(peerDispute);
        Transaction payoutTx = mock(Transaction.class);
        when(payoutTx.getTxId()).thenReturn(Sha256Hash.wrap(PAYOUT_TX_ID));
        AtomicBoolean completed = new AtomicBoolean();
        doAnswer(invocation -> {
            assertEquals(PAYOUT_TX_ID, selectedDispute.getDisputePayoutTxId());
            assertEquals(PAYOUT_TX_ID, peerDispute.getDisputePayoutTxId());
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(persistenceManager).persistNow(any(Runnable.class), any(Consumer.class));

        service.persistPayoutReservation(selectedDispute,
                payoutTx,
                () -> completed.set(true),
                throwable -> {
                });

        assertTrue(completed.get());
        assertTrue(selectedDispute.isPayoutDone());
        assertTrue(peerDispute.isPayoutDone());
        verify(payoutTx).setMemo(RefundPayoutReceipt.MEMO_PREFIX + DEPOSIT_A + ":" + DELAYED_PAYOUT_A);
    }

    @Test
    void refusesAnotherReservationAfterReceiptWasMarked() {
        Dispute paidDispute = dispute(DEPOSIT_A, DELAYED_PAYOUT_A, PAYOUT_TX_ID);
        Dispute replayDispute = dispute(DEPOSIT_A, DELAYED_PAYOUT_A, null);
        disputeList.add(paidDispute);
        disputeList.add(replayDispute);

        assertThrows(IllegalArgumentException.class, () -> service.persistPayoutReservation(
                replayDispute,
                mock(Transaction.class),
                () -> {
                },
                throwable -> {
                }));
    }

    @Test
    void refusesToReservePayoutForUnstoredDispute() {
        Transaction payoutTx = mock(Transaction.class);
        when(payoutTx.getTxId()).thenReturn(Sha256Hash.wrap(PAYOUT_TX_ID));

        assertThrows(IllegalArgumentException.class, () -> service.persistPayoutReservation(
                dispute(DEPOSIT_A, DELAYED_PAYOUT_A, null),
                payoutTx,
                () -> {
                },
                throwable -> {
                }));
    }

    private static Dispute dispute(String depositTxId, String delayedPayoutTxId, String payoutTxId) {
        Dispute dispute = mock(Dispute.class);
        AtomicReference<String> payoutTxIdState = new AtomicReference<>(payoutTxId);
        AtomicBoolean payoutDoneState = new AtomicBoolean(payoutTxId != null);

        when(dispute.getSupportType()).thenReturn(SupportType.REFUND);
        when(dispute.getDepositTxId()).thenReturn(depositTxId);
        when(dispute.getDelayedPayoutTxId()).thenReturn(delayedPayoutTxId);
        when(dispute.getDisputePayoutTxId()).thenAnswer(invocation -> payoutTxIdState.get());
        when(dispute.isPayoutDone()).thenAnswer(invocation -> payoutDoneState.get());
        doAnswer(invocation -> {
            payoutTxIdState.set(invocation.getArgument(0));
            return null;
        }).when(dispute).setDisputePayoutTxId(any());
        doAnswer(invocation -> {
            payoutDoneState.set(invocation.getArgument(0));
            return null;
        }).when(dispute).setPayoutDone(any(Boolean.class));
        return dispute;
    }
}
