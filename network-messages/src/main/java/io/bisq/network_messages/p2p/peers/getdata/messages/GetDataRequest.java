package io.bisq.network_messages.p2p.peers.getdata.messages;

import io.bisq.network_messages.Message;

import java.util.Set;

public interface GetDataRequest extends Message {
    int getNonce();

    Set<byte[]> getExcludedKeys();
}
