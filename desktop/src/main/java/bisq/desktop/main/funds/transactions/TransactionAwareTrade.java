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

import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.offer.Offer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;

import org.bitcoinj.core.Transaction;

import javafx.collections.ObservableList;

import java.util.Optional;

class TransactionAwareTrade implements TransactionAwareTradable {
    private final Trade delegate;
    private final DisputeManager disputeManager;

    TransactionAwareTrade(Trade delegate, DisputeManager disputeManager) {
        this.delegate = delegate;
        this.disputeManager = disputeManager;
    }

    @Override
    public boolean isRelatedToTransaction(Transaction transaction) {
        String txId = transaction.getHashAsString();

        boolean isTakerOfferFeeTx = txId.equals(delegate.getTakerFeeTxId());
        boolean isOfferFeeTx = isOfferFeeTx(txId);
        boolean isDepositTx = isDepositTx(txId);
        boolean isPayoutTx = isPayoutTx(txId);
        boolean isDisputedPayoutTx = isDisputedPayoutTx(txId);

        return isTakerOfferFeeTx || isOfferFeeTx || isDepositTx || isPayoutTx || isDisputedPayoutTx;
    }

    private boolean isPayoutTx(String txId) {
        return Optional.ofNullable(delegate.getPayoutTx())
                .map(Transaction::getHashAsString)
                .map(hash -> hash.equals(txId))
                .orElse(false);
    }

    private boolean isDepositTx(String txId) {
        return Optional.ofNullable(delegate.getDepositTx())
                .map(Transaction::getHashAsString)
                .map(hash -> hash.equals(txId))
                .orElse(false);
    }

    private boolean isOfferFeeTx(String txId) {
        return Optional.ofNullable(delegate.getOffer())
                .map(Offer::getOfferFeePaymentTxId)
                .map(paymentTxId -> paymentTxId.equals(txId))
                .orElse(false);
    }

    private boolean isDisputedPayoutTx(String txId) {
        String delegateId = delegate.getId();

        ObservableList<Dispute> disputes = disputeManager.getDisputesAsObservableList();
        return disputes.stream()
                .anyMatch(dispute -> {
                    String disputePayoutTxId = dispute.getDisputePayoutTxId();
                    boolean isDisputePayoutTx = txId.equals(disputePayoutTxId);

                    String disputeTradeId = dispute.getTradeId();
                    boolean isDisputeRelatedToThis = delegateId.equals(disputeTradeId);

                    return isDisputePayoutTx && isDisputeRelatedToThis;
                });
    }

    @Override
    public Tradable asTradable() {
        return delegate;
    }
}
