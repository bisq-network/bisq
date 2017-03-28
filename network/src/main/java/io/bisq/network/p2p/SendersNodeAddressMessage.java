package io.bisq.network.p2p;


public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
