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

import bisq.common.config.Config;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DonationAddressValidation {

    public static class DonationAddressException extends Exception {
        DonationAddressException(String msg) {
            super(msg);
        }
    }

    public static void validateDonationAddress(TransactionOutput output,
                                               Transaction transaction,
                                               DaoFacade daoFacade,
                                               BtcWalletService btcWalletService)
            throws DonationAddressException {
        String errorMsg;
        // Validate donation address
        // Get most recent donation address.
        // We do not support past DAO param addresses to avoid that those receive funds (no bond set up anymore).
        // Users who have not synced the DAO cannot trade.

        NetworkParameters params = btcWalletService.getParams();
        Address address = output.getAddressFromP2PKHScript(params);
        if (address == null) {
            // The donation address can be as well be a multisig address.
            address = output.getAddressFromP2SH(params);
            if (address == null) {
                errorMsg = "Donation address cannot be resolved (not of type P2PKHScript or P2SH). Output: " + output;
                log.error(errorMsg);
                log.error(transaction.toString());
                throw new DonationAddressException(errorMsg);
            }
        }

        String addressAsString = address.toString();

        // In case the seller has deactivated the DAO the default address will be used.
        String defaultDonationAddressString = Param.RECIPIENT_BTC_ADDRESS.getDefaultValue();
        boolean defaultNotMatching = !defaultDonationAddressString.equals(addressAsString);
        String recentDonationAddressString = daoFacade.getParamValue(Param.RECIPIENT_BTC_ADDRESS);
        boolean recentFromDaoNotMatching = !recentDonationAddressString.equals(addressAsString);

        // If verifier has DAO deactivated or not synced he will not be able to see recent address used by counterparty,
        // so we add it hard coded here. We need to support also the default one as
        // FIXME This is a quick fix and should be improved in future.
        // We use the default addresses for non mainnet networks. For dev testing it need to be changed here.
        // We use a list to gain more flexibility at updates of DAO param, but still might fail if buyer has not updated
        // software. Needs a better solution....
        List<String> hardCodedAddresses = Config.baseCurrencyNetwork().isMainnet() ?
                List.of("3EtUWqsGThPtjwUczw27YCo6EWvQdaPUyp", "3A8Zc1XioE2HRzYfbb5P8iemCS72M6vRJV") :  // mainnet
                Config.baseCurrencyNetwork().isDaoBetaNet() ? List.of("1BVxNn3T12veSK6DgqwU4Hdn7QHcDDRag7") :  // daoBetaNet
                        Config.baseCurrencyNetwork().isTestnet() ? List.of("2N4mVTpUZAnhm9phnxB7VrHB4aBhnWrcUrV") : // testnet
                                List.of("2MzBNTJDjjXgViKBGnatDU3yWkJ8pJkEg9w"); // regtest or DAO testnet (regtest)

        boolean noneOfHardCodedMatching = hardCodedAddresses.stream().noneMatch(e -> e.equals(addressAsString));

        // If counterparty DAO is deactivated as well we get default address
        if (recentFromDaoNotMatching && defaultNotMatching && noneOfHardCodedMatching) {
            errorMsg = "Donation address is invalid." +
                    "\nAddress used by BTC seller: " + addressAsString +
                    "\nRecent donation address:" + recentDonationAddressString +
                    "\nDefault donation address: " + defaultDonationAddressString;
            log.error(errorMsg);
            log.error(transaction.toString());
            throw new DonationAddressException(errorMsg);
        }
    }
}
