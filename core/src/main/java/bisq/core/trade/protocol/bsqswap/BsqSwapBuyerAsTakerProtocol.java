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


import bisq.core.trade.model.bsqswap.BsqSwapBuyerAsTakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.ApplyFilter;
import bisq.core.trade.protocol.bsqswap.tasks.taker.TakerPreparesData;
import bisq.core.trade.protocol.bsqswap.tasks.taker.TakerSendsBsqSwapRequest;

public class BsqSwapBuyerAsTakerProtocol extends BsqSwapBuyerProtocol implements BsqSwapTakerProtocol {

    public BsqSwapBuyerAsTakerProtocol(BsqSwapBuyerAsTakerTrade trade) {
        super(trade);
    }

    @Override
    public void onTakeOffer() {
        expect(preCondition(BsqSwapTrade.State.PREPARATION == buyerTrade.getState())
                .with(BsqSwapTakerEvent.TAKE_OFFER)
                .from(buyerTrade.getTradingPeerNodeAddress()))
                .setup(tasks(
                        ApplyFilter.class,
                        TakerPreparesData.class,
                        TakerSendsBsqSwapRequest.class
                ))
                .executeTasks();
    }
}
