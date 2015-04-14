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
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.messages.PayDepositRequest;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.CommitDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.tasks.seller.CreateAndSignDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessPayDepositRequest;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessPayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendDepositTxInputsRequest;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendFinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendPublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.seller.SignPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import io.bitsquare.trade.protocol.trade.tasks.taker.BroadcastTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.tasks.taker.CreateTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.tasks.taker.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.tasks.taker.VerifyOffererAccount;
import io.bitsquare.trade.states.SellerTradeState;
import io.bitsquare.trade.states.TradeState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerAsTakerProtocol extends TradeProtocol implements SellerProtocol, TakerProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerProtocol.class);

    private final SellerAsTakerTrade sellerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol(SellerAsTakerTrade trade) {
        super(trade.getProcessModel());

        log.debug("New SellerAsTakerProtocol " + this);
        this.sellerAsTakerTrade = trade;

        processModel.tradingPeer.setPubKeyRing(trade.getOffer().getPubKeyRing());

        // If we are after the timelock state we need to setup the listener again
        if (trade instanceof SellerTrade) {
            TradeState.ProcessState state = trade.processStateProperty().get();
            if (state == SellerTradeState.ProcessState.PAYOUT_TX_RECEIVED ||
                    state == SellerTradeState.ProcessState.PAYOUT_TX_COMMITTED ||
                    state == SellerTradeState.ProcessState.PAYOUT_BROAD_CASTED) {
                TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                        () -> {
                            log.debug("taskRunner completed");
                            // we are done!
                            processModel.onComplete();
                        },
                        this::handleTaskRunnerFault);

                taskRunner.addTasks(SetupPayoutTxLockTimeReachedListener.class);
                taskRunner.run();
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(Message message, Trade trade) {
        this.trade = trade;

        if (message instanceof PayoutTxFinalizedMessage) {
            handle((PayoutTxFinalizedMessage) message);
        }
        else {
            // Find first the actual peer address, as it might have changed in the meantime
            findPeerAddress(trade.getOffer().getPubKeyRing(),
                    () -> {
                        if (message instanceof FiatTransferStartedMessage) {
                            handle((FiatTransferStartedMessage) message);
                        }
                        else if (message instanceof DepositTxPublishedMessage) {
                            handle((DepositTxPublishedMessage) message);
                        }
                    },
                    (errorMessage -> {
                        log.error(errorMessage);
                    }));
        }
    }

    @Override
    public void takeAvailableOffer() {
        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> log.debug("taskRunner at takeAvailableOffer completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                CreateTakeOfferFeeTx.class,
                BroadcastTakeOfferFeeTx.class,
                SendDepositTxInputsRequest.class
        );
        taskRunner.run();
        startTimeout();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PayDepositRequest tradeMessage) {
        log.debug("handle RequestPayDepositMessage");
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> log.debug("taskRunner at RequestPayDepositMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessPayDepositRequest.class,
                VerifyOffererAccount.class,
                CreateAndSignContract.class,
                CreateAndSignDepositTx.class,
                SendPublishDepositTxRequest.class
        );
        taskRunner.run();
        startTimeout();
    }

    private void handle(DepositTxPublishedMessage tradeMessage) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> log.debug("taskRunner at DepositTxPublishedMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessDepositTxPublishedMessage.class,
                CommitDepositTx.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // After peer has started Fiat tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FiatTransferStartedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> log.debug("taskRunner at FiatTransferStartedMessage completed"),
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
        sellerAsTakerTrade.setProcessState(SellerTradeState.ProcessState.FIAT_PAYMENT_RECEIPT);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> {
                    log.debug("taskRunner at onFiatPaymentReceived completed");

                    // we are done!
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                VerifyOfferFeePayment.class,
                SignPayoutTx.class,
                SendFinalizePayoutTxRequest.class
        );
        taskRunner.run();
    }

    private void handle(PayoutTxFinalizedMessage tradeMessage) {
        log.debug("handle  PayoutTxFinalizedMessage");
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> log.debug("taskRunner at PayoutTxFinalizedMessage completed"),
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
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, Peer sender) {
        if (tradeMessage instanceof PayDepositRequest) {
            handle((PayDepositRequest) tradeMessage);
        }
        else if (tradeMessage instanceof DepositTxPublishedMessage) {
            handle((DepositTxPublishedMessage) tradeMessage);
        }
        else if (tradeMessage instanceof FiatTransferStartedMessage) {
            handle((FiatTransferStartedMessage) tradeMessage);
        }
        else if (tradeMessage instanceof PayoutTxFinalizedMessage) {
            handle((PayoutTxFinalizedMessage) tradeMessage);
        }
        else {
            log.error("Incoming message not supported. " + tradeMessage);
        }
    }
}
