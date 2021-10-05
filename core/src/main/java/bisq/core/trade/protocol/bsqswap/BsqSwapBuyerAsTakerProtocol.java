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


import bisq.core.offer.Offer;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.messages.bsqswap.FinalizeBsqSwapTxRequest;
import bisq.core.trade.model.bsqswap.BsqSwapBuyerAsTakerTrade;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsqswap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.BuyerFinalizeTx;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.BuyerPublishesTx;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.ProcessFinalizeBsqSwapTxRequest;
import bisq.core.trade.protocol.bsqswap.tasks.buyer.PublishTradeStatistics;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_taker.BuyerAsTakerCreatesBsqInputsAndChange;
import bisq.core.trade.protocol.bsqswap.tasks.buyer_as_taker.SendBsqSwapTakeOfferWithTxInputsRequest;

import bisq.network.p2p.NodeAddress;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bsqswap.BsqSwapTrade.State.PREPARATION;
import static bisq.core.trade.protocol.trade.TakerProtocol.TakerEvent.TAKE_OFFER;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BsqSwapBuyerAsTakerProtocol extends BsqSwapBuyerProtocol implements BsqSwapTakerProtocol {

    public BsqSwapBuyerAsTakerProtocol(BsqSwapBuyerAsTakerTrade trade) {
        super(trade);

        Offer offer = checkNotNull(trade.getOffer());
        tradeProtocolModel.getTradePeer().setPubKeyRing(offer.getPubKeyRing());
    }

    @Override
    public void onTakeOffer() {
        expect(preCondition(PREPARATION == bsqSwapTrade.getState())
                .with(TAKE_OFFER)
                .from(bsqSwapTrade.getTradingPeerNodeAddress()))
                .setup(tasks(
                        ApplyFilter.class,
                        BuyerAsTakerCreatesBsqInputsAndChange.class,
                        SendBsqSwapTakeOfferWithTxInputsRequest.class)
                        .withTimeout(60))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    void handle(FinalizeBsqSwapTxRequest message, NodeAddress sender) {
        expect(preCondition(PREPARATION == bsqSwapTrade.getState())
                .with(message)
                .from(sender))
                .setup(tasks(
                        ProcessFinalizeBsqSwapTxRequest.class,
                        BuyerFinalizeTx.class,
                        BuyerPublishesTx.class,
                        PublishTradeStatistics.class)
                        .using(new TradeTaskRunner(bsqSwapTrade,
                                () -> {
                                    stopTimeout();
                                    handleTaskRunnerSuccess(message);
                                },
                                errorMessage -> handleTaskRunnerFault(message, errorMessage))))
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
