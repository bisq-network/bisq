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

import bisq.core.trade.model.Tradable;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import java.util.stream.IntStream;

import javax.annotation.Nullable;

interface TransactionAwareTradable {
    int TX_FILTER_SIZE = 64;
    int DELAYED_PAYOUT_TX_BUCKET_INDEX = TX_FILTER_SIZE - 1;

    boolean isRelatedToTransaction(Transaction transaction);

    Tradable asTradable();

    /** Returns a list of bucket indices of all transactions which might be related to this Tradable. */
    IntStream getRelatedTransactionFilter();

    static int bucketIndex(Transaction tx) {
        return tx.getLockTime() == 0 ? bucketIndex(tx.getTxId()) : DELAYED_PAYOUT_TX_BUCKET_INDEX;
    }

    static int bucketIndex(Sha256Hash hash) {
        int i = hash.getBytes()[31] & 255;
        return i % TX_FILTER_SIZE != DELAYED_PAYOUT_TX_BUCKET_INDEX ?
                i % TX_FILTER_SIZE : i / TX_FILTER_SIZE;
    }

    static int bucketIndex(@Nullable String txId) {
        return txId != null ? bucketIndex(Sha256Hash.wrap(txId)) : -1;
    }
}
