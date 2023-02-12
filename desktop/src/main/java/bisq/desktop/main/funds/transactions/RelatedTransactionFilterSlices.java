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

package bisq.desktop.main.funds.transactions;

import org.bitcoinj.core.Transaction;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static bisq.desktop.main.funds.transactions.TransactionAwareTradable.TX_FILTER_SIZE;

public class RelatedTransactionFilterSlices {
    private final List<TransactionAwareTradable> tradables;
    private final BitSet[] filterSlices;

    public RelatedTransactionFilterSlices(Collection<? extends TransactionAwareTradable> tradables) {
        this.tradables = List.copyOf(tradables);

        filterSlices = new BitSet[TX_FILTER_SIZE];
        Arrays.setAll(filterSlices, i -> new BitSet(this.tradables.size()));

        IntStream.range(0, this.tradables.size())
                .forEach(j -> this.tradables.get(j).getRelatedTransactionFilter()
                        .forEach(i -> filterSlices[i].set(j)));
    }

    public Stream<TransactionAwareTradable> getAllRelatedTradables(Transaction tx) {
        int i = TransactionAwareTradable.bucketIndex(tx);
        return filterSlices[i].stream()
                .mapToObj(tradables::get)
                .filter(t -> t.isRelatedToTransaction(tx));
    }
}
