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


import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.MailboxMessage;
import io.bitsquare.trade.BuyerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.FinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.messages.PublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.*;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import io.bitsquare.trade.protocol.trade.tasks.taker.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerAsTakerProtocol extends TradeProtocol implements BuyerProtocol, TakerProtocol {
    private static final Logger log = LoggerFactory.getLogger(BuyerAsTakerProtocol.class);

    private final BuyerAsTakerTrade buyerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsTakerProtocol(BuyerAsTakerTrade trade) {
        super(trade);

        this.buyerAsTakerTrade = trade;

        processModel.tradingPeer.setPubKeyRing(trade.getOffer().getPubKeyRing());

        // If we are after the timeLock state we need to setup the listener again
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

        if (message instanceof FinalizePayoutTxRequest)
            handle((FinalizePayoutTxRequest) message, ((MailboxMessage) message).getSenderNodeAddress());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> handleTaskRunnerSuccess("takeAvailableOffer"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                SelectArbitrator.class,
                LoadCreateOfferFeeTx.class,
                CreateTakeOfferFeeTx.class,
                BroadcastTakeOfferFeeTx.class,
                CreateDepositTxInputsAsBuyer.class,
                SendPayDepositRequest.class
        );
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PublishDepositTxRequest tradeMessage, NodeAddress sender) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> handleTaskRunnerSuccess("PublishDepositTxRequest"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                ProcessPublishDepositTxRequest.class,
                VerifyOffererAccount.class,
                VerifyAndSignContract.class,
                SignAndPublishDepositTxAsBuyer.class,
                SendDepositTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    @Override
    public void onFiatPaymentStarted() {
        buyerAsTakerTrade.setState(Trade.State.FIAT_PAYMENT_STARTED);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> handleTaskRunnerSuccess("onFiatPaymentStarted"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                VerifyOfferFeePayment.class,
                SendFiatTransferStartedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FinalizePayoutTxRequest tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> {
                    handleTaskRunnerSuccess("FinalizePayoutTxRequest");
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessFinalizePayoutTxRequest.class,
                SignAndFinalizePayoutTx.class,
                CommitPayoutTx.class,
                SendPayoutTxFinalizedMessage.class,
                SetupPayoutTxLockTimeReachedListener.class
        );
        taskRunner.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof PublishDepositTxRequest) {
            handle((PublishDepositTxRequest) tradeMessage, sender);
        } else if (tradeMessage instanceof FinalizePayoutTxRequest) {
            handle((FinalizePayoutTxRequest) tradeMessage, sender);
        } else {
            log.error("Incoming message not supported. " + tradeMessage);
        }
    }
}
