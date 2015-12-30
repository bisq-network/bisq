/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade;


import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.messaging.MailboxMessage;
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.messages.PublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.*;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import io.bitsquare.trade.protocol.trade.tasks.taker.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerAsTakerProtocol extends TradeProtocol implements SellerProtocol, TakerProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerProtocol.class);

    private final SellerAsTakerTrade sellerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol(SellerAsTakerTrade trade) {
        super(trade);

        this.sellerAsTakerTrade = trade;

        processModel.tradingPeer.setPubKeyRing(trade.getOffer().getPubKeyRing());

        // If we are after the timeLock state we need to setup the listener again
        //TODO not sure if that is not called already from the checkPayoutTxTimeLock at tradeProtocol
        Trade.State tradeState = trade.getState();
        if (tradeState.getPhase() == Trade.Phase.PAYOUT_PAID) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        handleTaskRunnerSuccess("SetupPayoutTxLockTimeReachedListener");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(SetupPayoutTxLockTimeReachedListener.class);
            taskRunner.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(Message message, Trade trade) {
        this.trade = trade;

        if (message instanceof MailboxMessage) {
            Address peerAddress = ((MailboxMessage) message).getSenderAddress();
            if (message instanceof PayoutTxFinalizedMessage) {
                handle((PayoutTxFinalizedMessage) message, peerAddress);
            } else if (message instanceof FiatTransferStartedMessage) {
                handle((FiatTransferStartedMessage) message, peerAddress);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> handleTaskRunnerSuccess("takeAvailableOffer"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                SelectArbitrator.class,
                LoadCreateOfferFeeTx.class,
                CreateTakeOfferFeeTx.class,
                BroadcastTakeOfferFeeTx.class,
                CreateDepositTxInputsAsSeller.class,
                SendPayDepositRequest.class
        );
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PublishDepositTxRequest tradeMessage, Address sender) {
        log.debug("handle RequestPayDepositMessage");
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> handleTaskRunnerSuccess("PayDepositRequest"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessPublishDepositTxRequest.class,
                VerifyOffererAccount.class,
                VerifyAndSignContract.class,
                SignAndPublishDepositTxAsSeller.class,
                SendDepositTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // After peer has started Fiat tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FiatTransferStartedMessage tradeMessage, Address sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> handleTaskRunnerSuccess("FiatTransferStartedMessage"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(ProcessFiatTransferStartedMessage.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    @Override
    public void onFiatPaymentReceived() {
        sellerAsTakerTrade.setState(Trade.State.FIAT_PAYMENT_RECEIPT);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> handleTaskRunnerSuccess("onFiatPaymentReceived"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                VerifyOfferFeePayment.class,
                SignPayoutTx.class,
                SendFinalizePayoutTxRequest.class
        );
        taskRunner.run();
    }

    private void handle(PayoutTxFinalizedMessage tradeMessage, Address sender) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> {
                    handleTaskRunnerSuccess("PayoutTxFinalizedMessage");
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessPayoutTxFinalizedMessage.class,
                CommitPayoutTx.class,
                SetupPayoutTxLockTimeReachedListener.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, Address sender) {
        if (tradeMessage instanceof PublishDepositTxRequest) {
            handle((PublishDepositTxRequest) tradeMessage, sender);
        } else if (tradeMessage instanceof FiatTransferStartedMessage) {
            handle((FiatTransferStartedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof PayoutTxFinalizedMessage) {
            handle((PayoutTxFinalizedMessage) tradeMessage, sender);
        } else {
            log.error("Incoming message not supported. " + tradeMessage);
        }
    }
}
