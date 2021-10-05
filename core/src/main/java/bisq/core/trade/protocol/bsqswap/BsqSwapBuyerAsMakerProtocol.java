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


import bisq.core.trade.model.bsqswap.BsqSwapBuyerAsMakerTrade;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsqswap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.BuyerFinalizeTx;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.BuyerPublishesTx;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.ProcessBsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.PublishTradeStatistics;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_maker.BuyerAsMakerCreatesBsqInputsAndChange;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_maker.BuyerAsMakerRemoveOpenOffer;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_maker.ProcessSellersBsqSwapRequest;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_maker.SendBsqSwapTxInputsMessage;
import bisq.core.trade.protocol.messages.TradeMessage;
import bisq.core.trade.protocol.messages.bsqswap.BsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.messages.bsqswap.BsqSwapRequest;
import bisq.core.trade.protocol.messages.bsqswap.SellersBsqSwapRequest;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bsqswap.BsqSwapTrade.State.PREPARATION;

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
                        .withTimeout(60))
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
                        ProcessBsqSwapFinalizeTxRequest.class,
                        BuyerFinalizeTx.class,
                        BuyerPublishesTx.class,
                        BuyerAsMakerRemoveOpenOffer.class,
                        PublishTradeStatistics.class)
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
