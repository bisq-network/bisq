package io.bitsquare.trade.offerer;

import com.google.bitcoin.core.TransactionConfidence;

public interface OffererPaymentProtocolListener
{
    void onProgress(double progress);

    void onFailure(String failureMessage);

    void onDepositTxPublished(String depositTxID);

    void onDepositTxConfirmedInBlockchain();

    void onDepositTxConfirmedUpdate(TransactionConfidence confidence);

    void onPayoutTxPublished(String payoutTxID);
}
