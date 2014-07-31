package io.bitsquare.msg.listeners;

import java.util.Map;
import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;

public interface OrderBookListener
{
    @SuppressWarnings("UnusedParameters")
    void onOfferAdded(Data offerData, boolean success);

    void onOffersReceived(Map<Number640, Data> dataMap, boolean success);

    void onOfferRemoved(Data data, boolean success);
}