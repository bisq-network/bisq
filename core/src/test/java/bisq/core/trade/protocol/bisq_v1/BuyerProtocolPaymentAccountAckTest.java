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
import bisq.core.network.MessageState;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferMaker;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSendsShareBuyerPaymentAccountMessage;
import bisq.core.user.User;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.mailbox.MailboxMessage;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.UserThread;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The seller's ACK of the buyer's payment account, received by the buyer's protocol, ends the step that sends the
 * account, and the buyer then acknowledges the seller's deposit message. Real trade, process model, protocol and
 * task; the network is mocked.
 */
class BuyerProtocolPaymentAccountAckTest {
    private static final NodeAddress BUYER = new NodeAddress("buyer.onion", 9999);
    private static final NodeAddress SELLER = new NodeAddress("seller.onion", 9999);

    private final List<Sent> sent = new ArrayList<>();
    private PubKeyRing sellerKeys;
    private ProcessModel processModel;
    private MailboxMessageService mailboxMessageService;
    private BuyerAsTakerTrade trade;
    private TestBuyerProtocol protocol;
    private DepositTxAndDelayedPayoutTxMessage depositMessage;
    private boolean stepEnded;

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);

        Offer offer = make(a(OfferMaker.Offer, with(OfferMaker.direction, OfferDirection.SELL)));
        sellerKeys = keys();
        processModel = new ProcessModel(offer.getId(), "account-id", keys());
        mailboxMessageService = mock(MailboxMessageService.class);
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
        when(p2PService.getAddress()).thenReturn(BUYER);
        PaymentAccount paymentAccount = mock(PaymentAccount.class);
        when(paymentAccount.getPaymentAccountPayload()).thenReturn(mock(PaymentAccountPayload.class));
        User user = mock(User.class);
        when(user.getPaymentAccount(any())).thenReturn(paymentAccount);
        Provider provider = mock(Provider.class);
        when(provider.getP2PService()).thenReturn(p2PService);
        when(provider.getUser()).thenReturn(user);
        processModel.applyTransient(provider, mock(TradeManager.class), offer);
        trade = new BuyerAsTakerTrade(offer,
                Coin.valueOf(100_000) /* amount */,
                Coin.valueOf(1_000) /* txFee */,
                Coin.valueOf(100) /* takerFee */,
                true,
                100_000L /* price */,
                SELLER,
                null /* arbitratorNodeAddress */,
                null /* mediatorNodeAddress */,
                null /* refundAgentNodeAddress */,
                mock(BtcWalletService.class),
                processModel,
                "uid");
        trade.setState(Trade.State.BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);
        protocol = new TestBuyerProtocol(trade);
        processModel.getTradePeer().setPubKeyRing(sellerKeys);
        depositMessage = new DepositTxAndDelayedPayoutTxMessage("deposit-uid", offer.getId(), SELLER,
                new byte[]{1}, new byte[]{2}, null);
        doAnswer(invocation -> {
            sent.add(new Sent(invocation.getArgument(2), invocation.getArgument(3)));
            return null;
        }).when(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    @Test
    void theSellersAckEndsTheStepAndTheBuyerAcknowledgesTheDepositMessage() {
        sendPaymentAccount();
        accountMessages().get(0).listener().onStoredInMailbox();
        ManualTimer resend = ManualTimer.latest();

        protocol.onDirectMessage(signedBySeller(ack(ShareBuyerPaymentAccountMessage.class, true)), SELLER);

        assertTrue(stepEnded);
        assertTrue(resend.isStopped());
        assertEquals(MessageState.UNDEFINED, processModel.getDepositTxMessageStateProperty().get());
        List<AckMessage> acks = acksSent();
        assertEquals(1, acks.size());
        assertEquals(DepositTxAndDelayedPayoutTxMessage.class.getSimpleName(), acks.get(0).getSourceMsgClassName());
        assertTrue(acks.get(0).isSuccess());
    }

    @Test
    void theSellersAckFromTheMailboxEndsTheStep() {
        sendPaymentAccount();
        accountMessages().get(0).listener().onStoredInMailbox();
        AckMessage ack = ack(ShareBuyerPaymentAccountMessage.class, true);

        protocol.onMailboxMessageAdded(signedBySeller(ack), SELLER);

        assertTrue(stepEnded);
        verify(mailboxMessageService).removeMailboxMsg(ack);
    }

    @Test
    void anAckSignedWithAnotherKeyIsIgnored() {
        sendPaymentAccount();
        accountMessages().get(0).listener().onStoredInMailbox();
        AckMessage ack = ack(ShareBuyerPaymentAccountMessage.class, true);

        protocol.onDirectMessage(new DecryptedMessageWithPubKey(ack, keys().getSignaturePubKey()), SELLER);

        assertEquals(MessageState.UNDEFINED, processModel.getShareBuyerPaymentAccountMessageStateProperty().get());
        assertFalse(stepEnded);
        assertFalse(ManualTimer.latest().isStopped());
    }

    @Test
    void anAckOfAnotherTradeIsIgnored() {
        sendPaymentAccount();
        accountMessages().get(0).listener().onStoredInMailbox();
        AckMessage ack = new AckMessage(SELLER, AckMessageSourceType.TRADE_MESSAGE,
                ShareBuyerPaymentAccountMessage.class.getSimpleName(), "uid", "other-trade-id", true, null);

        protocol.onDirectMessage(signedBySeller(ack), SELLER);

        assertEquals(MessageState.UNDEFINED, processModel.getShareBuyerPaymentAccountMessageStateProperty().get());
        assertFalse(stepEnded);
    }

    @Test
    void anAckOfTheDepositMessageDoesNotEndTheStep() {
        sendPaymentAccount();
        accountMessages().get(0).listener().onStoredInMailbox();

        protocol.onDirectMessage(signedBySeller(ack(DepositTxAndDelayedPayoutTxMessage.class, true)), SELLER);

        assertEquals(MessageState.ACKNOWLEDGED, processModel.getDepositTxMessageStateProperty().get());
        assertEquals(MessageState.UNDEFINED, processModel.getShareBuyerPaymentAccountMessageStateProperty().get());
        assertFalse(stepEnded);
        assertFalse(ManualTimer.latest().isStopped());
    }

    @Test
    void aNackDoesNotEndTheStepAndTheBuyerSendsAgain() {
        sendPaymentAccount();
        accountMessages().get(0).listener().onStoredInMailbox();
        ManualTimer resend = ManualTimer.latest();

        protocol.onDirectMessage(signedBySeller(ack(ShareBuyerPaymentAccountMessage.class, false)), SELLER);

        assertEquals(MessageState.FAILED, processModel.getShareBuyerPaymentAccountMessageStateProperty().get());
        assertFalse(stepEnded);
        assertFalse(resend.isStopped());
        resend.fire();
        assertEquals(2, accountMessages().size());
    }

    @Test
    void anAckAfterTheDepositConfirmedEndsTheStep() {
        sendPaymentAccount();
        accountMessages().get(0).listener().onStoredInMailbox();
        trade.setState(Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN);
        ManualTimer.latest().fire();
        accountMessages().get(1).listener().onStoredInMailbox();
        assertEquals(1, ManualTimer.count(), "the buyer does not send again");
        assertFalse(stepEnded);

        protocol.onDirectMessage(signedBySeller(ack(ShareBuyerPaymentAccountMessage.class, true)), SELLER);

        assertTrue(stepEnded);
    }

    @Test
    void withoutDeliveryTheBuyerRefusesTheDepositMessage() {
        sendPaymentAccount();
        for (int attempt = 0; attempt < 7; attempt++) {
            accountMessages().get(attempt).listener().onStoredInMailbox();
            ManualTimer.latest().fire();
        }

        accountMessages().get(7).listener().onStoredInMailbox();

        assertFalse(stepEnded);
        assertTrue(trade.getErrorMessage().endsWith("when sending our payment account to the peer."));
        List<AckMessage> acks = acksSent();
        assertEquals(1, acks.size());
        assertEquals(DepositTxAndDelayedPayoutTxMessage.class.getSimpleName(), acks.get(0).getSourceMsgClassName());
        assertFalse(acks.get(0).isSuccess());
    }

    // The step of the processing of the deposit message that sends the buyer's payment account, with the result
    // handling of that processing
    private void sendPaymentAccount() {
        TradeTaskRunner runner = new TradeTaskRunner(trade,
                () -> {
                    stepEnded = true;
                    protocol.handleTaskRunnerSuccess(depositMessage);
                },
                errorMessage -> protocol.handleTaskRunnerFault(depositMessage, errorMessage));
        runner.addTasks(BuyerSendsShareBuyerPaymentAccountMessage.class);
        runner.run();
    }

    private List<Sent> accountMessages() {
        return sent.stream()
                .filter(s -> s.message() instanceof ShareBuyerPaymentAccountMessage)
                .collect(Collectors.toList());
    }

    private List<AckMessage> acksSent() {
        return sent.stream()
                .filter(s -> s.message() instanceof AckMessage)
                .map(s -> (AckMessage) s.message())
                .collect(Collectors.toList());
    }

    private AckMessage ack(Class<?> sourceMessageClass, boolean success) {
        return new AckMessage(SELLER, AckMessageSourceType.TRADE_MESSAGE, sourceMessageClass.getSimpleName(), "uid",
                trade.getId(), success, success ? null : "error");
    }

    private DecryptedMessageWithPubKey signedBySeller(AckMessage ack) {
        return new DecryptedMessageWithPubKey(ack, sellerKeys.getSignaturePubKey());
    }

    private static PubKeyRing keys() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
    }

    private record Sent(MailboxMessage message, SendMailboxMessageListener listener) {
    }

    // Makes the result handling of the processing of the deposit message callable from the test
    private static final class TestBuyerProtocol extends BuyerAsTakerProtocol {
        private TestBuyerProtocol(BuyerAsTakerTrade trade) {
            super(trade);
        }

        @Override
        protected void handleTaskRunnerSuccess(TradeMessage message) {
            super.handleTaskRunnerSuccess(message);
        }

        @Override
        protected void handleTaskRunnerFault(TradeMessage message, String errorMessage) {
            super.handleTaskRunnerFault(message, errorMessage);
        }
    }
}
