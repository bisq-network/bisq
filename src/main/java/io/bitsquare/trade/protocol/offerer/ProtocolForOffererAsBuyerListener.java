package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.TransactionConfidence;
import io.bitsquare.trade.Offer;

public interface ProtocolForOffererAsBuyerListener
{
    void onOfferAccepted(Offer offer);

    void onDepositTxPublished(String depositTxID);

    void onDepositTxConfirmedInBlockchain();

    void onDepositTxConfirmedUpdate(TransactionConfidence confidence);

    void onPayoutTxPublished(String payoutTxID);

    void onFault(Throwable throwable, ProtocolForOffererAsBuyer.State state);

    void onWaitingForPeerResponse(ProtocolForOffererAsBuyer.State state);

    void onCompleted(ProtocolForOffererAsBuyer.State state);

    void onWaitingForUserInteraction(ProtocolForOffererAsBuyer.State state);
}
