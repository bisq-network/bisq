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

package io.bisq.core.trade.protocol.tasks;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.filter.PaymentAccountFilter;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class CheckIfPeerIsBanned extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public CheckIfPeerIsBanned(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            final NodeAddress nodeAddress = processModel.getTempTradingPeerNodeAddress();
            PaymentAccountPayload paymentAccountPayload = checkNotNull(processModel.getTradingPeer().getPaymentAccountPayload());
            final PaymentAccountFilter[] appliedPaymentAccountFilter = new PaymentAccountFilter[1];

            if (nodeAddress != null && processModel.getFilterManager().isNodeAddressBanned(nodeAddress)) {
                failed("Other trader is banned by his node address.\n" +
                        "tradingPeerNodeAddress=" + nodeAddress);
            } else if (processModel.getFilterManager().isOfferIdBanned(trade.getId())) {
                failed("Offer ID is banned.\n" +
                        "Offer ID=" + trade.getId());
            } else if (processModel.getFilterManager().isCurrencyBanned(trade.getOffer().getCurrencyCode())) {
                failed("Currency is banned.\n" +
                        "Currency code=" + trade.getOffer().getCurrencyCode());
            } else if (processModel.getFilterManager().isPaymentMethodBanned(trade.getOffer().getPaymentMethod())) {
                failed("Payment method is banned.\n" +
                        "Payment method=" + trade.getOffer().getPaymentMethod().getId());
            } else if (processModel.getFilterManager().isPeersPaymentAccountDataAreBanned(paymentAccountPayload, appliedPaymentAccountFilter)) {
                failed("Other trader is banned by his trading account data.\n" +
                        "paymentAccountPayload=" + paymentAccountPayload.getPaymentDetails() + "\n" +
                        "banFilter=" + appliedPaymentAccountFilter[0].toString());
            } else {
                complete();
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}

