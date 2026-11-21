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

import bisq.core.network.MessageState;
import bisq.core.payment.payload.PaymentAccountPayload;
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
 * Canceling the task runner stops the resend timer and the ACK listener of the task that shares the buyer's payment
 * account, and a send that reports its outcome later does not schedule a new resend.
 */
class BuyerSendsShareBuyerPaymentAccountMessageCancelTest {
    private final ProcessModel processModel = mock(ProcessModel.class);
    private final TradeManager tradeManager = mock(TradeManager.class);
    private final Trade trade = mock(Trade.class);
    private final SimpleObjectProperty<MessageState> messageState = new SimpleObjectProperty<>(MessageState.UNDEFINED);
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private final List<SendMailboxMessageListener> listeners = new ArrayList<>();
    private TaskRunner<TradeModel> runner;

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);

        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getTradingPeerNodeAddress()).thenReturn(new NodeAddress("seller.onion", 9999));
        when(processModel.getOfferId()).thenReturn("trade-id");
        when(processModel.getMyNodeAddress()).thenReturn(new NodeAddress("buyer.onion", 9999));
        when(processModel.getPaymentAccountPayload(trade)).thenReturn(mock(PaymentAccountPayload.class));
        when(processModel.getTradeManager()).thenReturn(tradeManager);
        when(processModel.getShareBuyerPaymentAccountMessageStateProperty()).thenReturn(messageState);
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

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    @SuppressWarnings("unchecked")
    private void runTask() {
        runner = new TaskRunner<>(trade, (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true), errorMessage::set);
        runner.addTasks(BuyerSendsShareBuyerPaymentAccountMessage.class);
        runner.run();
    }

    @Test
    void withoutCancelTheTimerSendsTheMessageAgain() {
        runTask();
        listeners.get(0).onStoredInMailbox();
        ManualTimer resend = ManualTimer.latest();
        assertFalse(resend.isStopped());

        resend.fire();
        assertEquals(2, listeners.size());
        assertNull(errorMessage.get());
    }

    @Test
    void withoutCancelAnAckAfterTheFirstSendCompletesTheTask() {
        runTask();
        listeners.get(0).onStoredInMailbox();

        messageState.set(MessageState.ACKNOWLEDGED);

        assertTrue(completed.get());
        assertTrue(ManualTimer.latest().isStopped());
    }

    @Test
    void aCanceledTaskSendsNoMoreAndIgnoresALateAck() {
        runTask();
        listeners.get(0).onStoredInMailbox();
        ManualTimer resend = ManualTimer.latest();

        runner.cancel();
        assertTrue(resend.isStopped());
        listeners.get(0).onStoredInMailbox();
        listeners.get(0).onFault("fault");
        messageState.set(MessageState.ACKNOWLEDGED);

        assertSame(resend, ManualTimer.latest(), "no send is scheduled after the cancel");
        assertEquals(1, listeners.size());
        assertFalse(completed.get());
        assertNull(errorMessage.get());
        verify(tradeManager, never()).requestPersistence();
    }

    @Test
    void aCancelBeforeAnyOutcomeKeepsALaterOutcomeFromSchedulingAResend() {
        runTask();
        runner.cancel();

        listeners.get(0).onStoredInMailbox();
        listeners.get(0).onFault("fault");
        messageState.set(MessageState.ACKNOWLEDGED);
        verify(tradeManager, never()).requestPersistence();

        assertEquals(0, ManualTimer.count(), "no send is scheduled after the cancel");
        assertFalse(completed.get());
        assertNull(errorMessage.get());
    }
}
