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
import bisq.core.locale.Res;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.AckMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.persistence.PersistenceManager;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.params.MainNetParams;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bisq.core.support.dispute.refund.RefundClaimTestData.OPENING_DATE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundClaimLifecycleTest {
    private final BtcWalletService wallet = mock(BtcWalletService.class);
    @SuppressWarnings("unchecked")
    private final PersistenceManager<RefundDisputeList> persistence = mock(PersistenceManager.class);
    private final RefundDisputeListService disputes = new RefundDisputeListService(persistence);
    private final List<Runnable> delayedActions = new ArrayList<>();
    private final MailboxMessageService mailbox = mock(MailboxMessageService.class);
    private MockedStatic<UserThread> userThread;
    private MockedStatic<Version> version;
    private RefundManager manager;
    private RefundClaimTestData data;
    private PubKeyRing agent;

    @BeforeEach
    void setUp() {
        Res.setup();
        version = mockStatic(Version.class, CALLS_REAL_METHODS);
        version.when(Version::getP2PMessageVersion).thenReturn(10);
        userThread = mockStatic(UserThread.class, CALLS_REAL_METHODS);
        userThread.when(() -> UserThread.runAfter(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> {
                    delayedActions.add(invocation.getArgument(0));
                    return mock(Timer.class);
                });
        agent = new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
        data = new RefundClaimTestData(agent, true);
        manager = newManager(agent, RefundClaimTestData.AGENT_ADDRESS);
    }

    private RefundManager newManager(PubKeyRing localIdentity, NodeAddress localAddress) {
        KeyRing keyRing = mock(KeyRing.class);
        when(keyRing.getPubKeyRing()).thenReturn(localIdentity);
        P2PService network = mock(P2PService.class);
        when(network.getMailboxMessageService()).thenReturn(mailbox);
        when(network.getAddress()).thenReturn(localAddress);
        NetworkNode networkNode = mock(NetworkNode.class);
        when(network.getNetworkNode()).thenReturn(networkNode);
        when(networkNode.getNodeAddress()).thenReturn(localAddress);
        when(wallet.getParams()).thenReturn(MainNetParams.get());
        return new RefundManager(network, mock(TradeWalletService.class), wallet,
                mock(WalletsSetup.class), mock(TradeManager.class), mock(ClosedTradableManager.class),
                mock(FailedTradesManager.class), mock(OpenOfferManager.class), mock(DaoFacade.class),
                mock(DelayedPayoutTxReceiverService.class), keyRing, disputes, mock(Config.class),
                mock(PriceFeedService.class), mock(MempoolService.class), mock(RefundPayoutReceiptService.class));
    }

    @AfterEach
    void tearDown() {
        userThread.close();
        version.close();
    }

    @ParameterizedTest
    @EnumSource(value = BaseCurrencyNetwork.class, names = {"BTC_MAINNET", "BTC_REGTEST"})
    void admissionAndPayoutRequireProofOnBothNetworks(BaseCurrencyNetwork network) throws Exception {
        try (MockedStatic<Config> config = mockStatic(Config.class, CALLS_REAL_METHODS)) {
            config.when(Config::baseCurrencyNetwork).thenReturn(network);
            Dispute unsigned = data.unsigned(true, OPENING_DATE);
            assertDoesNotThrow(() -> receive(unsigned));
            assertRejected();
            assertThrows(IllegalArgumentException.class,
                    () -> manager.verifyRefundClaimForPayout(unsigned, Coin.valueOf(1_000), Coin.ZERO));

            Dispute signed = data.signed(true, OPENING_DATE + 1);
            receive(signed);
            assertEquals(List.of(signed), disputes.getDisputeList().getList());
            assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(signed, Coin.valueOf(1_000), Coin.ZERO));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not a signature"})
    void malformedClaimIsRejectedByMessageHandler(String signature) throws Exception {
        Dispute dispute = data.signed(true, OPENING_DATE);
        dispute.setRefundClaimSignature(signature);
        assertDoesNotThrow(() -> receive(dispute));
        assertRejected();
    }

    @Test
    void unsignedRequestGetsAuthenticatedUpgradeFeedbackWithoutAdmission() throws Exception {
        Dispute dispute = data.unsigned(true, OPENING_DATE);
        ChatMessage chat = openingChat(dispute, RefundClaimTestData.BUYER_ADDRESS);
        receive(dispute);
        assertRejected();
        ArgumentCaptor<AckMessage> ack = ArgumentCaptor.forClass(AckMessage.class);
        verify(mailbox).sendEncryptedMailboxMessage(eq(RefundClaimTestData.BUYER_ADDRESS), eq(data.buyer),
                ack.capture(), any());
        assertFalse(ack.getValue().isSuccess());
        assertEquals(chat.getUid(), ack.getValue().getSourceUid());
        assertEquals(dispute.getTradeId(), ack.getValue().getSourceId());
        assertTrue(ack.getValue().getErrorMessage().contains("Update your client"));
    }

    @Test
    void unsignedRequestWithWrongTransportSignerGetsNoReply() throws Exception {
        Dispute dispute = data.unsigned(true, OPENING_DATE);
        openingChat(dispute, RefundClaimTestData.BUYER_ADDRESS);
        manager.onSupportMessage(message(dispute), data.seller.getSignaturePubKey());
        assertRejected();
        verify(mailbox, never()).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"address", "trade", "trader", "support"})
    void rejectedClaimCannotRedirectReplyThroughEmbeddedChat(String changedField) throws Exception {
        Dispute dispute = data.unsigned(true, OPENING_DATE);
        dispute.addAndPersistChatMessage(new ChatMessage(
                changedField.equals("support") ? SupportType.MEDIATION : SupportType.REFUND,
                changedField.equals("trade") ? "another-trade" : dispute.getTradeId(),
                changedField.equals("trader") ? dispute.getTraderId() + 1 : dispute.getTraderId(),
                false, "Opening request",
                changedField.equals("address") ? RefundClaimTestData.SELLER_ADDRESS : RefundClaimTestData.BUYER_ADDRESS));
        receive(dispute);
        assertRejected();
        verify(mailbox, never()).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @Test
    void reopenedRequestRetainsItsChatForDeliveryAndAckTracking() throws Exception {
        Dispute stored = data.unsigned(true, OPENING_DATE);
        disputes.getDisputeList().add(stored);
        Dispute incoming = data.signed(true, OPENING_DATE + 1);
        RefundManager client = newManager(data.buyer, RefundClaimTestData.BUYER_ADDRESS);

        client.sendOpenNewDisputeMessage(incoming, true, () -> {}, (error, exception) -> fail(error));

        ArgumentCaptor<OpenNewDisputeMessage> request = ArgumentCaptor.forClass(OpenNewDisputeMessage.class);
        ArgumentCaptor<SendMailboxMessageListener> listener = ArgumentCaptor.forClass(SendMailboxMessageListener.class);
        verify(mailbox).sendEncryptedMailboxMessage(eq(RefundClaimTestData.AGENT_ADDRESS), eq(agent),
                request.capture(), listener.capture());
        ChatMessage sentChat = request.getValue().getDispute().getChatMessages().get(0);
        assertSame(sentChat, client.getAllChatMessages(stored.getTradeId()).get(0));
        listener.getValue().onArrived();
        assertTrue(stored.getChatMessages().get(0).arrivedProperty().get());
        assertEquals(List.of(stored), disputes.getDisputeList().getList());
        assertNull(stored.getRefundClaimSignature());
    }

    private ChatMessage openingChat(Dispute dispute, NodeAddress sender) {
        ChatMessage chat = new ChatMessage(SupportType.REFUND, dispute.getTradeId(), dispute.getTraderId(),
                false, "Opening refund request", sender);
        chat.setSystemMessage(true);
        dispute.addAndPersistChatMessage(chat);
        return chat;
    }

    @Test
    void wrongEscrowSignerIsRejectedByMessageHandler() throws Exception {
        Dispute dispute = data.signed(true, OPENING_DATE);
        dispute.setRefundClaimSignature(data.sellerKey.signMessage(
                RefundClaimSignature.getMessage(dispute, RefundClaimSignature.Claimant.BUYER)));
        receive(dispute);
        assertRejected();
    }

    @Test
    void wrongTransportSignerIsRejectedEvenWithValidClaim() throws Exception {
        Dispute dispute = data.signed(true, OPENING_DATE);
        manager.onSupportMessage(message(dispute), data.seller.getSignaturePubKey());
        assertRejected();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validProofWithSubstitutedEscrowKeyCannotAuthenticatePublicDeposit(boolean buyerIsMaker) throws Exception {
        data = new RefundClaimTestData(agent, buyerIsMaker);
        ECKey attackerKey = new ECKey();
        PubKeyRing attacker = new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
        var builder = data.unsigned(true, OPENING_DATE).toProtoMessage().toBuilder()
                .setTraderId(attacker.hashCode())
                .setTraderPubKeyRing(attacker.toProtoMessage())
                .clearMakerContractSignature()
                .clearTakerContractSignature();
        if (buyerIsMaker) {
            builder.getContractBuilder().setMakerMultiSigPubKey(ByteString.copyFrom(attackerKey.getPubKey()))
                    .setMakerPubKeyRing(attacker.toProtoMessage());
        } else {
            builder.getContractBuilder().setTakerMultiSigPubKey(ByteString.copyFrom(attackerKey.getPubKey()))
                    .setTakerPubKeyRing(attacker.toProtoMessage());
        }
        Dispute forged = RefundClaimTestData.restore(builder.build());
        forged.setRefundClaimOpeningDate(OPENING_DATE);
        forged.setRefundClaimSignature(RefundClaimSignature.sign(forged, attackerKey, null));
        assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(forged));

        receive(forged);
        assertRejected();
        assertTrue(manager.getValidationExceptions().get(0).getMessage().contains("multisig output script"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sellerCannotClaimBuyerRoleBySwappingContractEscrowKeys(boolean buyerIsMaker) throws Exception {
        data = new RefundClaimTestData(agent, buyerIsMaker);
        var builder = data.unsigned(true, OPENING_DATE).toProtoMessage().toBuilder()
                .setTraderId(data.seller.hashCode())
                .setTraderPubKeyRing(data.seller.toProtoMessage())
                .clearMakerContractSignature()
                .clearTakerContractSignature();
        builder.getContractBuilder()
                .setMakerMultiSigPubKey(ByteString.copyFrom(data.contract.getTakerMultiSigPubKey()))
                .setTakerMultiSigPubKey(ByteString.copyFrom(data.contract.getMakerMultiSigPubKey()));
        if (buyerIsMaker) {
            builder.getContractBuilder().setMakerPubKeyRing(data.seller.toProtoMessage());
        } else {
            builder.getContractBuilder().setTakerPubKeyRing(data.seller.toProtoMessage());
        }
        Dispute forged = RefundClaimTestData.restore(builder.build());
        forged.setRefundClaimOpeningDate(OPENING_DATE);
        forged.setRefundClaimSignature(RefundClaimSignature.sign(forged, data.sellerKey, null));
        assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(forged));

        receive(forged);
        assertRejected();
        assertTrue(manager.getValidationExceptions().get(0).getMessage().contains("multisig output script"));
    }

    @Test
    void missingDelayedPayoutIdentifierIsReportedWithoutEscapingHandler() throws Exception {
        Dispute dispute = data.signed(true, OPENING_DATE);
        dispute.setDelayedPayoutTxId(null);
        assertDoesNotThrow(() -> receive(dispute));
        assertRejected();
    }

    @Test
    void malformedSelectedSubjectReportsItsActualValidationFailure() throws Exception {
        Dispute selected = data.signed(true, OPENING_DATE);
        selected.setDelayedPayoutTxId(null);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> manager.verifyRefundClaimForPayout(selected, Coin.valueOf(1_000), Coin.ZERO));
        assertEquals("Refund delayed payout transaction ID must not be null", error.getMessage());
    }

    @Test
    void selectedRowWithoutOwnProofCanUseMatchingIndependentlyVerifiedClaim() throws Exception {
        Dispute selected = data.signed(true, OPENING_DATE);
        selected.setRefundClaimSignature(null);
        Dispute peer = data.signed(false, OPENING_DATE + 1);
        disputes.getDisputeList().add(selected);
        receive(peer);
        assertFalse(manager.requiresLegacyRefundClaimVerification(selected));
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(selected, Coin.ZERO, Coin.valueOf(1_000)));
        assertThrows(IllegalArgumentException.class,
                () -> manager.verifyRefundClaimForPayout(selected, Coin.valueOf(1_000), Coin.ZERO));
    }

    @Test
    void malformedCandidateDoesNotBlockValidSelectedClaim() throws Exception {
        Dispute selected = data.signed(true, OPENING_DATE);
        Dispute peer = RefundClaimTestData.restore(data.signed(false, OPENING_DATE + 1)
                .toProtoMessage().toBuilder().clearContractHash().build());
        disputes.getDisputeList().add(peer);
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(selected, Coin.valueOf(1_000), Coin.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> manager.verifyRefundClaimForPayout(selected, Coin.ZERO, Coin.valueOf(1_000)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mirrorAndLaterWinningPeerClaimSurviveSerialization(boolean buyerIsMaker) throws Exception {
        data = new RefundClaimTestData(agent, buyerIsMaker);
        Dispute buyer = data.signed(true, OPENING_DATE);
        receive(RefundClaimTestData.restore(buyer.toProtoMessage()));
        runDelayedActions();
        assertTrue(manager.getValidationExceptions().isEmpty());
        assertEquals(2, disputes.getDisputeList().size());
        Dispute peer = disputes.getDisputeList().stream()
                .filter(row -> !row.isDisputeOpenerIsBuyer()).findFirst().orElseThrow();
        long peerRowDate = peer.getOpeningDate().getTime();
        assertEquals(buyer.getRefundClaimSignature(), peer.getRefundClaimSignature());
        assertEquals(OPENING_DATE, peer.getRefundClaimOpeningDate());
        assertEquals(RefundClaimSignature.Claimant.BUYER,
                RefundClaimSignature.verifyClaimant(RefundClaimTestData.restore(peer.toProtoMessage())));
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(peer, Coin.valueOf(1_000), Coin.ZERO));
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(peer, Coin.ZERO, Coin.ZERO));
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(peer, Coin.valueOf(500), Coin.valueOf(500)));
        assertThrows(IllegalArgumentException.class,
                () -> manager.verifyRefundClaimForPayout(peer, Coin.ZERO, Coin.valueOf(1_000)));

        Dispute seller = data.signed(false, OPENING_DATE + 1);
        receive(RefundClaimTestData.restore(seller.toProtoMessage()));
        assertTrue(manager.getValidationExceptions().isEmpty());
        assertEquals(2, disputes.getDisputeList().size());
        assertEquals(peerRowDate, peer.getOpeningDate().getTime());
        assertEquals(seller.getRefundClaimSignature(), peer.getRefundClaimSignature());
        assertEquals(seller.getRefundClaimOpeningDate(), peer.getRefundClaimOpeningDate());
        Dispute restoredPeer = RefundClaimTestData.restore(peer.toProtoMessage());
        assertEquals(RefundClaimSignature.Claimant.SELLER, RefundClaimSignature.verifyClaimant(restoredPeer));
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(buyer, Coin.ZERO, Coin.valueOf(1_000)));
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(restoredPeer, Coin.ZERO, Coin.valueOf(1_000)));

        peer.setRefundClaimSignature(null);
        assertThrows(IllegalArgumentException.class,
                () -> manager.verifyRefundClaimForPayout(buyer, Coin.ZERO, Coin.valueOf(1_000)));
    }

    @Test
    void reopeningUpgradesStoredUnsignedRowWithoutReplacingAgentState() throws Exception {
        Dispute old = data.unsigned(true, OPENING_DATE);
        DisputeResult result = new DisputeResult(old.getTradeId(), old.getTraderId());
        old.setDisputeResult(result);
        old.setDisputePayoutTxId("dd".repeat(32));
        old.setIsClosed();
        disputes.getDisputeList().add(old);
        Dispute incoming = data.signed(true, OPENING_DATE + 1);
        receive(incoming);
        assertTrue(manager.getValidationExceptions().isEmpty());
        assertEquals(1, disputes.getDisputeList().size());
        assertSame(old, disputes.getDisputeList().getList().get(0));
        assertEquals(OPENING_DATE, old.getOpeningDate().getTime());
        assertEquals(incoming.getRefundClaimOpeningDate(), old.getRefundClaimOpeningDate());
        assertSame(result, old.disputeResultProperty().get());
        assertEquals("dd".repeat(32), old.getDisputePayoutTxId());
        assertTrue(old.isClosed());
        assertEquals(1, old.getChatMessages().size());
        assertTrue(old.getChatMessages().get(0).isSystemMessage());
        assertEquals(Res.get("support.refundClaimProofReceived"), old.getChatMessages().get(0).getMessage());
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(
                RefundClaimTestData.restore(old.toProtoMessage()), Coin.valueOf(1_000), Coin.ZERO));
        verify(persistence).requestPersistence();
    }

    @Test
    void repeatedProofUpdateDoesNotDuplicateTheLocalNotice() throws Exception {
        Dispute stored = data.unsigned(true, OPENING_DATE);
        disputes.getDisputeList().add(stored);
        Dispute incoming = data.signed(true, OPENING_DATE + 1);
        receive(incoming);
        receive(RefundClaimTestData.restore(incoming.toProtoMessage()));
        assertTrue(manager.getValidationExceptions().isEmpty());
        assertEquals(1, stored.getChatMessages().size());
        assertEquals(incoming.getRefundClaimSignature(), stored.getRefundClaimSignature());
        verify(mailbox, never()).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @Test
    void legacyGracePeriodEndsExactlyAtCutoffAndRequiresAnExistingUnsignedRow() throws Exception {
        Instant cutoff = Utilities.getUTCDate(2026, GregorianCalendar.NOVEMBER, 1).toInstant();
        Dispute legacy = data.unsigned(true, OPENING_DATE);
        assertFalse(manager.requiresLegacyRefundClaimVerification(legacy, cutoff.minusMillis(1)));
        disputes.getDisputeList().add(legacy);
        assertTrue(manager.requiresLegacyRefundClaimVerification(legacy, cutoff.minusMillis(1)));
        assertFalse(manager.requiresLegacyRefundClaimVerification(legacy, cutoff));
        assertFalse(manager.requiresLegacyRefundClaimVerification(legacy, cutoff.plusSeconds(1)));
        Dispute copy = RefundClaimTestData.restore(legacy.toProtoMessage());
        assertFalse(manager.requiresLegacyRefundClaimVerification(copy, cutoff.minusMillis(1)));
        legacy.setRefundClaimSignature("malformed");
        assertFalse(manager.requiresLegacyRefundClaimVerification(legacy, cutoff.minusMillis(1)));
        legacy.setRefundClaimSignature(" ");
        assertFalse(manager.requiresLegacyRefundClaimVerification(legacy, cutoff.minusMillis(1)));
        legacy.setRefundClaimSignature(null);
        legacy.setRefundClaimOpeningDate(OPENING_DATE);
        assertFalse(manager.requiresLegacyRefundClaimVerification(legacy, cutoff.minusMillis(1)));
    }

    @Test
    void legacyMissingProofAllowsCloseDuringGraceButNotAfterItOrForNewIntake() throws Exception {
        Instant cutoff = Utilities.getUTCDate(2026, GregorianCalendar.NOVEMBER, 1).toInstant();
        Dispute legacy = data.unsigned(true, OPENING_DATE);
        disputes.getDisputeList().add(legacy);
        try (MockedStatic<Instant> time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(cutoff.minusSeconds(1));
            assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(legacy, Coin.ZERO, Coin.valueOf(1_000)));
            assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(legacy, Coin.ZERO, Coin.ZERO));
            assertThrows(IllegalArgumentException.class,
                    () -> manager.verifyRefundClaimForPayout(legacy, Coin.valueOf(-1), Coin.valueOf(1_000)));
            receive(data.unsigned(false, OPENING_DATE + 1));
            assertEquals(1, manager.getValidationExceptions().size());
            assertEquals(1, disputes.getDisputeList().size());
            time.when(Instant::now).thenReturn(cutoff);
            assertThrows(IllegalArgumentException.class,
                    () -> manager.verifyRefundClaimForPayout(legacy, Coin.ZERO, Coin.valueOf(1_000)));
            assertThrows(IllegalArgumentException.class,
                    () -> manager.verifyRefundClaimForPayout(legacy, Coin.ZERO, Coin.ZERO));
        }
    }

    @Test
    void independentOpeningsBeforeMirrorTimerRunsDoNotCreateExtraRows() throws Exception {
        receive(data.signed(true, OPENING_DATE));
        receive(data.signed(false, OPENING_DATE + 1));
        runDelayedActions();
        assertTrue(manager.getValidationExceptions().isEmpty());
        assertEquals(2, disputes.getDisputeList().size());
        for (Dispute row : disputes.getDisputeList().getList()) {
            assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(row, Coin.ZERO, Coin.valueOf(1_000)));
            assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(row, Coin.valueOf(1_000), Coin.ZERO));
        }
    }

    @Test
    void peerProofForDifferentSubjectCannotAuthorizeOriginalRecipient() throws Exception {
        Dispute buyer = data.signed(true, OPENING_DATE);
        Dispute seller = data.signed(false, OPENING_DATE + 1);
        seller.setBurningManSelectionHeight(2);
        seller.setRefundClaimSignature(RefundClaimSignature.sign(seller, data.sellerKey, null));
        receive(buyer);
        receive(seller);
        runDelayedActions();
        assertTrue(manager.getValidationExceptions().isEmpty());
        assertEquals(2, disputes.getDisputeList().size());
        assertDoesNotThrow(() -> manager.verifyRefundClaimForPayout(seller, Coin.ZERO, Coin.valueOf(1_000)));
        assertThrows(IllegalArgumentException.class,
                () -> manager.verifyRefundClaimForPayout(buyer, Coin.ZERO, Coin.valueOf(1_000)));
    }

    @Test
    void replacementStillChecksOtherStoredRowsSharingFundingEvidence() throws Exception {
        Dispute buyer = data.signed(true, OPENING_DATE);
        receive(buyer);
        runDelayedActions();
        Dispute conflicting = RefundClaimTestData.restore(data.signed(false, OPENING_DATE + 1)
                .toProtoMessage().toBuilder().setTradeId("another-trade").build());
        disputes.getDisputeList().add(conflicting);
        String originalProof = buyer.getRefundClaimSignature();
        receive(data.signed(true, OPENING_DATE + 2));
        assertEquals(1, manager.getValidationExceptions().size());
        assertEquals(originalProof, buyer.getRefundClaimSignature());
        assertEquals(3, disputes.getDisputeList().size());
    }

    @Test
    void validPeerProofCannotReplaceAnotherTradersRowByCopyingItsNumericIdentifier() throws Exception {
        Dispute buyer = data.signed(true, OPENING_DATE);
        disputes.getDisputeList().add(buyer);
        String originalProof = buyer.getRefundClaimSignature();
        Dispute seller = RefundClaimTestData.restore(data.signed(false, OPENING_DATE + 1)
                .toProtoMessage().toBuilder().setTraderId(buyer.getTraderId()).build());
        receive(seller);
        assertEquals(1, manager.getValidationExceptions().size());
        assertEquals(originalProof, buyer.getRefundClaimSignature());
        verify(persistence, never()).requestPersistence();
    }

    @Test
    void validSignatureForDifferentSubjectCannotOverwriteStoredProof() throws Exception {
        Dispute old = data.signed(true, OPENING_DATE);
        disputes.getDisputeList().add(old);
        String originalSignature = old.getRefundClaimSignature();
        Dispute incoming = data.signed(true, OPENING_DATE + 1);
        incoming.setDelayedPayoutTxId("ee".repeat(32));
        incoming.setRefundClaimSignature(RefundClaimSignature.sign(incoming, data.buyerKey, null));
        receive(incoming);
        assertEquals(1, manager.getValidationExceptions().size());
        assertEquals(originalSignature, old.getRefundClaimSignature());
        assertEquals(OPENING_DATE, old.getRefundClaimOpeningDate());
        verify(persistence, never()).requestPersistence();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void signingServiceUsesOwnEscrowKeyAndAttachesVerifiableProof(boolean buyer) throws Exception {
        Dispute dispute = data.unsigned(buyer, OPENING_DATE);
        var key = buyer ? data.buyerKey : data.sellerKey;
        when(wallet.getMultiSigKeyPair(dispute.getTradeId(), key.getPubKey())).thenReturn(key);
        manager.signRefundClaim(dispute);
        assertEquals(OPENING_DATE, dispute.getRefundClaimOpeningDate());
        assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(dispute));
        verify(wallet).getMultiSigKeyPair(dispute.getTradeId(), key.getPubKey());
    }

    @Test
    void signingServiceUsesUnlockedEncryptedWallet() throws Exception {
        Dispute dispute = data.unsigned(true, OPENING_DATE);
        KeyCrypterScrypt crypter = new KeyCrypterScrypt();
        var aesKey = crypter.deriveKey("test password");
        when(wallet.getMultiSigKeyPair(dispute.getTradeId(), data.buyerKey.getPubKey()))
                .thenReturn(data.buyerKey.encrypt(crypter, aesKey, null));
        when(wallet.isEncrypted()).thenReturn(true);
        when(wallet.getAesKey()).thenReturn(aesKey);
        manager.signRefundClaim(dispute);
        assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(dispute));
    }

    @Test
    void missingWrongOrLockedWalletKeyCannotAttachProof() throws Exception {
        Dispute dispute = data.unsigned(true, OPENING_DATE);
        assertThrows(NullPointerException.class, () -> manager.signRefundClaim(dispute));
        assertNull(dispute.getRefundClaimSignature());
        assertEquals(0, dispute.getRefundClaimOpeningDate());
        when(wallet.getMultiSigKeyPair(anyString(), any(byte[].class))).thenReturn(data.sellerKey);
        assertThrows(IllegalArgumentException.class, () -> manager.signRefundClaim(dispute));
        assertNull(dispute.getRefundClaimSignature());
        assertEquals(0, dispute.getRefundClaimOpeningDate());
        when(wallet.getMultiSigKeyPair(anyString(), any(byte[].class))).thenReturn(data.buyerKey);
        when(wallet.isEncrypted()).thenReturn(true);
        assertThrows(NullPointerException.class, () -> manager.signRefundClaim(dispute));
        assertNull(dispute.getRefundClaimSignature());
        assertEquals(0, dispute.getRefundClaimOpeningDate());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void signingFailurePreservesUnsignedOrPreviouslySignedClaim(boolean previouslySigned) throws Exception {
        Dispute dispute = data.unsigned(true, OPENING_DATE);
        if (previouslySigned) {
            dispute.setRefundClaimOpeningDate(OPENING_DATE - 1);
            dispute.setRefundClaimSignature(RefundClaimSignature.sign(dispute, data.buyerKey, null));
        }
        protobuf.Dispute original = dispute.toProtoMessage();

        when(wallet.getMultiSigKeyPair(anyString(), any(byte[].class))).thenReturn(data.sellerKey);
        assertThrows(IllegalArgumentException.class, () -> manager.signRefundClaim(dispute));
        assertEquals(original, dispute.toProtoMessage());

        when(wallet.getMultiSigKeyPair(anyString(), any(byte[].class)))
                .thenReturn(data.buyerKey.dropPrivateBytes().dropParent());
        assertThrows(RuntimeException.class, () -> manager.signRefundClaim(dispute));
        assertEquals(original, dispute.toProtoMessage());

        KeyCrypterScrypt crypter = new KeyCrypterScrypt();
        when(wallet.getMultiSigKeyPair(anyString(), any(byte[].class)))
                .thenReturn(data.buyerKey.encrypt(crypter, crypter.deriveKey("correct password"), null));
        when(wallet.isEncrypted()).thenReturn(true);
        when(wallet.getAesKey()).thenReturn(crypter.deriveKey("wrong password"));
        assertThrows(RuntimeException.class, () -> manager.signRefundClaim(dispute));
        assertEquals(original, dispute.toProtoMessage());

        when(wallet.isEncrypted()).thenReturn(false);
        when(wallet.getMultiSigKeyPair(anyString(), any(byte[].class))).thenReturn(data.buyerKey);
        String delayedPayoutTxId = dispute.getDelayedPayoutTxId();
        dispute.setDelayedPayoutTxId(null);
        protobuf.Dispute malformedSubject = dispute.toProtoMessage();
        assertThrows(IllegalArgumentException.class, () -> manager.signRefundClaim(dispute));
        assertEquals(malformedSubject, dispute.toProtoMessage());
        dispute.setDelayedPayoutTxId(delayedPayoutTxId);
        manager.signRefundClaim(dispute);
        assertEquals(OPENING_DATE, dispute.getRefundClaimOpeningDate());
        assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(dispute));
    }

    private void receive(Dispute dispute) {
        manager.onSupportMessage(message(dispute), dispute.getTraderPubKeyRing().getSignaturePubKey());
    }

    private OpenNewDisputeMessage message(Dispute dispute) {
        return new OpenNewDisputeMessage(dispute, dispute.isDisputeOpenerIsBuyer() ?
                RefundClaimTestData.BUYER_ADDRESS : RefundClaimTestData.SELLER_ADDRESS,
                "claim-" + dispute.getRefundClaimOpeningDate(), SupportType.REFUND);
    }

    private void assertRejected() {
        assertTrue(disputes.getDisputeList().isEmpty());
        assertEquals(1, manager.getValidationExceptions().size());
        assertTrue(delayedActions.isEmpty());
        verify(persistence, never()).requestPersistence();
    }

    private void runDelayedActions() {
        List<Runnable> pending = List.copyOf(delayedActions);
        delayedActions.clear();
        pending.forEach(Runnable::run);
    }
}
