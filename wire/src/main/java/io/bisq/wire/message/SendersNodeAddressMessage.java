package io.bisq.wire.message;


import io.bisq.wire.payload.p2p.NodeAddress;

public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
