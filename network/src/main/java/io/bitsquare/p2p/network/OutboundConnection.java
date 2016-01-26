package io.bitsquare.p2p.network;

import io.bitsquare.p2p.NodeAddress;

import java.net.Socket;

public class OutboundConnection extends Connection {
    public OutboundConnection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener, NodeAddress peersNodeAddress) {
        super(socket, messageListener, connectionListener, peersNodeAddress);
    }
}
