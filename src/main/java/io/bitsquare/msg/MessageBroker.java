package io.bitsquare.msg;

import net.tomp2p.peers.PeerAddress;

public interface MessageBroker
{
    void handleMessage(Object message, PeerAddress peerAddress);
}
