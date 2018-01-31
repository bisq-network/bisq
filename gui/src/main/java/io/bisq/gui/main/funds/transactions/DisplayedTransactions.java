package io.bisq.gui.main.funds.transactions;

import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.gui.util.BSFormatter;
import org.bitcoinj.core.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DisplayedTransactions extends ObservableListDecorator<TransactionsListItem> {
    private final OpenOfferManager openOfferManager;
    private final TradeManager tradeManager;
    private final FailedTradesManager failedTradesManager;
    private final ClosedTradableManager closedTradableManager;
    private final BtcWalletService btcWalletService;
    private final DisputeManager disputeManager;
    private final BsqWalletService bsqWalletService;
    private final BSFormatter formatter;

    private void updateList() {
        Stream<Tradable> concat1 = Stream.concat(openOfferManager.getObservableList().stream(), tradeManager.getTradableList().stream());
        Stream<Tradable> concat2 = Stream.concat(concat1, closedTradableManager.getClosedTradables().stream());
        Stream<Tradable> concat3 = Stream.concat(concat2, failedTradesManager.getFailedTrades().stream());
        Set<Tradable> all = concat3.collect(Collectors.toSet());

        Set<Transaction> transactions = btcWalletService.getTransactions(false);
        List<TransactionsListItem> transactionsListItems = transactions.stream()
                .map(transaction -> {
                    Optional<Tradable> tradableOptional = all.stream()
                            .filter(tradable -> {
                                String txId = transaction.getHashAsString();
                                if (tradable instanceof OpenOffer)
                                    return tradable.getOffer().getOfferFeePaymentTxId().equals(txId);
                                else if (tradable instanceof Trade) {
                                    Trade trade = (Trade) tradable;
                                    boolean isTakeOfferFeeTx = txId.equals(trade.getTakerFeeTxId());
                                    boolean isOfferFeeTx = trade.getOffer() != null &&
                                            txId.equals(trade.getOffer().getOfferFeePaymentTxId());
                                    boolean isDepositTx = trade.getDepositTx() != null &&
                                            trade.getDepositTx().getHashAsString().equals(txId);
                                    boolean isPayoutTx = trade.getPayoutTx() != null &&
                                            trade.getPayoutTx().getHashAsString().equals(txId);

                                    boolean isDisputedPayoutTx = disputeManager.getDisputesAsObservableList().stream()
                                            .anyMatch(dispute -> txId.equals(dispute.getDisputePayoutTxId()) &&
                                                    tradable.getId().equals(dispute.getTradeId()));

                                    return isTakeOfferFeeTx || isOfferFeeTx || isDepositTx || isPayoutTx || isDisputedPayoutTx;
                                } else
                                    return false;
                            })
                            .findAny();
                    return new TransactionsListItem(transaction, btcWalletService, bsqWalletService, tradableOptional, formatter);
                })
                .collect(Collectors.toList());

        // are sorted by getRecentTransactions
        forEach(TransactionsListItem::cleanup);
        setAll(transactionsListItems);
    }
}
