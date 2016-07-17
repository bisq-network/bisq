package io.bitsquare.p2p.network.connection;

import java.net.Socket;

public class InboundConnection extends Connection {
    public InboundConnection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener) {
        super(socket, messageListener, connectionListener, null);
    }
}
