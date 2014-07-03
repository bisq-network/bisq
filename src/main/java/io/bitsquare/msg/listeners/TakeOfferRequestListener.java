package io.bitsquare.msg.listeners;

import net.tomp2p.peers.PeerAddress;

public interface TakeOfferRequestListener
{
    void onTakeOfferRequested(String offerId, PeerAddress sender);
}
