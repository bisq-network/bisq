package io.bitsquare.trade.payment.taker;

import io.bitsquare.trade.Trade;

public interface TakerAsSellerProtocolListener
{
    void onDepositTxPublished(String depositTxId);

    void onBankTransferInited(String tradeId);

    void onTradeCompleted(Trade trade, String hashAsString);
}
