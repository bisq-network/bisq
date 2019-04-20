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

package bisq.core.trade.protocol.tasks;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Trade;
import bisq.core.trade.statistics.TradeStatistics2;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class PublishTradeStatistics extends TradeTask {
    public PublishTradeStatistics(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (trade.getDepositTx() != null) {
                Map<String, String> extraDataMap = new HashMap<>();
                if (processModel.getReferralIdService().getOptionalReferralId().isPresent()) {
                    extraDataMap.put(OfferPayload.REFERRAL_ID, processModel.getReferralIdService().getOptionalReferralId().get());
                }

                NodeAddress arbitratorNodeAddress = trade.getArbitratorNodeAddress();
                if (arbitratorNodeAddress != null) {
                    // The first 4 chars are sufficient to identify an arbitrator
                    String address = arbitratorNodeAddress.getFullAddress().substring(0, 4);
                    extraDataMap.put(TradeStatistics2.ARBITRATOR_ADDRESS, address);
                }

                Offer offer = trade.getOffer();
                checkNotNull(offer, "offer must not ne null");
                checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not ne null");
                TradeStatistics2 tradeStatistics = new TradeStatistics2(offer.getOfferPayload(),
                        trade.getTradePrice(),
                        trade.getTradeAmount(),
                        trade.getDate(),
                        trade.getDepositTx().getHashAsString(),
                        extraDataMap);
                processModel.getP2PService().addPersistableNetworkPayload(tradeStatistics, true);
            }
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
