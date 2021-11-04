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

package bisq.core.trade.protocol.bsq_swap;


import bisq.core.trade.model.bsq_swap.BsqSwapBuyerAsMakerTrade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.messages.SellersBsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.BuyerPublishesTx;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.PublishTradeStatistics;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.SendFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsFinalizedTx;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_maker.BuyerAsMakerCreatesBsqInputsAndChange;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_maker.BuyerAsMakerProcessBsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_maker.BuyerAsMakerRemoveOpenOffer;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_maker.ProcessSellersBsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_maker.SendBsqSwapTxInputsMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bsq_swap.BsqSwapTrade.State.PREPARATION;

@Slf4j
public class BsqSwapBuyerAsMakerProtocol extends BsqSwapBuyerProtocol implements BsqSwapMakerProtocol {

    public BsqSwapBuyerAsMakerProtocol(BsqSwapBuyerAsMakerTrade trade) {
        super(trade);
    }

    @Override
    public void handleTakeOfferRequest(BsqSwapRequest bsqSwapRequest,
                                       NodeAddress sender,
                                       ErrorMessageHandler errorMessageHandler) {
        SellersBsqSwapRequest request = (SellersBsqSwapRequest) bsqSwapRequest;
        expect(preCondition(PREPARATION == trade.getTradeState())
                .with(request)
                .from(sender))
                .setup(tasks(
                        ApplyFilter.class,
                        ProcessSellersBsqSwapRequest.class,
                        BuyerAsMakerCreatesBsqInputsAndChange.class,
                        SendBsqSwapTxInputsMessage.class)
                        .using(new TradeTaskRunner(trade,
                                () -> handleTaskRunnerSuccess(request),
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(request, errorMessage);
                                }))
                        .withTimeout(40))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    void handle(BsqSwapFinalizeTxRequest message, NodeAddress sender) {
        expect(preCondition(PREPARATION == trade.getTradeState())
                .with(message)
                .from(sender))
                .setup(tasks(
                        BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
                        BuyerAsMakerCreatesAndSignsFinalizedTx.class,
                        BuyerPublishesTx.class,
                        BuyerAsMakerRemoveOpenOffer.class,
                        PublishTradeStatistics.class,
                        SendFinalizedTxMessage.class)
                        .using(new TradeTaskRunner(trade,
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

        if (message instanceof BsqSwapFinalizeTxRequest) {
            handle((BsqSwapFinalizeTxRequest) message, peer);
        }
    }
}
