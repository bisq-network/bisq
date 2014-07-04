package io.bitsquare.trade.protocol.taker;

import io.bitsquare.trade.Trade;

public interface TakerAsSellerProtocolListener
{
    void onDepositTxPublished(String depositTxId);

    void onBankTransferInited(String tradeId);

    void onPayoutTxPublished(Trade trade, String hashAsString);

    void onFault(Throwable throwable, TakerAsSellerProtocol.State state);

    void onWaitingForPeerResponse(TakerAsSellerProtocol.State state);

    void onCompleted(TakerAsSellerProtocol.State state);

    void onTakeOfferRequestRejected(Trade trade);
}
