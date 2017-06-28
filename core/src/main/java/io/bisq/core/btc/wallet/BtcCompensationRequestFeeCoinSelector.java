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

package io.bisq.core.btc.wallet;

import com.google.common.annotations.VisibleForTesting;
import io.bisq.core.btc.Restrictions;
import org.bitcoinj.core.*;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our address.
 */
class BtcCompensationRequestFeeCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(BtcCompensationRequestFeeCoinSelector.class);

    private final Wallet wallet;

    public BtcCompensationRequestFeeCoinSelector(Wallet wallet) {
        this.wallet = wallet;
    }

    public CoinSelection getCoinSelection(Coin target) throws InsufficientMoneyException, ChangeBelowDustException {
        return select(target, getCandidates());
    }

    private List<TransactionOutput> getCandidates() {
        return wallet.calculateAllSpendCandidates(true, true);
    }

    private CoinSelection select(Coin target, List<TransactionOutput> candidates) throws InsufficientMoneyException, ChangeBelowDustException {
        ArrayList<TransactionOutput> selected = new ArrayList<>();
        // Sort the inputs by value so we get the lowest values first.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        if (!target.equals(NetworkParameters.MAX_MONEY))
            sortOutputsByValue(sortedOutputs);

        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        long dust = Restrictions.getMinNonDustOutput().value;
        long targetValue = target.value;
        for (TransactionOutput output : sortedOutputs) {
            if (total >= targetValue) {
                long change = total - targetValue;
                // If we get a change it must not be smaller than dust
                if (change >= dust || change == 0)
                    break;
            }

            // Only pick chain-included transactions, or transactions that are ours and pending.
            if (!isSelectable(output.getParentTransaction()))
                continue;

            selected.add(output);
            total += output.getValue().value;
        }

        long missing = targetValue - total;
        if (missing > 0)
            throw new InsufficientMoneyException(Coin.valueOf(missing));

        long change = total - targetValue;
        if (change > 0 && change < dust)
            throw new ChangeBelowDustException(Coin.valueOf(change));

        return new CoinSelection(Coin.valueOf(total), selected);
    }

    @VisibleForTesting
    void sortOutputsByValue(ArrayList<TransactionOutput> outputs) {
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

    public boolean isSelectable(Transaction tx) {
        if (tx != null) {
            // Only pick chain-included transactions, or transactions that are ours and pending.
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
            return type.equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                    type.equals(TransactionConfidence.ConfidenceType.PENDING) &&
                            confidence.getSource().equals(TransactionConfidence.Source.SELF) &&
                            // In regtest mode we expect to have only one peer, so we won't see transactions propagate.
                            // TODO: The value 1 below dates from a time when transactions we broadcast *to* were counted, set to 0
                            (confidence.numBroadcastPeers() > 1 || tx.getParams() == RegTestParams.get());
        } else {
            return true;
        }
    }

}
