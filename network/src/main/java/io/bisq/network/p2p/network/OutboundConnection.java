package io.bisq.network.p2p.network;

import io.bisq.protobuffer.payload.p2p.NodeAddress;

import java.net.Socket;

public class OutboundConnection extends Connection {
    public OutboundConnection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener, NodeAddress peersNodeAddress) {
        super(socket, messageListener, connectionListener, peersNodeAddress);
    }
}
