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

import com.google.common.annotations.VisibleForTesting;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our address.
 */
class SquCoinSelector implements CoinSelector {
    private static final Logger log = LoggerFactory.getLogger(SquCoinSelector.class);
    
    private final boolean allowUnconfirmedSpend;
    private final NetworkParameters params;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SquCoinSelector(NetworkParameters params, boolean allowUnconfirmedSpend) {
        this.params = params;
        this.allowUnconfirmedSpend = allowUnconfirmedSpend;
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        log.trace("candidates.size: " + candidates.size());
        long targetAsLong = target.longValue();
        log.trace("value needed: " + targetAsLong);
        HashSet<TransactionOutput> selected = new HashSet<>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        if (!target.equals(NetworkParameters.MAX_MONEY)) {
            sortOutputs(sortedOutputs);
        }
        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        for (TransactionOutput output : sortedOutputs) {
            if (total >= targetAsLong) {
                break;
            }
            // Only pick chain-included transactions, or transactions that are ours and pending.
            // Only select outputs from our defined address(es)
            if (!shouldSelect(output.getParentTransaction()) || !matchesRequirement(output)) {
                continue;
            }

            selected.add(output);
            total += output.getValue().longValue();

            log.debug("adding up outputs: output/total: " + output.getValue().longValue() + "/" + total);
        }
        // Total may be lower than target here, if the given candidates were insufficient to create to requested
        // transaction.
        return new CoinSelection(Coin.valueOf(total), selected);
    }

    @VisibleForTesting
    private static void sortOutputs(ArrayList<TransactionOutput> outputs) {
        Collections.sort(outputs, (a, b) -> {
            int c2 = a.getValue().compareTo(b.getValue());
            if (c2 != 0) return c2;
            // They are entirely equivalent (possibly pending) so sort by hash to ensure a total ordering.
            Sha256Hash parentTransactionHash = a.getParentTransactionHash();
            Sha256Hash parentTransactionHash1 = b.getParentTransactionHash();
            if (parentTransactionHash != null && parentTransactionHash1 != null) {
                BigInteger aHash = parentTransactionHash.toBigInteger();
                BigInteger bHash = parentTransactionHash1.toBigInteger();
                return aHash.compareTo(bHash);
            } else {
                return 0;
            }
        });
    }

    protected boolean matchesRequirement(TransactionOutput transactionOutput) {
        if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
            boolean confirmationCheck = allowUnconfirmedSpend;
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

            return confirmationCheck;
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }

    }

    protected boolean shouldSelect(Transaction tx) {
        return true;
    }
}
