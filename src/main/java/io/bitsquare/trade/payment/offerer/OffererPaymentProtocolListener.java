package io.bitsquare.trade.payment.offerer;

import com.google.bitcoin.core.TransactionConfidence;

@SuppressWarnings("EmptyMethod")
public interface OffererPaymentProtocolListener
{
    @SuppressWarnings("UnusedParameters")
    void onProgress(double progress);

    void onFailure(String failureMessage);

    void onDepositTxPublished(String depositTxID);

    void onDepositTxConfirmedInBlockchain();

    @SuppressWarnings("UnusedParameters")
    void onDepositTxConfirmedUpdate(TransactionConfidence confidence);

    void onPayoutTxPublished(String payoutTxID);
}
