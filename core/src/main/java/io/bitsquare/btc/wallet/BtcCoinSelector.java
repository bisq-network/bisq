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

import com.google.common.collect.Sets;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches any of our addresses.
 */
class BtcCoinSelector extends BitsquareCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(BtcCoinSelector.class);

    private final Set<Address> addresses;
    private final boolean allowUnconfirmedSpend;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    BtcCoinSelector(NetworkParameters params, Set<Address> addresses, boolean allowUnconfirmedSpend) {
        super(params);
        this.addresses = addresses;
        this.allowUnconfirmedSpend = allowUnconfirmedSpend;
    }

    BtcCoinSelector(NetworkParameters params, Set<Address> addresses) {
        this(params, addresses, true);
    }

    BtcCoinSelector(NetworkParameters params, Address address, boolean allowUnconfirmedSpend) {
        this(params, Sets.newHashSet(address), allowUnconfirmedSpend);
    }

    BtcCoinSelector(NetworkParameters params, Address address) {
        this(params, Sets.newHashSet(address), true);
    }

    @Override
    protected boolean matchesRequirement(TransactionOutput transactionOutput) {
        if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
            boolean confirmationCheck = allowUnconfirmedSpend;
            if (!allowUnconfirmedSpend && transactionOutput.getParentTransaction() != null &&
                    transactionOutput.getParentTransaction().getConfidence() != null) {
                final TransactionConfidence.ConfidenceType confidenceType = transactionOutput.getParentTransaction().getConfidence().getConfidenceType();
                confirmationCheck = confidenceType == TransactionConfidence.ConfidenceType.BUILDING;
                if (!confirmationCheck)
                    log.debug("Tx is not in blockchain yet. confidenceType=" + confidenceType);
            }

            Address address = transactionOutput.getScriptPubKey().getToAddress(params);
            log.trace("only lookup in savings wallet address entries");
            log.trace(address.toString());

            boolean matches = addresses.contains(address);
            if (!matches)
                log.trace("No match found at matchesRequiredAddress address / addressEntry " +
                        address.toString() + " / " + address.toString());

            return matches && confirmationCheck;
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }
    }
}
