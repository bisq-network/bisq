package io.bitsquare.msg;

import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

import java.util.EventListener;
import java.util.Map;

public interface MessageListener extends EventListener
{
    void onMessage(Object message);

    void onPing();

    void onOfferPublished(boolean success);

    void onSendFailed();

    void onResponseFromSend(Object response);

    void onPeerFound();

    void onOffersReceived(Map<Number160, Data> dataMap, boolean success);

    void onOfferRemoved(boolean success);
}
