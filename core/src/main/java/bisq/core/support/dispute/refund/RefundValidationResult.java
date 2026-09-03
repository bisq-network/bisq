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

import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;

import bisq.common.util.Hex;

import org.bitcoinj.core.Coin;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Immutable binding between validated refund evidence and the exact payout authorization checked against it.
 */
public record RefundValidationResult(String contractHash,
                                     String depositTxId,
                                     String delayedPayoutTxId,
                                     long depositOutputValue,
                                     long delayedPayoutOutputValue,
                                     long maximumRefundAmount,
                                     long tradeTxFee,
                                     int burningManSelectionHeight,
                                     String donationAddress,
                                     long buyerPayoutAmount,
                                     long sellerPayoutAmount) {
    public RefundValidationResult {
        checkNotNull(contractHash, "contractHash must not be null");
        checkNotNull(depositTxId, "depositTxId must not be null");
        checkNotNull(delayedPayoutTxId, "delayedPayoutTxId must not be null");
        checkArgument(depositOutputValue > 0, "depositOutputValue must be positive");
        checkArgument(delayedPayoutOutputValue > 0, "delayedPayoutOutputValue must be positive");
        checkArgument(maximumRefundAmount >= 0, "maximumRefundAmount must not be negative");
        checkArgument(tradeTxFee > 0, "tradeTxFee must be positive");
        checkArgument(burningManSelectionHeight >= 0,
                "burningManSelectionHeight must not be negative");
        checkArgument(buyerPayoutAmount >= 0, "buyerPayoutAmount must not be negative");
        checkArgument(sellerPayoutAmount >= 0, "sellerPayoutAmount must not be negative");
        checkArgument(maximumRefundAmount <= depositOutputValue,
                "maximumRefundAmount must not exceed depositOutputValue");
        checkArgument(maximumRefundAmount <= delayedPayoutOutputValue,
                "maximumRefundAmount must not exceed delayedPayoutOutputValue");
        checkArgument(Math.addExact(buyerPayoutAmount, sellerPayoutAmount) <= maximumRefundAmount,
                "Payout amount must not exceed maximumRefundAmount");
    }

    public void verifyMatches(Dispute dispute, DisputeResult disputeResult) {
        Dispute checkedDispute = checkNotNull(dispute, "dispute must not be null");
        DisputeResult checkedDisputeResult = checkNotNull(disputeResult, "disputeResult must not be null");
        verifyMatches(checkedDispute,
                checkedDisputeResult.getBuyerPayoutAmount(),
                checkedDisputeResult.getSellerPayoutAmount());
    }

    public void verifyMatches(Dispute dispute, Coin buyerPayout, Coin sellerPayout) {
        Dispute checkedDispute = checkNotNull(dispute, "dispute must not be null");
        checkArgument(contractHash.equals(Hex.encode(checkNotNull(checkedDispute.getContractHash(),
                        "dispute contractHash must not be null"))),
                "Validated contract hash no longer matches the dispute");
        checkArgument(depositTxId.equals(checkedDispute.getDepositTxId()),
                "Validated deposit tx ID no longer matches the dispute");
        checkArgument(delayedPayoutTxId.equals(checkedDispute.getDelayedPayoutTxId()),
                "Validated delayed payout tx ID no longer matches the dispute");
        checkArgument(tradeTxFee == checkedDispute.getTradeTxFee(),
                "Trade tx fee changed after refund validation");
        checkArgument(burningManSelectionHeight == checkedDispute.getBurningManSelectionHeight(),
                "Burning Man selection height changed after refund validation");
        checkArgument(Objects.equals(donationAddress,
                        checkedDispute.getDonationAddressOfDelayedPayoutTx()),
                "Donation address changed after refund validation");
        checkArgument(buyerPayoutAmount == checkNotNull(buyerPayout,
                        "buyerPayout must not be null").value,
                "Buyer payout amount changed after refund validation");
        checkArgument(sellerPayoutAmount == checkNotNull(sellerPayout,
                        "sellerPayout must not be null").value,
                "Seller payout amount changed after refund validation");
    }
}
