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

import bisq.core.filter.FilterManager;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.Trade;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class ApplyFilter extends TradeTask {
    public ApplyFilter(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            NodeAddress nodeAddress = checkNotNull(processModel.getTempTradingPeerNodeAddress());
            @Nullable
            PaymentAccountPayload paymentAccountPayload = processModel.getTradingPeer().getPaymentAccountPayload();

            FilterManager filterManager = processModel.getFilterManager();
            if (filterManager.isNodeAddressBanned(nodeAddress)) {
                failed("Other trader is banned by their node address.\n" +
                        "tradingPeerNodeAddress=" + nodeAddress);
            } else if (filterManager.isOfferIdBanned(trade.getId())) {
                failed("Offer ID is banned.\n" +
                        "Offer ID=" + trade.getId());
            } else if (trade.getOffer() != null && filterManager.isCurrencyBanned(trade.getOffer().getCurrencyCode())) {
                failed("Currency is banned.\n" +
                        "Currency code=" + trade.getOffer().getCurrencyCode());
            } else if (filterManager.isPaymentMethodBanned(checkNotNull(trade.getOffer()).getPaymentMethod())) {
                failed("Payment method is banned.\n" +
                        "Payment method=" + trade.getOffer().getPaymentMethod().getId());
            } else if (paymentAccountPayload != null && filterManager.arePeersPaymentAccountDataBanned(paymentAccountPayload)) {
                failed("Other trader is banned by their trading account data.\n" +
                        "paymentAccountPayload=" + paymentAccountPayload.getPaymentDetails());
            } else if (filterManager.requireUpdateToNewVersionForTrading()) {
                failed("Your version of Bisq is not compatible for trading anymore. " +
                        "Please update to the latest Bisq version at https://bisq.network/downloads.");
            } else {
                complete();
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}

