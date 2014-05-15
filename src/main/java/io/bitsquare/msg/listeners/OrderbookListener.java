package io.bitsquare.msg.listeners;

import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

import java.util.Map;

public interface OrderBookListener
{
    void onOfferAdded(Data offerData, boolean success);

    void onOffersReceived(Map<Number160, Data> dataMap, boolean success);

    void onOfferRemoved(Data data, boolean success);

}
