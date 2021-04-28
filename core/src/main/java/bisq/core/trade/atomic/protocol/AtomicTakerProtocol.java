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

package bisq.core.trade.atomic.protocol;


import bisq.core.offer.Offer;
import bisq.core.trade.atomic.AtomicTakerTrade;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxResponse;
import bisq.core.trade.atomic.protocol.tasks.AtomicApplyFilter;
import bisq.core.trade.atomic.protocol.tasks.taker.AtomicTakerPreparesData;
import bisq.core.trade.atomic.protocol.tasks.taker.AtomicTakerPublishAtomicTx;
import bisq.core.trade.atomic.protocol.tasks.taker.AtomicTakerSendsAtomicRequest;
import bisq.core.trade.atomic.protocol.tasks.taker.AtomicTakerSetupTxListener;
import bisq.core.trade.atomic.protocol.tasks.taker.AtomicTakerVerifyAtomicTx;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.TakerProtocol;
import bisq.core.trade.protocol.TradeProtocol;

import bisq.network.p2p.NodeAddress;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicTakerProtocol extends TradeProtocol implements TakerProtocol {

    private final AtomicTakerTrade atomicTakerTrade;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AtomicTakerProtocol(AtomicTakerTrade trade) {
        super(trade);
        this.atomicTakerTrade = trade;
        Offer offer = checkNotNull(trade.getOffer());
        processModelI.getTradingPeer().setPubKeyRing(offer.getPubKeyRing());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onTakeOffer() {
        expect(preCondition(AtomicTrade.State.PREPARATION == atomicTakerTrade.getState())
                .with(TakerEvent.TAKE_OFFER))
                .setup(tasks(
                        AtomicApplyFilter.class,
                        AtomicTakerPreparesData.class,
                        AtomicTakerSendsAtomicRequest.class
                ))
                .run(() -> {
                    processModelI.setTempTradingPeerNodeAddress(tradeModel.getTradingPeerNodeAddress());
                    processModelI.getTradeManager().requestPersistence();
                })
                .executeTasks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    void handle(CreateAtomicTxResponse tradeMessage, NodeAddress peerNodeAddress) {
        expect(preCondition(AtomicTrade.State.PREPARATION == atomicTakerTrade.getState())
                .with(tradeMessage))
                .setup(tasks(
                        AtomicTakerVerifyAtomicTx.class,
                        AtomicTakerPublishAtomicTx.class,
                        AtomicTakerSetupTxListener.class
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

        if (message instanceof CreateAtomicTxResponse) {
            handle((CreateAtomicTxResponse) message, peer);
        }
    }
}
