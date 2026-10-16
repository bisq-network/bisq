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

import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferMaker;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bisq_v1.messages.MediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.ProcessMediatedPayoutTxPublishedMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.Task;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DisputeProtocol#handle(MediatedPayoutTxPublishedMessage, NodeAddress) on a real trade: once the trade is in
 * PAYOUT_PUBLISHED, the message is processed only if the node has its own payout.
 */
class MediatedPayoutTxPublishedMessageHandlingTest {
    private static final NodeAddress PEER = new NodeAddress("peer.onion:9999");

    // The peer had our signature of the mediated payout, did not publish it, and the refund agent paid out the
    // trade. The message must not set the mediated payout as the payout of the closed trade.
    @Test
    void messageIsRejectedAfterTheRefundAgentClosedTheTrade() {
        Trade trade = trade(Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG,
                Trade.DisputeState.REFUND_REQUEST_CLOSED, false);
        TestDisputeProtocol protocol = new TestDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertTrue(protocol.executedTasks.isEmpty());
    }

    // The mediation closed the trade, but the wallet does not know our payout yet, for example after a resync
    @Test
    void messageIsRejectedWhileOurPayoutIsNotKnown() {
        Trade trade = trade(Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG,
                Trade.DisputeState.MEDIATION_CLOSED, false);
        TestDisputeProtocol protocol = new TestDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertTrue(protocol.executedTasks.isEmpty());
    }

    // Both traders published the mediated payout, ours first
    @Test
    void messageIsProcessedAfterOurOwnPayout() {
        Trade trade = trade(Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG,
                Trade.DisputeState.MEDIATION_CLOSED, true);
        TestDisputeProtocol protocol = new TestDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertEquals(List.of(ProcessMediatedPayoutTxPublishedMessage.class), protocol.executedTasks);
    }

    // Positive control: before PAYOUT_PUBLISHED the message sets the payout as before
    @Test
    void messageIsProcessedWhileWaitingForThePayout() {
        Trade trade = trade(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG,
                Trade.DisputeState.MEDIATION_CLOSED, false);
        TestDisputeProtocol protocol = new TestDisputeProtocol(trade);

        protocol.handle(message(trade), PEER);

        assertEquals(List.of(ProcessMediatedPayoutTxPublishedMessage.class), protocol.executedTasks);
    }

    private static MediatedPayoutTxPublishedMessage message(Trade trade) {
        return new MediatedPayoutTxPublishedMessage(trade.getId(), new byte[]{1}, PEER, "uid");
    }

    private static Trade trade(Trade.State state, Trade.DisputeState disputeState, boolean hasOwnPayout) {
        Offer offer = make(a(OfferMaker.Offer, with(OfferMaker.direction, OfferDirection.SELL)));
        ProcessModel processModel = new ProcessModel(offer.getId(), "account-id",
                new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic()));
        processModel.applyTransient(mock(Provider.class), mock(TradeManager.class), offer);
        Trade trade = new SellerAsMakerTrade(offer,
                Coin.valueOf(1_000) /* txFee */,
                Coin.valueOf(100) /* takerFee */,
                true,
                null /* arbitratorNodeAddress */,
                null /* mediatorNodeAddress */,
                null /* refundAgentNodeAddress */,
                null /* btcWalletService */,
                processModel,
                "uid");
        trade.setState(state);
        trade.setDisputeState(disputeState);
        if (hasOwnPayout) {
            Transaction payoutTx = mock(Transaction.class);
            when(payoutTx.getTxId()).thenReturn(Sha256Hash.ZERO_HASH);
            trade.setPayoutTx(payoutTx);
        }
        return trade;
    }

    // Records the tasks of a handled message, without running them.
    private static final class TestDisputeProtocol extends DisputeProtocol {
        private final List<Class<? extends Task<TradeModel>>> executedTasks = new ArrayList<>();

        private TestDisputeProtocol(Trade trade) {
            super(trade);
        }

        @Override
        protected FluentProtocol expect(FluentProtocol.Condition condition) {
            return new FluentProtocol(this) {
                private Class<? extends Task<TradeModel>>[] tasks;

                {
                    condition(condition);
                }

                @Override
                public FluentProtocol setup(Setup setup) {
                    tasks = setup.getTasks();
                    return this;
                }

                // Records the tasks only if the condition is met, as executeTasks would run them
                @Override
                public FluentProtocol executeTasks() {
                    return run(() -> executedTasks.addAll(List.of(tasks)));
                }
            };
        }
    }
}
