package io.bitsquare.p2p.peers.getdata.messages;

import io.bitsquare.p2p.Message;

public interface GetDataRequest extends Message {
    int getNonce();
}
