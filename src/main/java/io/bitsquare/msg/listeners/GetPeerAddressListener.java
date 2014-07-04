package io.bitsquare.msg.listeners;

import net.tomp2p.peers.PeerAddress;

public interface GetPeerAddressListener
{
    void onResult(PeerAddress peerAddress);

    void onFailed();
}
