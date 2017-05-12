package io.bisq.network.p2p;


import io.bisq.common.network.NetworkEnvelope;

public interface SendersNodeAddressMsg extends NetworkEnvelope {
    NodeAddress getSenderNodeAddress();
}
