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

package bisq.core.btc.wallet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Used from org.bitcoinj.wallet.DefaultCoinSelector but added selectOutput method and changed static methods to
 * instance methods.
 * <p/>
 * <p/>
 * This class implements a {@link CoinSelector} which attempts to get the highest priority
 * possible. This means that the transaction is the most likely to get confirmed. Note that this means we may end up
 * "spending" more priority than would be required to get the transaction we are creating confirmed.
 */
@Slf4j
public abstract class BisqDefaultCoinSelector implements CoinSelector {

    protected final boolean permitForeignPendingTx;

    // TransactionOutputs to be used as candidates in the select method.
    // We reset the value to null just after we have applied it inside the select method.
    @Nullable
    @Setter
    private Set<TransactionOutput> utxoCandidates;

    public CoinSelection select(Coin target, Set<TransactionOutput> candidates) {
        return select(target, new ArrayList<>(candidates));
    }

    public BisqDefaultCoinSelector(boolean permitForeignPendingTx) {
        this.permitForeignPendingTx = permitForeignPendingTx;
    }

    public BisqDefaultCoinSelector() {
        permitForeignPendingTx = false;
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        ArrayList<TransactionOutput> selected = new ArrayList<>();
        // Sort the inputs by age*value so we get the highest "coin days" spent.

        ArrayList<TransactionOutput> sortedOutputs;
        if (utxoCandidates != null) {
            sortedOutputs = new ArrayList<>(utxoCandidates);
        } else {
            sortedOutputs = new ArrayList<>(candidates);
        }

        // If we spend all we don't need to sort
        if (!target.equals(NetworkParameters.MAX_MONEY))
            sortOutputs(sortedOutputs);

        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        long targetValue = target.value;
        for (TransactionOutput output : sortedOutputs) {
            if (!isDustAttackUtxo(output)) {
                if (total >= targetValue) {
                    long change = total - targetValue;
                    if (change == 0 || change >= Restrictions.getMinNonDustOutput().value)
                        break;
                }

                if (output.getParentTransaction() != null &&
                        isTxSpendable(output.getParentTransaction()) &&
                        isTxOutputSpendable(output)) {
                    selected.add(output);
                    total += output.getValue().value;
                }
            }
        }
        // Total may be lower than target here, if the given candidates were insufficient to create to requested
        // transaction.
        return new CoinSelection(Coin.valueOf(total), selected);
    }

    protected abstract boolean isDustAttackUtxo(TransactionOutput output);

    public Coin getChange(Coin target, CoinSelection coinSelection) throws InsufficientMoneyException {
        long value = target.value;
        long available = coinSelection.valueGathered.value;
        long change = available - value;
        if (change < 0)
            throw new InsufficientMoneyException(Coin.valueOf(change * -1));

        return Coin.valueOf(change);
    }

    // We allow spending from own unconfirmed txs and if permitForeignPendingTx is set as well from foreign
    // unconfirmed txs.
    protected boolean isTxSpendable(Transaction tx) {
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        boolean isConfirmed = type.equals(TransactionConfidence.ConfidenceType.BUILDING);
        boolean isPending = type.equals(TransactionConfidence.ConfidenceType.PENDING);
        boolean isOwnTx = confidence.getSource().equals(TransactionConfidence.Source.SELF);
        return isConfirmed || (isPending && (permitForeignPendingTx || isOwnTx));
    }

    abstract boolean isTxOutputSpendable(TransactionOutput output);

    // TODO Why it uses coin age and not try to minimize number of inputs as the highest priority?
    //      Asked Oscar and he also don't knows why coin age is used. Should be changed so that min. number of inputs is
    //      target.
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
            BigInteger aHash = a.getParentTransactionHash() != null ?
                    a.getParentTransactionHash().toBigInteger() : BigInteger.ZERO;
            BigInteger bHash = b.getParentTransactionHash() != null ?
                    b.getParentTransactionHash().toBigInteger() : BigInteger.ZERO;
            return aHash.compareTo(bHash);
        });
    }

}
