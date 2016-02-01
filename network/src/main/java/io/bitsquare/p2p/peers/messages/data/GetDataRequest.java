package io.bitsquare.p2p.peers.messages.data;

import io.bitsquare.p2p.Message;

public interface GetDataRequest extends Message {
    long getNonce();
}
