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

package bisq.core.support.dispute;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.SupportMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.config.Config;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import com.google.protobuf.Message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import java.security.PublicKey;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisputeManagerAuthTest {
    private static final String TRADE_ID = "trade-id";
    private static final String MESSAGE_CLASS_NAME = "TestDisputeMessage";
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer.onion", 9999);
    private static final NodeAddress BUYER_NODE_ADDRESS = new NodeAddress("aaaaaaaaaaaaaaaa.onion", 9999);
    private static final NodeAddress SELLER_NODE_ADDRESS = new NodeAddress("bbbbbbbbbbbbbbbb.onion", 9999);
    private static final NodeAddress MEDIATOR_NODE_ADDRESS = new NodeAddress("cccccccccccccccc.onion", 9999);
    private static final String DEPOSIT_TX_ID = "deposit-tx-id";

    @TempDir
    private File keyStorageDir;

    private TradeManager tradeManager;
    private ClosedTradableManager closedTradableManager;
    private FailedTradesManager failedTradesManager;
    private TestDisputeManager manager;

    @BeforeEach
    void setUp() {
        Res.setup();

        tradeManager = mock(TradeManager.class);
        closedTradableManager = mock(ClosedTradableManager.class);
        failedTradesManager = mock(FailedTradesManager.class);

        when(tradeManager.getTradeById(anyString())).thenReturn(Optional.empty());
        when(closedTradableManager.getClosedTrades()).thenReturn(List.of());
        when(failedTradesManager.getTradeById(anyString())).thenReturn(Optional.empty());

        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));

        manager = new TestDisputeManager(p2PService,
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir);
    }

    @Test
    void findTradeIncludesFailedTrades() {
        PubKeyRing agentPubKeyRing = pubKeyRing();
        Trade failedTrade = trade(TRADE_ID, agentPubKeyRing);
        when(failedTradesManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(failedTrade));

        Dispute dispute = dispute(TRADE_ID, agentPubKeyRing);

        assertSame(failedTrade, manager.findTrade(dispute).orElseThrow());
    }

    @Test
    void agentSignatureAcceptsFailedTradeAgentKeyFromLocalTrade() {
        PubKeyRing agentPubKeyRing = pubKeyRing();
        Trade failedTrade = trade(TRADE_ID, agentPubKeyRing);
        when(failedTradesManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(failedTrade));

        Dispute dispute = dispute(TRADE_ID, agentPubKeyRing);

        assertTrue(manager.isAgentSignatureValid(dispute,
                agentPubKeyRing.getSignaturePubKey(),
                MESSAGE_CLASS_NAME));
    }

    @Test
    void agentSignatureRejectsFailedTradeWhenDisputeAgentKeyDoesNotMatchLocalTrade() {
        PubKeyRing localAgentPubKeyRing = pubKeyRing();
        PubKeyRing payloadAgentPubKeyRing = pubKeyRing();
        Trade failedTrade = trade(TRADE_ID, localAgentPubKeyRing);
        when(failedTradesManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(failedTrade));

        Dispute dispute = dispute(TRADE_ID, payloadAgentPubKeyRing);

        assertFalse(manager.isAgentSignatureValid(dispute,
                localAgentPubKeyRing.getSignaturePubKey(),
                MESSAGE_CLASS_NAME));
    }

    @Test
    void agentSignatureRejectsUnknownTradeInsteadOfFallingBackToPayloadAgentKey() {
        PubKeyRing payloadAgentPubKeyRing = pubKeyRing();
        Dispute dispute = dispute(TRADE_ID, payloadAgentPubKeyRing);

        assertFalse(manager.isAgentSignatureValid(dispute,
                payloadAgentPubKeyRing.getSignaturePubKey(),
                MESSAGE_CLASS_NAME));
    }

    @Test
    void openNewDisputeWithInvalidOpenerSignatureDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService);

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = pubKeyRing();
        Dispute dispute = dispute(TRADE_ID,
                buyerPubKeyRing,
                agentPubKeyRing,
                contract(buyerPubKeyRing, sellerPubKeyRing));
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                PEER_NODE_ADDRESS,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, pubKeyRing().getSignaturePubKey());

        assertTrue(disputeList.isEmpty());
        assertEquals(1, manager.getValidationExceptions().size());
        DisputeValidation.ValidationException validationException =
                assertInstanceOf(DisputeValidation.ValidationException.class, manager.getValidationExceptions().get(0));
        assertEquals("Signature validation failed", validationException.getMessage());
        verify(disputeListService, never()).requestPersistence();
    }

    @Test
    void openNewDisputeWithInvalidContractDataDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService);

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = pubKeyRing();
        Dispute dispute = dispute(TRADE_ID,
                buyerPubKeyRing,
                agentPubKeyRing,
                contract(buyerPubKeyRing, sellerPubKeyRing));
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                PEER_NODE_ADDRESS,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, buyerPubKeyRing.getSignaturePubKey());

        assertTrue(disputeList.isEmpty());
        assertEquals(1, manager.getValidationExceptions().size());
        assertInstanceOf(DisputeValidation.ValidationException.class, manager.getValidationExceptions().get(0));
        verify(disputeListService, never()).requestPersistence();
    }

    @Test
    void openNewDisputeWithReplayValidationFailureDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService,
                mock(DaoFacade.class),
                localP2PConfig());

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = manager.getPubKeyRing();
        disputeList.add(dispute(TRADE_ID, 7, buyerPubKeyRing, agentPubKeyRing, null));
        disputeList.add(dispute(TRADE_ID, 8, buyerPubKeyRing, agentPubKeyRing, null));
        disputeList.add(dispute(TRADE_ID, 9, buyerPubKeyRing, agentPubKeyRing, null));

        Dispute dispute = validDispute(TRADE_ID,
                10,
                buyerPubKeyRing,
                agentPubKeyRing,
                validContract(TRADE_ID, buyerPubKeyRing, sellerPubKeyRing));
        dispute.setBurningManSelectionHeight(1);
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                BUYER_NODE_ADDRESS,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, buyerPubKeyRing.getSignaturePubKey());

        assertEquals(3, disputeList.size());
        assertEquals(1, manager.getValidationExceptions().size());
        assertInstanceOf(DisputeValidation.DisputeReplayException.class, manager.getValidationExceptions().get(0));
        verify(disputeListService, never()).requestPersistence();
    }

    @Test
    void openNewDisputeWithInvalidTraderNodeAddressDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService,
                mock(DaoFacade.class),
                torP2PConfig());

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = manager.getPubKeyRing();
        NodeAddress invalidBuyerNodeAddress = new NodeAddress("buyer.onion", 9999);

        Dispute dispute = validDispute(TRADE_ID,
                0,
                buyerPubKeyRing,
                agentPubKeyRing,
                validContract(TRADE_ID, buyerPubKeyRing, sellerPubKeyRing, invalidBuyerNodeAddress, SELLER_NODE_ADDRESS));
        dispute.setBurningManSelectionHeight(1);
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                invalidBuyerNodeAddress,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, buyerPubKeyRing.getSignaturePubKey());

        assertTrue(disputeList.isEmpty());
        assertEquals(1, manager.getValidationExceptions().size());
        assertInstanceOf(DisputeValidation.NodeAddressException.class, manager.getValidationExceptions().get(0));
        verify(disputeListService, never()).requestPersistence();
    }

    @Test
    void openNewDisputeFromNonTraderNodeDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService,
                mock(DaoFacade.class),
                localP2PConfig());

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = manager.getPubKeyRing();

        Dispute dispute = validDispute(TRADE_ID,
                0,
                buyerPubKeyRing,
                agentPubKeyRing,
                validContract(TRADE_ID, buyerPubKeyRing, sellerPubKeyRing));
        dispute.setBurningManSelectionHeight(1);
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                MEDIATOR_NODE_ADDRESS,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, buyerPubKeyRing.getSignaturePubKey());

        assertTrue(disputeList.isEmpty());
        assertEquals(1, manager.getValidationExceptions().size());
        DisputeValidation.NodeAddressException validationException =
                assertInstanceOf(DisputeValidation.NodeAddressException.class, manager.getValidationExceptions().get(0));
        assertEquals("senderNodeAddress not matching any of the traders node addresses",
                validationException.getMessage());
        verify(disputeListService, never()).requestPersistence();
    }

    @Test
    void openNewDisputeWithLegacyDonationAddressFailureDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        DaoFacade daoFacade = mock(DaoFacade.class);
        when(daoFacade.getAllDonationAddresses()).thenReturn(Set.of("known-donation-address"));
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService,
                daoFacade,
                localP2PConfig());

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = manager.getPubKeyRing();

        Dispute dispute = validDispute(TRADE_ID,
                0,
                buyerPubKeyRing,
                agentPubKeyRing,
                validContract(TRADE_ID, buyerPubKeyRing, sellerPubKeyRing));
        dispute.setDonationAddressOfDelayedPayoutTx("unknown-donation-address");
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                BUYER_NODE_ADDRESS,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, buyerPubKeyRing.getSignaturePubKey());

        assertTrue(disputeList.isEmpty());
        assertEquals(1, manager.getValidationExceptions().size());
        assertInstanceOf(DisputeValidation.AddressException.class, manager.getValidationExceptions().get(0));
        verify(disputeListService, never()).requestPersistence();
    }

    @Test
    void openNewDisputeSucceedsForFirstDisputeForTrade() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService,
                mock(DaoFacade.class),
                localP2PConfig());

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = manager.getPubKeyRing();

        Dispute dispute = validDispute(TRADE_ID,
                0,
                buyerPubKeyRing,
                agentPubKeyRing,
                validContract(TRADE_ID, buyerPubKeyRing, sellerPubKeyRing));
        dispute.setBurningManSelectionHeight(1);
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                BUYER_NODE_ADDRESS,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, buyerPubKeyRing.getSignaturePubKey());

        assertEquals(1, disputeList.size());
        assertSame(dispute, disputeList.getList().get(0));
        assertTrue(manager.getValidationExceptions().isEmpty());
        verify(disputeListService).requestPersistence();
    }

    @Test
    void openNewDisputeWithMismatchedSupportTypeDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService,
                mock(DaoFacade.class),
                localP2PConfig());

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = manager.getPubKeyRing();

        Dispute dispute = validDispute(TRADE_ID,
                0,
                buyerPubKeyRing,
                agentPubKeyRing,
                validContract(TRADE_ID, buyerPubKeyRing, sellerPubKeyRing),
                SupportType.ARBITRATION);
        dispute.setBurningManSelectionHeight(1);
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                BUYER_NODE_ADDRESS,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, buyerPubKeyRing.getSignaturePubKey());

        assertTrue(disputeList.isEmpty());
        assertEquals(1, manager.getValidationExceptions().size());
        DisputeValidation.ValidationException validationException =
                assertInstanceOf(DisputeValidation.ValidationException.class, manager.getValidationExceptions().get(0));
        assertEquals("Support type of dispute must match openNewDisputeMessage.getSupportType()",
                validationException.getMessage());
        verify(disputeListService, never()).requestPersistence();
    }

    @Test
    void openNewDisputeWithNonNewStateDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService,
                mock(DaoFacade.class),
                localP2PConfig());

        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing agentPubKeyRing = manager.getPubKeyRing();

        Dispute dispute = validDispute(TRADE_ID,
                0,
                buyerPubKeyRing,
                agentPubKeyRing,
                validContract(TRADE_ID, buyerPubKeyRing, sellerPubKeyRing));
        dispute.setState(Dispute.State.CLOSED);
        dispute.setBurningManSelectionHeight(1);
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute,
                BUYER_NODE_ADDRESS,
                "open-dispute-uid",
                SupportType.MEDIATION);

        manager.onOpenNewDispute(message, buyerPubKeyRing.getSignaturePubKey());

        assertTrue(disputeList.isEmpty());
        assertEquals(1, manager.getValidationExceptions().size());
        DisputeValidation.ValidationException validationException =
                assertInstanceOf(DisputeValidation.ValidationException.class, manager.getValidationExceptions().get(0));
        assertEquals("Support state of dispute must be NEW at opening dispute",
                validationException.getMessage());
        verify(disputeListService, never()).requestPersistence();
    }

    @Test
    void peerOpenedDisputeWithMismatchedAgentKeyDoesNotMutateDisputeList() {
        TestDisputeList disputeList = new TestDisputeList();
        DisputeListService<DisputeList<Dispute>> disputeListService = disputeListService(disputeList);
        TestDisputeManager manager = new TestDisputeManager(mockP2PService(),
                tradeManager,
                closedTradableManager,
                failedTradesManager,
                keyStorageDir,
                disputeListService);

        PubKeyRing localAgentPubKeyRing = pubKeyRing();
        PubKeyRing payloadAgentPubKeyRing = pubKeyRing();
        Trade localTrade = trade(TRADE_ID, localAgentPubKeyRing);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(localTrade));

        Dispute dispute = dispute(TRADE_ID, payloadAgentPubKeyRing);
        PeerOpenedDisputeMessage message = new PeerOpenedDisputeMessage(dispute,
                PEER_NODE_ADDRESS,
                "peer-opened-dispute-uid",
                SupportType.MEDIATION);

        manager.onPeerOpenedDispute(message, localAgentPubKeyRing.getSignaturePubKey());

        assertTrue(disputeList.isEmpty());
        verify(disputeListService, never()).requestPersistence();
    }

    private static Trade trade(String tradeId, PubKeyRing mediatorPubKeyRing) {
        Trade trade = mock(Trade.class);
        when(trade.getId()).thenReturn(tradeId);
        when(trade.getMediatorPubKeyRing()).thenReturn(mediatorPubKeyRing);
        return trade;
    }

    private static Dispute dispute(String tradeId, PubKeyRing agentPubKeyRing) {
        return dispute(tradeId, pubKeyRing(), agentPubKeyRing, null);
    }

    private static Dispute dispute(String tradeId,
                                   PubKeyRing traderPubKeyRing,
                                   PubKeyRing agentPubKeyRing,
                                   Contract contract) {
        return dispute(tradeId, 0, traderPubKeyRing, agentPubKeyRing, contract);
    }

    private static Dispute dispute(String tradeId,
                                   int traderId,
                                   PubKeyRing traderPubKeyRing,
                                   PubKeyRing agentPubKeyRing,
                                   Contract contract) {
        return new Dispute(
                0,
                tradeId,
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

    private static Dispute validDispute(String tradeId,
                                        int traderId,
                                        PubKeyRing traderPubKeyRing,
                                        PubKeyRing agentPubKeyRing,
                                        Contract contract) {
        return validDispute(tradeId, traderId, traderPubKeyRing, agentPubKeyRing, contract, SupportType.MEDIATION);
    }

    private static Dispute validDispute(String tradeId,
                                        int traderId,
                                        PubKeyRing traderPubKeyRing,
                                        PubKeyRing agentPubKeyRing,
                                        Contract contract,
                                        SupportType supportType) {
        String contractAsJson = JsonUtil.objectToJson(contract);
        return new Dispute(
                0,
                tradeId,
                traderId,
                true,
                true,
                traderPubKeyRing,
                0,
                0,
                contract,
                Hash.getSha256Hash(contractAsJson),
                null,
                null,
                DEPOSIT_TX_ID,
                null,
                contractAsJson,
                null,
                null,
                agentPubKeyRing,
                false,
                supportType);
    }

    private static Contract contract(PubKeyRing buyerPubKeyRing, PubKeyRing sellerPubKeyRing) {
        return new Contract(
                null,
                0,
                0,
                "takerFeeTxId",
                new NodeAddress("buyer.onion", 9999),
                new NodeAddress("seller.onion", 9999),
                PEER_NODE_ADDRESS,
                true,
                "makerAccountId",
                "takerAccountId",
                null,
                null,
                buyerPubKeyRing,
                sellerPubKeyRing,
                "makerPayoutAddress",
                "takerPayoutAddress",
                new byte[33],
                new byte[33],
                0,
                PEER_NODE_ADDRESS,
                null,
                null,
                PaymentMethod.SEPA_ID,
                PaymentMethod.SEPA_ID,
                0);
    }

    private static Contract validContract(String tradeId, PubKeyRing buyerPubKeyRing, PubKeyRing sellerPubKeyRing) {
        return validContract(tradeId, buyerPubKeyRing, sellerPubKeyRing, BUYER_NODE_ADDRESS, SELLER_NODE_ADDRESS);
    }

    private static Contract validContract(String tradeId,
                                          PubKeyRing buyerPubKeyRing,
                                          PubKeyRing sellerPubKeyRing,
                                          NodeAddress buyerNodeAddress,
                                          NodeAddress sellerNodeAddress) {
        return new Contract(
                offerPayload(tradeId, buyerPubKeyRing),
                10_000,
                1_000_000,
                "takerFeeTxId",
                buyerNodeAddress,
                sellerNodeAddress,
                MEDIATOR_NODE_ADDRESS,
                true,
                "makerAccountId",
                "takerAccountId",
                null,
                null,
                buyerPubKeyRing,
                sellerPubKeyRing,
                "makerPayoutAddress",
                "takerPayoutAddress",
                new byte[33],
                new byte[33],
                0,
                MEDIATOR_NODE_ADDRESS,
                null,
                null,
                PaymentMethod.SEPA_ID,
                PaymentMethod.SEPA_ID,
                0);
    }

    private static OfferPayload offerPayload(String tradeId, PubKeyRing makerPubKeyRing) {
        return new OfferPayload(
                tradeId,
                0,
                BUYER_NODE_ADDRESS,
                makerPubKeyRing,
                OfferDirection.BUY,
                1_000_000,
                0,
                false,
                10_000,
                10_000,
                "BTC",
                "USD",
                List.of(),
                List.of(),
                PaymentMethod.SEPA_ID,
                "makerAccountId",
                null,
                null,
                null,
                null,
                null,
                "1.0.0",
                0,
                0,
                0,
                true,
                0,
                0,
                0,
                0,
                false,
                false,
                0,
                0,
                false,
                null,
                null,
                0);
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
    }

    private static Config localP2PConfig() {
        return new Config("--baseCurrencyNetwork=BTC_REGTEST", "--useLocalhostForP2P=true");
    }

    private static Config torP2PConfig() {
        return new Config("--baseCurrencyNetwork=BTC_REGTEST");
    }

    private static P2PService mockP2PService() {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
        return p2PService;
    }

    private static DisputeListService<DisputeList<Dispute>> disputeListService() {
        return disputeListService(new TestDisputeList());
    }

    @SuppressWarnings("unchecked")
    private static DisputeListService<DisputeList<Dispute>> disputeListService(DisputeList<Dispute> disputeList) {
        DisputeListService<DisputeList<Dispute>> disputeListService = mock(DisputeListService.class);
        when(disputeListService.getDisputeList()).thenReturn(disputeList);
        return disputeListService;
    }

    private static final class TestDisputeList extends DisputeList<Dispute> {
        @Override
        public Message toProtoMessage() {
            return protobuf.PersistableEnvelope.newBuilder().build();
        }
    }

    private static final class TestDisputeManager extends DisputeManager<DisputeList<Dispute>> {
        private TestDisputeManager(P2PService p2PService,
                                   TradeManager tradeManager,
                                   ClosedTradableManager closedTradableManager,
                                   FailedTradesManager failedTradesManager,
                                   File keyStorageDir) {
            this(p2PService, tradeManager, closedTradableManager, failedTradesManager, keyStorageDir, disputeListService());
        }

        private TestDisputeManager(P2PService p2PService,
                                   TradeManager tradeManager,
                                   ClosedTradableManager closedTradableManager,
                                   FailedTradesManager failedTradesManager,
                                   File keyStorageDir,
                                   DisputeListService<DisputeList<Dispute>> disputeListService) {
            this(p2PService,
                    tradeManager,
                    closedTradableManager,
                    failedTradesManager,
                    keyStorageDir,
                    disputeListService,
                    mock(DaoFacade.class));
        }

        private TestDisputeManager(P2PService p2PService,
                                   TradeManager tradeManager,
                                   ClosedTradableManager closedTradableManager,
                                   FailedTradesManager failedTradesManager,
                                   File keyStorageDir,
                                   DisputeListService<DisputeList<Dispute>> disputeListService,
                                   DaoFacade daoFacade) {
            this(p2PService,
                    tradeManager,
                    closedTradableManager,
                    failedTradesManager,
                    keyStorageDir,
                    disputeListService,
                    daoFacade,
                    mock(Config.class));
        }

        private TestDisputeManager(P2PService p2PService,
                                   TradeManager tradeManager,
                                   ClosedTradableManager closedTradableManager,
                                   FailedTradesManager failedTradesManager,
                                   File keyStorageDir,
                                   DisputeListService<DisputeList<Dispute>> disputeListService,
                                   DaoFacade daoFacade,
                                   Config config) {
            super(p2PService,
                    mock(TradeWalletService.class),
                    mock(BtcWalletService.class),
                    mock(WalletsSetup.class),
                    tradeManager,
                    closedTradableManager,
                    failedTradesManager,
                    mock(OpenOfferManager.class),
                    daoFacade,
                    new KeyRing(new KeyStorage(keyStorageDir)),
                    disputeListService,
                    config,
                    mock(PriceFeedService.class));
        }

        private boolean isAgentSignatureValid(Dispute dispute,
                                              PublicKey senderSignaturePubKey,
                                              String messageClassName) {
            return isDisputeAgentSignaturePubKeyValid(dispute, senderSignaturePubKey, messageClassName);
        }

        private void onOpenNewDispute(OpenNewDisputeMessage openNewDisputeMessage,
                                      PublicKey senderSignaturePubKey) {
            onOpenNewDisputeMessage(openNewDisputeMessage, senderSignaturePubKey);
        }

        private void onPeerOpenedDispute(PeerOpenedDisputeMessage peerOpenedDisputeMessage,
                                         PublicKey senderSignaturePubKey) {
            onPeerOpenedDisputeMessage(peerOpenedDisputeMessage, senderSignaturePubKey);
        }

        private PubKeyRing getPubKeyRing() {
            return pubKeyRing;
        }

        @Override
        protected void addPriceInfoMessage(Dispute dispute, int counter) {
        }

        @Override
        protected void onSupportMessage(SupportMessage networkEnvelope, PublicKey senderSignaturePubKey) {
        }

        @Override
        public void onDisputeResultMessage(DisputeResultMessage disputeResultMessage, PublicKey senderSignaturePubKey) {
        }

        @Override
        public NodeAddress getAgentNodeAddress(Dispute dispute) {
            return null;
        }

        @Override
        protected PubKeyRing getExpectedAgentPubKeyRing(Trade trade) {
            return trade.getMediatorPubKeyRing();
        }

        @Override
        protected Trade.DisputeState getDisputeStateStartedByPeer() {
            return Trade.DisputeState.MEDIATION_STARTED_BY_PEER;
        }

        @Override
        public void cleanupDisputes() {
        }

        @Override
        protected String getDisputeInfo(Dispute dispute) {
            return "";
        }

        @Override
        protected String getDisputeIntroForPeer(String disputeInfo) {
            return "";
        }

        @Override
        protected String getDisputeIntroForDisputeCreator(String disputeInfo) {
            return "";
        }

        @Override
        public SupportType getSupportType() {
            return SupportType.MEDIATION;
        }

        @Override
        protected AckMessageSourceType getAckMessageSourceType() {
            return AckMessageSourceType.MEDIATION_MESSAGE;
        }
    }
}
