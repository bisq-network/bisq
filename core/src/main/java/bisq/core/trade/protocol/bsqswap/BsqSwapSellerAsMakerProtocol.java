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


import bisq.core.trade.model.bsqswap.BsqSwapSellerAsMakerTrade;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsqswap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsqswap.tasks.maker.RemoveOpenOffer;
import bisq.core.trade.protocol.bsqswap.tasks.seller.SellerSetupTxListener;
import bisq.core.trade.protocol.bsqswap.tasks.seller.SendFinalizeBsqSwapTxRequest;
import bisq.core.trade.protocol.bsqswap.tasks.seller_as_maker.ProcessBsqSwapTakeOfferWithTxInputsRequest;
import bisq.core.trade.protocol.bsqswap.tasks.seller_as_maker.SellerAsMakerCreatesAndSignsTx;
import bisq.core.trade.protocol.messages.TradeMessage;
import bisq.core.trade.protocol.messages.bsqswap.BsqSwapTakeOfferWithTxInputsRequest;
import bisq.core.trade.protocol.messages.bsqswap.TakeOfferRequest;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bsqswap.BsqSwapTrade.State.PREPARATION;

@Slf4j
public class BsqSwapSellerAsMakerProtocol extends BsqSwapSellerProtocol implements BsqSwapMakerProtocol {
    public BsqSwapSellerAsMakerProtocol(BsqSwapSellerAsMakerTrade trade) {
        super(trade);
        log.error("BsqSwapSellerAsMakerProtocol " + trade.getId());
    }

    @Override
    public void handleTakeOfferRequest(TakeOfferRequest takeOfferRequest,
                                       NodeAddress sender,
                                       ErrorMessageHandler errorMessageHandler) {
        BsqSwapTakeOfferWithTxInputsRequest request = (BsqSwapTakeOfferWithTxInputsRequest) takeOfferRequest;
        expect(preCondition(PREPARATION == trade.getTradeState())
                .with(request)
                .from(sender))
                .setup(tasks(
                        ApplyFilter.class,
                        ProcessBsqSwapTakeOfferWithTxInputsRequest.class,
                        SellerAsMakerCreatesAndSignsTx.class,
                        RemoveOpenOffer.class,
                        SellerSetupTxListener.class,
                        SendFinalizeBsqSwapTxRequest.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    stopTimeout();
                                    handleTaskRunnerSuccess(request);
                                },
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(request, errorMessage);
                                }))
                        .withTimeout(60))
                .executeTasks();
    }

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        //todo we could add a msg for the final tx so we can publish it as well for better resiliance
        // We do not expect a trade message beside the initial takeOfferMessage
    }
}
