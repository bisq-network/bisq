package io.bitsquare.trade.protocol.taker;

import io.bitsquare.trade.Trade;

public interface ProtocolForTakerAsSellerListener
{
    void onDepositTxPublished(String depositTxId);

    void onBankTransferInited(String tradeId);

    void onPayoutTxPublished(Trade trade, String hashAsString);

    void onFault(Throwable throwable, ProtocolForTakerAsSeller.State state);

    void onWaitingForPeerResponse(ProtocolForTakerAsSeller.State state);

    void onCompleted(ProtocolForTakerAsSeller.State state);

    void onTakeOfferRequestRejected(Trade trade);
}
