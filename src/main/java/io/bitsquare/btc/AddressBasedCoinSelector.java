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

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.params.RegTestParams;
import com.google.bitcoin.wallet.CoinSelection;
import com.google.bitcoin.wallet.DefaultCoinSelector;

import com.google.common.annotations.VisibleForTesting;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a {@link com.google.bitcoin.wallet.CoinSelector} which attempts to get the highest priority
 * possible. This means that the transaction is the most likely to get confirmed. Note that this means we may end up
 * "spending" more priority than would be required to get the transaction we are creating confirmed.
 */
class AddressBasedCoinSelector extends DefaultCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(AddressBasedCoinSelector.class);
    private final NetworkParameters params;
    private final AddressEntry addressEntry;
    private final boolean includePending;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressBasedCoinSelector(NetworkParameters params, AddressEntry addressEntry, boolean includePending) {
        this.params = params;
        this.addressEntry = addressEntry;
        this.includePending = includePending;
    }

    @VisibleForTesting
    static void sortOutputs(ArrayList<TransactionOutput> outputs) {
        Collections.sort(outputs, (a, b) -> {
            int depth1 = 0;
            int depth2 = 0;
            TransactionConfidence conf1 = a.getParentTransaction().getConfidence();
            TransactionConfidence conf2 = b.getParentTransaction().getConfidence();
            if (conf1.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                depth1 = conf1.getDepthInBlocks();
            if (conf2.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                depth2 = conf2.getDepthInBlocks();
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
            BigInteger aHash = a.getParentTransaction().getHash().toBigInteger();
            BigInteger bHash = b.getParentTransaction().getHash().toBigInteger();
            return aHash.compareTo(bHash);
        });
    }

    private static boolean isInBlockChainOrPending(Transaction tx) {
        // Pick chain-included transactions and transactions that are pending.
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return type.equals(TransactionConfidence.ConfidenceType.BUILDING) || type.equals(TransactionConfidence
                .ConfidenceType.PENDING) &&
                // In regtest mode we expect to have only one peer, so we won't see transactions propagate.
                // TODO: The value 1 below dates from a time when transactions we broadcast *to* were counted, set to 0
                (confidence.numBroadcastPeers() > 1 || tx.getParams() == RegTestParams.get());
    }

    private static boolean isInBlockChain(Transaction tx) {
        // Only pick chain-included transactions.
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return type.equals(TransactionConfidence.ConfidenceType.BUILDING);
    }

    /**
     * Sub-classes can override this to just customize whether transactions are usable, but keep age sorting.
     */
    protected boolean shouldSelect(Transaction tx) {
        if (includePending) {
            return isInBlockChainOrPending(tx);
        }
        else {
            return isInBlockChain(tx);
        }
    }

    private boolean matchesRequiredAddress(TransactionOutput transactionOutput) {
        if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isSentToP2SH
                ()) {
            Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
            if (addressEntry != null && addressOutput.equals(addressEntry.getAddress())) {
                return true;
            }
            log.warn("No match found at matchesRequiredAddress addressOutput/addressEntry " + addressOutput.toString
                    () + "/" + addressEntry.getAddress().toString());
        }
        return false;
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        log.debug("candidates.size: " + candidates.size());
        long targetAsLong = target.longValue();
        log.debug("value needed: " + targetAsLong);
        HashSet<TransactionOutput> selected = new HashSet<>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        // TODO: Consider changing the wallets internal format to track just outputs and keep them ordered.
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
            if (!shouldSelect(output.getParentTransaction()) || !matchesRequiredAddress(output)) {
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
