package io.bitsquare.trade.payment.offerer;

import com.google.bitcoin.core.TransactionConfidence;

public interface OffererAsBuyerProtocolListener
{
    void onDepositTxPublished(String depositTxID);

    void onDepositTxConfirmedInBlockchain();

    void onDepositTxConfirmedUpdate(TransactionConfidence confidence);

    void onPayoutTxPublished(String payoutTxID);
}
