package io.bisq.network.p2p;


import io.bisq.common.proto.network.NetworkEnvelope;

public interface SendersNodeAddressMessage extends NetworkEnvelope {
    NodeAddress getSenderNodeAddress();
}
