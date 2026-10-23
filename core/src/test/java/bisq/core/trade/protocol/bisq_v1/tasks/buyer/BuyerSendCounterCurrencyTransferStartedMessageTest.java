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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.network.MessageState;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.UserThread;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the short-circuit in BuyerSendCounterCurrencyTransferStartedMessage#run:
 * when the seller's ACK was already processed while no task instance was listening
 * (it can arrive after a restart, before the task is re-created at startup), the
 * task must not send again but only apply the BUYER_SAW_ARRIVED state transition
 * the ACK would have triggered.
 */
public class BuyerSendCounterCurrencyTransferStartedMessageTest {

    private Trade trade;
    private ProcessModel processModel;
    private SimpleObjectProperty<MessageState> paymentStartedMessageState;
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private final List<SendMailboxMessageListener> listeners = new ArrayList<>();
    private TaskRunner<TradeModel> runner;

    @BeforeEach
    public void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);

        trade = mock(Trade.class);
        processModel = mock(ProcessModel.class);
        paymentStartedMessageState = new SimpleObjectProperty<>(MessageState.UNDEFINED);

        when(trade.getProcessModel()).thenReturn(processModel);
        when(processModel.getPaymentStartedMessageStateProperty()).thenReturn(paymentStartedMessageState);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
    }

    @AfterEach
    public void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    private void prepareSending() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        AddressEntry payoutAddressEntry = mock(AddressEntry.class);
        when(payoutAddressEntry.getAddressString()).thenReturn("payout-address");
        when(btcWalletService.getOrCreateAddressEntry("trade-id", AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(payoutAddressEntry);
        when(processModel.getBtcWalletService()).thenReturn(btcWalletService);
        when(processModel.getOfferId()).thenReturn("trade-id");
        when(processModel.getPayoutTxSignature()).thenReturn(new byte[]{1, 2, 3});
        when(processModel.getMyNodeAddress()).thenReturn(new NodeAddress("buyer.onion", 9999));
        when(trade.getTradeState()).thenReturn(Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN);
        when(trade.getTradingPeerNodeAddress()).thenReturn(new NodeAddress("seller.onion", 9999));
        TradingPeer tradingPeer = new TradingPeer();
        tradingPeer.setPubKeyRing(new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic()));
        when(processModel.getTradePeer()).thenReturn(tradingPeer);
        P2PService p2PService = mock(P2PService.class);
        MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
        when(processModel.getP2PService()).thenReturn(p2PService);
        doAnswer(i -> {
            listeners.add(i.getArgument(3));
            return null;
        }).when(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private void runTask() {
        // same unchecked narrowing as TradeTaskRunner: tasks declare (TaskRunner, Trade)
        // constructors, so the runner must look them up with Trade.class
        runner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage::set);
        runner.addTasks(BuyerSendCounterCurrencyTransferStartedMessage.class);
        runner.run();
    }

    @Test
    public void alreadyAcknowledgedMessageIsNotSentAgainButStateIsApplied() {
        paymentStartedMessageState.set(MessageState.ACKNOWLEDGED);

        runTask();

        verify(trade).setStateIfValidTransitionTo(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
        // no send attempt: the wallet (payout address lookup for the message) is never touched
        verify(processModel, never()).getBtcWalletService();
        assertTrue(completed.get());
        assertNull(errorMessage.get());
    }

    @Test
    public void withoutAckTheSendPathIsTaken() {
        paymentStartedMessageState.set(MessageState.SENT);

        runTask();

        // the send path is attempted (and fails in this stripped-down environment,
        // proving the task did not short-circuit) and no ACK state transition happens
        verify(trade, never()).setStateIfValidTransitionTo(any());
        verify(processModel).getBtcWalletService();
        assertFalse(completed.get());
        assertNotNull(errorMessage.get());
    }

    @Test
    public void withoutCancelTheTimerSendsAgainAndTheAckCompletesTheTask() {
        prepareSending();
        runTask();
        listeners.get(0).onStoredInMailbox();
        ManualTimer resend = ManualTimer.latest();
        assertFalse(resend.isStopped());

        resend.fire();
        assertEquals(2, listeners.size());
        listeners.get(1).onStoredInMailbox();
        paymentStartedMessageState.set(MessageState.ACKNOWLEDGED);

        assertTrue(completed.get());
        assertNull(errorMessage.get());
        verify(trade).setStateIfValidTransitionTo(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
    }

    @Test
    public void aCanceledTaskSendsNoMoreAndIgnoresALateAck() {
        prepareSending();
        runTask();
        listeners.get(0).onStoredInMailbox();
        ManualTimer resend = ManualTimer.latest();

        runner.cancel();
        listeners.get(0).onStoredInMailbox();
        listeners.get(0).onArrived();
        listeners.get(0).onFault("fault");
        paymentStartedMessageState.set(MessageState.ACKNOWLEDGED);

        assertTrue(resend.isStopped());
        assertSame(resend, ManualTimer.latest(), "no send is scheduled after the cancel");
        assertEquals(1, listeners.size());
        assertFalse(completed.get());
        assertNull(errorMessage.get());
        verify(trade, never()).setStateIfValidTransitionTo(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
    }
}
