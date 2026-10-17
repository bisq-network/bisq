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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bisq_v1.messages.MediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.ProcessMediatedPayoutTxPublishedMessage;

import bisq.network.p2p.NodeAddress;

import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DisputeProtocol#handle(MediatedPayoutTxPublishedMessage, NodeAddress) on a real trade: once the trade is in
 * PAYOUT_PUBLISHED, the message is processed only if the node has its own payout, and during a refund request only if
 * the wallet has the mediated payout as confirmed.
 */
class MediatedPayoutTxPublishedMessageHandlingTest {
    private static final NodeAddress PEER = new NodeAddress("peer.onion:9999");
    private static final Sha256Hash PEERS_PAYOUT_TX_ID = Sha256Hash.of(new byte[]{1});

    private final BtcWalletService btcWalletService = mock(BtcWalletService.class);

    // The peer had our signature of the mediated payout, did not publish it, and the refund agent paid out the
    // trade. The message must not set the mediated payout as the payout of the closed trade.
    @Test
    void messageIsRejectedAfterTheRefundAgentClosedTheTrade() {
        Trade trade = trade(Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG,
                Trade.DisputeState.REFUND_REQUEST_CLOSED, false);
        RecordingDisputeProtocol protocol = new RecordingDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertTrue(protocol.executedTasks.isEmpty());
    }

    // The mediation closed the trade, but the wallet does not know our payout yet, for example after a resync
    @Test
    void messageIsRejectedWhileOurPayoutIsNotKnown() {
        Trade trade = trade(Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG,
                Trade.DisputeState.MEDIATION_CLOSED, false);
        RecordingDisputeProtocol protocol = new RecordingDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertTrue(protocol.executedTasks.isEmpty());
    }

    // Both traders published the mediated payout, ours first
    @Test
    void messageIsProcessedAfterOurOwnPayout() {
        Trade trade = trade(Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG,
                Trade.DisputeState.MEDIATION_CLOSED, true);
        RecordingDisputeProtocol protocol = new RecordingDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertEquals(List.of(ProcessMediatedPayoutTxPublishedMessage.class), protocol.executedTasks);
    }

    // During a refund request the delayed payout tx is published and spends the same deposit output
    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class, names = {"REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void messageIsRejectedDuringARefundRequestWhileTheWalletDoesNotKnowThePayout(Trade.DisputeState disputeState) {
        Trade trade = trade(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG, disputeState, false);
        RecordingDisputeProtocol protocol = new RecordingDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertTrue(protocol.executedTasks.isEmpty());
    }

    // An unconfirmed mediated payout can still lose against the delayed payout tx
    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class, names = {"REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void messageIsRejectedDuringARefundRequestWhileThePayoutIsUnconfirmed(Trade.DisputeState disputeState) {
        Trade trade = trade(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG, disputeState, false);
        TransactionConfidence pending = confidence(TransactionConfidence.ConfidenceType.PENDING);
        when(btcWalletService.getConfidenceForTxId(PEERS_PAYOUT_TX_ID.toString())).thenReturn(pending);
        RecordingDisputeProtocol protocol = new RecordingDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertTrue(protocol.executedTasks.isEmpty());
    }

    // A modified peer sends bytes that are no transaction
    @Test
    void messageWithAnInvalidPayoutIsRejectedDuringARefundRequest() {
        Trade trade = trade(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG,
                Trade.DisputeState.REFUND_REQUEST_STARTED_BY_PEER, false);
        when(btcWalletService.getTxFromSerializedTx(any())).thenThrow(new ProtocolException("invalid"));
        RecordingDisputeProtocol protocol = new RecordingDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertTrue(protocol.executedTasks.isEmpty());
    }

    // The peer published the mediated payout before the delayed payout tx, so the mediated payout is confirmed
    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class, names = {"REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void messageIsProcessedDuringARefundRequestOnceThePayoutIsConfirmed(Trade.DisputeState disputeState) {
        Trade trade = trade(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG, disputeState, false);
        TransactionConfidence confirmed = confidence(TransactionConfidence.ConfidenceType.BUILDING);
        when(btcWalletService.getConfidenceForTxId(PEERS_PAYOUT_TX_ID.toString())).thenReturn(confirmed);
        RecordingDisputeProtocol protocol = new RecordingDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertEquals(List.of(ProcessMediatedPayoutTxPublishedMessage.class), protocol.executedTasks);
    }

    // Positive control: before PAYOUT_PUBLISHED the message sets the payout as before
    @Test
    void messageIsProcessedWhileWaitingForThePayout() {
        Trade trade = trade(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG,
                Trade.DisputeState.MEDIATION_CLOSED, false);
        RecordingDisputeProtocol protocol = new RecordingDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertEquals(List.of(ProcessMediatedPayoutTxPublishedMessage.class), protocol.executedTasks);
    }

    private static MediatedPayoutTxPublishedMessage message(Trade trade) {
        return new MediatedPayoutTxPublishedMessage(trade.getId(), new byte[]{1}, PEER, "uid");
    }

    private static TransactionConfidence confidence(TransactionConfidence.ConfidenceType type) {
        TransactionConfidence confidence = mock(TransactionConfidence.class);
        when(confidence.getConfidenceType()).thenReturn(type);
        return confidence;
    }

    private Trade trade(Trade.State state, Trade.DisputeState disputeState, boolean hasOwnPayout) {
        Provider provider = mock(Provider.class);
        when(provider.getBtcWalletService()).thenReturn(btcWalletService);
        Transaction peersPayoutTx = mock(Transaction.class);
        when(peersPayoutTx.getTxId()).thenReturn(PEERS_PAYOUT_TX_ID);
        when(btcWalletService.getTxFromSerializedTx(any())).thenReturn(peersPayoutTx);
        Trade trade = MediationTestTrades.sellerAsMakerTrade(provider, state, disputeState);
        if (hasOwnPayout) {
            Transaction payoutTx = mock(Transaction.class);
            when(payoutTx.getTxId()).thenReturn(Sha256Hash.ZERO_HASH);
            trade.setPayoutTx(payoutTx);
        }
        return trade;
    }
}
