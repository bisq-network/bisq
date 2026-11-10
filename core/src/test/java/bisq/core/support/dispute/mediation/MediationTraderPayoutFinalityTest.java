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
import bisq.core.locale.Res;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.DisputeProtocol;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessage;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.config.Config;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.persistence.PersistenceManager;

import org.bitcoinj.core.Coin;

import java.io.File;

import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The trader's side of the rule that the payout of a mediated trade is final once the mediator sent a result
class MediationTraderPayoutFinalityTest {
    private static final String TRADE_ID = "trade-id";
    private static final NodeAddress MEDIATOR_NODE_ADDRESS = new NodeAddress("mediator.onion", 9999);
    private static final NodeAddress TRADER_NODE_ADDRESS = new NodeAddress("trader.onion", 9999);

    @TempDir
    private File keyStorageDir;

    private final PubKeyRing agentPubKeyRing = pubKeyRing();
    private final MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
    private final Trade trade = mock(Trade.class);
    private final TradeManager tradeManager = mock(TradeManager.class);
    private final ClosedTradableManager closedTradableManager = mock(ClosedTradableManager.class);
    private final DisputeProtocol disputeProtocol = mock(DisputeProtocol.class);
    private KeyRing keyRing;
    private MediationDisputeListService disputeListService;
    private MediationManager manager;
    private ProcessModel processModel;
    private Dispute ownDispute;

    @BeforeEach
    void setUp() {
        Res.setup();
        keyRing = new KeyRing(new KeyStorage(keyStorageDir));
        disputeListService = new MediationDisputeListService(persistenceManager());
        manager = mediationManager();
        processModel = new ProcessModel(TRADE_ID, "account-id", keyRing.getPubKeyRing());
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.getMediatorPubKeyRing()).thenReturn(agentPubKeyRing);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getDisputeState()).thenReturn(Trade.DisputeState.MEDIATION_CLOSED);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(trade));
        when(tradeManager.getTradeProtocol(trade)).thenReturn(disputeProtocol);
        ownDispute = dispute(keyRing.getPubKeyRing().hashCode(), keyRing.getPubKeyRing());
        disputeListService.getDisputeList().add(ownDispute);
    }

    @Test
    void refusesResultWithAnotherPayout() {
        DisputeResult firstResult = result(ownDispute, 80_000, 30_000);
        ownDispute.setDisputeResult(firstResult);
        processModel.setBuyerPayoutAmountFromMediation(80_000);
        processModel.setSellerPayoutAmountFromMediation(30_000);
        DisputeResult otherResult = result(ownDispute, 70_000, 40_000);

        manager.onDisputeResultMessage(message(otherResult), agentPubKeyRing.getSignaturePubKey());

        assertSame(firstResult, ownDispute.getDisputeResultProperty().get());
        assertFalse(hasChatMessage(ownDispute, otherResult));
        assertEquals(1, ownDispute.getChatMessages().size());
        assertTrue(ownDispute.getChatMessages().get(0).isSystemMessage());
        assertEquals(80_000, processModel.getBuyerPayoutAmountFromMediation());
        assertEquals(30_000, processModel.getSellerPayoutAmountFromMediation());
        verify(trade, never()).setDisputeState(any());
        AckMessage ackMessage = sentAckMessage();
        assertFalse(ackMessage.isSuccess());
        assertEquals(otherResult.getChatMessage().getUid(), ackMessage.getSourceUid());
    }

    @Test
    void refusesAnotherPayoutForAnotherDisputeOfTheTrade() {
        ownDispute.setDisputeResult(result(ownDispute, 80_000, 30_000));
        PubKeyRing otherPubKeyRing = pubKeyRing();
        Dispute otherDispute = dispute(otherPubKeyRing.hashCode(), otherPubKeyRing);
        disputeListService.getDisputeList().add(otherDispute);
        DisputeResult otherResult = result(otherDispute, 70_000, 40_000);

        manager.onDisputeResultMessage(message(otherResult), agentPubKeyRing.getSignaturePubKey());

        assertEquals(null, otherDispute.getDisputeResultProperty().get());
        assertFalse(hasChatMessage(otherDispute, otherResult));
        assertFalse(sentAckMessage().isSuccess());
    }

    @Test
    void acceptsCopyWithTheSamePayout() {
        ownDispute.setDisputeResult(result(ownDispute, 80_000, 30_000));
        DisputeResult copy = result(ownDispute, 80_000, 30_000);

        manager.onDisputeResultMessage(message(copy), agentPubKeyRing.getSignaturePubKey());

        assertSame(copy, ownDispute.getDisputeResultProperty().get());
        assertTrue(hasChatMessage(ownDispute, copy));
        assertTrue(sentAckMessage().isSuccess());
    }

    @Test
    void refusesDisputeOpenedByPeerForAnotherTrader() {
        PubKeyRing otherPubKeyRing = pubKeyRing();
        Dispute copy = dispute(otherPubKeyRing.hashCode(), otherPubKeyRing);

        manager.onSupportMessage(peerOpenedDisputeMessage(copy), agentPubKeyRing.getSignaturePubKey());

        assertEquals(List.of(ownDispute), disputeListService.getDisputeList().getList());
        verify(trade, never()).setDisputeState(any());
        assertFalse(sentAckMessage().isSuccess());
        // The trader has a case of its own, so there is nothing to tell
        assertTrue(manager.getTradeIdsOfRefusedPeerDisputes().isEmpty());
    }

    // The opener chose the key ring of the peer's dispute, so the trader has no case and only the trader can act
    @Test
    void reportsRefusedDisputeOpenedByPeerForAnotherTraderWithoutOwnCase() {
        disputeListService.getDisputeList().remove(ownDispute);
        PubKeyRing otherPubKeyRing = pubKeyRing();
        Dispute copy = dispute(otherPubKeyRing.hashCode(), otherPubKeyRing);

        manager.onSupportMessage(peerOpenedDisputeMessage(copy), agentPubKeyRing.getSignaturePubKey());
        manager.onSupportMessage(peerOpenedDisputeMessage(copy), agentPubKeyRing.getSignaturePubKey());

        assertTrue(disputeListService.getDisputeList().getList().isEmpty());
        verify(trade, never()).setDisputeState(any());
        assertEquals(List.of(TRADE_ID), manager.getTradeIdsOfRefusedPeerDisputes());
    }

    @Test
    void doesNotReportRefusedDisputeOpenedByPeerForAnotherTraderOfClosedTrade() {
        disputeListService.getDisputeList().remove(ownDispute);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.empty());
        when(closedTradableManager.getTradableById(TRADE_ID)).thenReturn(Optional.<Tradable>of(trade));
        PubKeyRing otherPubKeyRing = pubKeyRing();
        Dispute copy = dispute(otherPubKeyRing.hashCode(), otherPubKeyRing);

        manager.onSupportMessage(peerOpenedDisputeMessage(copy), agentPubKeyRing.getSignaturePubKey());

        verify(closedTradableManager, never()).remove(any());
        assertTrue(disputeListService.getDisputeList().getList().isEmpty());
        assertTrue(manager.getTradeIdsOfRefusedPeerDisputes().isEmpty());
    }

    @Test
    void refusesDisputeOpenedByPeerWithAnotherTraderId() {
        Dispute copy = dispute(keyRing.getPubKeyRing().hashCode() + 1, keyRing.getPubKeyRing());

        manager.onSupportMessage(peerOpenedDisputeMessage(copy), agentPubKeyRing.getSignaturePubKey());

        assertEquals(List.of(ownDispute), disputeListService.getDisputeList().getList());
        verify(trade, never()).setDisputeState(any());
        assertFalse(sentAckMessage().isSuccess());
    }

    @Test
    void refusesNewMediationOpenedByPeerAfterTheResult() {
        disputeListService.getDisputeList().remove(ownDispute);
        Dispute copy = dispute(keyRing.getPubKeyRing().hashCode(), keyRing.getPubKeyRing());

        manager.onSupportMessage(peerOpenedDisputeMessage(copy), agentPubKeyRing.getSignaturePubKey());

        assertTrue(disputeListService.getDisputeList().getList().isEmpty());
        verify(trade, never()).setDisputeState(any());
        assertFalse(sentAckMessage().isSuccess());
        // The dispute was addressed to the trader, who has the mediation result
        assertTrue(manager.getTradeIdsOfRefusedPeerDisputes().isEmpty());
    }

    @Test
    void doesNotMoveClosedTradeBackForNewMediationAfterTheResult() {
        disputeListService.getDisputeList().remove(ownDispute);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.empty());
        when(closedTradableManager.getTradableById(TRADE_ID)).thenReturn(Optional.<Tradable>of(trade));
        Dispute copy = dispute(keyRing.getPubKeyRing().hashCode(), keyRing.getPubKeyRing());

        manager.onSupportMessage(peerOpenedDisputeMessage(copy), agentPubKeyRing.getSignaturePubKey());

        verify(closedTradableManager, never()).remove(any());
        verify(tradeManager, never()).addTradeToPendingTrades(any());
        verify(trade, never()).setDisputeState(any());
        assertTrue(disputeListService.getDisputeList().getList().isEmpty());
    }

    @Test
    void storesMediationOpenedByPeerBeforeAnyDispute() {
        disputeListService.getDisputeList().remove(ownDispute);
        when(trade.getDisputeState()).thenReturn(Trade.DisputeState.NO_DISPUTE);
        Contract contract = mock(Contract.class);
        when(contract.getBuyerNodeAddress()).thenReturn(TRADER_NODE_ADDRESS);
        when(contract.getSellerNodeAddress()).thenReturn(MEDIATOR_NODE_ADDRESS);
        Dispute copy = dispute(keyRing.getPubKeyRing().hashCode(), keyRing.getPubKeyRing(), contract);

        manager.onSupportMessage(peerOpenedDisputeMessage(copy), agentPubKeyRing.getSignaturePubKey());

        assertEquals(List.of(copy), disputeListService.getDisputeList().getList());
        verify(trade).setDisputeState(Trade.DisputeState.MEDIATION_STARTED_BY_PEER);
        assertTrue(sentAckMessage().isSuccess());
    }

    @Test
    void repeatedAcceptanceKeepsTheAmountsOfOwnSignature() {
        // A result that replaced the accepted one before this version
        ownDispute.setDisputeResult(result(ownDispute, 70_000, 40_000));
        processModel.setBuyerPayoutAmountFromMediation(80_000);
        processModel.setSellerPayoutAmountFromMediation(30_000);
        processModel.setMediatedPayoutTxSignature(new byte[]{1, 2, 3});

        manager.onAcceptMediationResult(trade, () -> {
        }, errorMessage -> {
        });

        assertEquals(80_000, processModel.getBuyerPayoutAmountFromMediation());
        assertEquals(30_000, processModel.getSellerPayoutAmountFromMediation());
        verify(disputeProtocol).onAcceptMediationResult(any(), any());
    }

    @Test
    void firstAcceptanceTakesTheAmountsOfTheResult() {
        ownDispute.setDisputeResult(result(ownDispute, 80_000, 30_000));

        manager.onAcceptMediationResult(trade, () -> {
        }, errorMessage -> {
        });

        assertEquals(80_000, processModel.getBuyerPayoutAmountFromMediation());
        assertEquals(30_000, processModel.getSellerPayoutAmountFromMediation());
        verify(disputeProtocol).onAcceptMediationResult(any(), any());
    }

    @Test
    void firstAcceptanceOfOtherAmountsDropsThePeersSignature() {
        // A result that replaced the first one before this version, after the peer's signature was stored
        ownDispute.setDisputeResult(result(ownDispute, 70_000, 40_000));
        processModel.setBuyerPayoutAmountFromMediation(80_000);
        processModel.setSellerPayoutAmountFromMediation(30_000);
        processModel.getTradePeer().setMediatedPayoutTxSignature(new byte[]{4, 5, 6});

        manager.onAcceptMediationResult(trade, () -> {
        }, errorMessage -> {
        });

        assertEquals(70_000, processModel.getBuyerPayoutAmountFromMediation());
        assertEquals(40_000, processModel.getSellerPayoutAmountFromMediation());
        assertNull(processModel.getTradePeer().getMediatedPayoutTxSignature());
        verify(disputeProtocol).onAcceptMediationResult(any(), any());
        verify(disputeProtocol, never()).onFinalizeMediationResultPayout(any(), any());
    }

    @Test
    void firstAcceptanceOfTheSameAmountsKeepsThePeersSignature() {
        ownDispute.setDisputeResult(result(ownDispute, 80_000, 30_000));
        processModel.setBuyerPayoutAmountFromMediation(80_000);
        processModel.setSellerPayoutAmountFromMediation(30_000);
        byte[] peersSignature = {4, 5, 6};
        processModel.getTradePeer().setMediatedPayoutTxSignature(peersSignature);

        manager.onAcceptMediationResult(trade, () -> {
        }, errorMessage -> {
        });

        assertArrayEquals(peersSignature, processModel.getTradePeer().getMediatedPayoutTxSignature());
        verify(disputeProtocol).onFinalizeMediationResultPayout(any(), any());
    }

    private static PeerOpenedDisputeMessage peerOpenedDisputeMessage(Dispute dispute) {
        return new PeerOpenedDisputeMessage(dispute,
                MEDIATOR_NODE_ADDRESS,
                "peer-opened-dispute-uid",
                SupportType.MEDIATION);
    }

    // ChatMessage.equals needs the P2P message version, which only the application sets
    private static boolean hasChatMessage(Dispute dispute, DisputeResult result) {
        String uid = result.getChatMessage().getUid();
        return dispute.getChatMessages().stream().anyMatch(chatMessage -> chatMessage.getUid().equals(uid));
    }

    private AckMessage sentAckMessage() {
        ArgumentCaptor<MailboxMessage> captor = ArgumentCaptor.forClass(MailboxMessage.class);
        verify(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), captor.capture(), any());
        List<MailboxMessage> messages = captor.getAllValues();
        return (AckMessage) messages.get(messages.size() - 1);
    }

    private MediationManager mediationManager() {
        NetworkNode networkNode = mock(NetworkNode.class);
        when(networkNode.getNodeAddress()).thenReturn(TRADER_NODE_ADDRESS);
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
        when(p2PService.getNetworkNode()).thenReturn(networkNode);
        when(p2PService.getAddress()).thenReturn(TRADER_NODE_ADDRESS);
        when(closedTradableManager.getClosedTrades()).thenReturn(List.of());
        FailedTradesManager failedTradesManager = mock(FailedTradesManager.class);
        return new MediationManager(p2PService,
                mock(TradeWalletService.class),
                mock(BtcWalletService.class),
                mock(WalletsSetup.class),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                mock(OpenOfferManager.class),
                mock(DaoFacade.class),
                keyRing,
                disputeListService,
                mock(Config.class),
                mock(PriceFeedService.class));
    }

    private Dispute dispute(int traderId, PubKeyRing traderPubKeyRing) {
        return dispute(traderId, traderPubKeyRing, null);
    }

    private Dispute dispute(int traderId, PubKeyRing traderPubKeyRing, Contract contract) {
        return new Dispute(0,
                TRADE_ID,
                traderId,
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

    // A result as the mediator sends it, with the chat message that carries its summary
    private static DisputeResult result(Dispute dispute, long buyerPayoutAmount, long sellerPayoutAmount) {
        DisputeResult result = new DisputeResult(TRADE_ID, dispute.getTraderId());
        result.setBuyerPayoutAmount(Coin.valueOf(buyerPayoutAmount));
        result.setSellerPayoutAmount(Coin.valueOf(sellerPayoutAmount));
        result.setChatMessage(new ChatMessage(SupportType.MEDIATION,
                TRADE_ID,
                dispute.getTraderId(),
                false,
                "result",
                MEDIATOR_NODE_ADDRESS));
        return result;
    }

    private DisputeResultMessage message(DisputeResult result) {
        return new DisputeResultMessage(result,
                MEDIATOR_NODE_ADDRESS,
                result.getChatMessage().getUid(),
                SupportType.MEDIATION,
                agentPubKeyRing.getSignaturePubKey());
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
