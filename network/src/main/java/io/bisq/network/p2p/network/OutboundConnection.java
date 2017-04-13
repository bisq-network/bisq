package io.bisq.network.p2p.network;

import io.bisq.common.persistance.ProtobufferResolver;
import io.bisq.network.p2p.NodeAddress;

import java.net.Socket;

public class OutboundConnection extends Connection {
    public OutboundConnection(Socket socket,
                              MessageListener messageListener,
                              ConnectionListener connectionListener,
                              NodeAddress peersNodeAddress,
                              ProtobufferResolver protobufferResolver) {
        super(socket, messageListener, connectionListener, peersNodeAddress, protobufferResolver);
    }
}
