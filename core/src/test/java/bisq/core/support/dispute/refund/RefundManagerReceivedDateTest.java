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

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.persistence.PersistenceManager;

import java.security.PublicKey;

import java.time.Instant;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefundManagerReceivedDateTest {
    private static final String TRADE_ID = "trade-id";
    private static final int TRADER_ID = 0;
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer.onion", 9999);

    @Test
    void disputeResultMessageGetsTheLocalReceiveTime() {
        PubKeyRing agentPubKeyRing = pubKeyRing();
        RefundDisputeListService disputeListService = refundDisputeListService();
        Dispute dispute = dispute(agentPubKeyRing);
        disputeListService.getDisputeList().add(dispute);
        RefundManager manager = refundManager(tradeManager(agentPubKeyRing), disputeListService);

        ChatMessage chatMessage = new ChatMessage(SupportType.REFUND,
                TRADE_ID,
                TRADER_ID,
                false,
                "result",
                PEER_NODE_ADDRESS);
        // A receive time the agent could put into the message, it must not be kept
        chatMessage.setReceivedDate(42);
        DisputeResult disputeResult = new DisputeResult(TRADE_ID, TRADER_ID);
        disputeResult.setChatMessage(chatMessage);
        DisputeResultMessage message = new DisputeResultMessage(disputeResult,
                PEER_NODE_ADDRESS,
                "dispute-result-uid",
                SupportType.REFUND,
                null);

        long before = System.currentTimeMillis();
        manager.onDisputeResultMessage(message, agentPubKeyRing.getSignaturePubKey());

        assertTrue(dispute.getChatMessages().contains(chatMessage));
        long receivedDate = chatMessage.getReceivedDate();
        assertTrue(receivedDate >= before && receivedDate <= System.currentTimeMillis(),
                "receivedDate=" + receivedDate);
    }

    // The agent's node can send the result directly and store it in the mailbox as well, so a second copy arrives.
    // The copy is not stored and not applied again.
    @Test
    void aSecondCopyOfTheResultIsNotStoredAgain() {
        // The message version, which the application sets at startup, is needed for the proto
        int baseCurrencyNetwork = Version.getBaseCurrencyNetwork();
        Version.setBaseCryptoNetworkId(BaseCurrencyNetwork.BTC_REGTEST.ordinal());
        try {
            PubKeyRing agentPubKeyRing = pubKeyRing();
            RefundDisputeListService disputeListService = refundDisputeListService();
            Dispute dispute = dispute(agentPubKeyRing);
            disputeListService.getDisputeList().add(dispute);
            RefundManager manager = refundManager(tradeManager(agentPubKeyRing), disputeListService);
            ChatMessage chatMessage = new ChatMessage(SupportType.REFUND,
                    TRADE_ID,
                    TRADER_ID,
                    false,
                    "result",
                    PEER_NODE_ADDRESS);
            ChatMessage copy = ChatMessage.fromProto(chatMessage.toProtoNetworkEnvelope().getChatMessage(),
                    chatMessage.getMessageVersion());
            PublicKey agentKey = agentPubKeyRing.getSignaturePubKey();
            int numStoredMessages = dispute.getChatMessages().size();

            manager.onDisputeResultMessage(resultMessage(chatMessage, SupportType.REFUND), agentKey);
            long receivedDate = chatMessage.getReceivedDate();
            DisputeResult firstResult = dispute.disputeResultProperty().get();
            manager.onDisputeResultMessage(resultMessage(copy, SupportType.REFUND), agentKey);

            assertEquals(numStoredMessages + 1, dispute.getChatMessages().size());
            assertSame(chatMessage, dispute.getChatMessages().get(numStoredMessages));
            assertEquals(receivedDate, chatMessage.getReceivedDate());
            assertSame(firstResult, dispute.disputeResultProperty().get());
        } finally {
            Version.setBaseCryptoNetworkId(baseCurrencyNetwork);
        }
    }

    private static DisputeResultMessage resultMessage(ChatMessage chatMessage, SupportType supportType) {
        DisputeResult disputeResult = new DisputeResult(TRADE_ID, TRADER_ID);
        disputeResult.setChatMessage(chatMessage);
        return new DisputeResultMessage(disputeResult,
                PEER_NODE_ADDRESS,
                "dispute-result-uid",
                supportType,
                null);
    }

    private static RefundManager refundManager(TradeManager tradeManager,
                                               RefundDisputeListService disputeListService) {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
        NetworkNode networkNode = mock(NetworkNode.class);
        when(networkNode.getNodeAddress()).thenReturn(new NodeAddress("trader.onion", 9999));
        when(p2PService.getNetworkNode()).thenReturn(networkNode);
        ClosedTradableManager closedTradableManager = mock(ClosedTradableManager.class);
        when(closedTradableManager.getClosedTrades()).thenReturn(List.of());
        when(closedTradableManager.getSafeDateForSensitiveDataClearing()).thenReturn(Instant.EPOCH);
        FailedTradesManager failedTradesManager = mock(FailedTradesManager.class);
        when(failedTradesManager.getTradeById(anyString())).thenReturn(Optional.empty());
        return new RefundManager(p2PService,
                mock(TradeWalletService.class),
                mock(BtcWalletService.class),
                mock(WalletsSetup.class),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                mock(OpenOfferManager.class),
                mock(DaoFacade.class),
                mock(DelayedPayoutTxReceiverService.class),
                mock(KeyRing.class),
                disputeListService,
                mock(Config.class),
                mock(PriceFeedService.class),
                mock(MempoolService.class),
                mock(RefundPayoutReceiptService.class));
    }

    private static TradeManager tradeManager(PubKeyRing agentPubKeyRing) {
        Trade trade = mock(Trade.class);
        when(trade.getRefundAgentPubKeyRing()).thenReturn(agentPubKeyRing);
        TradeManager tradeManager = mock(TradeManager.class);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(trade));
        return tradeManager;
    }

    private static Dispute dispute(PubKeyRing agentPubKeyRing) {
        return new Dispute(
                0,
                TRADE_ID,
                TRADER_ID,
                true,
                true,
                pubKeyRing(),
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                null,
                null,
                agentPubKeyRing,
                false,
                SupportType.REFUND);
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
    }

    @SuppressWarnings("unchecked")
    private static RefundDisputeListService refundDisputeListService() {
        return new RefundDisputeListService(mock(PersistenceManager.class));
    }
}
