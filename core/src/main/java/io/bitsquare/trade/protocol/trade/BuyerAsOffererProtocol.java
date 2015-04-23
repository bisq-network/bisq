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
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeState;
import io.bitsquare.trade.protocol.trade.messages.FinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.messages.PublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.CreateDepositTxInputs;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessDepositTxInputsRequest;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessFinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessPublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendPayDepositRequest;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendPayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SignAndFinalizePayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.buyer.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.checkTradeId;

public class BuyerAsOffererProtocol extends TradeProtocol implements BuyerProtocol, OffererProtocol {
    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererProtocol.class);

    private final BuyerAsOffererTrade buyerAsOffererTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsOffererProtocol(BuyerAsOffererTrade trade) {
        super(trade.getProcessModel());

        this.buyerAsOffererTrade = trade;

        // If we are after the timelock state we need to setup the listener again
        TradeState tradeState = trade.tradeStateProperty().get();
        if (tradeState == TradeState.BuyerState.PAYOUT_TX_COMMITTED ||
                tradeState == TradeState.BuyerState.PAYOUT_TX_SENT ||
                tradeState == TradeState.BuyerState.PAYOUT_BROAD_CASTED) {

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

        // Find first the actual peer address, as it might have changed in the meantime
        findPeerAddress(processModel.tradingPeer.getPubKeyRing(),
                () -> {
                    if (message instanceof FinalizePayoutTxRequest) {
                        handle((FinalizePayoutTxRequest) message);
                    }
                },
                (errorMessage -> {
                    log.error(errorMessage);
                }));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, Peer taker) {
        checkTradeId(processModel.getId(), message);
        processModel.setTradeMessage(message);
        buyerAsOffererTrade.setTradingPeer(taker);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> handleTaskRunnerSuccess("handleTakeOfferRequest"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                ProcessDepositTxInputsRequest.class,
                CreateDepositTxInputs.class,
                SendPayDepositRequest.class
        );
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PublishDepositTxRequest tradeMessage) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> handleTaskRunnerSuccess("PublishDepositTxRequest"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                ProcessPublishDepositTxRequest.class,
                VerifyTakerAccount.class,
                VerifyAndSignContract.class,
                SignAndPublishDepositTx.class,
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
        buyerAsOffererTrade.setTradeState(TradeState.BuyerState.FIAT_PAYMENT_STARTED);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> handleTaskRunnerSuccess("onFiatPaymentStarted"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                VerifyTakeOfferFeePayment.class,
                SendFiatTransferStartedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FinalizePayoutTxRequest tradeMessage) {
        log.debug("handle RequestFinalizePayoutTxMessage called");
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
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
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, Peer sender) {
        if (tradeMessage instanceof PublishDepositTxRequest) {
            handle((PublishDepositTxRequest) tradeMessage);
        }
        else if (tradeMessage instanceof FinalizePayoutTxRequest) {
            handle((FinalizePayoutTxRequest) tradeMessage);
        }
        else {
            log.error("Incoming decrypted tradeMessage not supported. " + tradeMessage);
        }
    }
}
