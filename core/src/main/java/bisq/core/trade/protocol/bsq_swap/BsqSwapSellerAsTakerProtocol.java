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
import bisq.core.trade.model.bsq_swap.BsqSwapSellerAsTakerTrade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapTxInputsMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsq_swap.tasks.seller.SellerMaybePublishesTx;
import bisq.core.trade.protocol.bsq_swap.tasks.seller.SendBsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.ProcessBsqSwapTxInputsMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.SellerAsTakerCreatesAndSignsTx;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.SellerAsTakerProcessBsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.SellerAsTakerSetupTxListener;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.SendSellersBsqSwapRequest;

import bisq.network.p2p.NodeAddress;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bsq_swap.BsqSwapTrade.State.COMPLETED;
import static bisq.core.trade.model.bsq_swap.BsqSwapTrade.State.PREPARATION;
import static bisq.core.trade.protocol.bisq_v1.TakerProtocol.TakerEvent.TAKE_OFFER;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BsqSwapSellerAsTakerProtocol extends BsqSwapSellerProtocol implements BsqSwapTakerProtocol {
    public BsqSwapSellerAsTakerProtocol(BsqSwapSellerAsTakerTrade trade) {
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
                        SendSellersBsqSwapRequest.class)
                        .withTimeout(40))
                .executeTasks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(BsqSwapTxInputsMessage message, NodeAddress sender) {
        expect(preCondition(PREPARATION == trade.getTradeState())
                .with(message)
                .from(sender))
                .setup(tasks(
                        ProcessBsqSwapTxInputsMessage.class,
                        SellerAsTakerCreatesAndSignsTx.class,
                        SellerAsTakerSetupTxListener.class,
                        SendBsqSwapFinalizeTxRequest.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    stopTimeout();
                                    handleTaskRunnerSuccess(message);
                                },
                                errorMessage -> handleTaskRunnerFault(message, errorMessage))))
                .executeTasks();
    }

    // We treat BsqSwapFinalizedTxMessage as optional message, so we stop the timeout at handleBsqSwapTxInputsMessage
    private void handle(BsqSwapFinalizedTxMessage message, NodeAddress sender) {
        expect(preCondition(PREPARATION == trade.getTradeState() || COMPLETED == trade.getTradeState())
                .with(message)
                .from(sender))
                .setup(tasks(
                        SellerAsTakerProcessBsqSwapFinalizedTxMessage.class,
                        SellerMaybePublishesTx.class))
                .executeTasks();
    }

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        log.info("Received {} from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peer, message.getTradeId(), message.getUid());

        if (message instanceof BsqSwapTxInputsMessage) {
            handle((BsqSwapTxInputsMessage) message, peer);
        } else if (message instanceof BsqSwapFinalizedTxMessage) {
            handle((BsqSwapFinalizedTxMessage) message, peer);
        }
    }
}
