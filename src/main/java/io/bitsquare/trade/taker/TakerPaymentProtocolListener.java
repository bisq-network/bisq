package io.bitsquare.trade.taker;

import io.bitsquare.msg.TradeMessage;

public interface TakerPaymentProtocolListener
{
    void onProgress(double progress);

    void onFailure(String failureMessage);

    void onDepositTxPublished(String depositTxID);

    void onBankTransferInited(TradeMessage tradeMessage);

    void onTradeCompleted(String hashAsString);
}
