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

import bisq.core.dao.governance.param.Param;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

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

            Transaction preparedDelayedPayoutTx = checkNotNull(processModel.getPreparedDelayedPayoutTx(), "preparedDelayedPayoutTx must not be null");

            // Get most recent donation address.
            // We do not support past DAO param addresses to avoid that those receive funds (no bond set up anymore).
            // Users who have not synced the DAO cannot trade.
            String recentDonationAddressString = processModel.getDaoFacade().getParamValue(Param.RECIPIENT_BTC_ADDRESS);

            // In case the seller has deactivated the DAO the default address will be used.
            String defaultDonationAddressString = Param.RECIPIENT_BTC_ADDRESS.getDefaultValue();

            TransactionOutput output = preparedDelayedPayoutTx.getOutput(0);
            NetworkParameters params = processModel.getBtcWalletService().getParams();
            Address address = output.getAddressFromP2PKHScript(params);
            if (address == null) {
                // The donation address can be as well be a multisig address.
                address = output.getAddressFromP2SH(params);
                checkNotNull(address, "address must not be null");
            }

            String addressAsString = address.toString();
            if (recentDonationAddressString.equals(addressAsString) ||
                    defaultDonationAddressString.equals(addressAsString)) {
                complete();
            } else {
                failed("Sellers donation address not recognized." +
                        "\nAddress used by BTC seller: " + addressAsString +
                        "\nRecent donation address:" + recentDonationAddressString +
                        "\nDefault donation address: " + defaultDonationAddressString);
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
