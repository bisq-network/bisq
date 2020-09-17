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

package bisq.core.trade.protocol;

import bisq.core.trade.Trade;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.messages.cancel.CancelTradeRequestAcceptedMessage;
import bisq.core.trade.messages.cancel.CancelTradeRequestRejectedMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.buyer.cancel.ProcessCancelTradeRequestAcceptedMessage;
import bisq.core.trade.protocol.tasks.buyer.cancel.ProcessCancelTradeRequestRejectedMessage;
import bisq.core.trade.protocol.tasks.buyer.cancel.SendRequestCancelTradeMessage;
import bisq.core.trade.protocol.tasks.buyer.cancel.SetupCanceledTradePayoutTxListener;
import bisq.core.trade.protocol.tasks.cancel.SignCanceledTradePayoutTx;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyersCancelTradeProtocol extends CancelTradeProtocol {

    BuyersCancelTradeProtocol(Trade trade) {
        super(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User intent
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onRequestCancelTrade(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.isDisputed()) {
            errorMessageHandler.handleErrorMessage("We got onRequestCancelTrade called but the trade have been disputed already.");
            return;
        }

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> {
                    resultHandler.handleResult();
                    handleTaskRunnerSuccess("onRequestCancelTrade");
                },
                (errorMessage) -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(errorMessage);
                });
        taskRunner.addTasks(
                ApplyFilter.class,
                //TODO we must not sent the sig at that moment!
                SignCanceledTradePayoutTx.class,
                SendRequestCancelTradeMessage.class,
                SetupCanceledTradePayoutTxListener.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(CancelTradeRequestAcceptedMessage tradeMessage, NodeAddress sender) {
        trade.getProcessModel().setTradeMessage(tradeMessage);
        trade.getProcessModel().setTempTradingPeerNodeAddress(sender);

        if (trade.isDisputed()) {
            log.info("We received a CancelTradeRequestAcceptedMessage but the trade have been disputed already. We ignore the message.");
            trade.getProcessModel().removeMailboxMessageAfterProcessing(trade);
            return;
        }

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(tradeMessage, "handle CancelTradeRequestAcceptedMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                ProcessCancelTradeRequestAcceptedMessage.class
        );
        taskRunner.run();
    }

    protected void handle(CancelTradeRequestRejectedMessage tradeMessage, NodeAddress sender) {
        trade.getProcessModel().setTradeMessage(tradeMessage);
        trade.getProcessModel().setTempTradingPeerNodeAddress(sender);

        if (trade.isDisputed()) {
            log.info("We received a CancelTradeRequestRejectedMessage but the trade have been disputed already. We ignore the request.");
            trade.getProcessModel().removeMailboxMessageAfterProcessing(trade);
            return;
        }

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(tradeMessage, "handle CancelTradeRequestRejectedMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                ProcessCancelTradeRequestRejectedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof CancelTradeRequestAcceptedMessage) {
            handle((CancelTradeRequestAcceptedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof CancelTradeRequestRejectedMessage) {
            handle((CancelTradeRequestRejectedMessage) tradeMessage, sender);
        }
    }

    @Override
    public void doApplyMailboxTradeMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress) {
        if (tradeMessage instanceof CancelTradeRequestAcceptedMessage) {
            handle((CancelTradeRequestAcceptedMessage) tradeMessage, peerNodeAddress);
        } else if (tradeMessage instanceof CancelTradeRequestRejectedMessage) {
            handle((CancelTradeRequestRejectedMessage) tradeMessage, peerNodeAddress);
        }
    }
}
