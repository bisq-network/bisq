package io.bisq.network.p2p.network;

import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.network.p2p.NodeAddress;

import java.net.Socket;

public class OutboundConnection extends Connection {
    public OutboundConnection(Socket socket,
                              MessageListener messageListener,
                              ConnectionListener connectionListener,
                              NodeAddress peersNodeAddress,
                              NetworkProtoResolver networkProtoResolver) {
        super(socket, messageListener, connectionListener, peersNodeAddress, networkProtoResolver);
    }
}
