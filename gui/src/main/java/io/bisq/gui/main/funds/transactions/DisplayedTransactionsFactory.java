package io.bisq.gui.main.funds.transactions;

import io.bisq.core.btc.wallet.BtcWalletService;

import javax.inject.Inject;

class DisplayedTransactionsFactory {
    private final BtcWalletService btcWalletService;
    private final TradableRepository tradableRepository;
    private final TransactionListItemFactory transactionListItemFactory;
    private final TransactionAwareTradableFactory transactionAwareTradableFactory;

    @Inject
    DisplayedTransactionsFactory(BtcWalletService btcWalletService, TradableRepository tradableRepository,
                                 TransactionListItemFactory transactionListItemFactory,
                                 TransactionAwareTradableFactory transactionAwareTradableFactory) {
        this.btcWalletService = btcWalletService;
        this.tradableRepository = tradableRepository;
        this.transactionListItemFactory = transactionListItemFactory;
        this.transactionAwareTradableFactory = transactionAwareTradableFactory;
    }

    DisplayedTransactions create() {
        return new DisplayedTransactions(btcWalletService, tradableRepository, transactionListItemFactory,
                transactionAwareTradableFactory);
    }
}
