package io.bisq.protobuffer.message;


import io.bisq.protobuffer.payload.p2p.NodeAddress;

public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
