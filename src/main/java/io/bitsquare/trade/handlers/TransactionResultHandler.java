package io.bitsquare.trade.handlers;

import com.google.bitcoin.core.Transaction;

public interface TransactionResultHandler
{
    void onResult(Transaction transaction);
}
