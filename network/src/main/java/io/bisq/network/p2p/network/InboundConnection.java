package io.bisq.network.p2p.network;

import java.net.Socket;

public class InboundConnection extends Connection {
    public InboundConnection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener) {
        super(socket, messageListener, connectionListener, null);
    }
}
