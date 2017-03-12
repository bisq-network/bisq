package io.bisq.p2p.network.messages;

import io.bisq.messages.Message;
import io.bisq.p2p.NodeAddress;

public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
