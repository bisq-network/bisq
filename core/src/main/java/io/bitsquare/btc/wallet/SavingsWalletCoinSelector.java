/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc.wallet;

import io.bitsquare.btc.AddressEntry;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our addressEntry.
 */
class SavingsWalletCoinSelector extends BitsquareCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(SavingsWalletCoinSelector.class);

    private final Set<Address> savingsWalletAddressSet;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SavingsWalletCoinSelector(NetworkParameters params, @NotNull List<AddressEntry> addressEntryList) {
        super(params);
        savingsWalletAddressSet = addressEntryList.stream()
                .filter(addressEntry -> addressEntry.getContext() == AddressEntry.Context.AVAILABLE)
                .map(AddressEntry::getAddress)
                .collect(Collectors.toSet());
    }

    protected boolean matchesRequirement(TransactionOutput transactionOutput) {
        if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
            Address address = transactionOutput.getScriptPubKey().getToAddress(params);
            log.trace("only lookup in savings wallet address entries");
            log.trace(address.toString());

            boolean matches = savingsWalletAddressSet.contains(address);
            if (!matches)
                log.trace("No match found at matchesRequiredAddress address / addressEntry " +
                        address.toString() + " / " + address.toString());

            return matches;
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }
    }
}
