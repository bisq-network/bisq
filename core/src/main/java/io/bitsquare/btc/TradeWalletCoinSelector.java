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
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our address.
 */
class TradeWalletCoinSelector extends BitsquareCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(TradeWalletCoinSelector.class);
    private final Address address;
    private boolean allowUnconfirmedSpend;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeWalletCoinSelector(NetworkParameters params, @NotNull Address address) {
        this(params, address, true);
    }

    public TradeWalletCoinSelector(NetworkParameters params, @NotNull Address address, boolean allowUnconfirmedSpend) {
        super(params);
        this.address = address;
        this.allowUnconfirmedSpend = allowUnconfirmedSpend;
    }

    @Override
    protected boolean matchesRequirement(TransactionOutput transactionOutput) {
        if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
            boolean confirmationCheck = allowUnconfirmedSpend || false;
            if (!allowUnconfirmedSpend && transactionOutput.getParentTransaction() != null &&
                    transactionOutput.getParentTransaction().getConfidence() != null) {
                final TransactionConfidence.ConfidenceType confidenceType = transactionOutput.getParentTransaction().getConfidence().getConfidenceType();
                confirmationCheck = confidenceType == TransactionConfidence.ConfidenceType.BUILDING;
                if (!confirmationCheck)
                    log.error("Tx is not in blockchain yet. confidenceType=" + confidenceType);
            }

            Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
            log.trace("matchesRequiredAddress?");
            log.trace("addressOutput " + addressOutput.toString());
            log.trace("address " + address.toString());
            boolean matches = addressOutput.equals(address);
            if (!matches)
                log.trace("No match found at matchesRequiredAddress addressOutput / address " + addressOutput.toString
                        () + " / " + address.toString());

            return matches && confirmationCheck;
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }

    }
}
