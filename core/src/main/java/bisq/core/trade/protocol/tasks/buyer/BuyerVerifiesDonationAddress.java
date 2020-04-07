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

package bisq.core.trade.protocol.tasks.buyer;

import bisq.core.trade.DonationAddressValidation;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerVerifiesDonationAddress extends TradeTask {
    @SuppressWarnings({"unused"})
    public BuyerVerifiesDonationAddress(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            DonationAddressValidation.validate(processModel.getPreparedDelayedPayoutTx(),
                    processModel.getDaoFacade(),
                    processModel.getBtcWalletService());
            complete();
        } catch (DonationAddressValidation.DonationAddressException e) {
            failed("Sellers donation address is invalid." +
                    "\nAddress used by BTC seller: " + e.getAddressAsString() +
                    "\nRecent donation address:" + e.getRecentDonationAddressString() +
                    "\nDefault donation address: " + e.getDefaultDonationAddressString());
        } catch (Throwable t) {
            failed(t);
        }
    }
}
