package io.bisq.network.p2p;


public interface SendersNodeAddressMsg extends Msg {
    NodeAddress getSenderNodeAddress();
}
