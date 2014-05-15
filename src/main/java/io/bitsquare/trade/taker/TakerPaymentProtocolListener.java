package io.bitsquare.trade.taker;

public interface TakerPaymentProtocolListener
{
    void onProgress(double progress);

    void onFailure(String failureMessage);

    void onDepositTxPublished(String depositTxID);

    void onBankTransferInited();
}
