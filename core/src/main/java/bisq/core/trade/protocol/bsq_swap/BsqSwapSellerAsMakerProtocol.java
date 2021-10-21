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


import bisq.core.trade.model.bsq_swap.BsqSwapSellerAsMakerTrade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.messages.BuyersBsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsq_swap.tasks.seller.SellerMaybePublishesTx;
import bisq.core.trade.protocol.bsq_swap.tasks.seller.SendBsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.ProcessBuyersBsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.SellerAsMakerCreatesAndSignsTx;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.SellerAsMakerProcessBsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.SellerAsMakerSetupTxListener;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bsq_swap.BsqSwapTrade.State.COMPLETED;
import static bisq.core.trade.model.bsq_swap.BsqSwapTrade.State.PREPARATION;

@Slf4j
public class BsqSwapSellerAsMakerProtocol extends BsqSwapSellerProtocol implements BsqSwapMakerProtocol {
    public BsqSwapSellerAsMakerProtocol(BsqSwapSellerAsMakerTrade trade) {
        super(trade);
        log.error("BsqSwapSellerAsMakerProtocol " + trade.getId());
    }

    @Override
    public void handleTakeOfferRequest(BsqSwapRequest bsqSwapRequest,
                                       NodeAddress sender,
                                       ErrorMessageHandler errorMessageHandler) {
        BuyersBsqSwapRequest request = (BuyersBsqSwapRequest) bsqSwapRequest;
        expect(preCondition(PREPARATION == trade.getTradeState())
                .with(request)
                .from(sender))
                .setup(tasks(
                        ApplyFilter.class,
                        ProcessBuyersBsqSwapRequest.class,
                        SellerAsMakerCreatesAndSignsTx.class,
                        SellerAsMakerSetupTxListener.class,
                        SendBsqSwapFinalizeTxRequest.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    stopTimeout();
                                    handleTaskRunnerSuccess(request);
                                },
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(request, errorMessage);
                                }))
                        .withTimeout(40))
                .executeTasks();
    }

    // We treat BsqSwapFinalizedTxMessage as optional message, so we stop the timeout at handleTakeOfferRequest
    private void handle(BsqSwapFinalizedTxMessage message, NodeAddress sender) {
        expect(preCondition(PREPARATION == trade.getTradeState() || COMPLETED == trade.getTradeState())
                .with(message)
                .from(sender))
                .setup(tasks(
                        SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
                        SellerMaybePublishesTx.class))
                .executeTasks();
    }

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        log.info("Received {} from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peer, message.getTradeId(), message.getUid());

        if (message instanceof BsqSwapFinalizedTxMessage) {
            handle((BsqSwapFinalizedTxMessage) message, peer);
        }
    }
}
