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
import bisq.core.filter.FilterPolicyService;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bisq_v1.messages.MediatedPayoutTxSignatureMessage;
import bisq.core.trade.protocol.bisq_v1.messages.PeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.ProcessPeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.BroadcastMediatedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.FinalizeMediatedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.ProcessMediatedPayoutSignatureMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.SendMediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.SignMediatedPayoutTx;
import bisq.core.user.User;

import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.listeners.NewBestBlockListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The publication of the mediated payout without a click, on a real trade in which both traders have signed: it
 * waits for the wallet and for the mailbox messages of the trade at startup, and it does not happen once the
 * delayed payout tx is published.
 */
class MediatedPayoutAutomaticFinalizationTest {
    private static final NodeAddress PEER = new NodeAddress("peer.onion:9999");
    private static final List<Class<?>> FINALIZE_TASKS = List.of(ApplyFilter.class,
            SignMediatedPayoutTx.class,
            FinalizeMediatedPayoutTx.class,
            BroadcastMediatedPayoutTx.class,
            SendMediatedPayoutTxPublishedMessage.class);

    private final Provider provider = mock(Provider.class);
    private final BtcWalletService btcWalletService = mock(BtcWalletService.class);
    private final MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
    private final FilterPolicyService filterPolicyService = mock(FilterPolicyService.class);
    private Trade trade;
    private RecordingDisputeProtocol protocol;

    @BeforeEach
    void setUp() {
        P2PService p2PService = mock(P2PService.class);
        when(provider.getP2PService()).thenReturn(p2PService);
        when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
        when(provider.getUser()).thenReturn(mock(User.class));
        when(provider.getBtcWalletService()).thenReturn(btcWalletService);
        when(btcWalletService.isDownloadComplete()).thenReturn(true);
        when(btcWalletService.hasSufficientPeersForBroadcast()).thenReturn(true);
        // The startup mailbox messages are handled already, unless a test says otherwise
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mailboxMessageService).runOnceStartupMessagesHandled(any());

        trade = MediationTestTrades.sellerAsMakerTrade(provider,
                Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG,
                Trade.DisputeState.MEDIATION_CLOSED);
        ProcessModel processModel = trade.getProcessModel();
        processModel.setMediatedPayoutTxSignature(new byte[]{1});
        processModel.getTradePeer().setMediatedPayoutTxSignature(new byte[]{2});
        Transaction delayedPayoutTx = mock(Transaction.class);
        when(delayedPayoutTx.getTxId()).thenReturn(Sha256Hash.ZERO_HASH);
        trade.applyDelayedPayoutTx(delayedPayoutTx);
        protocol = new RecordingDisputeProtocol(trade);
        UserThread.setTimerClass(ManualTimer.class);
    }

    @AfterEach
    void tearDown() {
        UserThread.setTimerClass(FrameRateTimer.class);
        ManualTimer.clear();
    }

    @Test
    void publishesAtStartupWithASyncedWallet() {
        protocol.onStartupMailboxMessagesHandled();

        assertEquals(FINALIZE_TASKS, protocol.executedTasks);
    }

    // The dispute managers apply a refund request of the peer only with a synced wallet with enough peers, and the
    // deposit tx can be missing before
    @ParameterizedTest
    @CsvSource({"false, true", "true, false"})
    void waitsForTheWalletAndPublishesAtTheNextBlock(boolean downloadComplete, boolean sufficientPeers)
            throws Exception {
        when(btcWalletService.isDownloadComplete()).thenReturn(downloadComplete);
        when(btcWalletService.hasSufficientPeersForBroadcast()).thenReturn(sufficientPeers);

        protocol.onStartupMailboxMessagesHandled();

        assertTrue(protocol.executedTasks.isEmpty());
        ArgumentCaptor<NewBestBlockListener> listener = ArgumentCaptor.forClass(NewBestBlockListener.class);
        verify(btcWalletService).addNewBestBlockListener(listener.capture());

        when(btcWalletService.isDownloadComplete()).thenReturn(true);
        when(btcWalletService.hasSufficientPeersForBroadcast()).thenReturn(true);
        listener.getValue().notifyNewBestBlock(null);

        assertEquals(FINALIZE_TASKS, protocol.executedTasks);
        verify(btcWalletService).removeNewBestBlockListener(listener.getValue());
    }

    // A trader who went to arbitration has published the delayed payout tx, which spends the deposit output
    @Test
    void doesNotPublishOnceTheDelayedPayoutTxIsPublished() {
        when(btcWalletService.getConfidenceForTxId(Sha256Hash.ZERO_HASH.toString()))
                .thenReturn(mock(TransactionConfidence.class));

        protocol.onStartupMailboxMessagesHandled();

        assertTrue(protocol.executedTasks.isEmpty());
        verify(btcWalletService, never()).addNewBestBlockListener(any());
    }

    // The peer published the delayed payout tx while we were offline. Its message in our mailbox adds the tx to our
    // wallet, and the check at startup runs after it.
    @Test
    void checksAtStartupAfterTheMailboxMessagesOfTheTrade() {
        PeerPublishedDelayedPayoutTxMessage message = new PeerPublishedDelayedPayoutTxMessage("uid", trade.getId(),
                PEER);
        when(mailboxMessageService.getMyDecryptedMailboxMessages()).thenReturn(Set.of(
                new DecryptedMessageWithPubKey(message, Sig.generateKeyPair().getPublic())));
        // As ProcessPeerPublishedDelayedPayoutTxMessage, which the recording protocol does not run
        when(btcWalletService.getConfidenceForTxId(anyString())).thenAnswer(invocation ->
                protocol.executedTasks.contains(ProcessPeerPublishedDelayedPayoutTxMessage.class) ?
                        mock(TransactionConfidence.class) :
                        null);

        protocol.onInitialized();
        ManualTimer.firePendingTimers();

        assertEquals(List.of(ProcessPeerPublishedDelayedPayoutTxMessage.class), protocol.executedTasks);
    }

    // After a long time offline, the mailbox messages of the startup are decrypted on another thread and can reach the
    // trade only after its own startup handling. The check waits until they are handled.
    @Test
    void checksAtStartupAfterTheMailboxMessagesThatAreStillDecrypted() {
        PeerPublishedDelayedPayoutTxMessage message = new PeerPublishedDelayedPayoutTxMessage("uid", trade.getId(),
                PEER);
        when(mailboxMessageService.getMyDecryptedMailboxMessages()).thenReturn(Set.of());
        ArgumentCaptor<Runnable> startupMessagesHandled = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(mailboxMessageService).runOnceStartupMessagesHandled(startupMessagesHandled.capture());
        // As ProcessPeerPublishedDelayedPayoutTxMessage, which the recording protocol does not run
        when(btcWalletService.getConfidenceForTxId(anyString())).thenAnswer(invocation ->
                protocol.executedTasks.contains(ProcessPeerPublishedDelayedPayoutTxMessage.class) ?
                        mock(TransactionConfidence.class) :
                        null);

        protocol.onInitialized();
        ManualTimer.firePendingTimers();
        assertTrue(protocol.executedTasks.isEmpty());

        protocol.onMailboxMessageAdded(new DecryptedMessageWithPubKey(message, Sig.generateKeyPair().getPublic()),
                PEER);
        startupMessagesHandled.getValue().run();

        assertEquals(List.of(ProcessPeerPublishedDelayedPayoutTxMessage.class), protocol.executedTasks);
    }

    // Positive control on the same path
    @Test
    void publishesAtStartupWithoutMailboxMessages() {
        when(mailboxMessageService.getMyDecryptedMailboxMessages()).thenReturn(Set.of());

        protocol.onInitialized();
        ManualTimer.firePendingTimers();

        assertEquals(FINALIZE_TASKS, protocol.executedTasks);
    }

    // At runtime, the processed signature of the peer starts the publication. The trade has the signature already, as
    // ProcessMediatedPayoutSignatureMessage would have stored it.
    @Test
    void publishesOnceTheSignatureOfThePeerIsProcessed() {
        protocol.runResultHandlers = true;

        protocol.handle(signatureMessage(), PEER);

        List<Class<?>> expected = new ArrayList<>(List.of(ProcessMediatedPayoutSignatureMessage.class));
        expected.addAll(FINALIZE_TASKS);
        assertEquals(expected, protocol.executedTasks);
    }

    @Test
    void waitsForTheWalletWhenTheSignatureOfThePeerIsProcessed() {
        when(btcWalletService.isDownloadComplete()).thenReturn(false);
        protocol.runResultHandlers = true;

        protocol.handle(signatureMessage(), PEER);

        assertEquals(List.of(ProcessMediatedPayoutSignatureMessage.class), protocol.executedTasks);
        verify(btcWalletService).addNewBestBlockListener(any());
    }

    // With the real tasks, SignMediatedPayoutTx fails without the deposit tx. The trader did not start the attempt, so
    // the UI must not show it as an error of the trade.
    @Test
    void failedAttemptWithoutAClickIsNoTradeError() {
        DisputeProtocol realProtocol = protocolWithRealTasks();

        realProtocol.onStartupMailboxMessagesHandled();

        // ApplyFilter ran, so the attempt was made
        verify(filterPolicyService).isNodeAddressBanned(PEER);
        assertNull(trade.getErrorMessage());
        assertNull(trade.getPayoutTx());
    }

    // Positive control on the same tasks: the accept button shows the failure to the trader
    @Test
    void failedAttemptOfTheAcceptButtonIsATradeError() {
        DisputeProtocol realProtocol = protocolWithRealTasks();

        realProtocol.onFinalizeMediationResultPayout(() -> {
        }, errorMessage -> {
        });

        assertTrue(trade.getErrorMessage().contains(SignMediatedPayoutTx.class.getSimpleName()));
    }

    // The trade is moved to the failed trades while the publication waits for the wallet
    @Test
    void theProtocolOfAFailedTradeDoesNotPublishAtTheNextBlock() {
        when(btcWalletService.isDownloadComplete()).thenReturn(false);
        DisputeProtocol realProtocol = protocolWithRealTasks();
        realProtocol.onStartupMailboxMessagesHandled();
        ArgumentCaptor<NewBestBlockListener> listener = ArgumentCaptor.forClass(NewBestBlockListener.class);
        verify(btcWalletService).addNewBestBlockListener(listener.capture());

        realProtocol.shutDown();
        when(btcWalletService.isDownloadComplete()).thenReturn(true);
        listener.getValue().notifyNewBestBlock(null);

        verify(btcWalletService).removeNewBestBlockListener(listener.getValue());
        // ApplyFilter did not run, so no attempt was made
        verify(filterPolicyService, never()).isNodeAddressBanned(PEER);
        assertNull(trade.getPayoutTx());
    }

    // The check runs later on the user thread, and the trade can leave the pending trades before
    @Test
    void aDeferredCheckOfAFailedTradeDoesNotWaitForTheWallet() {
        when(btcWalletService.isDownloadComplete()).thenReturn(false);
        List<Runnable> deferred = new ArrayList<>();
        UserThread.setExecutor(deferred::add);
        try {
            DisputeProtocol realProtocol = protocolWithRealTasks();
            realProtocol.onStartupMailboxMessagesHandled();

            realProtocol.shutDown();
            deferred.forEach(Runnable::run);

            verify(btcWalletService, never()).addNewBestBlockListener(any());
        } finally {
            UserThread.resetForTests();
        }
    }

    // ApplyFilter passes, so the chain reaches SignMediatedPayoutTx
    private DisputeProtocol protocolWithRealTasks() {
        when(provider.getFilterPolicyService()).thenReturn(filterPolicyService);
        trade.setTradingPeerNodeAddress(PEER);
        return new DisputeProtocol(trade);
    }

    private MediatedPayoutTxSignatureMessage signatureMessage() {
        return new MediatedPayoutTxSignatureMessage(new byte[]{2}, trade.getId(), PEER, "uid");
    }
}
