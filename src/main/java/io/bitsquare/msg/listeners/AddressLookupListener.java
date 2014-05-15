package io.bitsquare.msg.listeners;

import net.tomp2p.peers.PeerAddress;

public interface AddressLookupListener
{
    void onResult(PeerAddress peerAddress);

    void onFailed();
}
