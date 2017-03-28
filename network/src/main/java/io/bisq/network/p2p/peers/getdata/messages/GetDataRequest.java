package io.bisq.network.p2p.peers.getdata.messages;

import io.bisq.network.p2p.Message;

import java.util.Set;

public interface GetDataRequest extends Message {
    int getNonce();

    Set<byte[]> getExcludedKeys();
}
