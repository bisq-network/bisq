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
import org.bitcoinj.core.TransactionInput;

import java.util.stream.IntStream;

import javax.annotation.Nullable;

interface TransactionAwareTradable {
    int TX_FILTER_SIZE = 64;
    // Delayed payout, warning, redirect and claim txs all go into one bucket (as there shouldn't be too many of them).
    int DELAYED_PAYOUT_TX_BUCKET_INDEX = TX_FILTER_SIZE - 1;

    boolean isRelatedToTransaction(Transaction transaction);

    Tradable asTradable();

    /** Returns a list of bucket indices of all transactions which might be related to this Tradable. */
    IntStream getRelatedTransactionFilter();

    static int bucketIndex(Transaction tx) {
        return tx.getInputs().size() == 1 && (tx.getLockTime() != 0 || isPossibleRedirectOrClaimTx(tx)) &&
                isPossibleEscrowSpend(tx.getInput(0)) ? DELAYED_PAYOUT_TX_BUCKET_INDEX : bucketIndex(tx.getTxId());
    }

    static int bucketIndex(Sha256Hash hash) {
        int i = hash.getBytes()[31] & 255;
        return i % TX_FILTER_SIZE != DELAYED_PAYOUT_TX_BUCKET_INDEX ? i % TX_FILTER_SIZE : i / TX_FILTER_SIZE;
    }

    static int bucketIndex(@Nullable String txId) {
        return txId != null ? bucketIndex(Sha256Hash.wrap(txId)) : -1;
    }

    static boolean isPossibleRedirectOrClaimTx(Transaction tx) {
        // The txs bitcoinj retrieves from the network frequently have missing witness data, so we must be lenient in
        // that case (hence the last possibility checked for here), and similarly in the method below.
        TransactionInput input = tx.getInput(0);
        return input.getWitness().getPushCount() == 5 || tx.hasRelativeLockTime() || (tx.getVersion() == 1 &&
                !input.hasWitness() && input.getScriptBytes().length == 0);
    }

    static boolean isPossibleEscrowSpend(TransactionInput input) {
        // The maximum ScriptSig length of a (canonically signed) P2PKH or P2SH-P2WH input is 107 bytes, whereas
        // multisig P2SH will always be longer than that. P2PKH, P2SH-P2WPKH and P2WPKH have a witness push count less
        // than 3, but all Segwit trade escrow spends have a witness push count of at least 3. So we catch all escrow
        // spends this way, without too many false positives.
        return input.getScriptBytes().length > 107 || input.getWitness().getPushCount() > 2 || (!input.hasWitness() &&
                input.getScriptBytes().length == 0);
    }
}
