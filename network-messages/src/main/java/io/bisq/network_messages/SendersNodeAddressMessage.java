package io.bisq.network_messages;


public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
