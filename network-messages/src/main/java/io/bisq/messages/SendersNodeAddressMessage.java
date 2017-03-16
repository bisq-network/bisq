package io.bisq.messages;


public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
