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
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.config.Config;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.persistence.PersistenceManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        return p2PService;
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
