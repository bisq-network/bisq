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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our addressEntry.
 */
class SavingsWalletCoinSelector implements CoinSelector {
    private static final Logger log = LoggerFactory.getLogger(SavingsWalletCoinSelector.class);
    protected final NetworkParameters params;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SavingsWalletCoinSelector(NetworkParameters params) {
        this.params = params;
    }

    @VisibleForTesting
    private static void sortOutputs(ArrayList<TransactionOutput> outputs) {
        Collections.sort(outputs, (a, b) -> {
            int depth1 = a.getParentTransactionDepthInBlocks();
            int depth2 = b.getParentTransactionDepthInBlocks();
            Coin aValue = a.getValue();
            Coin bValue = b.getValue();
            BigInteger aCoinDepth = BigInteger.valueOf(aValue.value).multiply(BigInteger.valueOf(depth1));
            BigInteger bCoinDepth = BigInteger.valueOf(bValue.value).multiply(BigInteger.valueOf(depth2));
            int c1 = bCoinDepth.compareTo(aCoinDepth);
            if (c1 != 0) return c1;
            // The "coin*days" destroyed are equal, sort by value alone to get the lowest transaction size.
            int c2 = bValue.compareTo(aValue);
            if (c2 != 0) return c2;
            // They are entirely equivalent (possibly pending) so sort by hash to ensure a total ordering.
            checkNotNull(a.getParentTransactionHash(), "a.getParentTransactionHash() must not be null");
            checkNotNull(b.getParentTransactionHash(), "b.getParentTransactionHash() must not be null");
            BigInteger aHash = a.getParentTransactionHash().toBigInteger();
            BigInteger bHash = b.getParentTransactionHash().toBigInteger();
            return aHash.compareTo(bHash);
        });
    }

    private static boolean isInBlockChainOrPending(Transaction tx) {
        // Pick chain-included transactions and transactions that are pending.
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();

        log.debug("numBroadcastPeers = " + confidence.numBroadcastPeers());
        return type.equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                type.equals(TransactionConfidence.ConfidenceType.PENDING);
    }

    /**
     * Sub-classes can override this to just customize whether transactions are usable, but keep age sorting.
     */
    protected boolean shouldSelect(Transaction tx) {
        return isInBlockChainOrPending(tx);
    }

    protected boolean matchesRequirement(TransactionOutput transactionOutput) {
        return (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash());
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

}
