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

package bisq.core.trade.protocol.tasks.seller;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.trade.statistics.TradeStatistics3;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.TorNetworkNode;

import bisq.common.app.Capability;
import bisq.common.taskrunner.TaskRunner;

import java.util.HashMap;
import java.util.Map;

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

                                Map<String, String> extraDataMap = new HashMap<>();
                                if (processModel.getReferralIdService().getOptionalReferralId().isPresent()) {
                                    extraDataMap.put(OfferPayload.REFERRAL_ID, processModel.getReferralIdService().getOptionalReferralId().get());
                                }

                                NodeAddress mediatorNodeAddress = checkNotNull(trade.getMediatorNodeAddress());
                                // The first 4 chars are sufficient to identify a mediator.
                                // For testing with regtest/localhost we use the full address as its localhost and would result in
                                // same values for multiple mediators.
                                NetworkNode networkNode = model.getProcessModel().getP2PService().getNetworkNode();
                                String truncatedMediatorNodeAddress = networkNode instanceof TorNetworkNode ?
                                        mediatorNodeAddress.getFullAddress().substring(0, 4) :
                                        mediatorNodeAddress.getFullAddress();

                                NodeAddress refundAgentNodeAddress = checkNotNull(trade.getRefundAgentNodeAddress());
                                String truncatedRefundAgentNodeAddress = networkNode instanceof TorNetworkNode ?
                                        refundAgentNodeAddress.getFullAddress().substring(0, 4) :
                                        refundAgentNodeAddress.getFullAddress();

                                Offer offer = checkNotNull(trade.getOffer());
                                TradeStatistics3 tradeStatistics = new TradeStatistics3(offer.getCurrencyCode(),
                                        trade.getTradePrice().getValue(),
                                        trade.getTradeAmountAsLong(),
                                        offer.getPaymentMethod().getId(),
                                        trade.getTakeOfferDate().getTime(),
                                        truncatedMediatorNodeAddress,
                                        truncatedRefundAgentNodeAddress,
                                        extraDataMap);
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
