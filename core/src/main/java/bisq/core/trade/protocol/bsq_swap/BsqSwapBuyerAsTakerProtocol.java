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


import bisq.core.offer.Offer;
import bisq.core.trade.model.bsq_swap.BsqSwapBuyerAsTakerTrade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.BuyerPublishesTx;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.PublishTradeStatistics;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.SendFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_taker.BuyerAsTakerCreatesAndSignsFinalizedTx;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_taker.BuyerAsTakerCreatesBsqInputsAndChange;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_taker.BuyerAsTakerProcessBsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_taker.SendBuyersBsqSwapRequest;

import bisq.network.p2p.NodeAddress;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bsq_swap.BsqSwapTrade.State.PREPARATION;
import static bisq.core.trade.protocol.bisq_v1.TakerProtocol.TakerEvent.TAKE_OFFER;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BsqSwapBuyerAsTakerProtocol extends BsqSwapBuyerProtocol implements BsqSwapTakerProtocol {

    public BsqSwapBuyerAsTakerProtocol(BsqSwapBuyerAsTakerTrade trade) {
        super(trade);

        Offer offer = checkNotNull(trade.getOffer());
        protocolModel.getTradePeer().setPubKeyRing(offer.getPubKeyRing());
    }

    @Override
    public void onTakeOffer() {
        expect(preCondition(PREPARATION == trade.getTradeState())
                .with(TAKE_OFFER)
                .from(trade.getTradingPeerNodeAddress()))
                .setup(tasks(
                        ApplyFilter.class,
                        BuyerAsTakerCreatesBsqInputsAndChange.class,
                        SendBuyersBsqSwapRequest.class)
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
                        BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class,
                        BuyerAsTakerCreatesAndSignsFinalizedTx.class,
                        BuyerPublishesTx.class,
                        PublishTradeStatistics.class,
                        SendFinalizedTxMessage.class)
                        .using(new TradeTaskRunner(trade,
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

        if (message instanceof BsqSwapFinalizeTxRequest) {
            handle((BsqSwapFinalizeTxRequest) message, peer);
        }
    }
}
