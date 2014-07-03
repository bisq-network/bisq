package io.bitsquare.trade.payment.taker.listeners;

import net.tomp2p.peers.PeerAddress;

public interface GetPeerAddressListener
{
    void onResult(PeerAddress peerAddress);

    void onFailed();
}
