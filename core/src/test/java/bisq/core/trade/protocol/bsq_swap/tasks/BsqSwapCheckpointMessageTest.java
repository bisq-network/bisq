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

package bisq.core.trade.protocol.bsq_swap.tasks;

import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.http.MemPoolSpaceTxBroadcaster;
import bisq.core.dao.DaoCheckpointTestFixture;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.BuyerPublishesTx;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.SendFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.seller.SendBsqSwapFinalizeTxRequest;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BsqSwapCheckpointMessageTest {
    @Test
    void sellerRechecksBeforeReleasingPreviouslyPreparedSignatures() {
        Fixture f = new Fixture();
        assertTrue(f.checkpoint.facade.isDaoStateReadyAndInSync());
        f.checkpoint.failCheckpoint();

        TaskResult result = runTasks(f, SendBsqSwapFinalizeTxRequest.class);

        assertFalse(result.completed.get());
        assertTrue(result.error.get().contains("DAO state is not ready and in sync"));
        verify(f.p2p, never()).sendEncryptedDirectMessage(any(), any(), any(), any());
    }

    @Test
    void healthySellerStillReleasesSignedRequest() {
        Fixture f = new Fixture();

        TaskResult result = runTasks(f, SendBsqSwapFinalizeTxRequest.class);

        assertTrue(result.completed.get());
        assertEquals("", result.error.get());
        verify(f.p2p).sendEncryptedDirectMessage(any(), any(), any(), any());
    }

    @Test
    void buyerWithoutHandoffCannotReleaseAfterFailure() {
        Fixture f = new Fixture();
        f.checkpoint.failCheckpoint();

        TaskResult result = runTasks(f, SendFinalizedTxMessage.class);

        assertFalse(result.completed.get());
        assertTrue(result.error.get().contains("transaction publication handoff is unknown"));
        verify(f.p2p, never()).sendEncryptedDirectMessage(any(), any(), any(), any());
    }

    @Test
    void healthyBuyerCanReleaseWithoutHandoff() {
        Fixture f = new Fixture();

        TaskResult result = runTasks(f, SendFinalizedTxMessage.class);

        assertTrue(result.completed.get());
        verify(f.p2p).sendEncryptedDirectMessage(any(), any(), any(), any());
    }

    @Test
    void differentTransactionHandoffCannotAuthorizeRelease() {
        Fixture f = new Fixture();
        f.model.recordTransactionPublication(new byte[]{1, 2, 3});
        f.checkpoint.failCheckpoint();

        TaskResult result = runTasks(f, SendFinalizedTxMessage.class);

        assertFalse(result.completed.get());
        verify(f.p2p, never()).sendEncryptedDirectMessage(any(), any(), any(), any());
    }

    @Test
    void publicationHandoffAllowsFinalNotificationBeforeSuccessCallback() {
        Fixture f = new Fixture();
        TaskResult publication = runTasks(f, BuyerPublishesTx.class);
        assertTrue(publication.completed.get());
        assertTrue(f.model.hasTransactionPublication(f.model.getTx()));
        verify(f.tradeManager, never()).onBsqSwapTradeCompleted(any());
        f.checkpoint.failCheckpoint();

        TaskResult notification = runTasks(f, SendFinalizedTxMessage.class);

        assertTrue(notification.completed.get());
        ArgumentCaptor<BsqSwapFinalizedTxMessage> sent = ArgumentCaptor.forClass(BsqSwapFinalizedTxMessage.class);
        verify(f.p2p).sendEncryptedDirectMessage(any(), any(), sent.capture(), any());
        f.model.getTx()[0] ^= 1;
        assertArrayEquals(f.tx.bitcoinSerialize(), sent.getValue().getTx());
        verify(f.wallets).publishAndCommitBsqTx(eq(f.tx), eq(TxType.TRANSFER_BSQ), any());
    }

    @Test
    void failureBeforePublicationCannotEstablishHandoff() {
        Fixture f = new Fixture();
        f.checkpoint.failCheckpoint();

        TaskResult result = runTasks(f, BuyerPublishesTx.class, SendFinalizedTxMessage.class);

        assertFalse(result.completed.get());
        assertTrue(result.error.get().contains("DAO checkpoint verification failed"));
        assertFalse(f.model.hasTransactionPublication(f.model.getTx()));
        verify(f.p2p, never()).sendEncryptedDirectMessage(any(), any(), any(), any());
    }

    @Test
    void walletMembershipDoesNotEstablishHandoffButStillRecordsSettlement() {
        Fixture f = new Fixture();
        when(f.tradeWallet.getWalletTx(f.tx.getTxId())).thenReturn(f.tx);
        f.checkpoint.failCheckpoint();

        TaskResult result = runTasks(f, BuyerPublishesTx.class, SendFinalizedTxMessage.class);

        assertFalse(result.completed.get());
        assertFalse(f.model.hasTransactionPublication(f.model.getTx()));
        verify(f.trade).applyTransaction(f.tx);
        verify(f.trade).setState(BsqSwapTrade.State.COMPLETED);
        verify(f.tradeManager).onBsqSwapTradeCompleted(f.trade);
        verifyNoInteractions(f.wallets);
        verify(f.p2p, never()).sendEncryptedDirectMessage(any(), any(), any(), any());
    }

    @Test
    void throwingPublicationDoesNotEstablishHandoffOrReleaseSignatures() {
        Fixture f = new Fixture();
        doThrow(new IllegalStateException("publication failed")).when(f.wallets)
                .publishAndCommitBsqTx(any(), any(), any());

        TaskResult result = runTasks(f, BuyerPublishesTx.class, SendFinalizedTxMessage.class);

        assertFalse(result.completed.get());
        assertFalse(f.model.hasTransactionPublication(f.model.getTx()));
        verify(f.p2p, never()).sendEncryptedDirectMessage(any(), any(), any(), any());
    }

    @Test
    void immediateSuccessCannotAdvanceBeforePublicationReturnsAndRecordsHandoff() {
        Fixture f = new Fixture();
        doAnswer(invocation -> {
            invocation.<TxBroadcaster.Callback>getArgument(2).onSuccess(f.tx);
            assertFalse(f.model.hasTransactionPublication(f.model.getTx()));
            verify(f.p2p, never()).sendEncryptedDirectMessage(any(), any(), any(), any());
            f.checkpoint.failCheckpoint();
            return null;
        }).when(f.wallets).publishAndCommitBsqTx(any(), any(), any());

        TaskResult result = runTasks(f, BuyerPublishesTx.class, SendFinalizedTxMessage.class);

        assertTrue(result.completed.get());
        assertTrue(f.model.hasTransactionPublication(f.model.getTx()));
        verify(f.p2p, times(1)).sendEncryptedDirectMessage(any(), any(), any(), any());
        verify(f.tradeManager).onBsqSwapTradeCompleted(f.trade);
    }

    @Test
    void realBroadcasterDefersImmediateResultAndLaterFailureDoesNotSuppressSettlement() {
        Fixture f = new Fixture();
        Wallet wallet = mock(Wallet.class);
        PeerGroup peers = mock(PeerGroup.class);
        SettableFuture<Transaction> future = SettableFuture.create();
        future.set(f.tx);
        when(peers.broadcastTransaction(f.tx)).thenReturn(TransactionBroadcast.createMockBroadcast(f.tx, future));
        List<Runnable> callbacks = new ArrayList<>();

        try (var userThread = mockStatic(UserThread.class);
             var mempool = mockStatic(MemPoolSpaceTxBroadcaster.class)) {
            userThread.when(() -> UserThread.runAfter(any(), anyLong())).thenReturn(mock(Timer.class));
            userThread.when(() -> UserThread.execute(any())).thenAnswer(invocation -> {
                callbacks.add(invocation.getArgument(0));
                return null;
            });
            doAnswer(invocation -> {
                f.checkpoint.monitor.assertCheckpointNotFailed();
                TxBroadcaster.broadcastTx(wallet, peers, f.tx, invocation.getArgument(2), 1);
                assertFalse(f.model.hasTransactionPublication(f.model.getTx()));
                verify(f.tradeManager, never()).onBsqSwapTradeCompleted(any());
                return null;
            }).when(f.wallets).publishAndCommitBsqTx(any(), any(), any());

            TaskResult publication = runTasks(f, BuyerPublishesTx.class);
            assertTrue(publication.completed.get());
            assertTrue(f.model.hasTransactionPublication(f.model.getTx()));
            assertEquals(1, callbacks.size());
            f.checkpoint.failCheckpoint();
            callbacks.getFirst().run();
            TaskResult notification = runTasks(f, SendFinalizedTxMessage.class);

            assertTrue(notification.completed.get());
            verify(f.trade).applyTransaction(f.tx);
            verify(f.trade).setState(BsqSwapTrade.State.COMPLETED);
            verify(f.tradeManager).onBsqSwapTradeCompleted(f.trade);
            verify(peers, times(1)).broadcastTransaction(f.tx);
        }
    }

    @SafeVarargs
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TaskResult runTasks(Fixture fixture, Class<? extends Task>... tasks) {
        TaskResult result = new TaskResult(new AtomicBoolean(), new AtomicReference<>(""));
        TaskRunner runner = new TaskRunner(fixture.trade, BsqSwapTrade.class,
                () -> result.completed.set(true), result.error::set);
        runner.addTasks(tasks);
        runner.run();
        return result;
    }

    private record TaskResult(AtomicBoolean completed, AtomicReference<String> error) {
    }

    private static class Fixture {
        private final DaoCheckpointTestFixture checkpoint = new DaoCheckpointTestFixture();
        private final TradeWalletService tradeWallet = mock(TradeWalletService.class);
        private final WalletsManager wallets = mock(WalletsManager.class);
        private final TradeManager tradeManager = mock(TradeManager.class);
        private final P2PService p2p = mock(P2PService.class);
        private final BsqSwapTrade trade = mock(BsqSwapTrade.class);
        private final BsqSwapProtocolModel model = new BsqSwapProtocolModel(mock(PubKeyRing.class));
        private final Transaction tx;

        private Fixture() {
            Context.propagate(new Context(RegTestParams.get()));
            tx = new Transaction(RegTestParams.get());
            Provider provider = mock(Provider.class);
            Offer offer = mock(Offer.class);
            when(offer.getId()).thenReturn("trade-id");
            when(provider.getDaoFacade()).thenReturn(checkpoint.facade);
            when(provider.getTradeWalletService()).thenReturn(tradeWallet);
            when(provider.getWalletsManager()).thenReturn(wallets);
            when(provider.getP2PService()).thenReturn(p2p);
            when(p2p.getAddress()).thenReturn(new NodeAddress("local.onion:8000"));
            when(trade.getTradingPeerNodeAddress()).thenReturn(new NodeAddress("peer.onion:8000"));
            when(trade.getBsqSwapProtocolModel()).thenReturn(model);
            when(trade.getState()).thenReturn(BsqSwapTrade.State.PREPARATION);
            model.applyTransient(provider, tradeManager, offer);
            model.applyTransaction(tx);
            model.setInputs(List.of());
            model.getTradePeer().setPubKeyRing(mock(PubKeyRing.class));
            doAnswer(invocation -> {
                invocation.<SendDirectMessageListener>getArgument(3).onArrived();
                return null;
            }).when(p2p).sendEncryptedDirectMessage(any(), any(), any(), any());
            doAnswer(invocation -> {
                checkpoint.monitor.assertCheckpointNotFailed();
                return null;
            }).when(wallets).publishAndCommitBsqTx(any(), any(), any());
        }
    }
}
