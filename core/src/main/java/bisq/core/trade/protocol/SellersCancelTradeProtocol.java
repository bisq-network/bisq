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
import bisq.core.trade.messages.cancel.RequestCancelTradeMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.cancel.SignCanceledTradePayoutTx;
import bisq.core.trade.protocol.tasks.seller.cancel.BroadcastCanceledTradePayoutTx;
import bisq.core.trade.protocol.tasks.seller.cancel.FinalizeCanceledTradePayoutTx;
import bisq.core.trade.protocol.tasks.seller.cancel.ProcessRequestCancelTradeMessage;
import bisq.core.trade.protocol.tasks.seller.cancel.SendCancelTradeRequestAcceptedMessage;
import bisq.core.trade.protocol.tasks.seller.cancel.SendCancelTradeRequestRejectedMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

public class SellersCancelTradeProtocol extends CancelTradeProtocol {

    SellersCancelTradeProtocol(Trade trade) {
        super(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User intent
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAcceptRequest(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.getPayoutTx() != null) {
            errorMessageHandler.handleErrorMessage("Payout tx is already published.");
            return;
        }

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> {
                    resultHandler.handleResult();
                    handleTaskRunnerSuccess("onAcceptCancelTradeRequest");
                },
                (errorMessage) -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(errorMessage);
                });
        taskRunner.addTasks(
                ApplyFilter.class,
                SignCanceledTradePayoutTx.class,
                FinalizeCanceledTradePayoutTx.class,
                BroadcastCanceledTradePayoutTx.class,
                SendCancelTradeRequestAcceptedMessage.class
        );
        taskRunner.run();
    }

    public void onRejectRequest(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.getPayoutTx() != null) {
            errorMessageHandler.handleErrorMessage("Payout tx is already published.");
            return;
        }

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> {
                    resultHandler.handleResult();
                    handleTaskRunnerSuccess("onRejectCancelTradeRequest");
                },
                (errorMessage) -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(errorMessage);
                });
        taskRunner.addTasks(
                ApplyFilter.class,
                SendCancelTradeRequestRejectedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(RequestCancelTradeMessage tradeMessage, NodeAddress sender) {
        trade.getProcessModel().setTradeMessage(tradeMessage);
        trade.getProcessModel().setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(tradeMessage, "handle RequestCancelTradeMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                ProcessRequestCancelTradeMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof RequestCancelTradeMessage) {
            handle((RequestCancelTradeMessage) tradeMessage, sender);
        }
    }

    @Override
    public void doApplyMailboxTradeMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress) {
        if (tradeMessage instanceof RequestCancelTradeMessage) {
            handle((RequestCancelTradeMessage) tradeMessage, peerNodeAddress);
        }
    }
}
