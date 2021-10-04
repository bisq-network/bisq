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

package bisq.core.trade.protocol.bsqswap;


import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.messages.bsqswap.BsqSwapTakeOfferRequest;
import bisq.core.trade.messages.bsqswap.FinalizeBsqSwapTxRequest;
import bisq.core.trade.messages.bsqswap.TakeOfferRequest;
import bisq.core.trade.model.bsqswap.BsqSwapBuyerAsMakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsqswap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.BuyerFinalizeTx;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.BuyerPublishesTx;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.ProcessFinalizeBsqSwapTxRequest;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.PublishTradeStatistics;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_maker.BuyerAsMakerCreatesBsqInputsAndChange;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_maker.ProcessBsqSwapTakeOfferRequest;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_maker.SendBsqSwapTxInputsMessage;
import bisq.core.trade.protocol.bsqswap.tasks.maker.RemoveOpenOffer;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BsqSwapBuyerAsMakerProtocol extends BsqSwapBuyerProtocol implements BsqSwapMakerProtocol {

    public BsqSwapBuyerAsMakerProtocol(BsqSwapBuyerAsMakerTrade trade) {
        super(trade);
    }

    @Override
    public void handleTakeOfferRequest(TakeOfferRequest takeOfferRequest,
                                       NodeAddress sender,
                                       ErrorMessageHandler errorMessageHandler) {
        BsqSwapTakeOfferRequest request = (BsqSwapTakeOfferRequest) takeOfferRequest;
        expect(preCondition(BsqSwapTrade.State.PREPARATION == bsqSwapTrade.getState())
                .with(request)
                .from(sender))
                .setup(tasks(
                        ApplyFilter.class,
                        ProcessBsqSwapTakeOfferRequest.class,
                        BuyerAsMakerCreatesBsqInputsAndChange.class,
                        SendBsqSwapTxInputsMessage.class)
                        .using(new TradeTaskRunner(bsqSwapTrade,
                                () -> handleTaskRunnerSuccess(request),
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(request, errorMessage);
                                }))
                        .withTimeout(60))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    void handle(FinalizeBsqSwapTxRequest message, NodeAddress sender) {
        expect(preCondition(BsqSwapTrade.State.PREPARATION == bsqSwapTrade.getState())
                .with(message)
                .from(sender))
                .setup(tasks(
                        ProcessFinalizeBsqSwapTxRequest.class,
                        BuyerFinalizeTx.class,
                        BuyerPublishesTx.class,
                        RemoveOpenOffer.class,
                        PublishTradeStatistics.class)
                        .using(new TradeTaskRunner(bsqSwapTrade,
                                () -> {
                                    stopTimeout();
                                    handleTaskRunnerSuccess(message);
                                },
                                errorMessage -> handleTaskRunnerFault(message, errorMessage)))
                )
                .executeTasks();
    }

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        log.info("Received {} from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peer, message.getTradeId(), message.getUid());

        if (message instanceof FinalizeBsqSwapTxRequest) {
            handle((FinalizeBsqSwapTxRequest) message, peer);
        }
    }
}
