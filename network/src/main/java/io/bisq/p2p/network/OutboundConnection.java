package io.bisq.p2p.network;

import io.bisq.payload.NodeAddress;

import java.net.Socket;

public class OutboundConnection extends Connection {
    public OutboundConnection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener, NodeAddress peersNodeAddress) {
        super(socket, messageListener, connectionListener, peersNodeAddress);
    }
}
