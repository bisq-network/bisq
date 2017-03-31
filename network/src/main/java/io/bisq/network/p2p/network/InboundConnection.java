package io.bisq.network.p2p.network;

import io.bisq.common.persistance.ProtobufferResolver;

import java.net.Socket;

public class InboundConnection extends Connection {
    public InboundConnection(Socket socket,
                             MessageListener messageListener,
                             ConnectionListener connectionListener,
                             ProtobufferResolver protobufferResolver) {
        super(socket, messageListener, connectionListener, null, protobufferResolver);
    }
}
