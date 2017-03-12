package io.bisq.p2p.peers.getdata.messages;

import io.bisq.messages.Message;

import java.util.Set;

public interface GetDataRequest extends Message {
    int getNonce();

    Set<byte[]> getExcludedKeys();
}
