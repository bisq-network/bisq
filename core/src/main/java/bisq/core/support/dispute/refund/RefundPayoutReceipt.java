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

import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;

import org.bitcoinj.core.Sha256Hash;

import java.util.Locale;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

final class RefundPayoutReceipt {
    static final String MEMO_PREFIX = "bisq-refund-payout-v1:";

    private final Sha256Hash depositTxId;
    private final Sha256Hash delayedPayoutTxId;

    private RefundPayoutReceipt(Sha256Hash depositTxId, Sha256Hash delayedPayoutTxId) {
        this.depositTxId = depositTxId;
        this.delayedPayoutTxId = delayedPayoutTxId;
    }

    static RefundPayoutReceipt fromDispute(Dispute dispute) {
        Dispute checkedDispute = checkNotNull(dispute, "dispute must not be null");
        checkArgument(checkedDispute.getSupportType() == SupportType.REFUND,
                "Support type must be REFUND");
        return new RefundPayoutReceipt(
                parseTxId(checkedDispute.getDepositTxId(), "depositTxId"),
                parseTxId(checkedDispute.getDelayedPayoutTxId(), "delayedPayoutTxId"));
    }

    static Optional<RefundPayoutReceipt> fromMemo(String memo) {
        if (memo == null || !memo.startsWith(MEMO_PREFIX)) {
            return Optional.empty();
        }

        String[] txIds = memo.substring(MEMO_PREFIX.length()).split(":", -1);
        if (txIds.length != 2) {
            return Optional.empty();
        }

        try {
            return Optional.of(new RefundPayoutReceipt(
                    parseTxId(txIds[0], "depositTxId"),
                    parseTxId(txIds[1], "delayedPayoutTxId")));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    boolean sharesFundingEvidenceWith(RefundPayoutReceipt other) {
        return depositTxId.equals(other.depositTxId) || delayedPayoutTxId.equals(other.delayedPayoutTxId);
    }

    boolean hasDepositTxId(String txId) {
        return depositTxId.equals(parseTxId(txId, "depositTxId"));
    }

    boolean hasDelayedPayoutTxId(String txId) {
        return delayedPayoutTxId.equals(parseTxId(txId, "delayedPayoutTxId"));
    }

    String toMemo() {
        return MEMO_PREFIX + depositTxId + ":" + delayedPayoutTxId;
    }

    private static Sha256Hash parseTxId(String txId, String fieldName) {
        checkArgument(txId != null && !txId.isBlank(), "%s must not be empty", fieldName);
        try {
            return Sha256Hash.wrap(txId.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " must be a 32-byte transaction ID", exception);
        }
    }
}
