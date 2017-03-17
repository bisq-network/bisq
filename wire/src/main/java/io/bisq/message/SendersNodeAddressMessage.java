package io.bisq.message;


import io.bisq.payload.NodeAddress;

public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
