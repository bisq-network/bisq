package io.bitsquare.p2p.peers.getdata.messages;

import io.bitsquare.messages.Message;

import java.util.Set;

public interface GetDataRequest extends Message {
    int getNonce();

    Set<byte[]> getExcludedKeys();
}
