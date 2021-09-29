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
import bisq.core.trade.messages.bsqswap.CreateBsqSwapTxResponse;
import bisq.core.trade.model.bsqswap.BsqSwapTakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.TakerProtocol;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.protocol.bsqswap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsqswap.tasks.taker.TakerPreparesData;
import bisq.core.trade.protocol.bsqswap.tasks.taker.TakerPublishBsqSwapTx;
import bisq.core.trade.protocol.bsqswap.tasks.taker.TakerSendsBsqSwapRequest;
import bisq.core.trade.protocol.bsqswap.tasks.taker.TakerSetupTxListener;
import bisq.core.trade.protocol.bsqswap.tasks.taker.TakerVerifyBsqSwapTx;

import bisq.network.p2p.NodeAddress;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BsqSwapTakerProtocol extends TradeProtocol implements TakerProtocol {

    private final BsqSwapTakerTrade bsqSwapTakerTrade;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqSwapTakerProtocol(BsqSwapTakerTrade trade) {
        super(trade);
        this.bsqSwapTakerTrade = trade;
        Offer offer = checkNotNull(trade.getOffer());
        tradeProtocolModel.getTradingPeer().setPubKeyRing(offer.getPubKeyRing());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onTakeOffer() {
        expect(preCondition(BsqSwapTrade.State.PREPARATION == bsqSwapTakerTrade.getState())
                .with(TakerEvent.TAKE_OFFER)
                .from(bsqSwapTakerTrade.getTradingPeerNodeAddress()))
                .setup(tasks(
                        ApplyFilter.class,
                        TakerPreparesData.class,
                        TakerSendsBsqSwapRequest.class
                ))
                .executeTasks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    void handle(CreateBsqSwapTxResponse message, NodeAddress sender) {
        expect(preCondition(BsqSwapTrade.State.PREPARATION == bsqSwapTakerTrade.getState())
                .with(message)
                .from(sender))
                .setup(tasks(
                        TakerVerifyBsqSwapTx.class,
                        TakerPublishBsqSwapTx.class,
                        TakerSetupTxListener.class
                        // TODO(sq)
                        // PublishTradeStatistics.class
                ).withTimeout(60))
                .executeTasks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        log.info("Received {} from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peer, message.getTradeId(), message.getUid());

        if (message instanceof CreateBsqSwapTxResponse) {
            handle((CreateBsqSwapTxResponse) message, peer);
        }
    }
}
