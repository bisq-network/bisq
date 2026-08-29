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

package bisq.core.support.dispute.refund;

import bisq.core.provider.mempool.MempoolTxStatus;

import org.bitcoinj.core.Transaction;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public record RefundTransactionChain(Transaction makerFeeTx,
                                     Transaction takerFeeTx,
                                     Transaction depositTx,
                                     Transaction delayedPayoutTx,
                                     MempoolTxStatus depositStatus,
                                     MempoolTxStatus delayedPayoutStatus) {
    public RefundTransactionChain {
        checkNotNull(makerFeeTx, "makerFeeTx must not be null");
        checkNotNull(takerFeeTx, "takerFeeTx must not be null");
        checkNotNull(depositTx, "depositTx must not be null");
        checkNotNull(delayedPayoutTx, "delayedPayoutTx must not be null");
        checkNotNull(depositStatus, "depositStatus must not be null");
        checkNotNull(delayedPayoutStatus, "delayedPayoutStatus must not be null");
        checkArgument(depositTx.getTxId().toString().equals(depositStatus.txId()),
                "Deposit status transaction ID does not match the deposit transaction");
        checkArgument(delayedPayoutTx.getTxId().toString().equals(delayedPayoutStatus.txId()),
                "Delayed payout status transaction ID does not match the delayed payout transaction");
    }

    public List<Transaction> transactions() {
        return List.of(makerFeeTx, takerFeeTx, depositTx, delayedPayoutTx);
    }
}
