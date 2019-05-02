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
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerVerifiesPeersAccountAge extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SellerVerifiesPeersAccountAge(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Offer offer = trade.getOffer();
            if (offer != null && PaymentMethod.hasChargebackRisk(offer.getPaymentMethod())) {
                AccountAgeWitnessService accountAgeWitnessService = processModel.getAccountAgeWitnessService();
                long accountCreationDate = new Date().getTime() - accountAgeWitnessService.getTradingPeersAccountAge(trade);
                if (accountCreationDate <= AccountAgeWitnessService.SAFE_ACCOUNT_AGE_DATE) {
                    complete();
                } else {
                    failed("Trade process failed because the buyer's payment account was created after March 15th 2019 and the payment method is considered " +
                            "risky regarding chargeback risk.");
                }
            } else {
                complete();
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
