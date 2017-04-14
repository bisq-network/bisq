package io.bisq.network.p2p.network;

import io.bisq.common.network.NetworkProtoResolver;

import java.net.Socket;

public class InboundConnection extends Connection {
    public InboundConnection(Socket socket,
                             MessageListener messageListener,
                             ConnectionListener connectionListener,
                             NetworkProtoResolver networkProtoResolver) {
        super(socket, messageListener, connectionListener, null, networkProtoResolver);
    }
}
