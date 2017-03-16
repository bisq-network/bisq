package io.bisq.message;


import io.bisq.NodeAddress;

public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
