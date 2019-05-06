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

import bisq.core.offer.OfferRestrictions;
import bisq.core.payment.AccountAgeRestrictions;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

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

            log.error("SellerVerifiesPeersAccountAge isOfferRisky={} isTradePeersAccountAgeImmature={}", OfferRestrictions.isTradeRisky(trade), AccountAgeRestrictions.isTradePeersAccountAgeImmature(processModel.getAccountAgeWitnessService(), trade));
            if (OfferRestrictions.isTradeRisky(trade) &&
                    AccountAgeRestrictions.isTradePeersAccountAgeImmature(processModel.getAccountAgeWitnessService(), trade)) {
                failed("Violation of security restrictions:\n" +
                        "  - The peer's account was created after March 1st 2019\n" +
                        "  - The trade amount is above 0.01 BTC\n" +
                        "  - The payment method for that offer is considered risky for bank chargebacks\n");
            } else {
                complete();
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
