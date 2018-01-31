package io.bisq.gui.main.funds.transactions;

import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Tradable;
import io.bisq.gui.util.BSFormatter;
import org.bitcoinj.core.Transaction;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Optional;

public class TransactionListItemFactory {
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final BSFormatter formatter;

    @Inject
    public TransactionListItemFactory(BtcWalletService btcWalletService, BsqWalletService bsqWalletService,
                                      BSFormatter formatter) {
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.formatter = formatter;
    }

    TransactionsListItem create(Transaction transaction, @Nullable TransactionAwareTradable tradable) {
        Optional<Tradable> maybeTradable = Optional.ofNullable(tradable)
                .map(TransactionAwareTradable::asTradable);

        return new TransactionsListItem(transaction, btcWalletService, bsqWalletService, maybeTradable, formatter);
    }
}
