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
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.offer.Offer;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerVerifyBuyerPaymentAccount;
import bisq.core.user.User;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.UserThread;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A seller whose deposit is not published yet, with a ProcessModel that went through the persistence as the
 * delayed payout signature response leaves it, and the real tasks that send the deposit message and publish the
 * deposit. The network, the wallet and the trade are mocks.
 */
class SellerProtocolRestartTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final String TRADE_ID = "trade-id";

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        Context.propagate(new Context(PARAMS));
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    @Test
    void aCryptoSellerSendsAgainAtARestartAndPublishesOnTheAck() {
        Fixture f = new Fixture(false);
        f.protocol.onInitialized();

        assertEquals(1, f.sendListeners.size());
        f.sendListeners.get(0).onArrived();
        f.verifyNoPublication();

        f.protocol.onAckMessage(f.ack(true), f.buyer);

        assertTrue(f.processModel.isDepositTxAndDelayedPayoutTxMessageDelivered());
        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(f.tradeWalletService, times(1)).broadcastTx(tx.capture(), any(TxBroadcaster.Callback.class));
        assertArrayEquals(f.finalizedDepositTx.bitcoinSerialize(), tx.getValue().bitcoinSerialize());
        // The ACK completed the running task, no second task sends the message again
        assertEquals(1, f.sendListeners.size());
    }

    @Test
    void aLateAckAfterTheGiveUpPublishesOnceAndRemovesItsError() {
        Fixture f = new Fixture(false);
        f.protocol.onInitialized();
        f.giveUp();
        assertTrue(f.errorMessage.get().endsWith("a restart of the application sends the message again."));
        f.verifyNoPublication();

        f.protocol.onAckMessage(f.ack(true), f.buyer);

        assertTrue(f.processModel.isDepositTxAndDelayedPayoutTxMessageDelivered());
        verify(f.tradeWalletService, times(1)).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
        assertNull(f.errorMessage.get());
        // The resumed task applies the ACK at once and does not send again
        assertEquals(8, f.sendListeners.size());

        f.protocol.onAckMessage(f.ack(true), f.buyer);
        verify(f.tradeWalletService, times(1)).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
        assertEquals(8, f.sendListeners.size());
    }

    // A seller of an older version stopped with its own text and restarts with this version
    @Test
    void theAckAfterARestartRemovesTheStopErrorOfAnOlderVersion() {
        Fixture f = new Fixture(false);
        f.errorMessage.set("An error occurred at task: SellerSendsDepositTxAndDelayedPayoutTxMessage\n" +
                "We never received an ACK message when sending the msg to the peer. " +
                "We fail here and do not publish the deposit tx.");
        f.protocol.onInitialized();

        f.protocol.onAckMessage(f.ack(true), f.buyer);

        assertNull(f.errorMessage.get());
        verify(f.tradeWalletService, times(1)).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
    }

    @Test
    void aLateAckKeepsAnotherErrorOfTheTrade() {
        Fixture f = new Fixture(false);
        f.protocol.onInitialized();
        f.giveUp();
        f.errorMessage.set("Another error");

        f.protocol.onAckMessage(f.ack(true), f.buyer);

        assertEquals("Another error", f.errorMessage.get());
    }

    @Test
    void aLateNackResumesNothing() {
        Fixture f = new Fixture(false);
        f.protocol.onInitialized();
        f.giveUp();

        f.protocol.onAckMessage(f.ack(false), f.buyer);

        assertFalse(f.processModel.isDepositTxAndDelayedPayoutTxMessageDelivered());
        f.verifyNoPublication();
        assertEquals(8, f.sendListeners.size());
    }

    @Test
    void anAckFromTheMailboxAtStartupPublishesOnce() {
        Fixture f = new Fixture(false);
        when(f.mailboxMessageService.getMyDecryptedMailboxMessages()).thenReturn(Set.of(
                new DecryptedMessageWithPubKey(f.ack(true), Sig.generateKeyPair().getPublic())));
        f.protocol.onInitialized();
        assertEquals(1, f.sendListeners.size());

        // The mailbox messages are handled after a short delay
        ManualTimer.latest().fire();

        assertTrue(f.processModel.isDepositTxAndDelayedPayoutTxMessageDelivered());
        verify(f.tradeWalletService, times(1)).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
        assertEquals(1, f.sendListeners.size());
    }

    @Test
    void aLateAckOfAFiatTradeWaitsForTheBuyerAccount() {
        Fixture f = new Fixture(true);
        f.protocol.onInitialized();
        f.giveUp();

        f.protocol.onAckMessage(f.ack(true), f.buyer);

        assertTrue(f.processModel.isDepositTxAndDelayedPayoutTxMessageDelivered());
        f.verifyNoPublication();
    }

    private static final class Fixture {
        private final Transaction finalizedDepositTx;
        private final ProcessModel processModel;
        private final SellerAsMakerTrade trade = mock(SellerAsMakerTrade.class);
        private final AtomicReference<String> errorMessage = new AtomicReference<>();
        private final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        private final MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
        private final List<SendMailboxMessageListener> sendListeners = new ArrayList<>();
        private final NodeAddress buyer = new NodeAddress("buyer.onion", 9999);
        private final SellerProtocol protocol;

        private Fixture(boolean isFiatOffer) {
            finalizedDepositTx = new Transaction(PARAMS);
            finalizedDepositTx.addInput(Sha256Hash.of(new byte[]{1}), 0, new Script(new byte[0]));
            finalizedDepositTx.addOutput(Coin.COIN, SegwitAddress.fromKey(PARAMS, new ECKey()));
            Transaction delayedPayoutTx = new Transaction(PARAMS);
            delayedPayoutTx.addInput(finalizedDepositTx.getTxId(), 0, new Script(new byte[0]));
            delayedPayoutTx.addOutput(Coin.COIN, SegwitAddress.fromKey(PARAMS, new ECKey()));

            // What SellerProcessDelayedPayoutTxSignatureResponse stores, after the persistence
            ProcessModel stored = new ProcessModel(TRADE_ID, "account-id", pubKeyRing());
            stored.setFinalizedDepositTx(finalizedDepositTx);
            processModel = ProcessModel.fromProto(stored.toProtoMessage(), mock(CoreProtoResolver.class));

            Offer offer = mock(Offer.class);
            when(offer.isFiatOffer()).thenReturn(isFiatOffer);
            when(offer.getId()).thenReturn(TRADE_ID);
            BtcWalletService btcWalletService = mock(BtcWalletService.class);
            when(btcWalletService.getParams()).thenReturn(PARAMS);
            when(btcWalletService.getTxFromSerializedTx(any()))
                    .thenAnswer(invocation -> new Transaction(PARAMS, invocation.getArgument(0)));
            P2PService p2PService = mock(P2PService.class);
            when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
            when(p2PService.getAddress()).thenReturn(new NodeAddress("seller.onion", 9999));
            doAnswer(invocation -> {
                sendListeners.add(invocation.getArgument(3));
                return null;
            }).when(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());
            PaymentAccount paymentAccount = mock(PaymentAccount.class);
            when(paymentAccount.getPaymentAccountPayload()).thenReturn(mock(PaymentAccountPayload.class));
            User user = mock(User.class);
            when(user.getPaymentAccount(any())).thenReturn(paymentAccount);
            Provider provider = mock(Provider.class);
            when(provider.getBtcWalletService()).thenReturn(btcWalletService);
            when(provider.getTradeWalletService()).thenReturn(tradeWalletService);
            when(provider.getP2PService()).thenReturn(p2PService);
            when(provider.getUser()).thenReturn(user);
            processModel.applyTransient(provider, mock(TradeManager.class), offer);

            when(trade.getProcessModel()).thenReturn(processModel);
            when(trade.getTradeProtocolModel()).thenReturn(processModel);
            when(trade.getId()).thenReturn(TRADE_ID);
            when(trade.getOffer()).thenReturn(offer);
            when(trade.getTradePhase()).thenReturn(Trade.Phase.TAKER_FEE_PUBLISHED);
            when(trade.getDelayedPayoutTxBytes()).thenReturn(delayedPayoutTx.bitcoinSerialize());
            when(trade.getDelayedPayoutTx()).thenReturn(delayedPayoutTx);
            when(trade.getTradingPeerNodeAddress()).thenReturn(buyer);
            doAnswer(invocation -> {
                errorMessage.set(invocation.getArgument(0));
                return null;
            }).when(trade).setErrorMessage(any());
            when(trade.getErrorMessage()).thenAnswer(invocation -> errorMessage.get());

            protocol = new SellerProtocol(trade) {
                @Override
                protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
                    return SellerVerifyBuyerPaymentAccount.class;
                }
            };
        }

        // The message arrives at every attempt, and no ACK comes
        private void giveUp() {
            for (int attempt = 0; attempt < 7; attempt++) {
                sendListeners.get(attempt).onArrived();
                ManualTimer.latest().fire();
            }
            sendListeners.get(7).onArrived();
            assertEquals(8, sendListeners.size());
        }

        private void verifyNoPublication() {
            verify(tradeWalletService, never()).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
        }

        private AckMessage ack(boolean success) {
            return new AckMessage(buyer,
                    AckMessageSourceType.TRADE_MESSAGE,
                    DepositTxAndDelayedPayoutTxMessage.class.getSimpleName(),
                    "uid",
                    TRADE_ID,
                    success,
                    success ? null : "error");
        }

        private static PubKeyRing pubKeyRing() {
            return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
        }
    }
}
