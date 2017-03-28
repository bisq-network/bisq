/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol;


import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.core.trade.SellerAsTakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.FiatTransferStartedMsg;
import io.bisq.core.trade.messages.PublishDepositTxRequest;
import io.bisq.core.trade.messages.TradeMsg;
import io.bisq.core.trade.protocol.tasks.seller.SellerBroadcastPayoutTx;
import io.bisq.core.trade.protocol.tasks.seller.SellerProcessFiatTransferStartedMessage;
import io.bisq.core.trade.protocol.tasks.seller.SellerSendPayoutTxPublishedMessage;
import io.bisq.core.trade.protocol.tasks.seller.SellerSignAndFinalizePayoutTx;
import io.bisq.core.trade.protocol.tasks.seller_as_taker.SellerAsTakerCreatesDepositTxInputs;
import io.bisq.core.trade.protocol.tasks.seller_as_taker.SellerAsTakerSignAndPublishDepositTx;
import io.bisq.core.trade.protocol.tasks.taker.*;
import io.bisq.network.p2p.MailboxMsg;
import io.bisq.network.p2p.Msg;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerAsTakerProtocol extends TradeProtocol implements SellerProtocol, TakerProtocol {
    private final SellerAsTakerTrade sellerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol(SellerAsTakerTrade trade) {
        super(trade);

        this.sellerAsTakerTrade = trade;

        processModel.tradingPeer.setPubKeyRing(trade.getOffer().getPubKeyRing());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(Msg msg, Trade trade) {
        this.trade = trade;

        if (msg instanceof MailboxMsg) {
            NodeAddress peerNodeAddress = ((MailboxMsg) msg).getSenderNodeAddress();
            if (msg instanceof PublishDepositTxRequest)
                handle((PublishDepositTxRequest) msg, peerNodeAddress);
            else if (msg instanceof FiatTransferStartedMsg)
                handle((FiatTransferStartedMsg) msg, peerNodeAddress);
            else
                log.error("We received an unhandled MailboxMessage" + msg.toString());
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
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class,
                TakerSelectArbitrator.class,
                TakerSelectMediator.class,
                TakerCreateTakerFeeTx.class,
                TakerPublishTakerFeeTx.class,
                SellerAsTakerCreatesDepositTxInputs.class,
                TakerSendPayDepositRequest.class
        );
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PublishDepositTxRequest tradeMessage, NodeAddress sender) {
        log.debug("handle RequestPayDepositMessage");
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> handleTaskRunnerSuccess("PayDepositRequest"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                TakerProcessPublishDepositTxRequest.class,
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class,
                TakerVerifyAndSignContract.class,
                SellerAsTakerSignAndPublishDepositTx.class,
                TakerSendDepositTxPublishedMessage.class,
                TakerPublishTradeStatistics.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // After peer has started Fiat tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FiatTransferStartedMsg tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> handleTaskRunnerSuccess("FiatTransferStartedMessage"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(SellerProcessFiatTransferStartedMessage.class,
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    @Override
    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.isFiatSent() && !trade.isFiatReceived()) {
            sellerAsTakerTrade.setState(Trade.State.SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT);
            TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentReceived");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });

            taskRunner.addTasks(
                    TakerVerifyMakerAccount.class,
                    TakerVerifyMakerFeePayment.class,
                    SellerSignAndFinalizePayoutTx.class,
                    SellerBroadcastPayoutTx.class,
                    SellerSendPayoutTxPublishedMessage.class
            );
            taskRunner.run();
        } else {
            log.warn("onFiatPaymentReceived called twice. " +
                    "That should not happen.\n" +
                    "state=" + sellerAsTakerTrade.getState());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMsg tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof PublishDepositTxRequest) {
            handle((PublishDepositTxRequest) tradeMessage, sender);
        } else if (tradeMessage instanceof FiatTransferStartedMsg) {
            handle((FiatTransferStartedMsg) tradeMessage, sender);
        } else {
            log.error("Incoming message not supported. " + tradeMessage);
        }
    }
}
