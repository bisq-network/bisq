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

package bisq.core.trade;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.param.Param;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class DonationAddressValidation {

    @Value
    public static class DonationAddressException extends Exception {
        private final String addressAsString;
        private final String recentDonationAddressString;
        private final String defaultDonationAddressString;

        public DonationAddressException(String addressAsString,
                                        String recentDonationAddressString,
                                        String defaultDonationAddressString) {

            this.addressAsString = addressAsString;
            this.recentDonationAddressString = recentDonationAddressString;
            this.defaultDonationAddressString = defaultDonationAddressString;
        }
    }

    public static void validate(Transaction delayedPayoutTx,
                                DaoFacade daoFacade,
                                BtcWalletService btcWalletService) throws DonationAddressException {
        checkNotNull(delayedPayoutTx, "delayedPayoutTx must not be null");

        // Get most recent donation address.
        // We do not support past DAO param addresses to avoid that those receive funds (no bond set up anymore).
        // Users who have not synced the DAO cannot trade.
        String recentDonationAddressString = daoFacade.getParamValue(Param.RECIPIENT_BTC_ADDRESS);

        // In case the seller has deactivated the DAO the default address will be used.
        String defaultDonationAddressString = Param.RECIPIENT_BTC_ADDRESS.getDefaultValue();

        TransactionOutput output = delayedPayoutTx.getOutput(0);
        NetworkParameters params = btcWalletService.getParams();
        Address address = output.getAddressFromP2PKHScript(params);
        if (address == null) {
            // The donation address can be as well be a multisig address.
            address = output.getAddressFromP2SH(params);
            checkNotNull(address, "address must not be null");
        }

        String addressAsString = address.toString();
        boolean isValid = recentDonationAddressString.equals(addressAsString) ||
                defaultDonationAddressString.equals(addressAsString);
        if (!isValid) {
            log.warn("Donation address is invalid." +
                    "\nAddress used by BTC seller: " + addressAsString +
                    "\nRecent donation address:" + recentDonationAddressString +
                    "\nDefault donation address: " + defaultDonationAddressString);
            throw new DonationAddressException(addressAsString, recentDonationAddressString, defaultDonationAddressString);
        }
    }
}
