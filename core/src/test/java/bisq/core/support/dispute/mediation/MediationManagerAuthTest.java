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

package bisq.core.support.dispute.mediation;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Contract;
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
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.persistence.PersistenceManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.security.KeyPair;
import java.security.PublicKey;

import java.io.File;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediationManagerAuthTest {
    private static final String TRADE_ID = "trade-id";
    private static final int TRADER_ID = 0;
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer.onion", 9999);

    @TempDir
    private File keyStorageDir;

    @Test
    void disputeResultSignedByNonAgentDoesNotMutateDispute() {
        PersistenceManager<MediationDisputeList> persistenceManager = persistenceManager();
        MediationDisputeListService disputeListService = new MediationDisputeListService(persistenceManager);

        PubKeyRing expectedAgentPubKeyRing = pubKeyRing();
        PubKeyRing nonAgentPubKeyRing = pubKeyRing();
        Dispute dispute = dispute(expectedAgentPubKeyRing);
        disputeListService.getDisputeList().add(dispute);

        TradeManager tradeManager = tradeManager(expectedAgentPubKeyRing);
        MediationManager manager = mediationManager(tradeManager, disputeListService);

        ChatMessage chatMessage = new ChatMessage(SupportType.MEDIATION,
                TRADE_ID,
                TRADER_ID,
                false,
                "result",
                PEER_NODE_ADDRESS);
        DisputeResult disputeResult = new DisputeResult(TRADE_ID, TRADER_ID);
        disputeResult.setChatMessage(chatMessage);
        DisputeResultMessage message = new DisputeResultMessage(disputeResult,
                PEER_NODE_ADDRESS,
                "dispute-result-uid",
                SupportType.MEDIATION,
                null);

        manager.onDisputeResultMessage(message, nonAgentPubKeyRing.getSignaturePubKey());

        assertFalse(dispute.getChatMessages().contains(chatMessage));
        assertFalse(dispute.isResultProposed());
        verify(persistenceManager, never()).requestPersistence();
    }

    @Test
    void disputeResultMessageGetsTheLocalReceiveTime() {
        PersistenceManager<MediationDisputeList> persistenceManager = persistenceManager();
        MediationDisputeListService disputeListService = new MediationDisputeListService(persistenceManager);

        PubKeyRing agentPubKeyRing = pubKeyRing();
        Dispute dispute = dispute(agentPubKeyRing);
        disputeListService.getDisputeList().add(dispute);
        MediationManager manager = mediationManager(tradeManager(agentPubKeyRing), disputeListService);

        ChatMessage chatMessage = new ChatMessage(SupportType.MEDIATION,
                TRADE_ID,
                TRADER_ID,
                false,
                "result",
                PEER_NODE_ADDRESS);
        // A receive time and flags the agent could put into the message, they must not be kept
        chatMessage.setReceivedDate(42);
        chatMessage.setSystemMessage(true);
        chatMessage.setWasDisplayed(true);
        DisputeResult disputeResult = new DisputeResult(TRADE_ID, TRADER_ID);
        disputeResult.setChatMessage(chatMessage);
        DisputeResultMessage message = new DisputeResultMessage(disputeResult,
                PEER_NODE_ADDRESS,
                "dispute-result-uid",
                SupportType.MEDIATION,
                null);

        long before = System.currentTimeMillis();
        manager.onDisputeResultMessage(message, agentPubKeyRing.getSignaturePubKey());

        assertTrue(dispute.getChatMessages().contains(chatMessage));
        long receivedDate = chatMessage.getReceivedDate();
        assertTrue(receivedDate >= before && receivedDate <= System.currentTimeMillis(),
                "receivedDate=" + receivedDate);
        assertFalse(chatMessage.isSystemMessage());
        assertFalse(chatMessage.isWasDisplayed());
    }

    // The agent's node can send the result directly and store it in the mailbox as well, so a second copy arrives.
    // The copy is not stored and not applied again.
    @Test
    void aSecondCopyOfTheResultIsNotStoredAgain() {
        // The message version, which the application sets at startup, is needed for the proto
        int baseCurrencyNetwork = Version.getBaseCurrencyNetwork();
        Version.setBaseCryptoNetworkId(BaseCurrencyNetwork.BTC_REGTEST.ordinal());
        try {
            PersistenceManager<MediationDisputeList> persistenceManager = persistenceManager();
            MediationDisputeListService disputeListService = new MediationDisputeListService(persistenceManager);
            PubKeyRing agentPubKeyRing = pubKeyRing();
            Dispute dispute = dispute(agentPubKeyRing);
            disputeListService.getDisputeList().add(dispute);
            MediationManager manager = mediationManager(tradeManager(agentPubKeyRing), disputeListService);
            ChatMessage chatMessage = new ChatMessage(SupportType.MEDIATION,
                    TRADE_ID,
                    TRADER_ID,
                    false,
                    "result",
                    PEER_NODE_ADDRESS);
            ChatMessage copy = ChatMessage.fromProto(chatMessage.toProtoNetworkEnvelope().getChatMessage(),
                    chatMessage.getMessageVersion());
            PublicKey agentKey = agentPubKeyRing.getSignaturePubKey();
            int numStoredMessages = dispute.getChatMessages().size();

            manager.onDisputeResultMessage(resultMessage(chatMessage, SupportType.MEDIATION), agentKey);
            long receivedDate = chatMessage.getReceivedDate();
            DisputeResult firstResult = dispute.disputeResultProperty().get();
            // The trader closed the ticket meanwhile
            dispute.setIsClosed();
            manager.onDisputeResultMessage(resultMessage(copy, SupportType.MEDIATION), agentKey);

            assertEquals(numStoredMessages + 1, dispute.getChatMessages().size());
            assertSame(chatMessage, dispute.getChatMessages().get(numStoredMessages));
            assertEquals(receivedDate, chatMessage.getReceivedDate());
            assertSame(firstResult, dispute.disputeResultProperty().get());
            assertTrue(dispute.isClosed());
        } finally {
            Version.setBaseCryptoNetworkId(baseCurrencyNetwork);
        }
    }

    // The cleanup of sensitive data removes the message of the result from the chat of a closed dispute. A copy that
    // arrives later, from the mailbox, is still recognized by the stored result.
    @Test
    void aCopyOfTheResultAfterTheCleanupOfTheChatIsNotAppliedAgain() {
        // The message version, which the application sets at startup, is needed for the proto
        int baseCurrencyNetwork = Version.getBaseCurrencyNetwork();
        Version.setBaseCryptoNetworkId(BaseCurrencyNetwork.BTC_REGTEST.ordinal());
        try {
            PersistenceManager<MediationDisputeList> persistenceManager = persistenceManager();
            MediationDisputeListService disputeListService = new MediationDisputeListService(persistenceManager);
            PubKeyRing agentPubKeyRing = pubKeyRing();
            Dispute dispute = dispute(agentPubKeyRing);
            // The cleanup keeps the first message of a chat
            dispute.addAndPersistChatMessage(chatMessage("first message"));
            disputeListService.getDisputeList().add(dispute);
            MediationManager manager = mediationManager(tradeManager(agentPubKeyRing), disputeListService);
            ChatMessage chatMessage = chatMessage("result");
            ChatMessage copy = ChatMessage.fromProto(chatMessage.toProtoNetworkEnvelope().getChatMessage(),
                    chatMessage.getMessageVersion());
            PublicKey agentKey = agentPubKeyRing.getSignaturePubKey();

            manager.onDisputeResultMessage(resultMessage(chatMessage, SupportType.MEDIATION), agentKey);
            DisputeResult firstResult = dispute.disputeResultProperty().get();
            dispute.setIsClosed();
            assertTrue(dispute.removeAllChatMessages());
            manager.onDisputeResultMessage(resultMessage(copy, SupportType.MEDIATION), agentKey);

            assertEquals(1, dispute.getChatMessages().size());
            assertSame(firstResult, dispute.disputeResultProperty().get());
            assertTrue(dispute.isClosed());
        } finally {
            Version.setBaseCryptoNetworkId(baseCurrencyNetwork);
        }
    }

    // The agent sets its result before it stores the message of the result, which must still be stored
    @Test
    void theAgentStoresTheMessageOfItsOwnResult() {
        Dispute dispute = dispute(pubKeyRing());
        ChatMessage chatMessage = chatMessage("result");
        DisputeResult disputeResult = new DisputeResult(TRADE_ID, TRADER_ID);
        dispute.setDisputeResult(disputeResult);
        disputeResult.setChatMessage(chatMessage);

        dispute.addAndPersistChatMessage(chatMessage);

        assertTrue(dispute.getChatMessages().contains(chatMessage));
    }

    // The trader sends the mediator a notice about a change of the disputed trade as a system message. A chat message
    // of the peer is never a system message, so the mediator sees it as a message of the trader.
    @Test
    void theMediatorSeesASystemMessageOfTheTraderAsAMessageOfTheTrader() {
        PersistenceManager<MediationDisputeList> persistenceManager = persistenceManager();
        MediationDisputeListService disputeListService = new MediationDisputeListService(persistenceManager);
        KeyPair traderSignatureKeyPair = Sig.generateKeyPair();
        PubKeyRing traderPubKeyRing = new PubKeyRing(traderSignatureKeyPair.getPublic(),
                Encryption.generateKeyPair().getPublic());
        Contract contract = mock(Contract.class);
        when(contract.getBuyerPubKeyRing()).thenReturn(traderPubKeyRing);
        when(contract.getBuyerNodeAddress()).thenReturn(PEER_NODE_ADDRESS);
        // The mediation manager loads the same key ring, so we are the mediator of the dispute
        PubKeyRing mediatorPubKeyRing = new KeyRing(new KeyStorage(keyStorageDir)).getPubKeyRing();
        Dispute dispute = dispute(traderPubKeyRing, contract, mediatorPubKeyRing);
        disputeListService.getDisputeList().add(dispute);
        MediationManager manager = mediationManager(tradeManager(mediatorPubKeyRing), disputeListService);
        ChatMessage tradeUpdate = new ChatMessage(SupportType.MEDIATION,
                TRADE_ID,
                TRADER_ID,
                true,
                "trade update",
                PEER_NODE_ADDRESS);
        tradeUpdate.setSystemMessage(true);
        tradeUpdate.setWasDisplayed(true);

        manager.onSupportMessage(tradeUpdate, traderSignatureKeyPair.getPublic());

        assertEquals(List.of(tradeUpdate), dispute.getChatMessages());
        assertFalse(tradeUpdate.isSystemMessage());
        assertFalse(tradeUpdate.isWasDisplayed());
    }

    private static ChatMessage chatMessage(String text) {
        return new ChatMessage(SupportType.MEDIATION,
                TRADE_ID,
                TRADER_ID,
                false,
                text,
                PEER_NODE_ADDRESS);
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

    private MediationManager mediationManager(TradeManager tradeManager,
                                             MediationDisputeListService disputeListService) {
        return new MediationManager(mockP2PService(),
                mock(TradeWalletService.class),
                mock(BtcWalletService.class),
                mock(WalletsSetup.class),
                tradeManager,
                closedTradableManager(),
                failedTradesManager(),
                mock(OpenOfferManager.class),
                mock(DaoFacade.class),
                new KeyRing(new KeyStorage(keyStorageDir)),
                disputeListService,
                mock(Config.class),
                mock(PriceFeedService.class));
    }

    private static TradeManager tradeManager(PubKeyRing expectedAgentPubKeyRing) {
        Trade localTrade = mock(Trade.class);
        when(localTrade.getMediatorPubKeyRing()).thenReturn(expectedAgentPubKeyRing);

        TradeManager tradeManager = mock(TradeManager.class);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(localTrade));
        return tradeManager;
    }

    private static ClosedTradableManager closedTradableManager() {
        ClosedTradableManager closedTradableManager = mock(ClosedTradableManager.class);
        when(closedTradableManager.getClosedTrades()).thenReturn(List.of());
        return closedTradableManager;
    }

    private static FailedTradesManager failedTradesManager() {
        FailedTradesManager failedTradesManager = mock(FailedTradesManager.class);
        when(failedTradesManager.getTradeById(anyString())).thenReturn(Optional.empty());
        return failedTradesManager;
    }

    private static P2PService mockP2PService() {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
        NetworkNode networkNode = mock(NetworkNode.class);
        when(networkNode.getNodeAddress()).thenReturn(new NodeAddress("trader.onion", 9999));
        when(p2PService.getNetworkNode()).thenReturn(networkNode);
        return p2PService;
    }

    private static Dispute dispute(PubKeyRing agentPubKeyRing) {
        return dispute(pubKeyRing(), null, agentPubKeyRing);
    }

    private static Dispute dispute(PubKeyRing traderPubKeyRing, Contract contract, PubKeyRing agentPubKeyRing) {
        return new Dispute(
                0,
                TRADE_ID,
                TRADER_ID,
                true,
                true,
                traderPubKeyRing,
                0,
                0,
                contract,
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
                SupportType.MEDIATION);
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
    }

    @SuppressWarnings("unchecked")
    private static PersistenceManager<MediationDisputeList> persistenceManager() {
        return mock(PersistenceManager.class);
    }
}
