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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.support.dispute.Dispute;
import bisq.core.trade.model.bisq_v1.Contract;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public final class RefundPayoutReceiptService {
    private final RefundDisputeListService disputeListService;
    private final BtcWalletService btcWalletService;

    @Inject
    public RefundPayoutReceiptService(RefundDisputeListService disputeListService,
                                      BtcWalletService btcWalletService) {
        this.disputeListService = disputeListService;
        this.btcWalletService = btcWalletService;
    }

    public synchronized Optional<String> findPayoutTxId(Dispute dispute) {
        RefundPayoutReceipt receipt = RefundPayoutReceipt.fromDispute(dispute);

        for (Dispute storedDispute : disputeListService.getDisputeList().getList()) {
            String payoutTxId = storedDispute.getDisputePayoutTxId();
            if (payoutTxId != null && !payoutTxId.isBlank() &&
                    receipt.sharesFundingEvidenceWith(RefundPayoutReceipt.fromDispute(storedDispute))) {
                return Optional.of(payoutTxId);
            }
        }

        return btcWalletService.getTransactions(true).stream()
                .filter(transaction -> RefundPayoutReceipt.fromMemo(transaction.getMemo())
                        .filter(receipt::sharesFundingEvidenceWith)
                        .isPresent())
                .map(transaction -> transaction.getTxId().toString())
                .findFirst();
    }

    public Coin getMaximumPayoutAmount(Dispute dispute) {
        RefundPayoutReceipt.fromDispute(dispute);
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin contractPayoutAmount = contract.getTradeAmount()
                .add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());

        Optional<Transaction> depositTx = dispute.findDepositTx(btcWalletService);
        if (depositTx.isEmpty()) {
            return Coin.ZERO;
        }
        return calculateMaximumPayoutAmount(contractPayoutAmount,
                depositTx.get().getOutput(0).getValue(),
                dispute.getTradeTxFee());
    }

    public synchronized void persistPayoutReservation(Dispute dispute,
                                                      Transaction payoutTx,
                                                      Runnable completeHandler,
                                                      Consumer<Throwable> errorHandler) {
        checkArgument(findPayoutTxId(dispute).isEmpty(),
                "A refund payout has already been created for this deposit or delayed payout transaction");

        RefundPayoutReceipt receipt = RefundPayoutReceipt.fromDispute(dispute);
        Transaction checkedPayoutTx = checkNotNull(payoutTx, "payoutTx must not be null");
        Runnable checkedCompleteHandler = checkNotNull(completeHandler, "completeHandler must not be null");
        Consumer<Throwable> checkedErrorHandler = checkNotNull(errorHandler, "errorHandler must not be null");
        checkArgument(disputeListService.getDisputeList().stream().anyMatch(storedDispute -> storedDispute == dispute),
                "The refund dispute must be stored before reserving a payout");

        String payoutTxId = checkedPayoutTx.getTxId().toString();
        checkedPayoutTx.setMemo(receipt.toMemo());

        for (Dispute storedDispute : disputeListService.getDisputeList().getList()) {
            if (receipt.sharesFundingEvidenceWith(RefundPayoutReceipt.fromDispute(storedDispute))) {
                markPaid(storedDispute, payoutTxId);
            }
        }

        disputeListService.getPersistenceManager().persistNow(
                checkedCompleteHandler,
                checkedErrorHandler);
    }

    private static void markPaid(Dispute dispute, String payoutTxId) {
        dispute.setDisputePayoutTxId(payoutTxId);
        dispute.setPayoutDone(true);
    }

    @VisibleForTesting
    static Coin calculateMaximumPayoutAmount(Coin contractPayoutAmount,
                                             Coin depositOutputAmount,
                                             long tradeTxFee) {
        Coin feeReserve = tradeTxFee > 0 ? Coin.valueOf(tradeTxFee) : Coin.ZERO;
        Coin receiptPayoutAmount = depositOutputAmount.subtract(feeReserve);
        if (receiptPayoutAmount.isNegative()) {
            return Coin.ZERO;
        }
        return contractPayoutAmount.compareTo(receiptPayoutAmount) <= 0
                ? contractPayoutAmount
                : receiptPayoutAmount;
    }
}
