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
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.trade.model.bisq_v1.Contract;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Slf4j
@Singleton
public final class RefundPayoutReceiptService {
    private final RefundDisputeListService disputeListService;
    private final BtcWalletService btcWalletService;
    private boolean payoutReservationInProgress;

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
                    sharesParseableFundingEvidence(receipt, storedDispute)) {
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
        checkState(!payoutReservationInProgress,
                "Another refund payout reservation is still being persisted");
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

        // Only rows presenting the same funding chain are marked. A row that shares just one of the two IDs may
        // belong to a different trade; marking it would let a crafted record transfer the paid state to an unrelated
        // ticket. Such rows stay blocked by the conflict check in findPayoutTxId as long as the paid record exists.
        List<Dispute> reservedDisputes = new ArrayList<>();
        for (Dispute storedDispute : disputeListService.getDisputeList().getList()) {
            if (parseStoredReceipt(storedDispute).filter(receipt::isSameFundingChainAs).isPresent()) {
                reservedDisputes.add(storedDispute);
            }
        }

        payoutReservationInProgress = true;
        reservedDisputes.forEach(reservedDispute -> markPaid(reservedDispute, payoutTxId));
        disputeListService.getPersistenceManager().persistNow(
                () -> {
                    finishReservation();
                    checkedCompleteHandler.run();
                },
                throwable -> {
                    // Nothing has been committed or broadcast at this point, so the receipt is not consumed. The
                    // marks are removed again; keeping them would persist a paid state without any transaction.
                    clearFailedReservation(reservedDisputes);
                    disputeListService.requestPersistence();
                    checkedErrorHandler.accept(throwable);
                });
    }

    private synchronized void finishReservation() {
        payoutReservationInProgress = false;
    }

    private synchronized void clearFailedReservation(List<Dispute> reservedDisputes) {
        reservedDisputes.forEach(RefundPayoutReceiptService::clearReservation);
        payoutReservationInProgress = false;
    }

    private static void markPaid(Dispute dispute, String payoutTxId) {
        dispute.setDisputePayoutTxId(payoutTxId);
        dispute.setPayoutDone(true);
    }

    private static void clearReservation(Dispute dispute) {
        dispute.setDisputePayoutTxId(null);
        dispute.setPayoutDone(false);
    }

    // A paid historical row can contain one malformed funding ID. Its other, parseable ID remains consumption
    // evidence because a match on either transaction is sufficient to block another payout.
    private static boolean sharesParseableFundingEvidence(RefundPayoutReceipt receipt, Dispute storedDispute) {
        if (storedDispute.getSupportType() != SupportType.REFUND) {
            log.warn("Ignoring non-refund dispute stored in the refund dispute list. tradeId={}",
                    storedDispute.getTradeId());
            return false;
        }

        boolean sharesDepositTxId = matchesStoredTxId(storedDispute,
                "depositTxId",
                storedDispute.getDepositTxId(),
                receipt::hasDepositTxId);
        boolean sharesDelayedPayoutTxId = matchesStoredTxId(storedDispute,
                "delayedPayoutTxId",
                storedDispute.getDelayedPayoutTxId(),
                receipt::hasDelayedPayoutTxId);
        return sharesDepositTxId || sharesDelayedPayoutTxId;
    }

    private static boolean matchesStoredTxId(Dispute storedDispute,
                                             String fieldName,
                                             String txId,
                                             Predicate<String> matcher) {
        try {
            return matcher.test(txId);
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring unparseable {} while matching stored refund dispute. tradeId={}, {}",
                    fieldName, storedDispute.getTradeId(), exception.getMessage());
            return false;
        }
    }

    // Marking requires both IDs to identify the same complete funding chain. A malformed stored row is not marked,
    // while the selected dispute is always parsed strictly before a payout can be reserved.
    private static Optional<RefundPayoutReceipt> parseStoredReceipt(Dispute storedDispute) {
        try {
            return Optional.of(RefundPayoutReceipt.fromDispute(storedDispute));
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring stored refund dispute with unparseable funding transaction IDs. tradeId={}, {}",
                    storedDispute.getTradeId(), exception.getMessage());
            return Optional.empty();
        }
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
