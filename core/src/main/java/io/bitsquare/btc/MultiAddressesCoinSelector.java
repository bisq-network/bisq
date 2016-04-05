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

package io.bitsquare.btc;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our addressEntry.
 */
class MultiAddressesCoinSelector extends BitsquareCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(MultiAddressesCoinSelector.class);
    private final Set<AddressEntry> addressEntries;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MultiAddressesCoinSelector(NetworkParameters params, @NotNull Set<AddressEntry> addressEntries) {
        super(params);
        this.addressEntries = addressEntries;
    }

    @Override
    protected boolean matchesRequirement(TransactionOutput transactionOutput) {
        if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
            Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
            log.trace("matchesRequiredAddress(es)?");
            log.trace(addressOutput.toString());
            log.trace(addressEntries.toString());
            for (AddressEntry entry : addressEntries) {
                if (addressOutput.equals(entry.getAddress()))
                    return true;
            }

            log.trace("No match found at matchesRequiredAddress addressOutput / addressEntries " + addressOutput.toString
                    () + " / " + addressEntries.toString());
            return false;
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }

    }
}
