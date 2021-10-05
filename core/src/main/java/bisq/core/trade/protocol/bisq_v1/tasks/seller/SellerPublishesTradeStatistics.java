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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.statistics.TradeStatistics3;

import bisq.network.p2p.network.TorNetworkNode;

import bisq.common.app.Capability;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerPublishesTradeStatistics extends TradeTask {
    public SellerPublishesTradeStatistics(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkNotNull(trade.getDepositTx());

            processModel.getP2PService().findPeersCapabilities(trade.getTradingPeerNodeAddress())
                    .filter(capabilities -> capabilities.containsAll(Capability.TRADE_STATISTICS_3))
                    .ifPresentOrElse(capabilities -> {
                                // Our peer has updated, so as we are the seller we will publish the trade statistics.
                                // The peer as buyer does not publish anymore with v.1.4.0 (where Capability.TRADE_STATISTICS_3 was added)

                                String referralId = processModel.getReferralIdService().getOptionalReferralId().orElse(null);
                                boolean isTorNetworkNode = processModel.getP2PService().getNetworkNode() instanceof TorNetworkNode;
                                TradeStatistics3 tradeStatistics = TradeStatistics3.from(trade, referralId, isTorNetworkNode);
                                if (tradeStatistics.isValid()) {
                                    log.info("Publishing trade statistics");
                                    processModel.getP2PService().addPersistableNetworkPayload(tradeStatistics, true);
                                } else {
                                    log.warn("Trade statistics are invalid. We do not publish. {}", tradeStatistics);
                                }

                                complete();
                            },
                            () -> {
                                log.info("Our peer does not has updated yet, so they will publish the trade statistics. " +
                                        "To avoid duplicates we do not publish from our side.");
                                complete();
                            });
        } catch (Throwable t) {
            failed(t);
        }
    }
}
