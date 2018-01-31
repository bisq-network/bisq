package io.bisq.gui.main.funds.transactions;

import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Tradable;
import org.bitcoinj.core.Transaction;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class DisplayedTransactions extends AbstractObservableListDecorator<TransactionsListItem> {
    private final BtcWalletService btcWalletService;
    private final TradableRepository tradableRepository;
    private final TransactionListItemFactory transactionListItemFactory;
    private final TransactionAwareTradableFactory transactionAwareTradableFactory;

    DisplayedTransactions(BtcWalletService btcWalletService, TradableRepository tradableRepository,
                          TransactionListItemFactory transactionListItemFactory,
                          TransactionAwareTradableFactory transactionAwareTradableFactory) {
        this.btcWalletService = btcWalletService;
        this.tradableRepository = tradableRepository;
        this.transactionListItemFactory = transactionListItemFactory;
        this.transactionAwareTradableFactory = transactionAwareTradableFactory;
    }

    void update() {
        List<TransactionsListItem> transactionsListItems = getTransactionListItems();
        // are sorted by getRecentTransactions
        forEach(TransactionsListItem::cleanup);
        setAll(transactionsListItems);
    }

    private List<TransactionsListItem> getTransactionListItems() {
        Set<Transaction> transactions = btcWalletService.getTransactions(false);
        return transactions.stream()
                .map(this::convertTransactionToListItem)
                .collect(Collectors.toList());
    }

    private TransactionsListItem convertTransactionToListItem(Transaction transaction) {
        Set<Tradable> tradables = tradableRepository.getAll();

        TransactionAwareTradable maybeTradable = tradables.stream()
                .map(transactionAwareTradableFactory::create)
                .filter(tradable -> tradable.isRelatedToTransaction(transaction))
                .findAny()
                .orElse(null);

        return transactionListItemFactory.create(transaction, maybeTradable);
    }
}
