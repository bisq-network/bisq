package io.bitsquare.p2p.network.messages;

import io.bitsquare.messages.Message;
import io.bitsquare.p2p.NodeAddress;

public interface SendersNodeAddressMessage extends Message {
    NodeAddress getSenderNodeAddress();
}
