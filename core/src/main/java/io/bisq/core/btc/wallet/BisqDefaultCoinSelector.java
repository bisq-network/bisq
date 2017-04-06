/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.btc.wallet;

import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.*;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Used from org.bitcoinj.wallet.DefaultCoinSelector but added selectOutput method and changed static methods to
 * instance methods.
 * <p>
 * <p>
 * This class implements a {@link CoinSelector} which attempts to get the highest priority
 * possible. This means that the transaction is the most likely to get confirmed. Note that this means we may end up
 * "spending" more priority than would be required to get the transaction we are creating confirmed.
 */
@Slf4j
public abstract class BisqDefaultCoinSelector implements CoinSelector {

    public CoinSelection select(Coin target, Set<TransactionOutput> candidates) {
        return select(target, new ArrayList<>(candidates));
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        ArrayList<TransactionOutput> selected = new ArrayList<>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        // TODO: Consider changing the wallets internal format to track just outputs and keep them ordered.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        // TODO: Take in network parameters when instantiated, and then test against the current network. Or just have a boolean parameter for "give me everything"
        if (!target.equals(NetworkParameters.MAX_MONEY)) {
            sortOutputs(sortedOutputs);
        }
        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        long targetValue = target.value;
        for (TransactionOutput output : sortedOutputs) {
            if (total >= targetValue) {
                long change = total - targetValue;
                if (change == 0 || change >= Transaction.MIN_NONDUST_OUTPUT.value)
                    break;
            }

            // Only pick chain-included transactions, or transactions that are ours and pending.
            if (!shouldSelect(output.getParentTransaction()) || !selectOutput(output))
                continue;

            selected.add(output);
            total += output.getValue().value;
        }
        // Total may be lower than target here, if the given candidates were insufficient to create to requested
        // transaction.
        return new CoinSelection(Coin.valueOf(total), selected);
    }

    public Coin getChange(Coin target, CoinSelection coinSelection) throws InsufficientMoneyException, ChangeBelowDustException {
        long targetValue = target.value;
        long total = coinSelection.valueGathered.value;
        long missing = targetValue - total;
        if (missing > 0)
            throw new InsufficientMoneyException(Coin.valueOf(missing));

        long change = total - targetValue;
        if (change > 0 && change < Transaction.MIN_NONDUST_OUTPUT.value)
            throw new ChangeBelowDustException(Coin.valueOf(change));

        return Coin.valueOf(change);
    }

    abstract boolean selectOutput(TransactionOutput output);

    protected void sortOutputs(ArrayList<TransactionOutput> outputs) {
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
            BigInteger aHash = a.getParentTransactionHash().toBigInteger();
            BigInteger bHash = b.getParentTransactionHash().toBigInteger();
            return aHash.compareTo(bHash);
        });
    }

    /**
     * Sub-classes can override this to just customize whether transactions are usable, but keep age sorting.
     */
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            return isSelectable(tx);
        }
        return true;
    }

    protected boolean isSelectable(Transaction tx) {
        // Only pick chain-included transactions, or transactions that are ours and pending.
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return type.equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                type.equals(TransactionConfidence.ConfidenceType.PENDING) &&
                        confidence.getSource().equals(TransactionConfidence.Source.SELF) &&
                        // In regtest mode we expect to have only one peer, so we won't see transactions propagate.
                        // TODO: The value 1 below dates from a time when transactions we broadcast *to* were counted, set to 0
                        (confidence.numBroadcastPeers() > 1 || tx.getParams() == RegTestParams.get());
    }
}
