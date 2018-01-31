package io.bisq.gui.main.funds.transactions;

import io.bisq.core.trade.Tradable;
import org.bitcoinj.core.Transaction;

interface TransactionAwareTradable {
    boolean isRelatedToTransaction(Transaction transaction);

    Tradable asTradable();
}
